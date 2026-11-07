package dev.realfishing.physics;

import net.minecraft.world.phys.Vec3;

/**
 * 鱼线求解器：24 个质点的 Verlet 点链 + 距离约束，负责下垂、张力与断线计时。
 * 不负责：渲染（在 client 包）、状态机（FishingManager）、端点从哪来（竿尖/浮漂由调用方钉）。
 *
 * <p>热路径零分配：坐标全部放在平铺 double[] 里，step() 只做算术，不 new 任何对象。
 * 单位统一为"格 / 秒"，与 CastMath 一致；SPEC §10 给的是每 tick 量纲，这里按 400 倍换算
 * （20 tick/s 的平方），换算依据见 docs/tuning.md。
 */
public final class FishingLine {
    public static final int NODES = 24;
    public static final double DT = 1.0 / 60.0;
    public static final int MAX_SUBSTEPS = 4;
    public static final int ITERATIONS = 10;
    public static final double DAMPING = 0.02;
    /** 格/s²。SPEC 的 0.05 格/t² 换算成秒制即 0.05 × 400。 */
    public static final double GRAVITY = 20.0;
    /** 与速度平方成正比的空气阻力系数。 */
    public static final double AIR_DRAG = 0.002;
    /** 节点在水下时的阻力系数。 */
    public static final double WATER_DRAG = 0.02;
    public static final double MAX_STRAIN = 0.12;
    public static final double BREAK_HOLD = 0.25;
    public static final double STIFFNESS = 0.9;
    /** 水面下的浮力加速度（格/s²）。 */
    public static final double BUOYANCY = 2.0;

    /** 世界采样：水下判定用 fluidTop，贴地用 groundY。测试里传常量实现即可。 */
    public interface Sampler {
        double fluidTop(double x, double z);

        double groundY(double x, double z);

        Sampler FLAT = new Sampler() {
            @Override
            public double fluidTop(double x, double z) {
                return Double.NEGATIVE_INFINITY;
            }

            @Override
            public double groundY(double x, double z) {
                return 0.0;
            }
        };
    }

    private final double[] px = new double[NODES];
    private final double[] py = new double[NODES];
    private final double[] pz = new double[NODES];
    private final double[] qx = new double[NODES];
    private final double[] qy = new double[NODES];
    private final double[] qz = new double[NODES];

    private double lineLength = 1.0;
    private double tension;
    private double breakTimer;
    private boolean snapped;
    private boolean initialised;

    /** 端点钉住状态：每个子步末尾强制回到目标点并把速度清零，避免"每 tick 重钉"给端点注入能量。 */
    private boolean startPinned;
    private boolean endPinned;
    private double startX;
    private double startY;
    private double startZ;
    private double endX;
    private double endY;
    private double endZ;

    public FishingLine() {
    }

    /** 沿直线均分 24 个节点，并清空速度。 */
    public void reset(Vec3 start, Vec3 end) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        lineLength = Math.max(dist, 1.0e-3);
        for (int i = 0; i < NODES; i++) {
            double t = i / (double) (NODES - 1);
            px[i] = start.x + dx * t;
            py[i] = start.y + dy * t;
            pz[i] = start.z + dz * t;
            qx[i] = px[i];
            qy[i] = py[i];
            qz[i] = pz[i];
        }
        tension = 0.0;
        breakTimer = 0.0;
        snapped = false;
        initialised = true;
        startPinned = false;
        endPinned = false;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public void setLength(double length) {
        if (length > 1.0e-3 && Double.isFinite(length)) {
            this.lineLength = length;
        }
    }

    public double length() {
        return lineLength;
    }

    public double tension() {
        return tension;
    }

    public boolean isSnapped() {
        return snapped;
    }

    public double nodeX(int i) {
        return px[i];
    }

    public double nodeY(int i) {
        return py[i];
    }

    public double nodeZ(int i) {
        return pz[i];
    }

    /** 渲染插值用：上一子步的位置。 */
    public double prevX(int i) {
        return qx[i];
    }

    public double prevY(int i) {
        return qy[i];
    }

    public double prevZ(int i) {
        return qz[i];
    }

    /** 首节点钉在竿尖。可在每 tick 调用，内部会记住目标点。 */
    public void pinStart(double x, double y, double z) {
        startPinned = true;
        startX = x;
        startY = y;
        startZ = z;
        setNode(0, x, y, z);
    }

    /** 末节点钉在浮漂（WAITING）或鱼（FIGHTING）。 */
    public void pinEnd(double x, double y, double z) {
        endPinned = true;
        endX = x;
        endY = y;
        endZ = z;
        setNode(NODES - 1, x, y, z);
    }

    /** 解除端点钉住（例如浮漂被回收）。 */
    public void unpinAll() {
        startPinned = false;
        endPinned = false;
    }

    private void setNode(int i, double x, double y, double z) {
        px[i] = x;
        py[i] = y;
        pz[i] = z;
        qx[i] = x;
        qy[i] = y;
        qz[i] = z;
    }

