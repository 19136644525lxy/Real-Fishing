package dev.realfishing.client;

import dev.realfishing.RealFishing;
import dev.realfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 开发期冒烟测试：设置环境变量 REALFISHING_AUTOCAST=1 时，进世界后自动走一遍
 * "/give → 右键蓄力 → 松手" 的真实客户端流程，用于没有键鼠输入的自动化环境验证抛投链路。
 * 不设置环境变量时第一 tick 就自我关闭，零开销；不影响正式玩法。
 */
final class DebugAutoCast {
    private static int stage;
    private static boolean disabled;

    private DebugAutoCast() {
    }

    static void tick(Minecraft client) {
        if (disabled) {
            return;
        }
        if (!"1".equals(System.getenv("REALFISHING_AUTOCAST"))) {
            disabled = true;
            return;
        }
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        int age = client.player.tickCount;
        switch (stage) {
            case 0 -> {
                if (age > 40) {
                    // 单人存档可能没开作弊，走 /give 会被拒；直接让内置服务端把竿塞进 0 号快捷栏。
                    var server = client.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> {
                            if (server.getPlayerList().getPlayers().isEmpty()) {
                                return;
                            }
                            var player = server.getPlayerList().getPlayers().get(0);
                            player.getInventory().setItem(0, new ItemStack(ModItems.FLOAT_ROD.get()));
                            player.getInventory().selected = 0;
                            RealFishing.LOGGER.info("[M1] autocast: rod placed in slot 0");
                        });
                    }
                    stage = 1;
                }
            }
            case 1 -> {
                if (age > 60) {
                    // 必须同时把"使用键"按住：Minecraft.handleKeybinds 每 tick 检查鼠标右键，
                    // 键没按下时会立刻 releaseUsingItem，导致蓄力永远是 0 tick。
                    client.options.keyUse.setDown(true);
                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                    RealFishing.LOGGER.info("[M1] autocast: using rod, item={}", client.player.getMainHandItem());
                    stage = 2;
                }
            }
            case 2 -> {
                if (age > 100) {
                    // 蓄满 40 tick（约 0.45 → 1.0 蓄力），验证满格也能正常松手抛出。
                    client.options.keyUse.setDown(false);
                    RealFishing.LOGGER.info("[M1] autocast: released");
                    stage = 3;
                }
            }
            case 3 -> {
                if (age > 116) {
                    shot(client, "autocast-flight.png");
                    stage = 4;
                }
            }
            case 4 -> {
                if (age > 180) {
                    shot(client, "autocast-landed.png");
                    stage = 5;
                }
            }
            default -> {
            }
        }
    }

    /** 没有外部截图能力时的自检：让游戏自己把画面存到 screenshots/。 */
    private static void shot(Minecraft client, String name) {
        if (!"1".equals(System.getenv("REALFISHING_SHOT"))) {
            return;
        }
        var line = ClientLineSim.line();
        if (client.level != null && client.player != null) {
            for (var b : client.level.getEntitiesOfClass(dev.realfishing.entity.BobberEntity.class,
                    client.player.getBoundingBox().inflate(40.0))) {
                RealFishing.LOGGER.info("[M2] bobber entity id={} pos={} predicted={} landed={} visible={}",
                        b.getId(), b.position(), b.isPredicted(), b.isLanded(), !b.isInvisible());
            }
        }
        if (line != null) {
            var cam = client.gameRenderer.getMainCamera();
            RealFishing.LOGGER.info("[M2] shot {} cam={} look={} node0=({},{},{}) node23=({},{},{})",
                    name, cam.getPosition(), new net.minecraft.world.phys.Vec3(cam.getLookVector()),
                    line.nodeX(0), line.nodeY(0), line.nodeZ(0),
                    line.nodeX(dev.realfishing.physics.FishingLine.NODES - 1),
                    line.nodeY(dev.realfishing.physics.FishingLine.NODES - 1),
                    line.nodeZ(dev.realfishing.physics.FishingLine.NODES - 1));
        }
        Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> {
        });
        RealFishing.LOGGER.info("[M1] autocast: screenshot {}", name);
    }
}
