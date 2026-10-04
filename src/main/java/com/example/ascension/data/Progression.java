package com.example.ascension.data;

/**
 * Mod level curve. Mod XP is tracked separately from vanilla levels, so spending levels on enchanting never costs
 * you progress. Tune these numbers to change how fast the tree fills.
 */
public final class Progression {
    /** Every this-many levels grants one upgrade point. */
    public static final int LEVELS_PER_POINT = 8;

    private Progression() {}

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static long costOfLevel(int level) {
        return 10 + level / 2;
    }

    public static int levelFor(long totalXp) {
        long xp = Math.max(0, totalXp);
        int level = 0;
        while (level < 100_000) {
            long cost = costOfLevel(level);
            if (xp < cost) break;
            xp -= cost;
            level++;
        }
        return level;
    }

    /** Total XP at which {@code level} is reached. */
    public static long xpForLevel(int level) {
        long sum = 0;
        for (int i = 0; i < level; i++) {
            sum += costOfLevel(i);
        }
        return sum;
    }

    public static int pointsForLevel(int level) {
        return level / LEVELS_PER_POINT;
    }
}
