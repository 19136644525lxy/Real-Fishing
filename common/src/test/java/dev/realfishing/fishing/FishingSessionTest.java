package dev.realfishing.fishing;

import dev.realfishing.item.RodStyle;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 会话存档往返与状态清理的测试：落盘字段一个都不能丢，reset 必须清干净。
 */
class FishingSessionTest {
    @Test
    void saveAndLoadRoundTrip() {
        FishingSession session = new FishingSession(UUID.randomUUID());
        session.transition(FishingState.WAITING, 1234L);
        session.sessionId = 7;
        session.charge = 0.8F;
        session.lineLength = 12.5;
        session.drag = 0.3;
        session.tension = 4.2;
        session.fishStamina = 66.0;
        session.rodStyle = RodStyle.LURE;
        session.bobberEntityId = 42;
        session.castGameTime = 1200L;
        session.dimensionId = "minecraft:overworld";

        CompoundTag tag = session.save();
        FishingSession loaded = FishingSession.load(tag);

        assertEquals(session.playerId, loaded.playerId);
        assertSame(FishingState.WAITING, loaded.state);
        assertEquals(7, loaded.sessionId);
        assertEquals(0.8F, loaded.charge, 1e-6);
        assertEquals(12.5, loaded.lineLength, 1e-9);
        assertEquals(0.3, loaded.drag, 1e-9);
        assertEquals(4.2, loaded.tension, 1e-9);
        assertEquals(66.0, loaded.fishStamina, 1e-9);
        assertSame(RodStyle.LURE, loaded.rodStyle);
        assertEquals(42, loaded.bobberEntityId);
        assertEquals(1200L, loaded.castGameTime);
        assertEquals("minecraft:overworld", loaded.dimensionId);
    }

    @Test
    void resetClearsTransientFields() {
        FishingSession session = new FishingSession(UUID.randomUUID());
        session.transition(FishingState.FIGHTING, 99L);
        session.charge = 1.0F;
        session.lineLength = 30.0;
        session.tension = 12.0;
        session.bobberEntityId = 5;
        session.castGameTime = 88L;

        session.reset(500L);

        assertSame(FishingState.IDLE, session.state);
        assertEquals(500L, session.stateSince);
        assertEquals(0.0F, session.charge, 1e-6);
        assertEquals(0.0, session.lineLength, 1e-9);
        assertEquals(0.0, session.tension, 1e-9);
        assertEquals(-1, session.bobberEntityId);
        assertEquals(0L, session.castGameTime);
    }
}
