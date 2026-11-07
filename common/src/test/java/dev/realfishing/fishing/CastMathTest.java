package dev.realfishing.fishing;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抛投数学的回归测试：AC-2 要求 8–24 格，这里用与游戏内完全相同的积分器验证。
 */
class CastMathTest {
    private static final double START_HEIGHT = 1.5;

    @Test
    void chargeMapsToSpeedBounds() {
        assertEquals(CastMath.MIN_SPEED, CastMath.speed(0.0F), 1e-9);
        assertEquals(CastMath.MAX_SPEED, CastMath.speed(1.0F), 1e-9);
        assertEquals(CastMath.MIN_SPEED, CastMath.speed(-0.5F), 1e-9);
        assertEquals(CastMath.MAX_SPEED, CastMath.speed(2.0F), 1e-9);
    }

    @Test
    void directionIsUnitLength() {
        for (float pitch = -90.0F; pitch <= 90.0F; pitch += 15.0F) {
            Vec3 dir = CastMath.direction(37.0F, pitch);
            assertEquals(1.0, dir.length(), 1e-6, "pitch=" + pitch);
        }
    }

    @Test
    void flatAimReachesAcceptanceRange() {
        Vec3 dir = CastMath.direction(0.0F, 0.0F);
        double min = CastMath.simulateRange(CastMath.speed(0.0F), dir, START_HEIGHT);
        double max = CastMath.simulateRange(CastMath.speed(1.0F), dir, START_HEIGHT);
        assertTrue(min >= 7.5 && min <= 9.0, "轻抛距离应在 8 格附近，实际 " + min);
        assertTrue(max >= 22.5 && max <= 24.5, "满蓄力距离应在 24 格附近，实际 " + max);
    }

    @Test
    void rangeGrowsWithCharge() {
        Vec3 dir = CastMath.direction(0.0F, 0.0F);
        double previous = -1.0;
        for (int i = 0; i <= 10; i++) {
            double distance = CastMath.simulateRange(CastMath.speed(i / 10.0F), dir, START_HEIGHT);
            assertTrue(distance > previous, "蓄力 " + i + " 时距离未增加：" + distance);
            previous = distance;
        }
    }
}
