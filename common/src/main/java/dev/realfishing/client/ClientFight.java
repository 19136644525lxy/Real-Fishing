package dev.realfishing.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.realfishing.fishing.FishingState;
import dev.realfishing.net.DragC2S;
import dev.realfishing.net.ReelC2S;
import dev.realfishing.net.PumpC2S;
import dev.realfishing.net.SessionS2C;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端搏鱼侧：保存服务端 10 Hz 快照、采集左键抬竿 / [ ] 泄力，并把输入发回服务端。
 * 不负责：张力与鱼的行为（服务端 FightModel 负责），也不画 HUD（FishingHud 负责）。
 */
public final class ClientFight {
    private static final String CATEGORY = "key.realfishing.category";

    public static KeyMapping dragUp;
    public static KeyMapping dragDown;

    private static int sessionId = -1;
    private static FishingState state = FishingState.IDLE;
    private static double tension;
    private static double stamina;
    private static double lineLength;
    private static double bobberX;
    private static double bobberY;
    private static double bobberZ;

    private static double drag = 0.2;
    private static boolean prevAttack;
    private static boolean prevUse;
    private static boolean prevDragUp;
    private static boolean prevDragDown;

    private ClientFight() {
    }

    public static void register() {
        dragUp = new KeyMapping("key.realfishing.drag_up", GLFW.GLFW_KEY_RIGHT_BRACKET, CATEGORY);
        dragDown = new KeyMapping("key.realfishing.drag_down", GLFW.GLFW_KEY_LEFT_BRACKET, CATEGORY);
        KeyMappingRegistry.register(dragUp);
        KeyMappingRegistry.register(dragDown);
        ClientTickEvent.CLIENT_POST.register(ClientFight::tick);
    }

    /** 服务端快照入口：只接受不比当前旧的 sessionId，避免快速重抛串状态。 */
    public static void accept(SessionS2C msg) {
        if (msg.sessionId() < sessionId) {
            return;
        }
        sessionId = msg.sessionId();
        state = msg.stateValue();
        tension = msg.tensionValue();
        stamina = msg.staminaValue();
        lineLength = msg.lineLength();
        bobberX = msg.x();
        bobberY = msg.y();
        bobberZ = msg.z();
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        boolean attack = minecraft.options.keyAttack.isDown();
        if (attack && !prevAttack && state == FishingState.FIGHTING) {
            new PumpC2S().sendToServer();
        }
        prevAttack = attack;

        // 右键：BITE 时按下 = 刺鱼，FIGHTING 时按住 = 收线/松开 = 停。
        boolean use = minecraft.options.keyUse.isDown();
        if (use && !prevUse && (state == FishingState.BITE || state == FishingState.FIGHTING)) {
            new ReelC2S(true).sendToServer();
        } else if (!use && prevUse && state == FishingState.FIGHTING) {
            new ReelC2S(false).sendToServer();
        }
        prevUse = use;

        boolean up = dragUp.isDown();
        boolean down = dragDown.isDown();
        if (up && !prevDragUp) {
            setDrag(drag + 0.1);
        } else if (down && !prevDragDown) {
            setDrag(drag - 0.1);
        }
        prevDragUp = up;
        prevDragDown = down;
    }

    private static void setDrag(double next) {
        drag = Mth.clamp(next, 0.0, 1.0);
        new DragC2S((float) drag).sendToServer();
    }

    /** 收到断线/收竿时由服务端状态驱动，本地不自行清空。 */
    public static void clear() {
        state = FishingState.IDLE;
        tension = 0.0;
        stamina = 0.0;
    }

    public static FishingState state() {
        return state;
    }

    public static double tension() {
        return tension;
    }

    public static double stamina() {
        return stamina;
    }

    public static double lineLength() {
        return lineLength;
    }

    public static double drag() {
        return drag;
    }

    public static double bobberX() {
        return bobberX;
    }

    public static double bobberY() {
        return bobberY;
    }

    public static double bobberZ() {
        return bobberZ;
    }

    public static boolean fighting() {
        return state == FishingState.FIGHTING || state == FishingState.LANDING;
    }
}
