package dev.realfishing.client;

import dev.realfishing.RealFishing;
import dev.realfishing.registry.ModItems;
import net.minecraft.client.Minecraft;
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
                    client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
                    RealFishing.LOGGER.info("[M1] autocast: using rod, item={}", client.player.getMainHandItem());
                    stage = 2;
                }
            }
            case 2 -> {
                if (age > 100) {
                    client.gameMode.releaseUsingItem(client.player);
                    RealFishing.LOGGER.info("[M1] autocast: released");
                    stage = 3;
                }
            }
            default -> {
            }
        }
    }
}
