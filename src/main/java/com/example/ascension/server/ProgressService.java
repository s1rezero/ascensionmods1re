package com.example.ascension.server;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.NodeDef;
import com.example.ascension.data.Nodes;
import com.example.ascension.data.PlayerProgress;
import com.example.ascension.data.Progression;
import com.example.ascension.network.ProgressSyncPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ProgressService {
    private ProgressService() {}

    public static PlayerProgress get(ServerPlayer player) {
        return player.getData(AscensionMod.PROGRESS);
    }

    public static void sync(ServerPlayer player) {
        PlayerProgress p = get(player);
        p.validateSlots();
        PacketDistributor.sendToPlayer(player,
                new ProgressSyncPayload(p.totalXp, p.unlocked.toLongArray(), p.introSeen, p.slots.clone()));
    }

    /** Adds mod XP, tells the player when they earn a point, and syncs. */
    public static void addXp(ServerPlayer player, long amount) {
        if (amount <= 0) return;
        PlayerProgress p = get(player);
        int pointsBefore = Progression.pointsForLevel(p.level());
        p.totalXp += amount;
        int pointsAfter = Progression.pointsForLevel(p.level());
        sync(player);
        if (pointsAfter > pointsBefore) {
            player.displayClientMessage(Component.literal("\u2726 Upgrade point gained  (press . to open your tree)")
                    .withStyle(ChatFormatting.AQUA), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.PLAYERS, 0.9F, 1.3F);
        }
    }

    /** Re-applies every unlocked orb's stat bonus to the player (safe to call any time). */
    public static void applyStats(ServerPlayer player) {
        PlayerProgress progress = get(player);
        for (NodeDef node : Nodes.ALL) {
            Holder<Attribute> attribute = node.effect().attribute();
            if (attribute == null) continue;
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) continue;
            instance.removeModifier(node.modifierId());
            if (progress.isUnlocked(node.id())) {
                instance.addTransientModifier(
                        new AttributeModifier(node.modifierId(), node.value(), node.effect().operation()));
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
