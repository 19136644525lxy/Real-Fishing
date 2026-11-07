package dev.realfishing.net;

import dev.architectury.networking.NetworkManager;
import dev.realfishing.RealFishing;
import dev.realfishing.fishing.FishingManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 客户端 → 服务端：松手抛投，携带蓄力值与松手瞬间的视角（SPEC §15.1）。
 * 不负责：落点计算与合法性判定（FishingManager.cast 负责，越权数据一律丢弃）。
 */
public record CastC2S(float charge, float yaw, float pitch) {
    public static final ResourceLocation ID = new ResourceLocation(RealFishing.MOD_ID, "cast");

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(charge);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
    }

    public static CastC2S decode(FriendlyByteBuf buf) {
        return new CastC2S(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    /** 客户端调用：编码后发包。缓冲区由 NetworkManager 接管。 */
    public void sendToServer() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        write(buf);
        NetworkManager.sendToServer(ID, buf);
    }

    public static void register() {
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, ID, (buf, context) -> {
            CastC2S msg = decode(buf);
            context.queue(() -> {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    FishingManager.cast(player, msg);
                }
            });
        });
    }
}
