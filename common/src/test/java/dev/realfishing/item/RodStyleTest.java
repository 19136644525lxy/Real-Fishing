package dev.realfishing.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M0 的冒烟测试：确认 :common:test 能跑起来（物理测试从 M2 开始加）。
 */
class RodStyleTest {
    @Test
    void v01OnlyEnablesFloat() {
        assertEquals(2, RodStyle.values().length);
        assertEquals(RodStyle.FLOAT, RodStyle.valueOf("FLOAT"));
        assertTrue(RodStyle.LURE.name().equals("LURE"));
    }
}
