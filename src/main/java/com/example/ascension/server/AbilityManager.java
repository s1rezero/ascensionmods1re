package com.example.ascension.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.ascension.data.Ability;
import com.example.ascension.data.PlayerProgress;
import com.example.ascension.network.CooldownPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cooldowns and timed ability effects (kept in memory; a restart simply resets cooldowns). */
public final class AbilityManager {
    /** An effect that keeps running for a while after the key press. */
    public interface Tickable {
        /** @return false when finished */
        boolean tick(ServerPlayer player);

        void end(ServerPlayer player);
    }

    private static final Map<UUID, long[]> COOLDOWN_END = new HashMap<>();
    private static final Map<UUID, List<Tickable>> ACTIVE = new HashMap<>();

    private AbilityManager() {}

    public static void use(ServerPlayer player, int slot) {
        if (slot < 0 || slot >= PlayerProgress.SLOTS) return;
        PlayerProgress progress = ProgressService.get(player);
        Ability ability = Ability.of(progress.slots[slot]);
        if (ability == null || ability.passive || !progress.has(ability)) return;

        long now = player.level().getGameTime();
        long[] ends = COOLDOWN_END.computeIfAbsent(player.getUUID(), k -> new long[Ability.COUNT]);
        long end = ends[ability.ordinal()];
        if (end > now) {
            long seconds = (end - now + 19) / 20;
            player.displayClientMessage(Component.literal(ability.label + " is recharging  (" + seconds + "s)")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!AbilityEffects.activate(player, ability)) {
            return; // nothing happened, so no cooldown
        }
        int ticks = cooldownFor(ability, progress);
        ends[ability.ordinal()] = now + ticks;
        PacketDistributor.sendToPlayer(player, new CooldownPayload(ability.ordinal(), ticks));
    }

    /** Base cooldown reduced by the "Ability Cooldown" orbs (never below half). */
    public static int cooldownFor(Ability ability, PlayerProgress progress) {
        return ability.cooldownFor(progress);
    }

    public static void start(ServerPlayer player, Tickable effect) {
        ACTIVE.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(effect);
    }

    public static boolean hasActive(ServerPlayer player) {
        List<Tickable> list = ACTIVE.get(player.getUUID());
        return list != null && !list.isEmpty();
    }

    public static void tick(ServerPlayer player) {
        List<Tickable> list = ACTIVE.get(player.getUUID());
        if (list == null || list.isEmpty()) return;
        Iterator<Tickable> it = list.iterator();
        while (it.hasNext()) {
            Tickable t = it.next();
            if (!player.isAlive()) {
                t.end(player);
                it.remove();
            } else if (!t.tick(player)) {
                t.end(player);
                it.remove();
            }
        }
        if (list.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        }
    }

    public static void endAll(ServerPlayer player) {
        List<Tickable> list = ACTIVE.remove(player.getUUID());
        if (list != null) {
            for (Tickable t : list) t.end(player);
        }
    }
}
