package dev.realfishing.physics;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 鱼线求解器的回归测试：静止长度、下垂、长时间稳定性、张力与断线。
 * 用常量 Sampler 隔离世界，保证测试只测积分与约束本身。
 */
class FishingLineTest {
    private static final FishingLine.Sampler FLAT = FishingLine.Sampler.FLAT;
    private static final double TICK = 0.05;

    @Test
    void keepsLengthWhenPinnedStraight() {
        FishingLine line = new FishingLine();
        line.reset(new Vec3(0, 5, 0), new Vec3(0, 5, 10));
        for (int t = 0; t < 400; t++) {
            line.pinStart(0, 5, 0);
            line.pinEnd(0, 5, 10);
            line.step(TICK, Vec3.ZERO, FLAT);
        }
        double total = 0.0;
        for (int i = 0; i < FishingLine.NODES - 1; i++) {
            total += Math.sqrt(sq(line.nodeX(i + 1) - line.nodeX(i))
                    + sq(line.nodeY(i + 1) - line.nodeY(i))
                    + sq(line.nodeZ(i + 1) - line.nodeZ(i)));
        }
        assertEquals(10.0, total, 0.2, " pinned 端点之间 10 格，总长应保持在 10 格附近");
        assertEquals(5.0, line.nodeY(0), 1e-9, "首节点必须钉死");
        assertEquals(10.0, line.nodeZ(FishingLine.NODES - 1), 1e-9, "末节点必须钉死");
    }

    @Test
    void sagsWhenSlack() {
        FishingLine line = new FishingLine();
        line.reset(new Vec3(0, 5, 0), new Vec3(0, 5, 10));
        line.setLength(14.0);
        for (int t = 0; t < 400; t++) {
            line.pinStart(0, 5, 0);
            line.pinEnd(0, 5, 10);
            line.step(TICK, Vec3.ZERO, FLAT);
        }
        double midY = line.nodeY(FishingLine.NODES / 2);
        assertTrue(midY < 4.5, "放线 14 格挂在 10 格跨距上应该下垂，实际中点 y=" + midY);
        assertTrue(midY > 0.05, "不应该掉到地面以下，实际 y=" + midY);
    }

    @Test
    void staysStableForLongRun() {
        FishingLine line = new FishingLine();
        line.reset(new Vec3(0, 8, 0), new Vec3(0, 6, 12));
        line.setLength(16.0);
        Vec3 gusty = new Vec3(1.5, 0.0, -0.5);
        for (int t = 0; t < 25_000; t++) {
            line.pinStart(0, 8, 0);
            line.pinEnd(0, 6, 12);
            line.step(TICK, gusty, FLAT);
            if (t % 5000 == 0) {
                assertFalse(line.isSnapped(), "无外力拉断时不应该断线");
            }
        }
        for (int i = 0; i < FishingLine.NODES; i++) {
            assertTrue(Double.isFinite(line.nodeX(i)), "节点 x 出现 NaN");
            assertTrue(Math.abs(line.nodeX(i)) < 1000.0 && Math.abs(line.nodeY(i)) < 1000.0,
                    "节点跑飞：(" + line.nodeX(i) + ", " + line.nodeY(i) + ")");
        }
    }

    @Test
    void tensionRisesAndSnapsWhenOverstretched() {
        FishingLine line = new FishingLine();
        line.reset(new Vec3(0, 5, 0), new Vec3(0, 5, 10));
        // 末节点被拽到 12 格处，线只有 10 格 → 拉伸 20%，超过 12% 的满张力线。
        for (int t = 0; t < 100; t++) {
            line.pinEnd(0, 5, 12);
            line.step(TICK, Vec3.ZERO, FLAT);
        }
        assertTrue(line.tension() > 1.0, "拉伸 20% 时张力应超过 1.0，实际 " + line.tension());
        assertTrue(line.isSnapped(), "满张力持续 0.25 s 应该断线");
    }

    private static double sq(double v) {
        return v * v;
    }
}
