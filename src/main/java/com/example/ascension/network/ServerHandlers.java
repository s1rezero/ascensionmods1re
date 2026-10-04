package com.example.ascension.network;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.NodeDef;
import com.example.ascension.data.Nodes;
import com.example.ascension.data.PlayerProgress;
import com.example.ascension.server.ProgressService;

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
            ProgressService.applyStats(player);
            ProgressService.sync(player);
            if (node.milestone()) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        });
    }
}
