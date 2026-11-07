package dev.realfishing.item;

import dev.realfishing.client.ClientCast;
import dev.realfishing.fishing.CastMath;
import dev.realfishing.fishing.FishingManager;
import dev.realfishing.net.CastC2S;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 台钓竿：右键蓄力（2 秒蓄满），松手把蓄力与视角发给服务端抛投（SPEC §12）。
 * 不负责：浮漂物理（BobberEntity）、会话状态（FishingManager）、HUD（client 包）。
 */
public class FloatRodItem extends RodItem {
    public FloatRodItem() {
        super(RodStyle.FLOAT, new Properties().stacksTo(1).durability(64));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return CastMath.CHARGE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            FishingManager.startCharging((ServerPlayer) player);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int used = getUseDuration(stack) - timeLeft;
        float charge = Mth.clamp(used / (float) CastMath.CHARGE_TICKS, 0.0F, 1.0F);
        if (charge < CastMath.MIN_CHARGE) {
            return;
        }
        if (level.isClientSide) {
            new CastC2S(charge, player.getYRot(), player.getXRot()).sendToServer();
            ClientCast.spawnPrediction(player, charge);
        }
    }
}
