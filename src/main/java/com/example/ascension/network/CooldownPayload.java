package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: this ability is now on cooldown for this many ticks. */
public record CooldownPayload(int ability, int ticks) implements CustomPacketPayload {
    public static final Type<CooldownPayload> TYPE = new Type<>(AscensionMod.id("cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CooldownPayload::ability, ByteBufCodecs.VAR_INT, CooldownPayload::ticks, CooldownPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
