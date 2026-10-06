package com.example.ascension.client;

import com.example.ascension.data.Ability;

import net.minecraft.client.Minecraft;

/** Client-side record of when each ability comes off cooldown (in game ticks). */
public final class ClientCooldowns {
    private static final long[] END = new long[Ability.COUNT];
    private static final int[] TOTAL = new int[Ability.COUNT];

    static {
        java.util.Arrays.fill(TOTAL, 1);
    }

    private ClientCooldowns() {}

    public static void start(int ability, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ability < 0 || ability >= Ability.COUNT || mc.level == null) return;
        END[ability] = mc.level.getGameTime() + ticks;
        TOTAL[ability] = Math.max(1, ticks);
    }

    /** Remaining cooldown in ticks (0 when ready). */
    public static float remaining(int ability, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (ability < 0 || ability >= Ability.COUNT || mc.level == null) return 0;
        return Math.max(0f, END[ability] - (mc.level.getGameTime() + partialTick));
    }

    public static float fraction(int ability, float partialTick) {
        if (ability < 0 || ability >= Ability.COUNT) return 0f;
        return remaining(ability, partialTick) / TOTAL[ability];
    }

    public static void clear() {
        java.util.Arrays.fill(END, 0);
        java.util.Arrays.fill(TOTAL, 1);
    }
}
