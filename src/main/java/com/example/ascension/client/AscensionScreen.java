package com.example.ascension.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.Ability;
import com.example.ascension.data.Branch;
import com.example.ascension.data.Effect;
import com.example.ascension.data.NodeDef;
import com.example.ascension.data.Nodes;
import com.example.ascension.data.PlayerProgress;
import com.example.ascension.data.Progression;
import com.example.ascension.data.TreeLayout;
import com.example.ascension.network.IntroSeenPayload;
import com.example.ascension.network.SetSlotPayload;
import com.example.ascension.network.UnlockNodePayload;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Full-screen, galaxy-style upgrade tree. Everything here is drawn by hand: no vanilla menu widgets.
 * Contains the opening cinematic, the unlock / milestone animations and the ability key bar.
 */
public class AscensionScreen extends Screen {
    private static final float UNIT = 58f;          // pixels per tree unit at zoom 1
    private static final float MIN_ZOOM = 0.18f;
    private static final float MAX_ZOOM = 2.2f;
    private static final float PAN_LIMIT = 17f;
    private static final int SEG = 16;               // samples per curved link

    private static final int LINE = 0xBFE3FF;        // light blue-white
    private static final int BRIGHT = 0xEAF8FF;
    private static final int GLOW_BLUE = 0x6CB8FF;
    private static final int DEEP = 0x24407A;

    private static final int LOCKED = 0, REACHABLE = 1, AVAILABLE = 2, UNLOCKED = 3;

    // opening cinematic timeline (seconds)
    private static final float VOICE_START = 0.5f;
    private static final float COLLAPSE_START = 5.75f;
    private static final float COLLAPSE_END = 6.55f;
    private static final float BLOOM_START = 6.5f;
    private static final float BLOOM_END = 9.6f;
    private static final float CINEMATIC_END = 10.0f;

    // ability key bar
    private static final int SLOT_W = 92, SLOT_H = 34, SLOT_GAP = 8;

    private static final class Particle {
        float x, y, vx, vy, age, life, size, maxAlpha, drag, spin;
        int rgb;
        boolean ambient;
    }

    private static final class Wave {
        float x, y, age, delay, life, maxR;
    }

    /** A bright orb that travels along the links from the core (or the parent) to a freshly unlocked orb. */
    private static final class Surge {
        int[] path;
        int target;
        float t, duration;
        boolean big, arrived;
    }

    private static final class Banner {
        String kicker, name, desc;
        float age, life;
        boolean big;
    }

    private final TreeLayout layout = TreeLayout.get();
    private final RandomSource rng = RandomSource.create();

    // per-frame geometry / state
    private final float[][] edgeX = new float[Nodes.COUNT][SEG + 1];
    private final float[][] edgeY = new float[Nodes.COUNT][SEG + 1];
    private final float[] nodeX = new float[Nodes.COUNT];
    private final float[] nodeY = new float[Nodes.COUNT];
    private final float[] dist = new float[Nodes.COUNT];
    private final float[] vis = new float[Nodes.COUNT];
    private final int[] state = new int[Nodes.COUNT];
    private final boolean[] onPath = new boolean[Nodes.COUNT];
    private final boolean[] pending = new boolean[Nodes.COUNT];
    private final boolean[] popped = new boolean[Nodes.COUNT];
    private final float[] hover = new float[Nodes.COUNT];
    private final float[] flash = new float[Nodes.COUNT];

    private final List<Particle> particles = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private final List<Surge> surges = new ArrayList<>();
    private Banner bigBanner;
    private Banner toast;
    private final float[][] stars = new float[240][5];   // x, y, depth, size, phase
    private final float[][] nebulae = {
            // x(0-1), y(0-1), size, rgb, alpha, depth
            {0.18f, 0.28f, 900, 0x1B3F9E, 0.17f, 0.30f},
            {0.82f, 0.22f, 800, 0x2A2F8F, 0.15f, 0.22f},
            {0.70f, 0.82f, 1000, 0x14508F, 0.16f, 0.35f},
            {0.12f, 0.80f, 760, 0x263A9A, 0.14f, 0.18f},
            {0.50f, 0.50f, 1200, 0x0E2A66, 0.20f, 0.10f},
    };

    private final long openTime = Util.getMillis();
    private long lastFrame = openTime;
    private boolean placed;
    private float panX, panY, zoom = 0.6f, intro, coreBeat;
    private float shake, shakeX, shakeY, screenFlash;
    private int hovered = -1;
    private int hoveredSlot = -1;
    private BitSet seenUnlocked;

    // cinematic state
    private boolean cinematic;
    private long cinematicStart;
    private float cineTime;
    private SimpleSoundInstance voice;
    private boolean voicePlayed, collapseDone;
    private float reveal = 999f;
    private float hudFade = 1f;
    private float coreAlpha = 1f;
    private float envSmooth;
    private float lastRingTime;
    private int lastRevealTier;

    private boolean pressed, dragged;
    private double pressX, pressY;

