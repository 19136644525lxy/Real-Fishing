package dev.realfishing.registry;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.realfishing.RealFishing;
import dev.realfishing.item.FloatRodItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品注册表：台钓竿、5 种生鱼、2 种饵，以及模组创造标签页。
 * 不负责物品行为（在 item 包）；也不注册路亚竿（按 AGENTS §9 留给 M9）。
 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(RealFishing.MOD_ID, Registries.ITEM);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(RealFishing.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<Item> FLOAT_ROD =
            ITEMS.register("float_rod", FloatRodItem::new);

    public static final RegistrySupplier<Item> RAW_TROUT =
            ITEMS.register("raw_trout", () -> new Item(fishFood()));
    public static final RegistrySupplier<Item> RAW_CARP =
            ITEMS.register("raw_carp", () -> new Item(fishFood()));
    public static final RegistrySupplier<Item> RAW_BASS =
            ITEMS.register("raw_bass", () -> new Item(fishFood()));
    public static final RegistrySupplier<Item> RAW_CATFISH =
            ITEMS.register("raw_catfish", () -> new Item(fishFood()));
    public static final RegistrySupplier<Item> RAW_EEL =
            ITEMS.register("raw_eel", () -> new Item(fishFood()));

    public static final RegistrySupplier<Item> BAIT_WORM =
            ITEMS.register("bait_worm", () -> new Item(new Item.Properties()));
    public static final RegistrySupplier<Item> BAIT_INSECT =
            ITEMS.register("bait_insect", () -> new Item(new Item.Properties()));

    public static final RegistrySupplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.realfishing"))
            .icon(() -> new ItemStack(FLOAT_ROD.get()))
            .displayItems((parameters, output) -> {
                output.accept(new ItemStack(FLOAT_ROD.get()));
                output.accept(new ItemStack(RAW_TROUT.get()));
                output.accept(new ItemStack(RAW_CARP.get()));
                output.accept(new ItemStack(RAW_BASS.get()));
                output.accept(new ItemStack(RAW_CATFISH.get()));
                output.accept(new ItemStack(RAW_EEL.get()));
                output.accept(new ItemStack(BAIT_WORM.get()));
                output.accept(new ItemStack(BAIT_INSECT.get()));
            })
            .build());

    private ModItems() {
    }

    private static Item.Properties fishFood() {
        return new Item.Properties().food(new FoodProperties.Builder()
                .nutrition(2)
                .saturationMod(0.3F)
                .build());
    }

    public static void register() {
        ITEMS.register();
        TABS.register();
    }
}
