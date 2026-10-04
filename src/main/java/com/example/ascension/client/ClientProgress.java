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

    public static void set(long xp, long[] words) {
        PlayerProgress p = new PlayerProgress();
        p.totalXp = xp;
        p.unlocked = BitSet.valueOf(words);
        current = p;
    }

    public static void clear() {
        current = new PlayerProgress();
    }
}
