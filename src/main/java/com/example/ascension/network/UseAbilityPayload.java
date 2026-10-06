package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: the player pressed the key for this slot. */
public record UseAbilityPayload(int slot) implements CustomPacketPayload {
    public static final Type<UseAbilityPayload> TYPE = new Type<>(AscensionMod.id("use_ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, UseAbilityPayload::slot, UseAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
