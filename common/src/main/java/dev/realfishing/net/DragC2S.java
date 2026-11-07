package dev.realfishing.net;

import dev.architectury.networking.NetworkManager;
import dev.realfishing.RealFishing;
import dev.realfishing.fishing.FishingManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 → 服务端：设置泄力档位（0..1，SPEC §11.1 的 [ / ] 或滚轮）。
 * 服务端只接受 0..1 的有限值，其余丢弃。
 */
public record DragC2S(float drag) {
    public static final ResourceLocation ID = new ResourceLocation(RealFishing.MOD_ID, "drag");

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(drag);
    }

    public static DragC2S decode(FriendlyByteBuf buf) {
        return new DragC2S(buf.readFloat());
    }

    public void sendToServer() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        write(buf);
        NetworkManager.sendToServer(ID, buf);
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ID, (buf, context) -> {
            DragC2S msg = decode(buf);
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    FishingManager.drag(player, msg.drag());
                }
            });
        });
    }
}
