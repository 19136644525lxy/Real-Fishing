package dev.realfishing.command;

import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.realfishing.RealFishing;
import dev.realfishing.fishing.FishingManager;
import dev.realfishing.platform.Platform;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /realfishing 调试命令：version、debug state、debug fight（跳过等漂直接进搏鱼，M4 接鱼表前调参用）。
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
                }))
                .then(Commands.literal("debug")
                        .then(Commands.literal("fight")
                                .executes(context -> startFight(context.getSource(), context.getSource().getPlayerOrException(), 1))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> startFight(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"), 1)))
                                .then(Commands.literal("small").executes(context -> startFight(context.getSource(),
                                        context.getSource().getPlayerOrException(), 0)))
                                .then(Commands.literal("medium").executes(context -> startFight(context.getSource(),
                                        context.getSource().getPlayerOrException(), 1)))
                                .then(Commands.literal("big").executes(context -> startFight(context.getSource(),
                                        context.getSource().getPlayerOrException(), 2))))));
    }

    private static int startFight(CommandSourceStack source, ServerPlayer player, int tier) {
        boolean ok = FishingManager.debugStartFight(player, tier);
        if (!ok) {
            source.sendFailure(Component.literal("需要先抛竿并让浮漂落水"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("debug fight started: tier=" + tier), false);
        return 1;
    }
}
