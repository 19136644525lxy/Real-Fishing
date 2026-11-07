package dev.realfishing.net;

import dev.architectury.networking.NetworkManager;
import dev.realfishing.RealFishing;
import dev.realfishing.fishing.FishingManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 → 服务端：抬竿（左键点按，SPEC §11.1）。每次一个事件，不带数据。
 */
public record PumpC2S() {
    public static final ResourceLocation ID = new ResourceLocation(RealFishing.MOD_ID, "pump");

    public void sendToServer() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        NetworkManager.sendToServer(ID, buf);
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ID, (buf, context) -> context.queue(() -> {
            if (context.getPlayer() instanceof ServerPlayer player) {
                FishingManager.pump(player);
            }
        }));
    }
}
