package com.example.ascension.client;

import org.joml.Matrix4f;

import com.example.ascension.AscensionMod;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Tiny immediate-mode batcher for the tree screen: textured sprites (rotated, tinted, optionally additive) and
 * soft ribbons for the branch links. Always: begin..., many draws, end... (one batch at a time).
 */
public final class Gfx {
    public static final ResourceLocation GLOW = AscensionMod.id("textures/gui/glow.png");
    public static final ResourceLocation ORB = AscensionMod.id("textures/gui/orb.png");
    public static final ResourceLocation RING = AscensionMod.id("textures/gui/ring.png");
    public static final ResourceLocation STAR = AscensionMod.id("textures/gui/star.png");

    private static BufferBuilder buf;

    private Gfx() {}

    public static int argb(float alpha, int rgb) {
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static void setup(boolean additive) {
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void finish() {
        MeshData mesh = buf.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
        buf = null;
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ textured sprites

    public static void beginTex(ResourceLocation texture, boolean additive) {
        setup(additive);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    /** A square sprite centred on (cx, cy), {@code size} pixels wide, rotated by {@code rot} radians. */
    public static void tex(Matrix4f m, float cx, float cy, float size, float rot, int argb) {
        float h = size * 0.5f;
        float c = Mth.cos(rot) * h;
        float s = Mth.sin(rot) * h;
        // corners (-h,-h) (h,-h) (h,h) (-h,h) rotated
        buf.addVertex(m, cx - c + s, cy - s - c, 0f).setUv(0f, 0f).setColor(argb);
        buf.addVertex(m, cx + c + s, cy + s - c, 0f).setUv(1f, 0f).setColor(argb);
        buf.addVertex(m, cx + c - s, cy + s + c, 0f).setUv(1f, 1f).setColor(argb);
        buf.addVertex(m, cx - c - s, cy - s + c, 0f).setUv(0f, 1f).setColor(argb);
    }

    public static void endTex() {
        finish();
    }

    // ------------------------------------------------------------------ colored ribbons

    public static void beginColor(boolean additive) {
        setup(additive);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    }

    /** A smooth ribbon through the points; colour fades from {@code c0} (first point) to {@code c1} (last). */
    public static void ribbon(Matrix4f m, float[] xs, float[] ys, int count, float width, int c0, int c1) {
        float hw = width * 0.5f;
        float[] lx = new float[count];
        float[] ly = new float[count];
        float[] rx = new float[count];
        float[] ry = new float[count];
        for (int i = 0; i < count; i++) {
            int a = Math.max(0, i - 1);
            int b = Math.min(count - 1, i + 1);
            float tx = xs[b] - xs[a];
            float ty = ys[b] - ys[a];
            float len = Mth.sqrt(tx * tx + ty * ty);
            if (len < 1e-4f) {
                tx = 1;
                ty = 0;
                len = 1;
            }
            float nx = -ty / len * hw;
            float ny = tx / len * hw;
            lx[i] = xs[i] + nx;
            ly[i] = ys[i] + ny;
            rx[i] = xs[i] - nx;
            ry[i] = ys[i] - ny;
        }
        for (int i = 0; i < count - 1; i++) {
            float t0 = (float) i / (count - 1);
            float t1 = (float) (i + 1) / (count - 1);
            int col0 = lerpArgb(c0, c1, t0);
            int col1 = lerpArgb(c0, c1, t1);
            buf.addVertex(m, lx[i], ly[i], 0f).setColor(col0);
            buf.addVertex(m, rx[i], ry[i], 0f).setColor(col0);
            buf.addVertex(m, rx[i + 1], ry[i + 1], 0f).setColor(col1);
            buf.addVertex(m, lx[i + 1], ly[i + 1], 0f).setColor(col1);
        }
    }

    public static void endColor() {
        finish();
    }

    private static int lerpArgb(int a, int b, float t) {
        int aa = (int) Mth.lerp(t, (a >>> 24) & 255, (b >>> 24) & 255);
        return (aa << 24) | lerpRgb(a & 0xFFFFFF, b & 0xFFFFFF, t);
    }
}
