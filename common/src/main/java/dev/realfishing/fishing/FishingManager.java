package dev.realfishing.fishing;

import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import dev.realfishing.RealFishing;
import dev.realfishing.data.FishingSavedData;
import dev.realfishing.entity.BobberEntity;
import dev.realfishing.item.FloatRodItem;
import dev.realfishing.net.CastC2S;
import dev.realfishing.registry.ModEntities;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 服务端钓鱼会话驱动：蓄力 → 抛投 → 等漂，负责状态迁移、浮漂生成/回收与会话落盘。
 * 不负责：客户端表现（HUD/渲染在 client 包）、鱼线求解（M2）、鱼与搏鱼（M3+）。
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

    private static MinecraftServer boundServer;
    private static boolean restored;

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
        if (session.state == FishingState.WAITING || session.state == FishingState.CASTING) {
            // 再按右键＝收竿（M1 的"收回"路径，M3 再换成真正的收线）
            cleanup(player, session, now);
            return;
        }
        session.transition(FishingState.CHARGING, now);
        session.charge = 0.0F;
        session.dimensionId = dimension(player);
        data.markDirty();
    }

    /** 处理 CastC2S：所有字段都要重新校验，绝不信任客户端。 */
    public static void cast(ServerPlayer player, CastC2S msg) {
        FishingSavedData data = data(player.server);
        long now = gameTime(player.server);
        FishingSession session = data.get(player.getUUID());
        if (session == null || session.state != FishingState.CHARGING) {
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
        RealFishing.LOGGER.info("[M1] cast accepted: player={} charge={} speed={}",
                player.getGameProfile().getName(), charge, CastMath.speed(charge));
    }

    private static void tick(MinecraftServer server) {
        if (server != boundServer) {
            boundServer = server;
            restored = false;
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
        switch (session.state) {
            case CASTING -> {
                if (bobber == null) {
                    reset(player, session, now);
                } else if (bobber.isLanded()) {
                    session.lineLength = lineLength(player, bobber);
                    session.transition(FishingState.WAITING, now);
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
                }
            }
            default -> {
            }
        }
    }

    private static void cleanup(ServerPlayer player, FishingSession session, long now) {
        discardBobber(player, session);
        session.reset(now);
        data(player.server).markDirty();
    }

    private static void reset(ServerPlayer player, FishingSession session, long now) {
        session.reset(now);
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
