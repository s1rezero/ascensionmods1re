package com.example.ascension.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.example.ascension.AscensionMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Loudness of the intro voice over time (0..1), measured ahead of time so the orb can pulse in sync with it. */
public final class IntroEnvelope {
    public static final float VOICE_SECONDS = 4.96f;

    private static float[] values;
    private static float window = 0.04f;

    private IntroEnvelope() {}

    private static void load() {
        if (values != null) return;
        try {
            ResourceLocation file = AscensionMod.id("intro_envelope.json");
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(file);
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                window = root.get("windowMs").getAsFloat() / 1000f;
                JsonArray arr = root.getAsJsonArray("values");
                float[] v = new float[arr.size()];
                for (int i = 0; i < v.length; i++) v[i] = arr.get(i).getAsFloat();
                values = v;
            }
        } catch (Exception e) {
            // fall back to a gentle synthetic breathing pattern so the intro still works
            values = new float[125];
            for (int i = 0; i < values.length; i++) {
                values[i] = 0.55f + 0.35f * (float) Math.sin(i * 0.5) * (float) Math.sin(i * 0.13 + 1);
            }
            window = 0.04f;
        }
    }

    /** Loudness at {@code seconds} after the voice starts (0 outside the clip). */
    public static float at(float seconds) {
        load();
        if (seconds < 0) return 0f;
        float pos = seconds / window;
        int i = (int) pos;
        if (i >= values.length - 1) return 0f;
        float f = pos - i;
        return values[i] * (1f - f) + values[i + 1] * f;
    }
}
