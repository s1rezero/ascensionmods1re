package com.example.ascension.server;

import com.example.ascension.data.PlayerProgress;
import com.example.ascension.data.Progression;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.example.ascension.AscensionMod;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Testing helpers (operators only): addlevels, addxp, respec, reset, intro (replays the opening cinematic). */
@EventBusSubscriber(modid = AscensionMod.MODID)
public final class AscensionCommands {
    private AscensionCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ascension")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("addlevels")
                        .then(Commands.argument("levels", IntegerArgumentType.integer(1, 2000))
                                .executes(ctx -> addLevels(ctx, IntegerArgumentType.getInteger(ctx, "levels")))))
                .then(Commands.literal("addxp")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> addXp(ctx, LongArgumentType.getLong(ctx, "amount")))))
                .then(Commands.literal("intro").executes(AscensionCommands::intro))
                .then(Commands.literal("respec").executes(AscensionCommands::respec))
                .then(Commands.literal("reset").executes(AscensionCommands::reset)));
    }

    private static int addLevels(CommandContext<CommandSourceStack> ctx, int levels) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgress p = ProgressService.get(player);
        long target = Progression.xpForLevel(p.level() + levels);
        ProgressService.addXp(player, target - p.totalXp);
        ctx.getSource().sendSuccess(() -> Component.literal("Ascension level is now " + p.level()), false);
        return 1;
    }

    private static int addXp(CommandContext<CommandSourceStack> ctx, long amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ProgressService.addXp(player, amount);
        ctx.getSource().sendSuccess(() -> Component.literal("Added " + amount + " ascension XP"), false);
        return 1;
    }

    private static int intro(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ProgressService.get(player).introSeen = false;
        ProgressService.sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal("The opening cinematic will play the next time you open the tree"), false);
        return 1;
    }

    private static int respec(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgress progress = ProgressService.get(player);
        progress.unlocked.clear();
        java.util.Arrays.fill(progress.slots, -1);
        ProgressService.applyStats(player);
        ProgressService.sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal("All upgrades refunded"), false);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PlayerProgress p = ProgressService.get(player);
        p.unlocked.clear();
        java.util.Arrays.fill(p.slots, -1);
        p.totalXp = 0;
        ProgressService.applyStats(player);
        ProgressService.sync(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Ascension progress reset"), false);
        return 1;
    }
}
