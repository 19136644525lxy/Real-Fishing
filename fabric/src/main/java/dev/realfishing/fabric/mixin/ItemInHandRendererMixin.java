package dev.realfishing.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.realfishing.item.RodItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 负责：持竿时取消原版第一人称手部（手臂 + 物品）渲染，改由世界锚定的
 * ClientRodRenderer 绘制（SPEC AC-1）。不负责：竿本身的绘制。
 * <p>
 * 只取消主手且物品是 RodItem 的情况，副手与其他物品保持原版。
 * Fabric 没有等价的“取消手部渲染”事件，所以必须在原版类注入（docs/mixins.md 登记）。
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void realfishing$hideVanillaHand(AbstractClientPlayer player, float partialTicks, float pitch,
                                             InteractionHand hand, float swingProgress, ItemStack stack,
                                             float equipProgress, PoseStack poseStack,
                                             MultiBufferSource buffer, int combinedLight, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND && stack.getItem() instanceof RodItem) {
            ci.cancel();
        }
    }
}
