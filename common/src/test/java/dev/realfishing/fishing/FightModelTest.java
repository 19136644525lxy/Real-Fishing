package dev.realfishing.fishing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FightModelTest {
    private static final double DT = 0.05;

    private static void settle(FightModel model, int ticks) {
        for (int i = 0; i < ticks; i++) {
            model.step(DT, 0, 0, 10, 0, false, 0.2);
        }
    }

    @Test
    void staminaDrainsToZeroAndStopsSprinting() {
        FightModel model = FightModel.ofTier(FightModel.TIER_SMALL, 1L);
        settle(model, 8 * 20 * 3);
        assertEquals(0.0, model.stamina(), 0.02);
        assertFalse(model.isSprinting());
    }

    @Test
    void reelingBuildsTensionWithLowDrag() {
        FightModel model = FightModel.ofTier(FightModel.TIER_MEDIUM, 2L);
        for (int i = 0; i < 40; i++) {
            model.step(DT, 0, 0, 6, 0, true, 0.0);
        }
        assertTrue(model.tension() > 0.3, "收线时张力应上升，实际 " + model.tension());
    }

    @Test
    void highDragAutoReleasesLine() {
        FightModel model = FightModel.ofTier(FightModel.TIER_BIG, 3L);
        model.setLineLength(6.0);
        for (int i = 0; i < 60; i++) {
            model.step(DT, 0, 0, 6, 0, true, 0.05);
        }
        assertTrue(model.lineLength() > 6.0, "张力大于泄力时应自动放线，实际 " + model.lineLength());
    }

    @Test
    void sustainedTensionSnapsLine() {
        FightModel model = FightModel.ofTier(FightModel.TIER_BIG, 4L);
        int ticks = 0;
        while (!model.isSnapped() && ticks < 400) {
            model.step(DT, 0, 0, 20, 0, true, 0.0);
            ticks++;
        }
        assertTrue(model.isSnapped(), "满张力持续 0.25 s 应断线，tension=" + model.tension());
        assertTrue(ticks >= FightModel.BREAK_TICKS, "至少要坚持 5 tick 才断，实际 " + ticks);
    }

    @Test
    void fishFleesAwayFromPlayer() {
        FightModel model = FightModel.ofTier(FightModel.TIER_MEDIUM, 5L);
        model.step(DT, 0, 0, 10, 0, false, 0.2);
        assertTrue(model.vx() > 0.0, "鱼应朝远离玩家（+X）方向游，实际 vx=" + model.vx());
    }

    @Test
    void pumpPullsFishCloser() {
        FightModel model = FightModel.ofTier(FightModel.TIER_SMALL, 6L);
        model.setLineLength(5.0);
        model.pump();
        assertEquals(4.7, model.lineLength(), 1e-6);
    }
}
