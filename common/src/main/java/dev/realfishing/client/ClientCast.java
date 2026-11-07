package dev.realfishing.client;

import dev.realfishing.entity.BobberEntity;
import dev.realfishing.fishing.CastMath;
import dev.realfishing.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端抛投预测：松手瞬间先本地生成一只"替身浮漂"，让手感不等 RTT；
 * 服务器实体同步到达后丢弃替身，位置由原版实体插值接管。
 * 不负责：物理（替身与服务器浮漂跑同一套确定性积分）、状态机。
 */
public final class ClientCast {
    private static final Map<UUID, BobberEntity> PREDICTED = new HashMap<>();

    private ClientCast() {
    }

    public static void spawnPrediction(Player player, float charge) {
        if (!(player.level() instanceof ClientLevel level)) {
            return;
        }
        Vec3 dir = CastMath.direction(player.getYRot(), player.getXRot());
        BobberEntity bobber = new BobberEntity(ModEntities.BOBBER.get(), level);
        bobber.setPredicted(true);
        bobber.setOwner(player.getUUID());
        Vec3 start = player.getEyePosition().add(dir.scale(0.4));
        bobber.moveTo(start.x, start.y, start.z, player.getYRot(), player.getXRot());
        bobber.launch(dir.scale(CastMath.speed(charge)));
        level.addFreshEntity(bobber);

        BobberEntity old = PREDICTED.put(player.getUUID(), bobber);
        if (old != null) {
            old.discard();
        }
    }

    /** 该玩家是否正有一只本地预测的替身浮漂在飞（渲染器据此隐藏服务器实体，避免双漂/回跳）。 */
    public static boolean hasPrediction(UUID owner) {
        BobberEntity ghost = PREDICTED.get(owner);
        return ghost != null && !ghost.isRemoved();
    }

    public static void clientTick(Minecraft minecraft) {
        if (PREDICTED.isEmpty() || minecraft.level == null) {
            return;
        }
        Iterator<Map.Entry<UUID, BobberEntity>> iterator = PREDICTED.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BobberEntity> entry = iterator.next();
            BobberEntity ghost = entry.getValue();
            if (ghost.isRemoved() || ghost.level() != minecraft.level) {
                iterator.remove();
                continue;
            }
            List<BobberEntity> authoritative = minecraft.level.getEntitiesOfClass(
                    BobberEntity.class,
                    ghost.getBoundingBox().inflate(6.0),
                    other -> other != ghost && other.owner().map(entry.getKey()::equals).orElse(false));
            // 服务器实体到达后最多再让替身领先 5 tick：替换瞬间两者位置差很小，
            // 又不会出现"两个浮漂并排"的画面。
            if (!authoritative.isEmpty() && ghost.tickCount > 5) {
                ghost.discard();
                iterator.remove();
                }
        }
    }
}