    public AscensionScreen() {
        super(Component.translatable("screen.ascension.title"));
        RandomSource r = RandomSource.create(1337);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat();
            float d = r.nextFloat();
            s[2] = 0.04f + 0.36f * d * d;
            s[3] = 2f + r.nextFloat() * 6f * (0.4f + d);
            s[4] = r.nextFloat() * 6.2831f;
        }
        for (int id = 0; id < Nodes.COUNT; id++) {
            dist[id] = (float) Math.hypot(layout.x[id], layout.y[id]);
        }
    }

    @Override
    protected void init() {
        if (!placed) {
            placed = true;
            zoom = fitZoom();
            seenUnlocked = (BitSet) ClientProgress.get().unlocked.clone();
            if (!ClientProgress.get().introSeen) {
                startCinematic();
            } else {
                playUi(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.5f);
            }
        }
    }

    private float fitZoom() {
        return Mth.clamp(Math.min(width, height) / (2f * 15.5f * UNIT) * 1.08f, MIN_ZOOM, 1.0f);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // keep the world (and our network traffic) running while the tree is open
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty: we paint our own backdrop in render()
    }

    @Override
    public void removed() {
        stopVoice();
        super.removed();
    }

    // ================================================================== cinematic

    private void startCinematic() {
        cinematic = true;
        cinematicStart = Util.getMillis();
        cineTime = 0;
        voicePlayed = false;
        collapseDone = false;
        reveal = 0f;
        hudFade = 0f;
        coreAlpha = 0f;
        envSmooth = 0f;
        lastRingTime = 0f;
        lastRevealTier = 0;
        Arrays.fill(popped, false);
        panX = 0;
        panY = 0;
        zoom = fitZoom();
        particles.removeIf(p -> !p.ambient);
        waves.clear();
        surges.clear();
        Arrays.fill(pending, false);
    }

    private void endCinematic(boolean skipped) {
        if (!cinematic) return;
        cinematic = false;
        reveal = 999f;
        hudFade = 1f;
        coreAlpha = 1f;
        if (skipped) stopVoice();
        PlayerProgress progress = ClientProgress.get();
        if (!progress.introSeen) {
            progress.introSeen = true;
            PacketDistributor.sendToServer(new IntroSeenPayload());
        }
    }

    private void stopVoice() {
        if (voice != null) {
            Minecraft.getInstance().getSoundManager().stop(voice);
            voice = null;
        }
    }

    private void updateCinematic(float dt) {
        float ct = (Util.getMillis() - cinematicStart) / 1000f;
        cineTime = ct;

        if (!voicePlayed && ct >= VOICE_START) {
            voicePlayed = true;
            voice = SimpleSoundInstance.forUI(AscensionMod.INTRO_VOICE.get(), 1.0f, 1.0f);
            Minecraft.getInstance().getSoundManager().play(voice);
        }

        float env = IntroEnvelope.at(ct - VOICE_START);
        envSmooth += (env - envSmooth) * Math.min(1f, dt * 16f);

        // soft rings that leave the ball on loud moments
        if (ct > VOICE_START && ct < COLLAPSE_START && env > 0.55f && ct - lastRingTime > 0.38f) {
            lastRingTime = ct;
            Wave w = new Wave();
            w.x = 0;
            w.y = 0;
            w.life = 1.1f;
            w.maxR = 190f;
            waves.add(w);
        }
        // motes drifting off the ball while it speaks
        if (ct > 0.4f && ct < COLLAPSE_START && rng.nextFloat() < 0.35f + envSmooth * 0.6f) {
            float a = rng.nextFloat() * 6.2831f;
            float r0 = 0.5f + envSmooth * 0.5f;
            spawn(Mth.cos(a) * r0, Mth.sin(a) * r0, Mth.cos(a) * 0.5f, Mth.sin(a) * 0.5f,
                    1.2f + rng.nextFloat(), 8f + rng.nextFloat() * 10f, 0.8f, rng.nextFloat() < 0.5f ? BRIGHT : GLOW_BLUE, false);
        }

        coreAlpha = ct < COLLAPSE_END - 0.1f ? 0f : ease(Mth.clamp((ct - (COLLAPSE_END - 0.1f)) / 0.5f, 0f, 1f));

        if (!collapseDone && ct >= COLLAPSE_END) {
            collapseDone = true;
            coreBeat = 1f;
            screenFlash = 0.55f;
            shake = 7f;
            for (int i = 0; i < 90; i++) {
                float a = rng.nextFloat() * 6.2831f;
                float sp = 0.8f + rng.nextFloat() * 3.4f;
                spawn(0, 0, Mth.cos(a) * sp, Mth.sin(a) * sp, 0.8f + rng.nextFloat() * 1.3f,
                        8f + rng.nextFloat() * 16f, 0.95f, rng.nextFloat() < 0.5f ? BRIGHT : GLOW_BLUE, false);
            }
            for (int i = 0; i < 3; i++) {
                Wave w = new Wave();
                w.delay = i * 0.14f;
                w.life = 1.0f + i * 0.2f;
                w.maxR = 260f * (1f + i * 0.4f);
                waves.add(w);
            }
            playUi(SoundEvents.BEACON_ACTIVATE, 1.0f, 0.8f);
            playUi(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 0.9f);
        }

        reveal = ct < BLOOM_START ? 0f
                : ease(Mth.clamp((ct - BLOOM_START) / (BLOOM_END - BLOOM_START), 0f, 1f)) * 16.5f;
        hudFade = Mth.clamp((ct - 9.0f) / 1.0f, 0f, 1f);

        int tier = (int) (reveal / 1.45f);
        while (lastRevealTier < tier) {
            lastRevealTier++;
            playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f + lastRevealTier * 0.09f, 0.35f);
        }

        if (ct >= CINEMATIC_END) {
            endCinematic(false);
        }
    }

    // ================================================================== helpers

    private float scale() {
        return UNIT * zoom * (0.85f + 0.15f * intro);
    }

    private float nodeScale() {
        return (float) Math.pow(zoom, 0.6) * (0.9f + 0.1f * intro);
    }

    private float sx(float wx) {
        return width * 0.5f + shakeX + (wx - panX) * scale();
    }

    private float sy(float wy) {
        return height * 0.5f + shakeY + (wy - panY) * scale();
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void playUi(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private float baseRadius(NodeDef n) {
        return (n.milestone() ? 15f : 8.5f) * nodeScale();
    }

    // ================================================================== frame

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = (now - openTime) / 1000f;
        PlayerProgress prog = ClientProgress.get();

        intro = ease(Mth.clamp(t / 0.9f, 0f, 1f));
        if (cinematic) updateCinematic(dt);
        shake *= (float) Math.exp(-dt * 5.0);
        shakeX = (rng.nextFloat() - 0.5f) * 2f * shake;
        shakeY = (rng.nextFloat() - 0.5f) * 2f * shake;

        detectChanges(prog);
        updateGeometry(prog);
        updateHover(mouseX, mouseY);
        updateAnimation(dt);

        g.fillGradient(0, 0, width, height, 0xFF02040C, 0xFF0B1738);
        g.flush();

        Matrix4f m = g.pose().last().pose();
        drawBackdrop(m, t);
        drawEdges(m, t);
        drawNodes(m, t);
        if (cinematic) drawBall(m);
        drawSurges(m);
        drawParticles(m);

        drawLabelsAndHud(g, prog);
        if (cinematic) {
            drawSubtitles(g);
        } else {
            drawLoadout(g, prog, mouseX, mouseY);
        }
        drawBanners(g);
        if (screenFlash > 0.01f) {
            g.fill(0, 0, width, height, Gfx.argb(Math.min(0.5f, screenFlash * 0.45f), LINE));
        }
        if (!cinematic) drawTooltip(g, mouseX, mouseY, prog);
    }

    // ------------------------------------------------------------------ update

    private void detectChanges(PlayerProgress prog) {
        for (int id = 0; id < Nodes.COUNT; id++) {
            if (prog.isUnlocked(id) && !seenUnlocked.get(id)) {
                celebrate(id, prog);
            }
        }
        seenUnlocked = (BitSet) prog.unlocked.clone();
    }

    private void updateGeometry(PlayerProgress prog) {
        for (int id = 0; id < Nodes.COUNT; id++) {
            NodeDef n = Nodes.get(id);
            int p = layout.parent[id];
            float x1 = (float) layout.x[id];
            float y1 = (float) layout.y[id];
            float x0 = p < 0 ? 0f : (float) layout.x[p];
            float y0 = p < 0 ? 0f : (float) layout.y[p];
            nodeX[id] = sx(x1);
            nodeY[id] = sy(y1);

            float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f;
            float dx = x1 - x0, dy = y1 - y0;
            float b = (float) layout.bend[id];
            float cx = mx - dy * b, cy = my + dx * b;
            for (int k = 0; k <= SEG; k++) {
                float t = (float) k / SEG;
                float a = (1 - t) * (1 - t), bb = 2 * (1 - t) * t, c = t * t;
                edgeX[id][k] = sx(a * x0 + bb * cx + c * x1);
                edgeY[id][k] = sy(a * y0 + bb * cy + c * y1);
            }

            if (prog.isUnlocked(id)) {
                state[id] = pending[id] ? AVAILABLE : UNLOCKED;   // not lit until the surge arrives
            } else if (prog.check(n) == PlayerProgress.Check.OK) {
                state[id] = AVAILABLE;
            } else {
                boolean parentOk = n.parent() < 0 || prog.isUnlocked(n.parent());
                state[id] = parentOk ? REACHABLE : LOCKED;
            }
            vis[id] = Mth.clamp((reveal - dist[id]) / 1.5f, 0f, 1f);
        }
    }

    private void updateHover(int mx, int my) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        hoveredSlot = cinematic ? -1 : slotAt(mx, my);
        if (!cinematic && hoveredSlot < 0 && intro > 0.6f) {
            for (int id = 0; id < Nodes.COUNT; id++) {
                if (vis[id] < 0.9f) continue;
                float r = baseRadius(Nodes.get(id)) * 1.3f + 3f;
                float dx = mx - nodeX[id], dy = my - nodeY[id];
                float d = dx * dx + dy * dy;
                if (d <= r * r && d < bestD) {
                    best = id;
                    bestD = d;
                }
            }
        }
        if (best != hovered) {
            hovered = best;
            if (hovered >= 0) {
                playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f + rng.nextFloat() * 0.5f, 0.3f);
            }
        }
        Arrays.fill(onPath, false);
        for (int id = hovered; id >= 0; id = layout.parent[id]) {
            onPath[id] = true;
        }
    }

    private void updateAnimation(float dt) {
        for (int id = 0; id < Nodes.COUNT; id++) {
            float target = id == hovered ? 1f : 0f;
            hover[id] += (target - hover[id]) * Math.min(1f, dt * 14f);
            flash[id] = Math.max(0f, flash[id] - dt * 1.3f);
            // orbs "pop" as the cinematic's bloom reaches them
            if (cinematic && !popped[id] && vis[id] > 0.5f) {
                popped[id] = true;
                flash[id] = Nodes.get(id).milestone() ? 0.9f : 0.5f;
                if (rng.nextFloat() < 0.5f) {
                    float wx = (float) layout.x[id], wy = (float) layout.y[id];
                    for (int i = 0; i < 4; i++) {
                        spawn(wx, wy, (rng.nextFloat() - 0.5f) * 1.2f, (rng.nextFloat() - 0.5f) * 1.2f,
                                0.6f + rng.nextFloat() * 0.6f, 7f + rng.nextFloat() * 7f, 0.9f, BRIGHT, false);
                    }
                }
            }
        }
        coreBeat = Math.max(0f, coreBeat - dt * 1.2f);
        screenFlash = Math.max(0f, screenFlash - dt * 1.5f);

        if (hovered >= 0 && rng.nextFloat() < 0.55f) {
            spawn((float) layout.x[hovered] + (rng.nextFloat() - 0.5f) * 0.25f,
                    (float) layout.y[hovered] + (rng.nextFloat() - 0.5f) * 0.25f,
                    (rng.nextFloat() - 0.5f) * 0.5f, (rng.nextFloat() - 0.5f) * 0.5f - 0.15f,
                    0.5f + rng.nextFloat() * 0.5f, 6f + rng.nextFloat() * 6f, 0.9f, BRIGHT, false);
        }

        // ambient drifting motes across the visible area
        int ambient = 0;
        for (Particle p : particles) if (p.ambient) ambient++;
        float halfW = width * 0.5f / scale(), halfH = height * 0.5f / scale();
        for (int i = 0; i < 2 && ambient < 110; i++, ambient++) {
            spawn(panX + (rng.nextFloat() * 2 - 1) * halfW, panY + (rng.nextFloat() * 2 - 1) * halfH,
                    (rng.nextFloat() - 0.5f) * 0.12f, -0.03f - rng.nextFloat() * 0.12f,
                    7f + rng.nextFloat() * 7f, 5f + rng.nextFloat() * 7f, 0.38f,
                    rng.nextFloat() < 0.3f ? GLOW_BLUE : LINE, true);
        }

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                particles.remove(i);
                continue;
            }
            if (p.spin != 0f) {                       // swirl: rotate the velocity as it flies
                float c = Mth.cos(p.spin * dt), s = Mth.sin(p.spin * dt);
                float nvx = p.vx * c - p.vy * s;
                float nvy = p.vx * s + p.vy * c;
                p.vx = nvx;
                p.vy = nvy;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float damp = Math.max(0f, 1f - p.drag * dt);
            p.vx *= damp;
            p.vy *= damp;
        }
        for (int i = waves.size() - 1; i >= 0; i--) {
            Wave w = waves.get(i);
            w.age += dt;
            if (w.age - w.delay >= w.life) {
                waves.remove(i);
            }
        }

        // surges travelling along the links; the orb lights up when its surge arrives
        float[] pos = new float[2];
        for (int i = surges.size() - 1; i >= 0; i--) {
            Surge s = surges.get(i);
            s.t += dt;
            float p = s.t / s.duration;
            if (!s.arrived) {
                surgePoint(s, smooth(p), pos);
                float wx = panX + (pos[0] - shakeX - width * 0.5f) / scale();
                float wy = panY + (pos[1] - shakeY - height * 0.5f) / scale();
                for (int k = 0; k < (s.big ? 3 : 1); k++) {
                    spawn(wx, wy, (rng.nextFloat() - 0.5f) * 0.6f, (rng.nextFloat() - 0.5f) * 0.6f,
                            0.4f + rng.nextFloat() * 0.4f, 6f + rng.nextFloat() * (s.big ? 12f : 6f), 0.9f,
                            rng.nextFloat() < 0.5f ? BRIGHT : GLOW_BLUE, false);
                }
            }
            if (!s.arrived && p >= 1f) {
                s.arrived = true;
                impact(s.target, s.big);
            }
            if (s.arrived) {
                surges.remove(i);
            }
        }

        for (Banner b : new Banner[] {bigBanner, toast}) {
            if (b != null) b.age += dt;
        }
        if (bigBanner != null && bigBanner.age > bigBanner.life) bigBanner = null;
        if (toast != null && toast.age > toast.life) toast = null;
    }

    private void spawn(float x, float y, float vx, float vy, float life, float size, float alpha, int rgb, boolean ambient) {
        Particle p = new Particle();
        p.x = x;
        p.y = y;
        p.vx = vx;
        p.vy = vy;
        p.life = life;
        p.size = size;
        p.maxAlpha = alpha;
        p.rgb = rgb;
        p.ambient = ambient;
        p.drag = ambient ? 0f : 1.6f;
        particles.add(p);
    }

    // ------------------------------------------------------------------ unlock effects

    /** Called the moment the server confirms an unlock: start the surge; the burst happens when it arrives. */
    private void celebrate(int id, PlayerProgress prog) {
        NodeDef n = Nodes.get(id);
        pending[id] = true;
        Surge s = new Surge();
        s.target = id;
        s.big = n.milestone();
        if (s.big) {
            List<Integer> chain = new ArrayList<>();
            for (int c = id; c >= 0; c = layout.parent[c]) chain.add(0, c);
            s.path = new int[chain.size()];
            for (int i = 0; i < s.path.length; i++) s.path[i] = chain.get(i);
            s.duration = Math.min(2.4f, 0.55f + 0.28f * s.path.length);
            playUi(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 0.9f);
            playUi(SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.8f);
        } else {
            s.path = new int[] {id};
            s.duration = 0.5f;
            playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f, 0.5f);
        }
        surges.add(s);
    }

    private void impact(int id, boolean big) {
        NodeDef n = Nodes.get(id);
        pending[id] = false;
        float wx = (float) layout.x[id], wy = (float) layout.y[id];
        flash[id] = 1f;
        coreBeat = 1f;

        int count = big ? 170 : 44;
        for (int i = 0; i < count; i++) {
            float a = rng.nextFloat() * 6.2831f;
            float speed = (big ? 1.2f : 0.7f) + rng.nextFloat() * (big ? 3.6f : 1.9f);
            int rgb = rng.nextFloat() < 0.5f ? BRIGHT : (rng.nextFloat() < 0.5f ? GLOW_BLUE : LINE);
            spawn(wx, wy, Mth.cos(a) * speed, Mth.sin(a) * speed, 0.8f + rng.nextFloat() * (big ? 1.8f : 1.2f),
                    8f + rng.nextFloat() * (big ? 20f : 14f), 0.95f, rgb, false);
            Particle last = particles.get(particles.size() - 1);
            if (rng.nextFloat() < 0.6f) last.spin = (rng.nextBoolean() ? 1 : -1) * (1.5f + rng.nextFloat() * 2.5f);
        }
        int rings = big ? 5 : 2;
        for (int i = 0; i < rings; i++) {
            Wave w = new Wave();
            w.x = wx;
            w.y = wy;
            w.delay = i * (big ? 0.13f : 0.12f);
            w.life = 0.9f + i * 0.15f;
            w.maxR = (big ? 240f : 100f) * (1f + i * 0.35f);
            waves.add(w);
        }

        PlayerProgress prog = ClientProgress.get();
        if (big) {
            shake = 16f;
            screenFlash = 0.8f;
            playUi(SoundEvents.TOTEM_USE, 1.25f, 0.7f);
            playUi(SoundEvents.BEACON_ACTIVATE, 1.0f, 0.8f);
            playUi(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 0.9f);
            playUi(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.5f, 0.3f);
            playUi(SoundEvents.PLAYER_LEVELUP, 1.1f, 0.8f);
            Banner b = new Banner();
            b.kicker = "M I L E S T O N E   U N L O C K E D";
            b.name = n.name().toUpperCase();
            b.desc = n.description();
            b.life = 4.2f;
            b.big = true;
            bigBanner = b;
        } else {
            // a rising scale: the more orbs you own in this branch, the higher the chime
            float[] scale = {0.8f, 0.9f, 1.0f, 1.2f, 1.35f, 1.5f, 1.8f, 2.0f};
            float pitch = scale[prog.unlockedInBranch(n.branch()) % scale.length];
            playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, pitch, 0.9f);
            playUi(SoundEvents.AMETHYST_BLOCK_CHIME, pitch * 1.25f, 0.7f);
            shake = Math.max(shake, 3f);
            screenFlash = Math.max(screenFlash, 0.15f);
            Banner b = new Banner();
            b.kicker = "UPGRADE UNLOCKED";
            b.name = n.name();
            b.desc = n.description();
            b.life = 2.4f;
            toast = b;
        }
    }

    private void surgePoint(Surge s, float p, float[] out) {
        int len = s.path.length;
        float f = Mth.clamp(p, 0f, 1f) * len * SEG;
        int e = Math.min(len - 1, (int) (f / SEG));
        float within = f - e * SEG;
        int i0 = Math.min(SEG - 1, (int) within);
        float k = within - i0;
        int id = s.path[e];
        out[0] = Mth.lerp(k, edgeX[id][i0], edgeX[id][i0 + 1]);
        out[1] = Mth.lerp(k, edgeY[id][i0], edgeY[id][i0 + 1]);
    }

    // ------------------------------------------------------------------ drawing: world

    private void drawBackdrop(Matrix4f m, float t) {
        float pxPan = panX * scale(), pyPan = panY * scale();
        Gfx.beginTex(Gfx.GLOW, true);
        for (float[] n : nebulae) {
            float px = n[0] * width - pxPan * n[5];
            float py = n[1] * height - pyPan * n[5];
            Gfx.tex(m, px, py, n[2] * (0.6f + 0.6f * zoom), 0, Gfx.argb(n[4] * intro, (int) n[3]));
        }
        // soft light behind the core
        Gfx.tex(m, sx(0), sy(0), 760f * zoom + 160f, 0,
                Gfx.argb((0.20f + 0.15f * coreBeat) * intro * (cinematic ? 0.6f + 0.4f * coreAlpha : 1f), 0x2F6FD0));

        float fw = width + 240f, fh = height + 240f;
        for (float[] s : stars) {
            float x = mod(s[0] * fw - pxPan * s[2], fw) - 120f;
            float y = mod(s[1] * fh - pyPan * s[2], fh) - 120f;
            float tw = 0.55f + 0.45f * Mth.sin(t * (0.8f + s[2] * 3f) + s[4]);
            Gfx.tex(m, x, y, s[3], 0, Gfx.argb(0.55f * tw * intro, LINE));
        }
        Gfx.endTex();

        Gfx.beginTex(Gfx.STAR, true);
        for (int i = 0; i < stars.length; i += 6) {
            float[] s = stars[i];
            float x = mod(s[0] * fw - pxPan * s[2], fw) - 120f;
            float y = mod(s[1] * fh - pyPan * s[2], fh) - 120f;
            float tw = 0.5f + 0.5f * Mth.sin(t * 1.3f + s[4] * 2f);
            Gfx.tex(m, x, y, s[3] * 4f, s[4], Gfx.argb(0.5f * tw * intro, BRIGHT));
        }
        Gfx.endTex();
    }

    private static float mod(float v, float m) {
        return ((v % m) + m) % m;
    }

    private void drawEdges(Matrix4f m, float t) {
        float nodeScale = nodeScale();
        for (int pass = 0; pass < 2; pass++) {
            Gfx.beginColor(true);
            for (int id = 0; id < Nodes.COUNT; id++) {
                float v = vis[id];
                if (v < 0.01f) continue;
                int childState = state[id];
                int p = layout.parent[id];
                int parentState = p < 0 ? UNLOCKED : state[p];
                int level = childState == UNLOCKED ? 2 : (parentState == UNLOCKED ? 1 : 0);
                boolean path = onPath[id];

                float coreA = level == 2 ? 0.95f : level == 1 ? 0.5f : 0.2f;
                float glowA = level == 2 ? 0.20f : level == 1 ? 0.07f : 0f;
                if (path) {
                    coreA = Math.min(1f, coreA + 0.4f);
                    glowA += 0.20f;
                }
                coreA *= intro * v;
                glowA *= intro * v;
                int rgb = level == 2 || path ? BRIGHT : LINE;

                if (pass == 0) {
                    if (glowA > 0.005f) {
                        Gfx.ribbon(m, edgeX[id], edgeY[id], SEG + 1, 8f * nodeScale + (path ? 4f : 0f),
                                Gfx.argb(glowA * 0.5f, rgb), Gfx.argb(glowA, rgb));
                    }
                } else {
                    Gfx.ribbon(m, edgeX[id], edgeY[id], SEG + 1, Math.max(1.1f, 1.7f * nodeScale),
                            Gfx.argb(coreA * 0.55f, rgb), Gfx.argb(coreA, rgb));
                }
            }
            Gfx.endColor();
        }

        // light pulses travelling along unlocked links
        Gfx.beginTex(Gfx.GLOW, true);
        for (int id = 0; id < Nodes.COUNT; id++) {
            if (state[id] != UNLOCKED || vis[id] < 0.9f) continue;
            float f = (t * 0.33f + id * 0.137f) % 1f;
            float fi = f * SEG;
            int i0 = Math.min(SEG - 1, (int) fi);
            float k = fi - i0;
            float x = Mth.lerp(k, edgeX[id][i0], edgeX[id][i0 + 1]);
            float y = Mth.lerp(k, edgeY[id][i0], edgeY[id][i0 + 1]);
            Gfx.tex(m, x, y, 13f * nodeScale, 0, Gfx.argb(0.8f * Mth.sin(f * 3.14159f) * intro, BRIGHT));
        }
        Gfx.endTex();
    }

    private float radius(int id) {
        return baseRadius(Nodes.get(id)) * (1f + 0.28f * hover[id] + 0.4f * flash[id]);
    }

    private void drawNodes(Matrix4f m, float t) {
        float ns = nodeScale();
        float pulse = 0.5f + 0.5f * Mth.sin(t * 2.6f);
        float ca = coreAlpha;

        // glows
        Gfx.beginTex(Gfx.GLOW, true);
        {
            float cr = 26f * ns * (1f + 0.25f * coreBeat);
            Gfx.tex(m, sx(0), sy(0), cr * 7f, 0,
                    Gfx.argb((0.55f + 0.25f * pulse + 0.2f * coreBeat) * intro * ca, GLOW_BLUE));
        }
        for (int id = 0; id < Nodes.COUNT; id++) {
            float v = vis[id];
            if (v < 0.01f) continue;
            float r = radius(id);
            float a;
            int rgb = GLOW_BLUE;
            float size;
            switch (state[id]) {
                case UNLOCKED -> { a = 0.5f + 0.15f * Mth.sin(t * 2f + id); size = r * 5.4f; }
                case AVAILABLE -> { a = 0.30f + 0.22f * pulse; size = r * 4.4f; }
                case REACHABLE -> { a = 0.12f; size = r * 3.2f; rgb = 0x4A86D8; }
                default -> { a = 0.07f; size = r * 2.6f; rgb = DEEP; }
            }
            a += 0.35f * hover[id] + 0.6f * flash[id];
            Gfx.tex(m, nodeX[id], nodeY[id], size, 0, Gfx.argb(a * intro * v, rgb));
        }
        Gfx.endTex();

        // orb bodies
        Gfx.beginTex(Gfx.ORB, false);
        Gfx.tex(m, sx(0), sy(0), 26f * ns * 2f * (1f + 0.15f * coreBeat), 0, Gfx.argb(intro * ca, 0xF4FCFF));
        for (int id = 0; id < Nodes.COUNT; id++) {
            float v = vis[id];
            if (v < 0.01f) continue;
            float r = radius(id);
            int rgb;
            float a = 1f;
            switch (state[id]) {
                case UNLOCKED -> rgb = 0xF4FCFF;
                case AVAILABLE -> rgb = 0xA8D3F5;
                case REACHABLE -> { rgb = 0x4F70A8; a = 0.95f; }
                default -> { rgb = 0x1B2A55; a = 0.9f; }
            }
            Gfx.tex(m, nodeX[id], nodeY[id], r * 2f, 0, Gfx.argb(a * intro * v, rgb));
        }
        Gfx.endTex();

        // rings, sparkles and the milestone stars
        Gfx.beginTex(Gfx.RING, true);
        Gfx.tex(m, sx(0), sy(0), 26f * ns * 3.0f, 0, Gfx.argb((0.55f + 0.3f * coreBeat) * intro * ca, BRIGHT));
        Gfx.tex(m, sx(0), sy(0), 26f * ns * (3.9f + 0.5f * pulse), 0, Gfx.argb(0.28f * intro * ca, GLOW_BLUE));
        for (int id = 0; id < Nodes.COUNT; id++) {
            float v = vis[id];
            if (v < 0.01f) continue;
            NodeDef n = Nodes.get(id);
            float r = radius(id);
            int s = state[id];
            if (s == AVAILABLE) {
                float k = (t * 0.9f + id * 0.31f) % 1f;
                Gfx.tex(m, nodeX[id], nodeY[id], r * (2.2f + 1.6f * k), 0, Gfx.argb((1f - k) * 0.75f * intro * v, BRIGHT));
            }
            if (n.milestone()) {
                float a = s == UNLOCKED ? 0.85f : s == LOCKED ? 0.18f : 0.5f;
                Gfx.tex(m, nodeX[id], nodeY[id], r * 2.9f, 0, Gfx.argb(a * intro * v, LINE));
            } else if (hover[id] > 0.02f) {
                Gfx.tex(m, nodeX[id], nodeY[id], r * 2.7f, 0, Gfx.argb(0.6f * hover[id] * intro, BRIGHT));
            }
        }
        for (Wave w : waves) {
            float a = w.age - w.delay;
            if (a < 0) continue;
            float k = a / w.life;
            float rad = w.maxR * ease(k) * ns;
            Gfx.tex(m, sx(w.x), sy(w.y), rad * 2f, 0, Gfx.argb((1f - k) * (1f - k) * 0.9f, BRIGHT));
        }
        Gfx.endTex();

        Gfx.beginTex(Gfx.STAR, true);
        Gfx.tex(m, sx(0), sy(0), 26f * ns * 7f, t * 0.25f, Gfx.argb((0.5f + 0.4f * coreBeat) * intro * ca, BRIGHT));
        for (int id = 0; id < Nodes.COUNT; id++) {
            float v = vis[id];
            if (v < 0.01f) continue;
            NodeDef n = Nodes.get(id);
            int s = state[id];
            float r = radius(id);
            if (n.milestone()) {
                float a = s == UNLOCKED ? 0.8f : s == LOCKED ? 0.12f : 0.4f;
                Gfx.tex(m, nodeX[id], nodeY[id], r * 5.2f, t * 0.35f + id, Gfx.argb(a * intro * v, BRIGHT));
            } else if (s == UNLOCKED) {
                Gfx.tex(m, nodeX[id], nodeY[id], r * 3.4f, 0.6f, Gfx.argb(0.28f * intro * v, BRIGHT));
            }
            if (flash[id] > 0.01f) {
                Gfx.tex(m, nodeX[id], nodeY[id], r * 9f * flash[id] + r * 3f, t * 2f, Gfx.argb(flash[id] * intro, BRIGHT));
            }
        }
        Gfx.endTex();
    }

    /** The speaking orb of the opening cinematic: swells and shrinks with the voice, then collapses into the core. */
    private void drawBall(Matrix4f m) {
        float ct = cineTime;
        float in = ease(Mth.clamp(ct / 0.9f, 0f, 1f));
        float baseR = Math.min(width, height) * 0.075f;
        float r = baseR * (0.5f + 1.2f * envSmooth) * in;
        float collapse = Mth.clamp((ct - COLLAPSE_START) / (COLLAPSE_END - COLLAPSE_START), 0f, 1f);
        float coreR = 26f * nodeScale();
        r = Mth.lerp(collapse * collapse, r, coreR);
        float alpha = ct < COLLAPSE_END ? 1f : Mth.clamp(1f - (ct - COLLAPSE_END) / 0.3f, 0f, 1f);
        if (alpha <= 0.01f || r <= 0.5f) return;
        float cx = sx(0), cy = sy(0);

        Gfx.beginTex(Gfx.GLOW, true);
        Gfx.tex(m, cx, cy, r * 7.5f, 0, Gfx.argb((0.35f + 0.35f * envSmooth) * alpha, GLOW_BLUE));
        Gfx.tex(m, cx, cy, r * 3.6f, 0, Gfx.argb((0.55f + 0.3f * envSmooth) * alpha, 0xA8D8FF));
        Gfx.endTex();

        Gfx.beginTex(Gfx.ORB, false);
        Gfx.tex(m, cx, cy, r * 2f, 0, Gfx.argb(alpha, 0xEAF8FF));
        Gfx.endTex();

        Gfx.beginTex(Gfx.RING, true);
        Gfx.tex(m, cx, cy, r * (2.5f + 0.5f * envSmooth), 0, Gfx.argb((0.5f + 0.4f * envSmooth) * alpha, BRIGHT));
        float k = (ct * 0.8f) % 1f;
        Gfx.tex(m, cx, cy, r * (3f + 2.2f * k), 0, Gfx.argb((1f - k) * 0.5f * alpha, GLOW_BLUE));
        Gfx.endTex();

        Gfx.beginTex(Gfx.STAR, true);
        Gfx.tex(m, cx, cy, r * 6f, ct * 0.4f, Gfx.argb((0.25f + 0.5f * envSmooth) * alpha, BRIGHT));
        Gfx.endTex();
    }

    private void drawSurges(Matrix4f m) {
        if (surges.isEmpty()) return;
        float ns = nodeScale();
        float[] pos = new float[2];
        Gfx.beginTex(Gfx.GLOW, true);
        for (Surge s : surges) {
            float p = smooth(s.t / s.duration);
            float size = (s.big ? 46f : 26f) * ns;
            for (int j = 12; j >= 1; j--) {            // trail
                float pj = p - j * 0.02f;
                if (pj < 0f) continue;
                surgePoint(s, pj, pos);
                Gfx.tex(m, pos[0], pos[1], size * (1f - j / 16f), 0, Gfx.argb((1f - j / 13f) * 0.5f * intro, GLOW_BLUE));
            }
            surgePoint(s, p, pos);
            Gfx.tex(m, pos[0], pos[1], size * 1.7f, 0, Gfx.argb(0.9f * intro, BRIGHT));
        }
        Gfx.endTex();
        Gfx.beginTex(Gfx.STAR, true);
        for (Surge s : surges) {
            surgePoint(s, smooth(s.t / s.duration), pos);
            Gfx.tex(m, pos[0], pos[1], (s.big ? 90f : 50f) * ns, s.t * 6f, Gfx.argb(0.9f * intro, BRIGHT));
        }
        Gfx.endTex();
    }

    private void drawParticles(Matrix4f m) {
        float ns = nodeScale();
        Gfx.beginTex(Gfx.GLOW, true);
        for (Particle p : particles) {
            float k = p.age / p.life;
            float fade = Math.min(1f, p.age / 0.25f) * (1f - k);
            Gfx.tex(m, sx(p.x), sy(p.y), p.size * ns * (p.ambient ? 1f : 0.6f + 0.6f * (1f - k)), 0,
                    Gfx.argb(p.maxAlpha * fade * intro, p.rgb));
        }
        if (bigBanner != null) {
            float a = bannerAlpha(bigBanner);
            float cy = height * 0.2f + 24f;
            for (int i = -2; i <= 2; i++) {
                Gfx.tex(m, width / 2f + i * 150f, cy, 360f - Math.abs(i) * 60f, 0, Gfx.argb(0.16f * a, GLOW_BLUE));
            }
        }
        Gfx.endTex();
    }

    // ------------------------------------------------------------------ drawing: text / HUD

    private void text(GuiGraphics g, String s, float x, float y, float scale, float alpha, int rgb, boolean center) {
        if (alpha < 0.05f) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(scale, scale, 1f);
        int ox = center ? -font.width(s) / 2 : 0;
        g.drawString(font, s, ox, 0, Gfx.argb(alpha, rgb), false);
        g.pose().popPose();
    }

    private static float bannerAlpha(Banner b) {
        float fadeIn = Mth.clamp(b.age / 0.35f, 0f, 1f);
        float fadeOut = Mth.clamp((b.life - b.age) / 0.8f, 0f, 1f);
        return Math.min(fadeIn, fadeOut);
    }

    private void drawBanners(GuiGraphics g) {
        if (bigBanner != null) {
            float a = bannerAlpha(bigBanner);
            float y = height * 0.2f;
            text(g, bigBanner.kicker, width / 2f, y - 22, 0.85f, 0.85f * a, LINE, true);
            text(g, bigBanner.name, width / 2f, y, 2.6f, a, 0xFFFFFF, true);
            text(g, bigBanner.desc, width / 2f, y + 34, 1.0f, 0.85f * a, LINE, true);
            int half = (int) (80 + 130 * ease(Mth.clamp(bigBanner.age / 0.8f, 0f, 1f)));
            g.fill((int) (width / 2f - half), (int) (y + 52), (int) (width / 2f + half), (int) (y + 53), Gfx.argb(0.6f * a, BRIGHT));
        }
        if (toast != null) {
            float a = bannerAlpha(toast);
            float y = height - 124f - 8f * (1f - ease(Mth.clamp(toast.age / 0.3f, 0f, 1f)));
            text(g, toast.kicker, width / 2f, y - 14, 0.75f, 0.75f * a, LINE, true);
            text(g, toast.name, width / 2f, y, 1.5f, a, 0xFFFFFF, true);
            text(g, toast.desc, width / 2f, y + 18, 0.9f, 0.8f * a, LINE, true);
        }
    }

    private void drawSubtitles(GuiGraphics g) {
        float ct = cineTime;
        float fadeOut = Mth.clamp((COLLAPSE_START + 0.2f - ct) / 0.6f, 0f, 1f);
        float a1 = Mth.clamp((ct - 0.7f) / 0.5f, 0f, 1f) * fadeOut;
        float a2 = Mth.clamp((ct - 3.3f) / 0.5f, 0f, 1f) * fadeOut;
        float y = height * 0.76f;
        text(g, "This is your new life.", width / 2f, y, 1.5f, a1, BRIGHT, true);
        text(g, "Welcome to your upgrade tree.", width / 2f, y + 22, 1.5f, a2, BRIGHT, true);
        float skip = Mth.clamp((ct - 1.5f) / 0.8f, 0f, 1f) * 0.5f;
        text(g, "Click to skip", width / 2f, height - 20, 0.85f, skip, LINE, true);
    }

    private void drawLabelsAndHud(GuiGraphics g, PlayerProgress prog) {
        float ha = intro * hudFade;

        // branch titles at the tip of each arm
        float labelScale = Mth.clamp(zoom * 1.25f, 0.75f, 1.7f);
        for (int b = 0; b < TreeLayout.BRANCHES; b++) {
            float maxR = 0;
            for (int i = 0; i < TreeLayout.PER_BRANCH; i++) {
                int id = b * TreeLayout.PER_BRANCH + i;
                maxR = Math.max(maxR, (float) Math.hypot(layout.x[id], layout.y[id]));
            }
            float lx = (float) Math.cos(TreeLayout.AXIS[b]) * (maxR + 1.9f);
            float ly = (float) Math.sin(TreeLayout.AXIS[b]) * (maxR + 1.9f);
            Branch br = Branch.of(b);
            text(g, br.label.toUpperCase(), sx(lx), sy(ly) - 6 * labelScale, labelScale * 1.15f, 0.9f * ha, BRIGHT, true);
            text(g, prog.unlockedInBranch(br) + " / " + TreeLayout.PER_BRANCH, sx(lx), sy(ly) + 6 * labelScale,
                    labelScale * 0.85f, 0.6f * ha, LINE, true);
        }

        // title
        text(g, "A S C E N S I O N", width / 2f, 14, 1.6f, 0.95f * ha, BRIGHT, true);

        // level + xp bar (top left)
        int level = prog.level();
        long into = prog.totalXp - Progression.xpForLevel(level);
        long need = Progression.costOfLevel(level);
        text(g, "LEVEL " + level, 22, 16, 1.4f, ha, BRIGHT, false);
        float barW = 190f;
        if (ha > 0.05f) {
            g.fill(22, 36, (int) (22 + barW), 40, Gfx.argb(0.55f * ha, 0x16305E));
            g.fill(22, 36, (int) (22 + barW * Mth.clamp(into / (float) need, 0f, 1f)), 40, Gfx.argb(ha, 0xA9D8FF));
        }
        text(g, into + " / " + need + " XP", 22, 44, 0.85f, 0.7f * ha, LINE, false);

        // points (top right)
        int points = prog.availablePoints();
        text(g, "UPGRADE POINTS", width - 22 - font.width("UPGRADE POINTS") * 0.9f, 16, 0.9f, 0.8f * ha, LINE, false);
        String pts = Integer.toString(points);
        text(g, pts, width - 22 - font.width(pts) * 2.4f, 28, 2.4f, ha, points > 0 ? 0xFFFFFF : 0x7F95BD, false);
        text(g, "next point at level " + ((level / Progression.LEVELS_PER_POINT + 1) * Progression.LEVELS_PER_POINT),
                width - 22 - font.width("next point at level 0000") * 0.8f, 56, 0.8f, 0.55f * ha, LINE, false);

        // hint
        String key = ClientEvents.OPEN_KEY.getTranslatedKeyMessage().getString();
        text(g, "Drag to pan  \u2022  Scroll to zoom  \u2022  Click an orb to unlock  \u2022  R recentre  \u2022  I replay intro  \u2022  ["
                + key + "] or Esc to close", width / 2f, height - 18, 0.8f, 0.6f * ha, LINE, true);
    }

    // ------------------------------------------------------------------ ability key bar

    private int slotX(int i) {
        int total = PlayerProgress.SLOTS * SLOT_W + (PlayerProgress.SLOTS - 1) * SLOT_GAP;
        return (width - total) / 2 + i * (SLOT_W + SLOT_GAP);
    }

    private int slotY() {
        return height - 72;
    }

    private int slotAt(int mx, int my) {
        for (int i = 0; i < PlayerProgress.SLOTS; i++) {
            int x = slotX(i);
            if (mx >= x && mx < x + SLOT_W && my >= slotY() && my < slotY() + SLOT_H) return i;
        }
        return -1;
    }

    private void drawLoadout(GuiGraphics g, PlayerProgress prog, int mx, int my) {
        float ha = intro * hudFade;
        if (ha < 0.05f) return;
        text(g, "ABILITY KEYS", width / 2f, slotY() - 12, 0.8f, 0.7f * ha, LINE, true);
        for (int i = 0; i < PlayerProgress.SLOTS; i++) {
            int x = slotX(i), y = slotY();
            boolean over = i == hoveredSlot;
            Ability a = Ability.of(prog.slots[i]);
            g.fill(x - 1, y - 1, x + SLOT_W + 1, y + SLOT_H + 1, Gfx.argb((over ? 1f : 0.7f) * ha, over ? BRIGHT : 0x4F8FD6));
            g.fill(x, y, x + SLOT_W, y + SLOT_H, Gfx.argb(0.93f * ha, 0x060C20));
            String key = ClientEvents.ABILITY_KEYS[i].getTranslatedKeyMessage().getString();
            text(g, "[" + key + "]", x + 6, y + 5, 0.9f, ha, LINE, false);
            if (a == null) {
                text(g, "empty", x + SLOT_W / 2f, y + 19, 1.0f, 0.5f * ha, 0x7F95BD, true);
            } else {
                text(g, a.label, x + SLOT_W / 2f, y + 19, 1.0f, ha, 0xFFFFFF, true);
            }
        }
    }

    private List<Integer> equipOptions(PlayerProgress prog, int slot) {
        List<Integer> options = new ArrayList<>();
        options.add(-1);
        for (Ability a : Ability.values()) {
            if (a.passive || !prog.has(a)) continue;
            int at = prog.slotOf(a);
            if (at < 0 || at == slot) options.add(a.ordinal());
        }
        return options;
    }

    private void cycleSlot(int slot, int dir) {
        PlayerProgress prog = ClientProgress.get();
        List<Integer> options = equipOptions(prog, slot);
        int idx = Math.max(0, options.indexOf(prog.slots[slot]));
        int next = options.get(Math.floorMod(idx + dir, options.size()));
        PacketDistributor.sendToServer(new SetSlotPayload(slot, next));
        playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f + 0.1f * slot, 0.6f);
    }

    // ------------------------------------------------------------------ tooltip

    private static void add(List<FormattedCharSequence> seqs, List<Integer> cols, String s, int rgb) {
        seqs.add(Component.literal(s).getVisualOrderText());
        cols.add(rgb);
    }

    private void drawBox(GuiGraphics g, int mx, int my, List<FormattedCharSequence> seqs, List<Integer> cols) {
        int w = 0;
        for (FormattedCharSequence s : seqs) w = Math.max(w, font.width(s));
        w += 20;
        int h = seqs.size() * 10 + 14;
        int x = mx + 16, y = my + 10;
        if (x + w > width - 6) x = mx - w - 12;
        if (y + h > height - 6) y = height - 6 - h;
        if (y < 6) y = 6;

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xCC4F8FD6);
        g.fill(x, y, x + w, y + h, 0xF0050B1F);
        g.fill(x, y, x + w, y + 1, 0xFFBFE3FF);
        for (int i = 0; i < seqs.size(); i++) {
            g.drawString(font, seqs.get(i), x + 10, y + 8 + i * 10, 0xFF000000 | cols.get(i), false);
        }
    }

    private void drawTooltip(GuiGraphics g, int mx, int my, PlayerProgress prog) {
        if (intro < 0.9f || hudFade < 0.9f) return;
        List<FormattedCharSequence> seqs = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();

        if (hoveredSlot >= 0) {
            Ability a = Ability.of(prog.slots[hoveredSlot]);
            String key = ClientEvents.ABILITY_KEYS[hoveredSlot].getTranslatedKeyMessage().getString();
            add(seqs, cols, "ABILITY KEY  [" + key + "]", 0xFFFFFF);
            add(seqs, cols, a == null ? "Nothing assigned" : a.label, a == null ? 0x7F95BD : 0xD6EAFF);
            add(seqs, cols, "", 0);
            add(seqs, cols, "Left-click: next ability", 0xA9D8FF);
            add(seqs, cols, "Right-click: previous", 0xA9D8FF);
            add(seqs, cols, "Rebind the key in Controls.", 0x7184AA);
            drawBox(g, mx, my, seqs, cols);
            return;
        }
        if (hovered < 0) return;

        NodeDef n = Nodes.get(hovered);
        PlayerProgress.Check check = prog.check(n);
        int maxW = 210;

        add(seqs, cols, n.name().toUpperCase(), 0xFFFFFF);
        add(seqs, cols, n.branch().label + (n.milestone() ? "  \u2022  MILESTONE" : "  \u2022  Upgrade"), 0x7FA6D6);
        add(seqs, cols, "", 0);
        for (FormattedCharSequence s : font.split(Component.literal(n.description()), maxW)) {
            seqs.add(s);
            cols.add(0xD6EAFF);
        }
        Ability ability = Ability.ofNode(n.id());
        if (ability != null && !ability.passive) {
            int secs = ability.cooldownFor(prog) / 20;
            add(seqs, cols, "Cooldown: " + secs + "s", 0x8FB4E0);
            if (prog.isUnlocked(n.id())) {
                int at = prog.slotOf(ability);
                String keyName = at >= 0 ? ClientEvents.ABILITY_KEYS[at].getTranslatedKeyMessage().getString() : "";
                add(seqs, cols, at >= 0 ? "Equipped on [" + keyName + "]" : "Not equipped - pick a key below", 0x8FD0FF);
            }
        } else if (n.effect() == Effect.STACK_SIZE) {
            add(seqs, cols, "Stacks cap at 99 (Minecraft's limit).", 0x7184AA);
        } else if (n.effect() == Effect.ABILITY) {
            add(seqs, cols, "Adds Sort buttons to your inventory and chests.", 0x8FB4E0);
        }
        add(seqs, cols, "", 0);
        String cost = "Cost: " + n.cost() + (n.cost() == 1 ? " point" : " points");
        switch (check) {
            case ALREADY -> add(seqs, cols, "UNLOCKED", 0x8FD0FF);
            case OK -> {
                add(seqs, cols, "Click to unlock", 0xFFFFFF);
                add(seqs, cols, cost, 0xA9D8FF);
            }
            case NEED_PARENT -> {
                add(seqs, cols, "Requires: " + Nodes.get(n.parent()).name(), 0x8497BE);
                add(seqs, cols, cost, 0x6F82AA);
            }
            case NEED_BRANCH -> {
                add(seqs, cols, "Requires " + n.requiredSpent() + " points spent in " + n.branch().label
                        + " (you have " + prog.spentInBranch(n.branch()) + ")", 0x8497BE);
                add(seqs, cols, cost, 0x6F82AA);
            }
            case NEED_POINTS -> add(seqs, cols, cost + "  -  you have " + prog.availablePoints(), 0xE0A8A8);
        }
        drawBox(g, mx, my, seqs, cols);
    }

    // ================================================================== input

    private void clampPan() {
        panX = Mth.clamp(panX, -PAN_LIMIT, PAN_LIMIT);
        panY = Mth.clamp(panY, -PAN_LIMIT, PAN_LIMIT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (cinematic) {
            endCinematic(true);
            return true;
        }
        int slot = slotAt((int) mx, (int) my);
        if (slot >= 0 && (button == 0 || button == 1)) {
            cycleSlot(slot, button == 0 ? 1 : -1);
            return true;
        }
        if (button == 0) {
            pressed = true;
            dragged = false;
            pressX = mx;
            pressY = my;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pressed && button == 0) {
            if (!dragged && Math.hypot(mx - pressX, my - pressY) > 5) {
                dragged = true;
            }
            if (dragged) {
                panX -= (float) (dx / scale());
                panY -= (float) (dy / scale());
                clampPan();
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && pressed) {
            pressed = false;
            if (!dragged) {
                clickAt();
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (cinematic) return true;
        float before = scale();
        float wx = panX + (float) (mx - width * 0.5) / before;
        float wy = panY + (float) (my - height * 0.5) / before;
        zoom = Mth.clamp(zoom * (float) Math.pow(1.13, scrollY), MIN_ZOOM, MAX_ZOOM);
        float after = scale();
        panX = wx - (float) (mx - width * 0.5) / after;
        panY = wy - (float) (my - height * 0.5) / after;
        clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientEvents.OPEN_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (cinematic) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                endCinematic(true);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            panX = 0;
            panY = 0;
            zoom = fitZoom();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_I) {
            startCinematic();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clickAt() {
        if (hovered < 0) return;
        PlayerProgress prog = ClientProgress.get();
        NodeDef n = Nodes.get(hovered);
        if (prog.check(n) == PlayerProgress.Check.OK) {
            PacketDistributor.sendToServer(new UnlockNodePayload(n.id()));
            playUi(SoundEvents.AMETHYST_BLOCK_PLACE, 1.4f, 0.6f);
        } else if (prog.check(n) != PlayerProgress.Check.ALREADY) {
            playUi(SoundEvents.DISPENSER_FAIL, 0.7f, 0.5f);
        }
    }
}
