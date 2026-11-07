package dev.realfishing.fishing;

import java.util.Random;

/**
 * 搏鱼模型（SPEC §11）：鱼的体力、冲刺、朝向扰动，以及张力/收放线的耦合。
 * 纯 Java、无 Minecraft 类型，方便单测；不负责状态机与会话落盘（FishingManager 负责）。
 *
 * <p>一次 {@link #step} 相当于一个服务端 tick（dt=0.05）。所有速度用"格/秒"。
 */
public final class FightModel {
    /** 收线速度（格/秒），SPEC §11.1。 */
    public static final double REEL_SPEED = 1.2;
    /** 张力超过泄力时每秒放出的线长系数，SPEC §11.3。 */
    public static final double AUTO_RELEASE = 6.0;
    /** 断线阈值与持续时间（0.25 s = 5 tick）。 */
    public static final double BREAK_TENSION = 1.0;
    public static final int BREAK_TICKS = 5;
    /** 线长范围，SPEC §11.3。 */
    public static final double MIN_LINE = 1.5;
    public static final double MAX_LINE = 80.0;

    public static final int TIER_SMALL = 0;
    public static final int TIER_MEDIUM = 1;
    public static final int TIER_BIG = 2;

    private final float strength;
    private final float staminaSeconds;
    private final float turnRate;
    private final Random random;

    private double stamina = 1.0;
    private double tension;
    private double lineLength;
    private boolean sprint;
    private int sprintTicksLeft;
    private int sprintCooldown;
    private int wanderTicks;
    private int breakTicks;
    private float heading;
    private double vx;
    private double vz;

    private FightModel(float strength, float staminaSeconds, float turnRate, long seed) {
        this.strength = strength;
        this.staminaSeconds = staminaSeconds;
        this.turnRate = turnRate;
        this.random = new Random(seed);
        this.sprintCooldown = 40 + random.nextInt(60);
        this.wanderTicks = 10;
    }

    /** 按鱼档创建（SPEC §11.5 的初值）。 */
    public static FightModel ofTier(int tier, long seed) {
        return switch (tier) {
            case TIER_SMALL -> new FightModel(0.25F, 8.0F, 0.8F, seed);
            case TIER_BIG -> new FightModel(0.8F, 35.0F, 0.3F, seed);
            default -> new FightModel(0.5F, 18.0F, 0.5F, seed);
        };
    }

    /**
     * 推进一个 tick。
     *
     * @param dt       秒（固定 0.05）
     * @param playerX  玩家 X
     * @param playerZ  玩家 Z
     * @param bobberX  浮漂当前 X（鱼所在位置）
     * @param bobberZ  浮漂当前 Z
     * @param reeling  玩家是否按住收线
     * @param drag     泄力 0..1
     */
    public void step(double dt, double playerX, double playerZ, double bobberX, double bobberZ,
                     boolean reeling, double drag) {
        if (sprintTicksLeft > 0) {
            sprintTicksLeft--;
            if (sprintTicksLeft == 0) {
                sprint = false;
                sprintCooldown = 40 + random.nextInt(60);
            }
        } else if (sprintCooldown > 0) {
            sprintCooldown--;
        } else if (stamina > 0.0 && breakTicks == 0) {
            sprint = true;
            sprintTicksLeft = 12 + random.nextInt(18);
        }
        if (stamina <= 0.0) {
            sprint = false;
        }

        // 体力衰减：冲刺时 2.5 倍。
        double drain = (sprint ? 2.5 : 1.0) / staminaSeconds;
        stamina = Math.max(0.0, stamina - dt * drain);

        // 朝向：远离玩家为主，每隔 0.3–1.0 s 加一次扰动。
        if (--wanderTicks <= 0) {
            double away = Math.atan2(bobberZ - playerZ, bobberX - playerX);
            heading = (float) (away + (random.nextDouble() - 0.5) * 2.0 * turnRate);
            wanderTicks = 6 + random.nextInt(14);
        }

        double speed = (1.2 + strength * 2.8) * (sprint ? 1.6 : 1.0) * (stamina > 0.0 ? 1.0 : 0.35)
                * (1.0 - drag * 0.35);
        vx = Math.cos(heading) * speed;
        vz = Math.sin(heading) * speed;

        // 张力：鱼的拉力为主，收线加压、泄力减压；一阶低通避免 HUD 抖动。
        double pull = strength * (1.0 + (sprint ? 0.6 : 0.0)) * (stamina > 0.0 ? 1.0 : 0.35);
        double target = pull * 1.1 + (reeling ? 0.15 : 0.0) - drag * 1.3 + (lineLength < 3.0 ? 0.08 : 0.0);
        tension += (target - tension) * 0.2;
        tension = Math.max(0.0, Math.min(tension, 1.6));

        // 收放线：张力超过泄力就自动放线，否则收线。
        if (tension > drag) {
            lineLength = Math.min(MAX_LINE, lineLength + (tension - drag) * AUTO_RELEASE * dt);
        } else if (reeling) {
            lineLength = Math.max(MIN_LINE, lineLength - REEL_SPEED * dt);
        }

        if (tension > BREAK_TENSION) {
            breakTicks++;
        } else {
            breakTicks = 0;
        }
    }

    /** 抬竿：短时抬张力、把鱼往回收 0.3 格（SPEC §11.1）。 */
    public void pump() {
        tension = Math.min(1.6, tension + 0.15);
        lineLength = Math.max(MIN_LINE, lineLength - 0.3);
    }

    /** 鱼撞到岸/障碍：掉头继续拉（SPEC §11.2）。 */
    public void bounce() {
        heading += (float) Math.PI;
    }

    public boolean isSnapped() {
        return breakTicks >= BREAK_TICKS;
    }

    public boolean isSprinting() {
        return sprint;
    }

    public double tension() {
        return tension;
    }

    public double stamina() {
        return stamina;
    }

    public double lineLength() {
        return lineLength;
    }

    public double vx() {
        return vx;
    }

    public double vz() {
        return vz;
    }

    public float strength() {
        return strength;
    }

    public void setLineLength(double length) {
        this.lineLength = Math.max(MIN_LINE, Math.min(MAX_LINE, length));
    }
}
