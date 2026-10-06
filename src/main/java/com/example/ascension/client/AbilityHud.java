package com.example.ascension.client;

import org.joml.Matrix4f;

import com.example.ascension.data.Ability;
import com.example.ascension.data.PlayerProgress;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.Util;

/** The four ability keys in the bottom-right corner of the screen, with cooldown overlays. */
public final class AbilityHud {
    private static final int BOX = 30;
    private static final int GAP = 5;

    private AbilityHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof AscensionScreen) return;
        PlayerProgress progress = ClientProgress.get();

        boolean any = false;
        for (int s : progress.slots) any |= s >= 0;
        if (!any) return;

        float partial = delta.getGameTimeDeltaPartialTick(false);
        float time = (Util.getMillis() % 100000L) / 1000f;
        int total = PlayerProgress.SLOTS * BOX + (PlayerProgress.SLOTS - 1) * GAP;
        int x0 = g.guiWidth() - total - 10;
        int y0 = g.guiHeight() - BOX - 10;

        // soft glow behind ready abilities
        g.flush();
        Matrix4f m = g.pose().last().pose();
        Gfx.beginTex(Gfx.GLOW, true);
        for (int i = 0; i < PlayerProgress.SLOTS; i++) {
            int a = progress.slots[i];
            if (a < 0 || ClientCooldowns.remaining(a, partial) > 0) continue;
            float pulse = 0.5f + 0.5f * Mth.sin(time * 3f + i);
            Gfx.tex(m, x0 + i * (BOX + GAP) + BOX / 2f, y0 + BOX / 2f, BOX * 2.2f, 0,
                    Gfx.argb(0.25f + 0.15f * pulse, 0x6CB8FF));
        }
        Gfx.endTex();

        for (int i = 0; i < PlayerProgress.SLOTS; i++) {
            int x = x0 + i * (BOX + GAP);
            Ability ability = Ability.of(progress.slots[i]);
            g.fill(x - 1, y0 - 1, x + BOX + 1, y0 + BOX + 1, 0xCC4F8FD6);
            g.fill(x, y0, x + BOX, y0 + BOX, 0xEE060C20);
            String key = ClientEvents.ABILITY_KEYS[i].getTranslatedKeyMessage().getString();
            g.drawString(mc.font, key, x + 3, y0 + 3, 0xFFBFE3FF, false);
            if (ability == null) {
                g.drawString(mc.font, "-", x + BOX / 2 - 2, y0 + BOX / 2 - 3, 0xFF3C4F7A, false);
                continue;
            }
            String name = shortName(ability);
            g.pose().pushPose();
            g.pose().translate(x + BOX / 2f, y0 + 15f, 0f);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.drawString(mc.font, name, -mc.font.width(name) / 2, 0, 0xFFEAF8FF, false);
            g.pose().popPose();

            float remaining = ClientCooldowns.remaining(ability.ordinal(), partial);
            if (remaining > 0) {
                float frac = Mth.clamp(ClientCooldowns.fraction(ability.ordinal(), partial), 0f, 1f);
                int h = (int) (BOX * frac);
                g.fill(x, y0 + BOX - h, x + BOX, y0 + BOX, 0xAA10244E);
                String sec = remaining >= 200 ? Integer.toString((int) (remaining / 20f)) : String.format("%.1f", remaining / 20f);
                g.drawString(mc.font, sec, x + BOX / 2 - mc.font.width(sec) / 2, y0 + BOX - 11, 0xFFFFFFFF, false);
            }
        }
    }

    private static String shortName(Ability a) {
        String[] words = a.label.split(" ");
        return words[0];
    }
}