    /** 子步末尾重新钉住端点并清零速度，防止端点被重力/约束拖着累积虚假动能。 */
    private void enforcePins() {
        if (startPinned) {
            setNode(0, startX, startY, startZ);
        }
        if (endPinned) {
            setNode(NODES - 1, endX, endY, endZ);
        }
    }

    /**
     * 推进一个 tick：内部按 1/60 s 子步跑 3 次（20 Hz 下 1/20 s）。
     *
     * @param dt     tick 时长（秒），服务端固定 0.05
     * @param wind   风加速度（格/s²），无风传 Vec3.ZERO
     * @param sampler 水面/地面采样
     */
    public void step(double dt, Vec3 wind, Sampler sampler) {
        if (!initialised) {
            return;
        }
        int substeps = (int) Math.round(dt / DT);
        substeps = Math.max(1, Math.min(MAX_SUBSTEPS, substeps));
        double h = dt / substeps;
        for (int s = 0; s < substeps; s++) {
            substep(h, wind, sampler);
        }
    }

    private void substep(double h, Vec3 wind, Sampler sampler) {
        double windX = wind.x;
        double windY = wind.y;
        double windZ = wind.z;
        for (int i = 0; i < NODES; i++) {
            double vx = (px[i] - qx[i]) * (1.0 - DAMPING);
            double vy = (py[i] - qy[i]) * (1.0 - DAMPING);
            double vz = (pz[i] - qz[i]) * (1.0 - DAMPING);
            qx[i] = px[i];
            qy[i] = py[i];
            qz[i] = pz[i];

            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            boolean underwater = py[i] < sampler.fluidTop(px[i], pz[i]);
            double k = underwater ? WATER_DRAG : AIR_DRAG;
            double drag = k * speed;

            double ax = windX - drag * vx;
            double ay = windY - GRAVITY - drag * vy;
            double az = windZ - drag * vz;
            if (underwater) {
                ay += BUOYANCY;
            }

            px[i] += vx + ax * h * h;
            py[i] += vy + ay * h * h;
            pz[i] += vz + az * h * h;
        }
        solveConstraints();
        applyTerrain(sampler);
        enforcePins();
        updateTension(h);
    }

    private void solveConstraints() {
        // 固定 10 次 Gauss-Seidel 在"绷紧"情形下收敛不够（10 格绳挂 10 格跨距会拉长 7%）。
        // 因此按残余应变自适应补迭代：绷紧时最多 40 次，松垂时 10 次就停。
        for (int it = 0; it < 40; it++) {
            relaxOnce();
            if (it >= ITERATIONS - 1 && maxStrain() <= 0.01) {
                break;
            }
        }
    }

    private void relaxOnce() {
        double rest = lineLength / (NODES - 1);
        int last = NODES - 1;
        for (int i = 0; i < last; i++) {
            double dx = px[i + 1] - px[i];
            double dy = py[i + 1] - py[i];
            double dz = pz[i + 1] - pz[i];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-9) {
                continue;
            }
            double diff = (len - rest) / len * 0.5 * STIFFNESS;
            double ox = dx * diff;
            double oy = dy * diff;
            double oz = dz * diff;
            if (i != 0) {
                px[i] += ox;
                py[i] += oy;
                pz[i] += oz;
            }
            if (i + 1 != last) {
                px[i + 1] -= ox;
                py[i + 1] -= oy;
                pz[i + 1] -= oz;
            }
        }
    }

    /** 当前最大相对拉伸（负值表示松垂）。 */
    private double maxStrain() {
        double rest = lineLength / (NODES - 1);
        double maxStrain = -1.0;
        for (int i = 0; i < NODES - 1; i++) {
            double dx = px[i + 1] - px[i];
            double dy = py[i + 1] - py[i];
            double dz = pz[i + 1] - pz[i];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double strain = (len - rest) / rest;
            if (strain > maxStrain) {
                maxStrain = strain;
            }
        }
        return maxStrain;
    }

    private void applyTerrain(Sampler sampler) {
        for (int i = 0; i < NODES; i++) {
            double ground = sampler.groundY(px[i], pz[i]) + 0.05;
            if (py[i] < ground) {
                py[i] = ground;
                qx[i] = px[i];
                qy[i] = py[i];
                qz[i] = pz[i];
            }
        }
    }

    private void updateTension(double h) {
        double rest = lineLength / (NODES - 1);
        double maxStrain = 0.0;
        for (int i = 0; i < NODES - 1; i++) {
            double dx = px[i + 1] - px[i];
            double dy = py[i + 1] - py[i];
            double dz = pz[i + 1] - pz[i];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double strain = (len - rest) / rest;
            if (strain > maxStrain) {
                maxStrain = strain;
            }
        }
        double raw = Math.max(0.0, Math.min(1.5, maxStrain / MAX_STRAIN));
        tension += (raw - tension) * 0.25;
        if (tension > 1.0) {
            breakTimer += h;
            if (breakTimer > BREAK_HOLD) {
                snapped = true;
            }
        } else {
            breakTimer = 0.0;
        }
    }
}
