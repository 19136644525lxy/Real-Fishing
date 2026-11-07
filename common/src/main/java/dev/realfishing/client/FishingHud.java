package dev.realfishing.client;

import dev.realfishing.fishing.CastMath;
import dev.realfishing.item.FloatRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 钓鱼 HUD：蓄力力度条（准星下方）+ 搏鱼时的张力条/体力条（右侧竖条）。
 * 不负责：数值计算（蓄力读玩家 use tick；张力/体力直接读服务端 10 Hz 快照）。
 */
public final class FishingHud {
    private static final int WIDTH = 60;
    private static final int HEIGHT = 4;
    private static final int BAR_WIDTH = 6;
    private static final int BAR_HEIGHT = 80;

    private FishingHud() {
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        if (ClientFight.fighting()) {
            renderFight(graphics);
            return;
        }
        if (!player.isUsingItem()) {
            return;
        }
        ItemStack stack = player.getUseItem();
        if (!(stack.getItem() instanceof FloatRodItem)) {
            return;
        }
        // 蓄力以"已经用了多少 tick"计，而不是剩余 tick：竿的 UseDuration 是弓式长时长，
        // 用剩余 tick 会在按住的第一帧就显示满格。
        int used = Math.min(player.getTicksUsingItem(), CastMath.CHARGE_TICKS);
        float charge = Mth.clamp(used / (float) CastMath.CHARGE_TICKS, 0.0F, 1.0F);

        int x = (graphics.guiWidth() - WIDTH) / 2;
        int y = graphics.guiHeight() / 2 + 22;
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0x90000000);
        int color = charge >= 1.0F ? 0xFFE04A3A : 0xFF5FC9F0;
        graphics.fill(x, y, x + Math.round(WIDTH * charge), y + HEIGHT, color);
    }

    private static void renderFight(GuiGraphics graphics) {
        int x = graphics.guiWidth() - 26;
        int y = (graphics.guiHeight() - BAR_HEIGHT) / 2;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0x90000000);

        double tension = Mth.clamp(ClientFight.tension(), 0.0, 1.0);
        int filled = (int) Math.round(BAR_HEIGHT * tension);
        int color = tension < 0.6 ? 0xFF4CD464 : tension < 0.85 ? 0xFFE0C24A : 0xFFE04A3A;
        // 从下往上长，张力越满越靠近顶部。
        graphics.fill(x, y + BAR_HEIGHT - filled, x + BAR_WIDTH, y + BAR_HEIGHT, color);

        double stamina = Mth.clamp(ClientFight.stamina(), 0.0, 1.0);
        int sx = x - 7;
        graphics.fill(sx - 1, y - 1, sx + 3 + 1, y + BAR_HEIGHT + 1, 0x70000000);
        int sFilled = (int) Math.round(BAR_HEIGHT * stamina);
        graphics.fill(sx, y + BAR_HEIGHT - sFilled, sx + 3, y + BAR_HEIGHT, 0xFF9AD8FF);
    }
}
