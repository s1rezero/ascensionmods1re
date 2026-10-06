package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the opening cinematic has been played. */
public record IntroSeenPayload() implements CustomPacketPayload {
    public static final Type<IntroSeenPayload> TYPE = new Type<>(AscensionMod.id("intro_seen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IntroSeenPayload> STREAM_CODEC =
            StreamCodec.unit(new IntroSeenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
