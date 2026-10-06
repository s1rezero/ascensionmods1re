package com.example.ascension.server;

import com.example.ascension.AscensionMod;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

@EventBusSubscriber(modid = AscensionMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    /** Every point of XP the player actually gains also counts toward the upgrade tree. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onXp(PlayerXpEvent.XpChange event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof ServerPlayer player) {
            ProgressService.addXp(player, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityEffects.cleanupStaleFlight(player);
            ProgressService.applyStats(player);
            ProgressService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityManager.endAll(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AbilityEffects.cleanupStaleFlight(player);
            ProgressService.applyStats(player);
            ProgressService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProgressService.applyStats(player);
            ProgressService.sync(player);
        }
    }
}
