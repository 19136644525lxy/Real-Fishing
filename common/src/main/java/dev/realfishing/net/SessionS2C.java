package dev.realfishing.net;

import dev.architectury.networking.NetworkManager;
import dev.realfishing.RealFishing;
import dev.realfishing.fishing.FishingState;
import dev.realfishing.platform.Platform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：10 Hz 会话同步（SPEC §15.3）。浮漂位置按 1/16 格量化，张力/体力按 byte 量化。
 * 不负责：客户端插值（ClientFight 负责），客户端只把它当作"当前权威快照"。
 */
public record SessionS2C(int sessionId, byte state, byte tension, byte stamina, float lineLength,
                         double x, double y, double z) {
    public static final ResourceLocation ID = new ResourceLocation(RealFishing.MOD_ID, "session");

    private static byte quantize(double v) {
        return (byte) Math.max(0, Math.min(255, Math.round(v / 1.5 * 255.0)));
    }

    private static double dequantize(byte b) {
        return (b & 0xFF) / 255.0 * 1.5;
    }

    public static SessionS2C of(int sessionId, FishingState state, double tension, double stamina,
                                double lineLength, double x, double y, double z) {
        return new SessionS2C(sessionId, state.id(), quantize(tension), quantize(stamina),
                (float) lineLength,
                Math.round(x * 16.0) / 16.0, Math.round(y * 16.0) / 16.0, Math.round(z * 16.0) / 16.0);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeByte(state);
        buf.writeByte(tension);
        buf.writeByte(stamina);
        buf.writeFloat(lineLength);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    public static SessionS2C decode(FriendlyByteBuf buf) {
        return new SessionS2C(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public double tensionValue() {
        return dequantize(tension);
    }

    public double staminaValue() {
        return dequantize(stamina);
    }

    public FishingState stateValue() {
        return FishingState.byId(state);
    }

    /** 只在客户端注册：S2C 接收器在专用服务器上注册会抛异常。 */
    public static void register() {
        if (!Platform.get().isClient()) {
            return;
        }
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, ID, (buf, context) -> {
            SessionS2C msg = decode(buf);
            context.queue(() -> dev.realfishing.client.ClientFight.accept(msg));
        });
    }
}
