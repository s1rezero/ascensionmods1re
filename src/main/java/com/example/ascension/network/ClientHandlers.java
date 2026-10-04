package com.example.ascension.network;

import com.example.ascension.client.ClientProgress;

import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientHandlers {
    private ClientHandlers() {}

    public static void onSync(ProgressSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientProgress.set(payload.xp(), payload.words()));
    }
}
