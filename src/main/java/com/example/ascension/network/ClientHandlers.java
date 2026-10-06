package com.example.ascension.network;

import com.example.ascension.client.ClientCooldowns;
import com.example.ascension.client.ClientProgress;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientHandlers {
    private ClientHandlers() {}

    public static void onSync(ProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientProgress.set(payload.xp(), payload.words(), payload.introSeen(), payload.slots()));
    }

    public static void onCooldown(CooldownPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCooldowns.start(payload.ability(), payload.ticks()));
    }
}
