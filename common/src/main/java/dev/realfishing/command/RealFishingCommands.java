package dev.realfishing.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.realfishing.RealFishing;
import dev.realfishing.platform.Platform;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /realfishing 调试命令骨架。M0 只有 version；debug line/state/packets 按 SPEC §7 在后续里程碑填。
 * 不负责：任何玩法逻辑，只做查询与调试开关。
 */
public final class RealFishingCommands {
    private RealFishingCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register(RealFishingCommands::onRegister);
    }

    private static void onRegister(CommandDispatcher<CommandSourceStack> dispatcher,
                                   CommandBuildContext buildContext,
                                   Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("realfishing")
                .then(Commands.literal("version").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Real Fishing " + RealFishing.VERSION
                                    + " (" + Platform.get().loaderName() + ")"), false);
                    return 1;
                })));
    }
}
