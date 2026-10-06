package com.example.ascension.network;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.Ability;
import com.example.ascension.data.NodeDef;
import com.example.ascension.data.Nodes;
import com.example.ascension.data.PlayerProgress;
import com.example.ascension.server.AbilityManager;
import com.example.ascension.server.ProgressService;
import com.example.ascension.server.Sorter;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerHandlers {
    private ServerHandlers() {}

    public static void onUnlock(UnlockNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int id = payload.nodeId();
            if (id < 0 || id >= Nodes.COUNT) return;

            PlayerProgress progress = player.getData(AscensionMod.PROGRESS);
            NodeDef node = Nodes.get(id);
            if (progress.check(node) != PlayerProgress.Check.OK) {
                ProgressService.sync(player); // client was out of date; correct it
                return;
            }
            progress.unlocked.set(id);

            // a newly unlocked ability goes on the first free key automatically
            Ability ability = Ability.ofNode(id);
            if (ability != null && !ability.passive) {
                for (int i = 0; i < PlayerProgress.SLOTS; i++) {
                    if (progress.slots[i] < 0) {
                        progress.slots[i] = ability.ordinal();
                        break;
                    }
                }
            }

            ProgressService.applyStats(player);
            ProgressService.sync(player);

            ServerLevel level = player.serverLevel();
            if (node.milestone()) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(),
                        70, 0.7, 0.9, 0.7, 0.14);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.4F);
            } else {
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(),
                        18, 0.5, 0.6, 0.5, 0.6);
            }
        });
    }

    public static void onSetSlot(SetSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            int slot = payload.slot();
            if (slot < 0 || slot >= PlayerProgress.SLOTS) return;
            PlayerProgress progress = player.getData(AscensionMod.PROGRESS);

            Ability ability = Ability.of(payload.ability());
            if (payload.ability() < 0) {
                progress.slots[slot] = -1;
            } else if (ability != null && !ability.passive && progress.has(ability)) {
                int other = progress.slotOf(ability);
                if (other >= 0) progress.slots[other] = -1;   // an ability lives on one key at a time
                progress.slots[slot] = ability.ordinal();
            }
            ProgressService.sync(player);
        });
    }

    public static void onUseAbility(UseAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AbilityManager.use(player, payload.slot());
            }
        });
    }

    public static void onSort(SortPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.getData(AscensionMod.PROGRESS).has(Ability.SORT_BUTTON)) return;
            Sorter.sort(player, payload.external());
        });
    }

    public static void onIntroSeen(IntroSeenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            player.getData(AscensionMod.PROGRESS).introSeen = true;
            ProgressService.sync(player);
        });
    }
}
