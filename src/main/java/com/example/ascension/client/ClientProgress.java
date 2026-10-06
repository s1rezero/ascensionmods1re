package com.example.ascension.client;

import java.util.BitSet;

import com.example.ascension.data.PlayerProgress;

/** The client's copy of the player's progress, updated by the server. */
public final class ClientProgress {
    private static PlayerProgress current = new PlayerProgress();

    private ClientProgress() {}

    public static PlayerProgress get() {
        return current;
    }

    public static void set(long xp, long[] words, boolean introSeen, int[] slots) {
        PlayerProgress p = new PlayerProgress();
        p.totalXp = xp;
        p.unlocked = BitSet.valueOf(words);
        p.introSeen = introSeen;
        for (int i = 0; i < PlayerProgress.SLOTS && i < slots.length; i++) p.slots[i] = slots[i];
        current = p;
    }

    public static void clear() {
        current = new PlayerProgress();
        ClientCooldowns.clear();
    }
}
