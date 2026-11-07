package dev.realfishing.fishing;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 抛投的纯数学部分：蓄力→初速、视线→方向、弹道积分（用于单测与参数标定）。
 * 不负责：生成实体、发送网络包、状态机（属于 FishingManager）。
 *
 * <p>参数标定见 docs/tuning.md：AC-2 要求 8–24 格，取 g=6.0 格/s²、二次阻力 k=0.02，
 * 起点高 1.5 格、仰角偏置 +0.15 时可命中 8.0–23.6 格。SPEC §12 的 0.08/0.002 若按每 tick
 * 解释等价于 32 m/s²，会把 22 格/s 的抛投压到 4–5 格，与 AC-2 冲突，故以验收标准为准。
 */
public final class CastMath {
    /** 低于这个蓄力松手视为取消，不抛投（SPEC §9.2）。 */
    public static final float MIN_CHARGE = 0.15F;
    /** 蓄满所需 tick 数（2 秒）。 */
    public static final int CHARGE_TICKS = 40;

    public static final double MIN_SPEED = 9.0;
    public static final double MAX_SPEED = 22.0;
    /** 格/秒²。 */
    public static final double GRAVITY = 6.0;
    /** 二次空气阻力系数（1/格）。 */
    public static final double AIR_DRAG = 0.02;
    /** 视线抬升量，避免平视时砸脚面（SPEC §12）。 */
    public static final double UPWARD_BIAS = 0.15;
    /** 单 tick 物理子步长，保证弹道平滑且与单测模拟一致。 */
    public static final double SUBSTEP = 0.0125;
    public static final int SUBSTEPS = 4;

    public static final double MAX_FLIGHT_SECONDS = 5.0;
    public static final double MAX_LINE_LENGTH = 32.0;

    private CastMath() {
    }

    /** 蓄力线性映射到初速：charge=0 → 9 格/s，charge=1 → 22 格/s。 */
    public static double speed(float charge) {
        return MIN_SPEED + (MAX_SPEED - MIN_SPEED) * Mth.clamp(charge, 0.0F, 1.0F);
    }

    /** 视线方向 + 向上偏置后归一化，保证仰角略高于准星。 */
    public static Vec3 direction(float yaw, float pitch) {
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        return look.add(0.0, UPWARD_BIAS, 0.0).normalize();
    }

    /**
     * 一模一样的弹道积分（dt=SUBSTEP），返回落回起点水平面（相对高度 0）时的水平距离。
     * 供 JUnit 校验 AC-2，以及 /realfishing debug cast 预览落点。
     */
    public static double simulateRange(double speed, Vec3 dir, double startHeight) {
        Vec3 pos = new Vec3(0.0, startHeight, 0.0);
        Vec3 vel = dir.scale(speed);
        for (double t = 0.0; t < MAX_FLIGHT_SECONDS; t += SUBSTEP) {
            double drag = AIR_DRAG * vel.length();
            vel = vel.add(-drag * vel.x * SUBSTEP,
                    (-GRAVITY - drag * vel.y) * SUBSTEP,
                    -drag * vel.z * SUBSTEP);
            pos = pos.add(vel.scale(SUBSTEP));
            if (pos.y <= 0.0) {
                return Math.sqrt(pos.x * pos.x + pos.z * pos.z);
            }
        }
        return Math.sqrt(pos.x * pos.x + pos.z * pos.z);
    }
}
