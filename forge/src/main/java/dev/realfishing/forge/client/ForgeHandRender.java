package dev.realfishing.forge.client;

import dev.realfishing.item.RodItem;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge 端取消原版第一人称手部渲染（AC-1）：Forge 有 {@link RenderHandEvent}，不需要 mixin。
 * 只取消主手持竿的情况；副手与其它物品照常。
 */
public final class ForgeHandRender {
    private ForgeHandRender() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(ForgeHandRender.class);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().getItem() instanceof RodItem) {
            event.setCanceled(true);
        }
    }
}
