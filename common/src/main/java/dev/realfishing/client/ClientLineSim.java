package dev.realfishing.client;

import dev.realfishing.RealFishing;
import dev.realfishing.entity.BobberEntity;
import dev.realfishing.physics.FishingLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端鱼线：跟踪本地玩家的浮漂，用 24 质点求解器把竿尖和浮漂连起来。
 * 不负责：张力判定与断线（服务端权威，M3 起用会话里的 line 结果覆盖视觉）。
 *
 * <p>只做视觉积分，输入是手柄位置 + 浮漂位置，因此服务器延迟不会让线断开。
 */
public final class ClientLineSim {
    private static final FishingLine LINE = new FishingLine();
    private static BobberEntity tracked;
    private static boolean active;
    private static float tension;
    private static Vec3 smoothTip;
    private static boolean degraded;
    private static boolean loggedFailure;
    private static boolean loggedTip;

    private ClientLineSim() {
    }

    /** 当前可渲染的线；没有活跃浮漂时为 null。 */
    public static FishingLine line() {
        return active ? LINE : null;
    }

    public static boolean debugMarker() {
        return Boolean.getBoolean("realfishing.marker");
    }

    public static float tension() {
        return tension;
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            drop();
            return;
        }
        BobberEntity bobber = findOwnBobber(player, level);
        if (bobber == null) {
            drop();
            return;
        }
        Vec3 tip = rodTip(player);
        // 竿尖带一点惯性：玩家走动/转身时线不会瞬间甩过去
        smoothTip = smoothTip == null ? tip : smoothTip.lerp(tip, 0.35);
        Vec3 end = bobber.position().add(0.0, 0.12, 0.0);
        if (!active || tracked != bobber) {
            LINE.reset(smoothTip, end);
            tracked = bobber;
            active = true;
            tension = 0.0F;
        }
        if (degraded) {
            // 降级：不再积分，直接一条直线，保证画面还有线、日志只记一次。
            LINE.reset(smoothTip, end);
            tension = 0.0F;
            return;
        }
        LINE.pinStart(smoothTip.x, smoothTip.y, smoothTip.z);
        LINE.pinEnd(end.x, end.y, end.z);
        // 抛投阶段线在出线：长度跟着两端距离长，避免固定长度被拉伸误判为断线。
        LINE.setLength(Math.max(LINE.length(), smoothTip.distanceTo(end)));
        try {
            LINE.step(0.05, Vec3.ZERO, new LevelSampler(level, bobber));
        } catch (RuntimeException exception) {
            if (!loggedFailure) {
                loggedFailure = true;
                RealFishing.LOGGER.error("[M2] 鱼线求解异常，降级为直线", exception);
            }
            degraded = true;
            LINE.reset(smoothTip, end);
            return;
        }
        tension = (float) LINE.tension();
        if (LINE.isSnapped()) {
            drop();
        }
    }

    private static void drop() {
        active = false;
        tracked = null;
        tension = 0.0F;
        smoothTip = null;
        degraded = false;
    }

    private static BobberEntity findOwnBobber(LocalPlayer player, ClientLevel level) {
        AABB box = player.getBoundingBox().inflate(64.0);
        BobberEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BobberEntity candidate : level.getEntitiesOfClass(BobberEntity.class, box,
                entity -> entity.owner().map(player.getUUID()::equals).orElse(false))) {
            double distance = candidate.distanceToSqr(player);
            // 预测替身优先：它就是玩家松手那一刻看到的漂。
            if (candidate.isPredicted()) {
                return candidate;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 第一人称近似：用相机基向量估算竿尖（右手、视线前方约 1 米、略高）。 */
    private static Vec3 rodTip(LocalPlayer player) {
        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 look = new Vec3(camera.getLookVector());
        Vec3 right = new Vec3(camera.getLeftVector()).scale(-1.0);
        if (!loggedTip) {
            loggedTip = true;
            RealFishing.LOGGER.info("[M2] camera look={} left={} right={} pos={}",
                    look, camera.getLeftVector(), right, camera.getPosition());
        }
        return camera.getPosition()
                .add(look.scale(0.45))
                .add(right.scale(0.88))
                .add(0.0, 0.03, 0.0);
    }

    /** 用浮漂所在水域近似水面（线只在漂附近入水），地面用高度图。 */
    private record LevelSampler(Level level, BobberEntity bobber) implements FishingLine.Sampler {
        @Override
        public double fluidTop(double x, double z) {
            if (!bobber.isInWaterState()) {
                return Double.NEGATIVE_INFINITY;
            }
            BlockPos pos = BlockPos.containing(x, bobber.getY(), z);
            if (level.getFluidState(pos).is(FluidTags.WATER)) {
                return pos.getY() + 0.9;
            }
            return bobber.getY();
        }

        @Override
        public double groundY(double x, double z) {
            BlockPos pos = BlockPos.containing(x, bobber.getY() + 8.0, z);
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        }
    }
}
