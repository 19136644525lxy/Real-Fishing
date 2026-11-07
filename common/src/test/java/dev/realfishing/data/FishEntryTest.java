package dev.realfishing.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.realfishing.item.RodStyle;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FishEntryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FishEntry parse(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return FishEntry.parse(new ResourceLocation("realfishing", "test_fish"), object);
    }

    @Test
    void parsesFullEntry() {
        FishEntry fish = parse("""
                {
                  "display_name": "测试鱼",
                  "strength": 0.42,
                  "stamina_seconds": 12,
                  "turn_rate": 0.6,
                  "weight": 77,
                  "conditions": {
                    "biomes": ["#minecraft:is_river"],
                    "time": ["day"],
                    "weather": ["rain"],
                    "min_water_depth": 2,
                    "max_water_depth": 9
                  },
                  "loot": [
                    { "item": "realfishing:raw_trout", "count": [1, 3], "chance": 0.5 }
                  ],
                  "xp": [2, 6],
                  "rod_styles": ["float"]
                }
                """);
        assertEquals("测试鱼", fish.displayName());
        assertEquals(0.42F, fish.strength(), 1e-6);
        assertEquals(12.0F, fish.staminaSeconds(), 1e-6);
        assertEquals(77, fish.weight());
        assertEquals(2, fish.minXp());
        assertEquals(6, fish.maxXp());
        assertEquals(1, fish.loot().size());
        assertEquals(3, fish.loot().get(0).maxCount());
        assertTrue(fish.allowsRod(RodStyle.FLOAT));
        assertFalse(fish.allowsRod(RodStyle.LURE));
    }

    @Test
    void missingOptionalFieldsUseDefaults() {
        FishEntry fish = parse("{}");
        assertEquals(0.5F, fish.strength(), 1e-6);
        assertEquals(18.0F, fish.staminaSeconds(), 1e-6);
        assertEquals(10, fish.weight());
        assertTrue(fish.loot().isEmpty());
        // 缺省 rod_styles = 两种竿都能钓
        assertTrue(fish.allowsRod(RodStyle.FLOAT));
        assertTrue(fish.allowsRod(RodStyle.LURE));
        assertFalse(fish.lureOnly());
    }

    @Test
    void badLootReferenceDoesNotThrow() {
        FishEntry fish = parse("""
                {
                  "loot": [
                    { "item": "realfishing:does_not_exist", "count": [1, 1], "chance": 1.0 }
                  ]
                }
                """);
        assertEquals(1, fish.loot().size());
        assertEquals(new ResourceLocation("realfishing:does_not_exist"), fish.loot().get(0).itemId());
    }

    @Test
    void timeLabelsMatchMinecraftClock() {
        assertTrue(FishEntry.matchesTime(0, "day"));
        assertTrue(FishEntry.matchesTime(6000, "day"));
        assertFalse(FishEntry.matchesTime(12000, "day"));

        assertTrue(FishEntry.matchesTime(12000, "dawn_dusk"));
        assertTrue(FishEntry.matchesTime(23000, "dawn_dusk"));
        assertFalse(FishEntry.matchesTime(18000, "dawn_dusk"));

        assertTrue(FishEntry.matchesTime(18000, "night"));
        assertFalse(FishEntry.matchesTime(6000, "night"));
    }

    @Test
    void emptyBaitHasNeutralMultiplier() {
        FishEntry fish = parse("""
                { "conditions": { "bait": ["#realfishing:bait_worm"] } }
                """);
        assertEquals(1.0F, fish.baitMultiplier(ItemStack.EMPTY), 1e-6);
    }

    @Test
    void lureOnlyFlagIsParsed() {
        FishEntry fish = parse("""
                { "lure_only": true, "rod_styles": ["lure"] }
                """);
        assertTrue(fish.lureOnly());
        assertFalse(fish.allowsRod(RodStyle.FLOAT));
        assertTrue(fish.allowsRod(RodStyle.LURE));
    }
}
