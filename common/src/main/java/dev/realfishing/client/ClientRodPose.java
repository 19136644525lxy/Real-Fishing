package dev.realfishing.client;

import dev.realfishing.fishing.CastMath;
import dev.realfishing.fishing.FishingState;
import dev.realfishing.item.RodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 第一人称鱼竿的世界坐标位姿（SPEC §14.1 / AC-1）。
 *
 * <p>竿锚在玩家身体上，而不是贴在相机上：基向量取 bodyYaw（不是视线），
 * 所以转头时竿留在世界里，只靠低通滤波产生 1–2 帧惯性。手部不做 IK，
 * 用 idle / cast / fight 三组 grip 偏移插值切换。不负责绘制，也不读服务端状态。
 */
public final class ClientRodPose {
    /** 竿子分 4 段，5 个点。 */
    public static final int SEGMENTS = 4;
    public static final int POINTS = SEGMENTS + 1;

    /** 惯性：越小越"粘"，0.35 约为 2 帧延迟。 */
    private static final double LAG = 0.35;
    private static final double ROD_LENGTH = 1.55;

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static Vec3 anchor = Vec3.ZERO;
    private static Vec3 prevAnchor = Vec3.ZERO;
    private static Vec3 dir = new Vec3(0, 0, 1);
    private static Vec3 prevDir = new Vec3(0, 0, 1);
    private static float bend;
    private static float prevBend;
    private static boolean active;

    private static final Vec3[] points = new Vec3[POINTS];

    private ClientRodPose() {
    }

    public static boolean active() {
        return active;
    }

    public static Vec3 point(int i) {
        return points[i];
    }

    public static void tick(Minecraft client) {
        Player player = client.player;
        boolean holding = player != null && player.getMainHandItem().getItem() instanceof RodItem;
        if (!holding || !client.options.getCameraType().isFirstPerson()) {
            active = false;
            return;
        }

        // 身体朝向（不是视线）：转头时竿不该跟着刚性旋转。
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = forward.cross(UP).normalize();
        Vec3 eye = player.getEyePosition();

        Grip grip = gripFor(player);
        Vec3 target = eye
                .add(forward.scale(grip.forward))
                .add(right.scale(grip.side))
                .add(0.0, grip.up, 0.0);
        Vec3 base = forward.scale(grip.dirForward)
                .add(right.scale(grip.dirSide))
                .add(0.0, grip.dirUp, 0.0)
                .normalize();
        // 抬手时竿跟着抬一点，但只吃 35% 的俯仰，避免变成"贴在准星上"。
        float pitch = player.getXRot() * Mth.DEG_TO_RAD;
        Vec3 targetDir = rotate(base, right, -pitch * 0.35F).normalize();

        float targetBend = bendFor(client, player);

        prevAnchor = anchor;
        prevDir = dir;
        prevBend = bend;
        if (!active) {
            anchor = target;
            dir = targetDir;
            bend = targetBend;
            active = true;
        } else {
            anchor = anchor.add(target.subtract(anchor).scale(LAG));
            dir = dir.add(targetDir.subtract(dir).scale(LAG)).normalize();
            bend = Mth.lerp((float) LAG, bend, targetBend);
        }
    }

    /** 三组固定的手部 grip 偏移：idle / cast / fight（SPEC §14.1，不做 IK）。 */
    private static Grip gripFor(Player player) {
        FishingState state = ClientFight.state();
        if (state == FishingState.FIGHTING || state == FishingState.BITE || state == FishingState.LANDING) {
            // 搏鱼：竿压低、指向水面，好把力传到鱼身上。
            return new Grip(0.30, 0.28, -0.50, 0.85, 0.50, -0.05);
        }
        if (player.isUsingItem()) {
            // 蓄力：竿举起、略向后引。
            return new Grip(0.10, 0.32, -0.30, -0.15, 0.97, 0.00);
        }
        // 待机：竿竖在身前偏右。
        return new Grip(0.20, 0.30, -0.42, 0.30, 0.93, 0.02);
    }

    private static float bendFor(Minecraft client, Player player) {
        // 现实里竿的弯曲从竿尖向根部累积；这里 b 是总弯曲系数，
        // 4 段各转 b*((i+1)/4)^1.7，累加约 2.02*b。b=0.5 时竿尖约 58°。
        if (ClientFight.state() == FishingState.FIGHTING) {
            // 张力 1.0（临界断线）约 65°，张力 0.3 约 20°。
            return (float) Mth.clamp(0.55F * ClientFight.tension(), 0.06F, 0.56F);
        }
        if (player.isUsingItem()) {
            // 蓄力：满蓄力时竿身后仰约 34°。
            float charge = Mth.clamp(player.getTicksUsingItem() / (float) CastMath.CHARGE_TICKS, 0.0F, 1.0F);
            return charge * 0.30F;
        }
        return 0.03F;
    }

    /** 按 partialTick 在上一帧与当前帧之间插值，再计算 5 个世界坐标点。 */
    public static void build(float partialTick) {
        if (!active) {
            return;
        }
        float t = Mth.clamp(partialTick, 0.0F, 1.0F);
        Vec3 a = prevAnchor.add(anchor.subtract(prevAnchor).scale(t));
        Vec3 d = prevDir.add(dir.subtract(prevDir).scale(t)).normalize();
        float b = Mth.lerp(t, prevBend, bend);

        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 right = new Vec3(camera.getLeftVector()).scale(-1.0);

        Vec3 current = a;
        points[0] = current;
        double seg = ROD_LENGTH / SEGMENTS;
        // 弯曲绕相机右轴，把竿尖往下（蓄力/搏鱼时更明显）。
        Vec3 axis = right.normalize();
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = b * (float) Math.pow((i + 1) / (double) SEGMENTS, 1.7);
            Vec3 segDir = rotate(d, axis, -angle);
            current = current.add(segDir.scale(seg));
            points[i + 1] = current;
        }
    }

    private static Vec3 rotate(Vec3 v, Vec3 axis, float angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vec3 cross = axis.cross(v);
        double dot = axis.dot(v);
        return v.scale(cos).add(cross.scale(sin)).add(axis.scale(dot * (1.0 - cos)));
    }

    /** 一组手部偏移：锚点的前/右/上分量，以及竿在身体坐标系里的基准朝向分量。 */
    private record Grip(double forward, double side, double up,
                        double dirForward, double dirUp, double dirSide) {
    }
}
