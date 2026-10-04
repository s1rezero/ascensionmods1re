package com.example.ascension.client;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.joml.Matrix4f;

import com.example.ascension.data.Branch;
import com.example.ascension.data.NodeDef;
import com.example.ascension.data.Nodes;
import com.example.ascension.data.PlayerProgress;
import com.example.ascension.data.Progression;
import com.example.ascension.data.TreeLayout;
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

/** Full-screen, galaxy-style upgrade tree. Everything here is drawn by hand: no vanilla menu widgets. */
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

    private static final class Particle {
        float x, y, vx, vy, age, life, size, maxAlpha, drag;
        int rgb;
        boolean ambient;
    }

    private static final class Wave {
        float x, y, age, delay, life, maxR;
    }

    private final TreeLayout layout = TreeLayout.get();
    private final RandomSource rng = RandomSource.create();

    // per-frame geometry / state
    private final float[][] edgeX = new float[Nodes.COUNT][SEG + 1];
    private final float[][] edgeY = new float[Nodes.COUNT][SEG + 1];
    private final float[] nodeX = new float[Nodes.COUNT];
    private final float[] nodeY = new float[Nodes.COUNT];
    private final int[] state = new int[Nodes.COUNT];
    private final boolean[] onPath = new boolean[Nodes.COUNT];
    private final float[] hover = new float[Nodes.COUNT];
    private final float[] flash = new float[Nodes.COUNT];

    private final List<Particle> particles = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
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
    private int hovered = -1;
    private BitSet seenUnlocked;

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
    }

    @Override
    protected void init() {
        if (!placed) {
            placed = true;
            zoom = Mth.clamp(Math.min(width, height) / (2f * 15.5f * UNIT) * 1.08f, MIN_ZOOM, 1.0f);
            seenUnlocked = (BitSet) ClientProgress.get().unlocked.clone();
            playUi(SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.5f);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // keep the world (and our network traffic) running while the tree is open
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // intentionally empty: we paint our own backdrop in render()
    }

    // ================================================================== helpers

    private float scale() {
        return UNIT * zoom * (0.85f + 0.15f * intro);
    }

    private float nodeScale() {
        return (float) Math.pow(zoom, 0.6) * (0.9f + 0.1f * intro);
    }

    private float sx(float wx) {
        return width * 0.5f + (wx - panX) * scale();
    }

    private float sy(float wy) {
        return height * 0.5f + (wy - panY) * scale();
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t) * (1f - t);
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
        updateGeometry(prog);
        updateHover(mouseX, mouseY);
        updateAnimation(dt, prog);

        g.fillGradient(0, 0, width, height, 0xFF02040C, 0xFF0B1738);
        g.flush();

        Matrix4f m = g.pose().last().pose();
        drawBackdrop(m, t);
        drawEdges(m, t);
        drawNodes(m, t);
        drawParticles(m);
        drawLabelsAndHud(g, prog);
        drawTooltip(g, mouseX, mouseY, prog);
    }

    // ------------------------------------------------------------------ update

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
                state[id] = UNLOCKED;
            } else if (prog.check(n) == PlayerProgress.Check.OK) {
                state[id] = AVAILABLE;
            } else {
                boolean parentOk = n.parent() < 0 || prog.isUnlocked(n.parent());
                state[id] = parentOk ? REACHABLE : LOCKED;
            }
        }
    }

    private void updateHover(int mx, int my) {
        int best = -1;
        float bestD = Float.MAX_VALUE;
        if (intro > 0.6f) {
            for (int id = 0; id < Nodes.COUNT; id++) {
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
        java.util.Arrays.fill(onPath, false);
        for (int id = hovered; id >= 0; id = layout.parent[id]) {
            onPath[id] = true;
        }
    }

    private void updateAnimation(float dt, PlayerProgress prog) {
        for (int id = 0; id < Nodes.COUNT; id++) {
            float target = id == hovered ? 1f : 0f;
            hover[id] += (target - hover[id]) * Math.min(1f, dt * 14f);
            flash[id] = Math.max(0f, flash[id] - dt * 1.3f);
        }
        coreBeat = Math.max(0f, coreBeat - dt * 1.2f);

        // newly unlocked orbs -> celebration
        for (int id = 0; id < Nodes.COUNT; id++) {
            if (prog.isUnlocked(id) && !seenUnlocked.get(id)) {
                celebrate(id);
            }
        }
        seenUnlocked = (BitSet) prog.unlocked.clone();

        // hover sparkle
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

    private void celebrate(int id) {
        NodeDef n = Nodes.get(id);
        float wx = (float) layout.x[id], wy = (float) layout.y[id];
        flash[id] = 1f;
        coreBeat = 1f;
        int count = n.milestone() ? 110 : 36;
        for (int i = 0; i < count; i++) {
            float a = rng.nextFloat() * 6.2831f;
            float speed = (n.milestone() ? 1.2f : 0.7f) + rng.nextFloat() * (n.milestone() ? 3.2f : 1.8f);
            int rgb = rng.nextFloat() < 0.5f ? BRIGHT : (rng.nextFloat() < 0.5f ? GLOW_BLUE : LINE);
            spawn(wx, wy, Mth.cos(a) * speed, Mth.sin(a) * speed, 0.8f + rng.nextFloat() * 1.4f,
                    8f + rng.nextFloat() * 16f, 0.95f, rgb, false);
        }
        int rings = n.milestone() ? 3 : 1;
        for (int i = 0; i < rings; i++) {
            Wave w = new Wave();
            w.x = wx;
            w.y = wy;
            w.delay = i * 0.16f;
            w.life = 0.9f + i * 0.15f;
            w.maxR = (n.milestone() ? 230f : 90f) * (1f + i * 0.35f);
            waves.add(w);
        }
        if (n.milestone()) {
            playUi(SoundEvents.BEACON_ACTIVATE, 1.2f, 0.7f);
            playUi(SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7f, 0.9f);
        } else {
            playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f + rng.nextFloat() * 0.3f, 0.8f);
        }
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
        Gfx.tex(m, sx(0), sy(0), 760f * zoom + 160f, 0, Gfx.argb((0.20f + 0.15f * coreBeat) * intro, 0x2F6FD0));

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
                coreA *= intro;
                glowA *= intro;
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
            if (state[id] != UNLOCKED) continue;
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

        // glows
        Gfx.beginTex(Gfx.GLOW, true);
        {
            float cr = 26f * ns * (1f + 0.25f * coreBeat);
            Gfx.tex(m, sx(0), sy(0), cr * 7f, 0, Gfx.argb((0.55f + 0.25f * pulse + 0.2f * coreBeat) * intro, GLOW_BLUE));
        }
        for (int id = 0; id < Nodes.COUNT; id++) {
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
            Gfx.tex(m, nodeX[id], nodeY[id], size, 0, Gfx.argb(a * intro, rgb));
        }
        Gfx.endTex();

        // orb bodies
        Gfx.beginTex(Gfx.ORB, false);
        Gfx.tex(m, sx(0), sy(0), 26f * ns * 2f * (1f + 0.15f * coreBeat), 0, Gfx.argb(intro, 0xF4FCFF));
        for (int id = 0; id < Nodes.COUNT; id++) {
            float r = radius(id);
            int rgb;
            float a = 1f;
            switch (state[id]) {
                case UNLOCKED -> rgb = 0xF4FCFF;
                case AVAILABLE -> rgb = 0xA8D3F5;
                case REACHABLE -> { rgb = 0x4F70A8; a = 0.95f; }
                default -> { rgb = 0x1B2A55; a = 0.9f; }
            }
            Gfx.tex(m, nodeX[id], nodeY[id], r * 2f, 0, Gfx.argb(a * intro, rgb));
        }
        Gfx.endTex();

        // rings, sparkles and the milestone stars
        Gfx.beginTex(Gfx.RING, true);
        Gfx.tex(m, sx(0), sy(0), 26f * ns * 3.0f, 0, Gfx.argb((0.55f + 0.3f * coreBeat) * intro, BRIGHT));
        Gfx.tex(m, sx(0), sy(0), 26f * ns * (3.9f + 0.5f * pulse), 0, Gfx.argb(0.28f * intro, GLOW_BLUE));
        for (int id = 0; id < Nodes.COUNT; id++) {
            NodeDef n = Nodes.get(id);
            float r = radius(id);
            int s = state[id];
            if (s == AVAILABLE) {
                float k = (t * 0.9f + id * 0.31f) % 1f;
                Gfx.tex(m, nodeX[id], nodeY[id], r * (2.2f + 1.6f * k), 0, Gfx.argb((1f - k) * 0.75f * intro, BRIGHT));
            }
            if (n.milestone()) {
                float a = s == UNLOCKED ? 0.85f : s == LOCKED ? 0.18f : 0.5f;
                Gfx.tex(m, nodeX[id], nodeY[id], r * 2.9f, 0, Gfx.argb(a * intro, LINE));
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
        Gfx.tex(m, sx(0), sy(0), 26f * ns * 7f, t * 0.25f, Gfx.argb((0.5f + 0.4f * coreBeat) * intro, BRIGHT));
        for (int id = 0; id < Nodes.COUNT; id++) {
            NodeDef n = Nodes.get(id);
            int s = state[id];
            float r = radius(id);
            if (n.milestone()) {
                float a = s == UNLOCKED ? 0.8f : s == LOCKED ? 0.12f : 0.4f;
                Gfx.tex(m, nodeX[id], nodeY[id], r * 5.2f, t * 0.35f + id, Gfx.argb(a * intro, BRIGHT));
            } else if (s == UNLOCKED) {
                Gfx.tex(m, nodeX[id], nodeY[id], r * 3.4f, 0.6f, Gfx.argb(0.28f * intro, BRIGHT));
            }
            if (flash[id] > 0.01f) {
                Gfx.tex(m, nodeX[id], nodeY[id], r * 9f * flash[id] + r * 3f, t * 2f, Gfx.argb(flash[id] * intro, BRIGHT));
            }
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

    private void drawLabelsAndHud(GuiGraphics g, PlayerProgress prog) {
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
            text(g, br.label.toUpperCase(), sx(lx), sy(ly) - 6 * labelScale, labelScale * 1.15f, 0.9f * intro, BRIGHT, true);
            text(g, prog.unlockedInBranch(br) + " / " + TreeLayout.PER_BRANCH, sx(lx), sy(ly) + 6 * labelScale,
                    labelScale * 0.85f, 0.6f * intro, LINE, true);
        }

        // title
        text(g, "A S C E N S I O N", width / 2f, 14, 1.6f, 0.95f * intro, BRIGHT, true);

        // level + xp bar (top left)
        int level = prog.level();
        long into = prog.totalXp - Progression.xpForLevel(level);
        long need = Progression.costOfLevel(level);
        text(g, "LEVEL " + level, 22, 16, 1.4f, intro, BRIGHT, false);
        float barW = 190f;
        g.fill(22, 36, (int) (22 + barW), 40, Gfx.argb(0.55f * intro, 0x16305E));
        g.fill(22, 36, (int) (22 + barW * Mth.clamp(into / (float) need, 0f, 1f)), 40, Gfx.argb(intro, 0xA9D8FF));
        text(g, into + " / " + need + " XP", 22, 44, 0.85f, 0.7f * intro, LINE, false);

        // points (top right)
        int points = prog.availablePoints();
        text(g, "UPGRADE POINTS", width - 22 - font.width("UPGRADE POINTS") * 0.9f, 16, 0.9f, 0.8f * intro, LINE, false);
        String pts = Integer.toString(points);
        text(g, pts, width - 22 - font.width(pts) * 2.4f, 28, 2.4f, intro, points > 0 ? 0xFFFFFF : 0x7F95BD, false);
        text(g, "next point at level " + ((level / Progression.LEVELS_PER_POINT + 1) * Progression.LEVELS_PER_POINT),
                width - 22 - font.width("next point at level 0000") * 0.8f, 56, 0.8f, 0.55f * intro, LINE, false);

        // hint
        String key = ClientEvents.OPEN_KEY.getTranslatedKeyMessage().getString();
        text(g, "Drag to pan  \u2022  Scroll to zoom  \u2022  Click an orb to unlock  \u2022  R to recentre  \u2022  [" + key
                + "] or Esc to close", width / 2f, height - 18, 0.9f, 0.6f * intro, LINE, true);
    }

    private void drawTooltip(GuiGraphics g, int mx, int my, PlayerProgress prog) {
        if (hovered < 0 || intro < 0.9f) return;
        NodeDef n = Nodes.get(hovered);
        PlayerProgress.Check check = prog.check(n);
        int maxW = 200;

        List<FormattedCharSequence> seqs = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        add(seqs, cols, n.name().toUpperCase(), 0xFFFFFF);
        add(seqs, cols, n.branch().label + (n.milestone() ? "  \u2022  MILESTONE" : "  \u2022  Upgrade"), 0x7FA6D6);
        add(seqs, cols, "", 0);
        for (FormattedCharSequence s : font.split(Component.literal(n.description()), maxW)) {
            seqs.add(s);
            cols.add(0xD6EAFF);
        }
        if (!n.live()) {
            for (FormattedCharSequence s : font.split(Component.literal("Takes effect in a later update."), maxW)) {
                seqs.add(s);
                cols.add(0x7184AA);
            }
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
            case NEED_POINTS -> {
                add(seqs, cols, cost + "  -  you have " + prog.availablePoints(), 0xE0A8A8);
            }
        }

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

    private static void add(List<FormattedCharSequence> seqs, List<Integer> cols, String s, int rgb) {
        seqs.add(Component.literal(s).getVisualOrderText());
        cols.add(rgb);
    }

    // ================================================================== input

    private void clampPan() {
        panX = Mth.clamp(panX, -PAN_LIMIT, PAN_LIMIT);
        panY = Mth.clamp(panY, -PAN_LIMIT, PAN_LIMIT);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
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
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            panX = 0;
            panY = 0;
            zoom = Mth.clamp(Math.min(width, height) / (2f * 15.5f * UNIT) * 1.08f, MIN_ZOOM, 1.0f);
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
