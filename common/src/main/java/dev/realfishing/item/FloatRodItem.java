package dev.realfishing.item;

import dev.realfishing.RealFishing;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 台钓竿。M0 只做"右键打日志"，验证物品注册与主类链路；抛投在 M1 接上。
 * 不负责：状态机（属于 fishing 包）。
 */
public class FloatRodItem extends RodItem {
    public FloatRodItem() {
        super(RodStyle.FLOAT, new Properties().stacksTo(1).durability(64));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        RealFishing.LOGGER.info("[M0] float_rod 被使用：side={}, player={}",
                level.isClientSide ? "client" : "server", player.getGameProfile().getName());
        return InteractionResultHolder.success(stack);
    }
}
