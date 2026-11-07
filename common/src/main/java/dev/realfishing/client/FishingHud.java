package dev.realfishing.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.realfishing.fishing.CastMath;
import dev.realfishing.item.FloatRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 蓄力力度条：按住右键时准星下方画一条横向进度条，满蓄力变红。
 * 不负责：蓄力数值本身（直接读玩家 use 剩余 tick，与服务端同源）。
 */
public final class FishingHud {
    private static final int WIDTH = 60;
    private static final int HEIGHT = 4;

    private FishingHud() {
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof FloatRodItem rod)) {
            return;
        }
        int duration = rod.getUseDuration(stack);
        int remaining = player.getUseItemRemainingTicks();
        float charge = Mth.clamp((duration - remaining) / (float) CastMath.CHARGE_TICKS, 0.0F, 1.0F);

        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() / 2 + 22;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
        int color = charge >= 1.0F ? 0xFFE04A3A : 0xFF5FC9F0;
        graphics.fill(x, y, x + Math.round(WIDTH * charge), y + HEIGHT, color);
    }
}
