package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: put an ability (or -1 for none) on one of the four keys. */
public record SetSlotPayload(int slot, int ability) implements CustomPacketPayload {
    public static final Type<SetSlotPayload> TYPE = new Type<>(AscensionMod.id("set_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSlotPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SetSlotPayload::slot, ByteBufCodecs.INT, SetSlotPayload::ability, SetSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
