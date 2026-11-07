package dev.realfishing.fishing;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.architectury.networking.NetworkManager;
import dev.realfishing.RealFishing;
import dev.realfishing.data.FishEntry;
import dev.realfishing.data.FishTable;
import dev.realfishing.data.FishingSavedData;
import dev.realfishing.entity.BobberEntity;
import dev.realfishing.item.FloatRodItem;
import dev.realfishing.net.SessionS2C;
import dev.realfishing.registry.ModEntities;
import dev.realfishing.registry.ModSounds;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 服务端钓鱼会话驱动：蓄力 → 抛投 → 等漂 → 咬钩 → 搏鱼 → 上鱼/断线。
 * 不负责：客户端表现（HUD/渲染在 client 包）、鱼线求解（physics 包）、鱼表数据（M4）。
 *
 * <p>所有入口都做了"玩家是否真的拿着台钓竿"的校验；客户端发来的视角若与服务端偏差
 * 超过 40°，整包丢弃，避免伪造落点。
 */
public final class FishingManager {
    /** CASTING 阶段最长飞行时间（tick），超时强制回收（SPEC §9.2 的 5 秒）。 */
    private static final int CAST_TIMEOUT_TICKS = 100;
    /** 蓄力到松手至少要这么久，防止 0 tick 连点。 */
    private static final int MIN_CHARGE_TICKS = 3;
    /** 客户端视角与服务端视角允许的最大偏差（度）。 */
    private static final float MAX_AIM_DELTA = 40.0F;
    /** 咬钩窗口 0.8 s（SPEC §9.2）。 */
    private static final int BITE_WINDOW_TICKS = 16;
    /** 上鱼收竿动画 0.8 s。 */
    private static final int LANDING_TICKS = 16;
    /** 断线后保留 SNAPPED 状态 2 s 再复位。 */
    private static final int SNAPPED_TICKS = 40;
    /** 等漂 3–15 s。 */
    private static final int BITE_MIN_TICKS = 60;
    private static final int BITE_MAX_TICKS = 300;
    /** 10 Hz 会话同步。 */
    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final double FISH_MAX_RANGE = 40.0;

    private static MinecraftServer boundServer;
    private static boolean restored;
    private static final Random RANDOM = new Random();
    private static final Map<UUID, FightModel> FIGHTS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_BITE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SYNC = new HashMap<>();

    private FishingManager() {
    }

    public static void register() {
        TickEvent.SERVER_POST.register(FishingManager::tick);
        PlayerEvent.PLAYER_QUIT.register(FishingManager::onQuit);
    }

    public static void startCharging(ServerPlayer player) {
        FishingSavedData data = data(player.server);
        long now = gameTime(player.server);
        FishingSession session = data.getOrCreate(player.getUUID());
        if (session.state == FishingState.CHARGING) {
            return;
        }
        // 搏鱼/刺鱼期间右键是"收线"，不能把会话打回蓄力。
        if (session.state == FishingState.FIGHTING || session.state == FishingState.BITE
                || session.state == FishingState.LANDING) {
            return;
        }
        if (session.state != FishingState.IDLE) {
            // 再按右键：先把上一竿收回来，然后同一按继续蓄力——
            // 否则玩家会以为"按了没反应"（只能收竿、不能接着抛）。
            cleanup(player, session, now);
            session = data.get(player.getUUID());
            if (session == null) {
                return;
            }
        }
        session.transition(FishingState.CHARGING, now);
        session.charge = 0.0F;
        session.dimensionId = dimension(player);
        data.markDirty();
    }

