package com.example.ascension.network;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.PlayerProgress;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: your total mod XP, the unlocked-orb bitset, your key slots and whether you've seen the intro. */
public record ProgressSyncPayload(long xp, long[] words, boolean introSeen, int[] slots) implements CustomPacketPayload {
    public static final Type<ProgressSyncPayload> TYPE = new Type<>(AscensionMod.id("progress_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressSyncPayload> STREAM_CODEC =
            StreamCodec.of(ProgressSyncPayload::write, ProgressSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ProgressSyncPayload p) {
        buf.writeVarLong(p.xp);
        buf.writeVarInt(p.words.length);
        for (long w : p.words) buf.writeLong(w);
        buf.writeBoolean(p.introSeen);
        for (int i = 0; i < PlayerProgress.SLOTS; i++) buf.writeVarInt(p.slots[i] + 1);
    }

    private static ProgressSyncPayload read(RegistryFriendlyByteBuf buf) {
        long xp = buf.readVarLong();
        int n = Math.min(buf.readVarInt(), 16);
        long[] words = new long[n];
        for (int i = 0; i < n; i++) words[i] = buf.readLong();
        boolean intro = buf.readBoolean();
        int[] slots = new int[PlayerProgress.SLOTS];
        for (int i = 0; i < slots.length; i++) slots[i] = buf.readVarInt() - 1;
        return new ProgressSyncPayload(xp, words, intro, slots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
