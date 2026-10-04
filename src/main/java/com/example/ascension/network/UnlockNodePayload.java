package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: "I clicked this orb". The server re-checks everything before unlocking. */
public record UnlockNodePayload(int nodeId) implements CustomPacketPayload {
    public static final Type<UnlockNodePayload> TYPE = new Type<>(AscensionMod.id("unlock_node"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnlockNodePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, UnlockNodePayload::nodeId, UnlockNodePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