    /** 处理 CastC2S：所有字段都要重新校验，绝不信任客户端。 */
    public static void cast(ServerPlayer player, dev.realfishing.net.CastC2S msg) {
        FishingSavedData data = data(player.server);
        long now = gameTime(player.server);
        FishingSession session = data.get(player.getUUID());
        if (session == null || session.state != FishingState.CHARGING) {
            RealFishing.LOGGER.info("[M1] cast rejected: session={} state={}",
                    session != null, session == null ? null : session.state);
            return;
        }
        if (!holdingRod(player)) {
            session.reset(now);
            data.markDirty();
            return;
        }
        if (now - session.stateSince < MIN_CHARGE_TICKS) {
            return;
        }
        if (!Float.isFinite(msg.charge()) || !Float.isFinite(msg.yaw()) || !Float.isFinite(msg.pitch())) {
            return;
        }
        float charge = Mth.clamp(msg.charge(), 0.0F, 1.0F);
        if (charge < CastMath.MIN_CHARGE) {
            session.reset(now);
            data.markDirty();
            return;
        }
        if (Math.abs(Mth.wrapDegrees(msg.yaw() - player.getYRot())) > MAX_AIM_DELTA
                || Math.abs(msg.pitch() - player.getXRot()) > MAX_AIM_DELTA) {
            return;
        }

        ServerLevel level = player.serverLevel();
        discardBobber(player, session);
        Vec3 dir = CastMath.direction(msg.yaw(), msg.pitch());
        Vec3 start = player.getEyePosition().add(dir.scale(0.4));

        BobberEntity bobber = new BobberEntity(ModEntities.BOBBER.get(), level);
        bobber.setOwner(player.getUUID());
        bobber.moveTo(start.x, start.y, start.z, msg.yaw(), msg.pitch());
        bobber.launch(dir.scale(CastMath.speed(charge)));
        if (!level.addFreshEntity(bobber)) {
            session.reset(now);
            data.markDirty();
            return;
        }

        session.sessionId++;
        session.charge = charge;
        session.bobberEntityId = bobber.getId();
        session.castGameTime = now;
        session.dimensionId = dimension(player);
        session.lineLength = 0.0;
        session.transition(FishingState.CASTING, now);
        data.markDirty();
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.CAST.get(), player.getSoundSource(), 1.0F, 1.4F);
        RealFishing.LOGGER.info("[M1] cast accepted: player={} charge={} speed={}",
                player.getGameProfile().getName(), charge, CastMath.speed(charge));
    }

    /** 收线键（BITE 期间按下 = 刺鱼，FIGHTING 期间 = 收/放）。 */
    public static void reel(ServerPlayer player, boolean reeling) {
        FishingSession session = data(player.server).get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.state == FishingState.BITE && reeling) {
            startFight(player, session);
            return;
        }
        if (session.state == FishingState.FIGHTING) {
            session.reeling = reeling;
        }
    }

    /** 抬竿：短时加张力、把鱼往回收一点。 */
    public static void pump(ServerPlayer player) {
        FishingSession session = data(player.server).get(player.getUUID());
        if (session == null || session.state != FishingState.FIGHTING) {
            return;
        }
        FightModel fight = FIGHTS.get(player.getUUID());
        if (fight != null) {
            fight.pump();
        }
    }

    /** 调泄力（客户端已经 clamp 过，这里再 clamp 一次）。 */
    public static void drag(ServerPlayer player, float drag) {
        FishingSession session = data(player.server).get(player.getUUID());
        if (session == null || !Float.isFinite(drag)) {
            return;
        }
        session.drag = Mth.clamp(drag, 0.0, 1.0);
    }

    private static void startFight(ServerPlayer player, FishingSession session) {
        long now = gameTime(player.server);
        FishEntry fish = fishEntry(session);
        int tier = fish == null ? pickTier() : tierOf(fish);
        long seed = player.getUUID().getLeastSignificantBits() ^ now;
        FightModel fight = fish == null
                ? FightModel.ofTier(tier, seed)
                : FightModel.ofFish(fish.strength(), fish.staminaSeconds(), fish.turnRate(), seed);
        fight.setLineLength(Math.max(FightModel.MIN_LINE, session.lineLength));
        FIGHTS.put(player.getUUID(), fight);
        session.fishTier = tier;
        session.fishStamina = 1.0;
        session.tension = 0.0;
        session.reeling = false;
        session.drag = 0.2;
        session.transition(FishingState.FIGHTING, now);
        sync(player, session);
        RealFishing.LOGGER.info("[M3] fight start: player={} fish={} tier={} line={}",
                player.getGameProfile().getName(), session.fishId, tier,
                String.format("%.2f", session.lineLength));
    }

    /** 咬钩时选鱼：环境 + 竿型 + 饵系数，SPEC §13.2。 */
    private static FishEntry pickFish(ServerPlayer player, FishingSession session, BobberEntity bobber, long now) {
        ServerLevel level = player.serverLevel();
        long seed = now ^ player.getUUID().getLeastSignificantBits() ^ bobber.blockPosition().asLong();
        return FishTable.pick(level, bobber.blockPosition(), session.rodStyle, bait(player),
                RandomSource.create(seed), level.getDayTime()).orElse(null);
    }

    private static FishEntry fishEntry(FishingSession session) {
        if (session.fishId == null || session.fishId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(session.fishId);
        return id == null ? null : FishTable.byId(id).orElse(null);
    }

    /** 鱼档只用于 HUD 的粗略提示；真实手感来自鱼表的 strength/stamina/turn_rate。 */
    private static int tierOf(FishEntry fish) {
        if (fish.strength() < 0.4F) {
            return FightModel.TIER_SMALL;
        }
        if (fish.strength() < 0.6F) {
            return FightModel.TIER_MEDIUM;
        }
        return FightModel.TIER_BIG;
    }

    /** 饵放在副手；没有副手物品就是空钩。 */
    private static ItemStack bait(ServerPlayer player) {
        return player.getOffhandItem();
    }

    private static int pickTier() {
        int roll = RANDOM.nextInt(100);
        if (roll < 55) {
            return FightModel.TIER_SMALL;
        }
        if (roll < 90) {
            return FightModel.TIER_MEDIUM;
        }
        return FightModel.TIER_BIG;
    }

    private static void tick(MinecraftServer server) {
        if (server != boundServer) {
            boundServer = server;
            restored = false;
            FIGHTS.clear();
            NEXT_BITE.clear();
            NEXT_SYNC.clear();
        }
        FishingSavedData data = data(server);
        long now = gameTime(server);
        if (!restored) {
            restored = true;
            restoreSessions(data, now);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FishingSession session = data.get(player.getUUID());
            if (session != null && session.state != FishingState.IDLE) {
                tickSession(player, session, now);
            }
        }
    }

    /** 重启后会话里的浮漂一定不在了（实体 noSave），统一复位成 IDLE，避免幽灵状态。 */
    private static void restoreSessions(FishingSavedData data, long now) {
        for (FishingSession session : data.sessions()) {
            if (session.state != FishingState.IDLE) {
                session.reset(now);
            }
        }
        data.markDirty();
    }

    private static void tickSession(ServerPlayer player, FishingSession session, long now) {
        if (!dimension(player).equals(session.dimensionId) || player.isDeadOrDying() || !holdingRod(player)) {
            cleanup(player, session, now);
            return;
        }
        BobberEntity bobber = bobber(player, session);
        UUID id = player.getUUID();
        switch (session.state) {
            case CASTING -> {
                if (bobber == null) {
                    reset(player, session, now);
                } else if (bobber.isLanded()) {
                    session.lineLength = lineLength(player, bobber);
                    session.transition(FishingState.WAITING, now);
                    NEXT_BITE.put(id, now + BITE_MIN_TICKS + RANDOM.nextInt(BITE_MAX_TICKS - BITE_MIN_TICKS));
                    data(player.server).markDirty();
                    RealFishing.LOGGER.info("[M1] landed: player={} line={}", player.getGameProfile().getName(),
                            String.format("%.2f", session.lineLength));
                } else if (now - session.castGameTime > CAST_TIMEOUT_TICKS) {
                    cleanup(player, session, now);
                }
            }
            case WAITING -> {
                if (bobber == null) {
                    reset(player, session, now);
                    return;
                }
                session.lineLength = lineLength(player, bobber);
                if (session.lineLength > CastMath.MAX_LINE_LENGTH) {
                    cleanup(player, session, now);
                    return;
                }
                if (now >= NEXT_BITE.getOrDefault(id, now + BITE_MIN_TICKS)) {
                    FishEntry fish = pickFish(player, session, bobber, now);
                    if (fish == null) {
                        // 没有候选鱼（群系/时段/水深/饵都不匹配）：空钩，重掷计时。
                        NEXT_BITE.put(id, now + BITE_MIN_TICKS + RANDOM.nextInt(BITE_MAX_TICKS - BITE_MIN_TICKS));
                    } else {
                        session.fishId = fish.id().toString();
                        session.transition(FishingState.BITE, now);
                        bobber.splash();
                        player.serverLevel().playSound(null, bobber.blockPosition(), ModSounds.BITE.get(),
                                player.getSoundSource(), 1.0F, 1.0F);
                        sync(player, session);
                        RealFishing.LOGGER.info("[M4] bite: player={} fish={}",
                                player.getGameProfile().getName(), fish.id());
                    }
                }
            }
            case BITE -> {
                if (bobber == null) {
                    reset(player, session, now);
                    return;
                }
                if (now - session.stateSince > BITE_WINDOW_TICKS) {
                    // 错过窗口：假咬，回到等漂，重新计时。
                    session.transition(FishingState.WAITING, now);
                    NEXT_BITE.put(id, now + BITE_MIN_TICKS + RANDOM.nextInt(BITE_MAX_TICKS - BITE_MIN_TICKS));
                    sync(player, session);
                }
            }
            case FIGHTING -> tickFighting(player, session, bobber, now);
            case LANDING -> {
                if (now - session.stateSince >= LANDING_TICKS) {
                    award(player, session);
                    cleanup(player, session, now);
                }
            }
            case SNAPPED -> {
                if (now - session.stateSince >= SNAPPED_TICKS) {
                    cleanup(player, session, now);
                }
            }
            default -> {
            }
        }
    }

    private static void tickFighting(ServerPlayer player, FishingSession session, BobberEntity bobber, long now) {
        UUID id = player.getUUID();
        FightModel fight = FIGHTS.get(id);
        if (bobber == null || fight == null) {
            endWithSnap(player, session, now, false);
            return;
        }
        double dt = 0.05;
        fight.step(dt, player.getX(), player.getZ(), bobber.getX(), bobber.getZ(),
                session.reeling, session.drag);

        // 鱼拖动浮漂：新位置不是水就掉头，避免鱼"游上岸"。
        double nx = bobber.getX() + fight.vx() * dt;
        double nz = bobber.getZ() + fight.vz() * dt;
        BlockPos next = BlockPos.containing(nx, bobber.getY(), nz);
        boolean water = !player.serverLevel().getFluidState(next).isEmpty();
        if (water) {
            double dist = Math.sqrt(player.distanceToSqr(nx, player.getY(), nz));
            if (dist <= FISH_MAX_RANGE) {
                bobber.setPos(nx, bobber.getY(), nz);
            }
        } else {
            fight.bounce();
        }

        session.tension = fight.tension();
        session.fishStamina = fight.stamina();
        session.lineLength = fight.lineLength();

        if (fight.isSnapped()) {
            endWithSnap(player, session, now, true);
            return;
        }
        // 只比水平距离：玩家站在岸上时浮漂可能低 2~3 格，用 3D 距离会永远够不着。
        // 线收到最短也算"到脚边"——岸边有落差时浮漂被水岸挡住，水平距离可能永远 > 2.5。
        double dx = player.getX() - bobber.getX();
        double dz = player.getZ() - bobber.getZ();
        boolean close = dx * dx + dz * dz < 6.25 || fight.lineLength() <= FightModel.MIN_LINE + 0.1;
        if (fight.stamina() <= 0.0 && session.tension < 0.6 && close) {
            session.transition(FishingState.LANDING, now);
            bobber.splash();
            player.serverLevel().playSound(null, bobber.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                    player.getSoundSource(), 1.0F, 1.2F);
            sync(player, session);
            data(player.server).markDirty();
            return;
        }

        Long nextSync = NEXT_SYNC.get(id);
        if (nextSync == null || now >= nextSync) {
            NEXT_SYNC.put(id, now + SYNC_INTERVAL_TICKS);
            sync(player, session);
        }
    }

    private static void endWithSnap(ServerPlayer player, FishingSession session, long now, boolean snapped) {
        if (snapped) {
            ServerLevel level = player.serverLevel();
            Vec3 at = session.bobberEntityId >= 0 && level.getEntity(session.bobberEntityId) != null
                    ? level.getEntity(session.bobberEntityId).position()
                    : player.position();
            level.playSound(null, BlockPos.containing(at), SoundEvents.ITEM_BREAK, player.getSoundSource(), 1.0F, 0.8F);
            level.playSound(null, BlockPos.containing(at), ModSounds.SNAP.get(), player.getSoundSource(), 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.05);
            RealFishing.LOGGER.info("[M3] line snapped: player={} tension={}",
                    player.getGameProfile().getName(), String.format("%.2f", session.tension));
        }
        session.transition(FishingState.SNAPPED, now);
        discardBobber(player, session);
        FIGHTS.remove(player.getUUID());
        sync(player, session);
        data(player.server).markDirty();
    }

    private static void award(ServerPlayer player, FishingSession session) {
        FishEntry fish = fishEntry(session);
        if (fish == null) {
            // 没有鱼表数据（旧存档/调试），退回按档位给原版鱼，保证不会空手。
            ItemStack fallback = switch (session.fishTier) {
                case FightModel.TIER_SMALL -> new ItemStack(Items.COD);
                case FightModel.TIER_BIG -> new ItemStack(Items.COD, 3);
                default -> new ItemStack(Items.SALMON);
            };
            give(player, fallback);
            player.giveExperiencePoints(1 + RANDOM.nextInt(6));
            return;
        }
        RandomSource random = RandomSource.create(player.serverLevel().getRandom().nextLong());
        FishEntry.addLoot(fish, random, stack -> give(player, stack));
        int xp = fish.minXp() + random.nextInt(Math.max(1, fish.maxXp() - fish.minXp() + 1));
        player.giveExperiencePoints(xp);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.CATCH.get(), player.getSoundSource(), 1.0F, 1.2F);
        // 用掉的饵：只在咬钩时匹配到的饵才消耗，避免"空钩也吃饵"。
        ItemStack bait = bait(player);
        if (!bait.isEmpty() && fish.baitMultiplier(bait) > 0.5F) {
            bait.shrink(1);
        }
        RealFishing.LOGGER.info("[M4] landed fish: player={} fish={} xp={}",
                player.getGameProfile().getName(), fish.id(), xp);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void sync(ServerPlayer player, FishingSession session) {
        Entity entity = session.bobberEntityId >= 0 ? player.serverLevel().getEntity(session.bobberEntityId) : null;
        Vec3 p = entity != null ? entity.position() : player.position();
        SessionS2C msg = SessionS2C.of(session.sessionId, session.state, session.tension, session.fishStamina,
                session.lineLength, p.x, p.y, p.z);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        msg.write(buf);
        NetworkManager.sendToPlayer(player, SessionS2C.ID, buf);
    }

    /** 调试用：直接进入搏鱼，跳过等漂（M4 接上鱼表前用于手感调参）。 */
    public static boolean debugStartFight(ServerPlayer player, int tier) {
        FishingSavedData data = data(player.server);
        FishingSession session = data.get(player.getUUID());
        if (session == null || session.state == FishingState.IDLE) {
            return false;
        }
        if (session.bobberEntityId < 0) {
            return false;
        }
        long now = gameTime(player.server);
        FightModel fight = FightModel.ofTier(tier, now);
        fight.setLineLength(Math.max(FightModel.MIN_LINE, session.lineLength));
        FIGHTS.put(player.getUUID(), fight);
        session.fishTier = tier;
        session.fishStamina = 1.0;
        session.tension = 0.0;
        session.drag = 0.2;
        session.transition(FishingState.FIGHTING, now);
        sync(player, session);
        data.markDirty();
        return true;
    }

    private static void cleanup(ServerPlayer player, FishingSession session, long now) {
        RealFishing.LOGGER.info("[M3] cleanup: player={} from={} bobber={} held={}",
                player.getGameProfile().getName(), session.state, session.bobberEntityId,
                player.getMainHandItem().getItem());
        discardBobber(player, session);
        session.reset(now);
        FIGHTS.remove(player.getUUID());
        NEXT_BITE.remove(player.getUUID());
        NEXT_SYNC.remove(player.getUUID());
        data(player.server).markDirty();
        // 收竿/上鱼/断线都要推一次 IDLE，否则客户端 HUD 会停在最后一个状态。
        SessionS2C idle = SessionS2C.of(session.sessionId, FishingState.IDLE, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        FriendlyByteBuf idleBuf = new FriendlyByteBuf(Unpooled.buffer());
        idle.write(idleBuf);
        NetworkManager.sendToPlayer(player, SessionS2C.ID, idleBuf);
    }

    private static void reset(ServerPlayer player, FishingSession session, long now) {
        session.reset(now);
        FIGHTS.remove(player.getUUID());
        NEXT_BITE.remove(player.getUUID());
        data(player.server).markDirty();
    }

    private static void discardBobber(ServerPlayer player, FishingSession session) {
        if (session.bobberEntityId < 0) {
            return;
        }
        Entity entity = player.serverLevel().getEntity(session.bobberEntityId);
        if (entity != null) {
            entity.discard();
        }
        session.bobberEntityId = -1;
    }

    private static BobberEntity bobber(ServerPlayer player, FishingSession session) {
        if (session.bobberEntityId < 0) {
            return null;
        }
        Entity entity = player.serverLevel().getEntity(session.bobberEntityId);
        return entity instanceof BobberEntity bobber && !bobber.isRemoved() ? bobber : null;
    }

    private static double lineLength(ServerPlayer player, BobberEntity bobber) {
        return Math.min(Math.sqrt(player.distanceToSqr(bobber)), CastMath.MAX_LINE_LENGTH);
    }

    private static boolean holdingRod(ServerPlayer player) {
        return isRod(player.getMainHandItem()) || isRod(player.getOffhandItem());
    }

    private static boolean isRod(ItemStack stack) {
        return stack.getItem() instanceof FloatRodItem;
    }

    private static void onQuit(ServerPlayer player) {
        if (boundServer == null || player.server != boundServer) {
            return;
        }
        FishingSavedData data = data(boundServer);
        FishingSession session = data.get(player.getUUID());
        if (session != null) {
            discardBobber(player, session);
            session.reset(gameTime(boundServer));
            data.remove(player.getUUID());
            data.markDirty();
        }
        FIGHTS.remove(player.getUUID());
        NEXT_BITE.remove(player.getUUID());
        NEXT_SYNC.remove(player.getUUID());
    }

    private static String dimension(ServerPlayer player) {
        return player.serverLevel().dimension().location().toString();
    }

    private static FishingSavedData data(MinecraftServer server) {
        return FishingSavedData.get(server.overworld());
    }

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }
}
