package dev.realfishing.net;

import dev.architectury.networking.NetworkManager;
import dev.realfishing.RealFishing;
import dev.realfishing.fishing.FishingManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 → 服务端：收线键的按下/松开（SPEC §11.1）。BITE 期间按下表示"刺鱼"。
 * 不负责：张力与放线计算（服务端 FightModel 负责）。
 */
public record ReelC2S(boolean reeling) {
    public static final ResourceLocation ID = new ResourceLocation(RealFishing.MOD_ID, "reel");

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(reeling);
    }

    public static ReelC2S decode(FriendlyByteBuf buf) {
        return new ReelC2S(buf.readBoolean());
    }

    public void sendToServer() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        write(buf);
        NetworkManager.sendToServer(ID, buf);
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ID, (buf, context) -> {
            ReelC2S msg = decode(buf);
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    FishingManager.reel(player, msg.reeling());
                }
            });
        });
    }
}
