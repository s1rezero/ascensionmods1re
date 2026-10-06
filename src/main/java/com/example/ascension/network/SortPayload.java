package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: sort the open container (external=true) or your own inventory (false). */
public record SortPayload(boolean external) implements CustomPacketPayload {
    public static final Type<SortPayload> TYPE = new Type<>(AscensionMod.id("sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, SortPayload::external, SortPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
