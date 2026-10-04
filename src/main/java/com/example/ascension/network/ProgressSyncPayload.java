package com.example.ascension.network;

import com.example.ascension.AscensionMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: your total mod XP and the unlocked-orb bitset. */
public record ProgressSyncPayload(long xp, long[] words) implements CustomPacketPayload {
    public static final Type<ProgressSyncPayload> TYPE = new Type<>(AscensionMod.id("progress_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSyncPayload> STREAM_CODEC =
            StreamCodec.of(ProgressSyncPayload::write, ProgressSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ProgressSyncPayload p) {
        buf.writeVarLong(p.xp);
        buf.writeVarInt(p.words.length);
        for (long w : p.words) buf.writeLong(w);
    }

    private static ProgressSyncPayload read(RegistryFriendlyByteBuf buf) {
        long xp = buf.readVarLong();
        int n = Math.min(buf.readVarInt(), 16);
        long[] words = new long[n];
        for (int i = 0; i < n; i++) words[i] = buf.readLong();
        return new ProgressSyncPayload(xp, words);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
