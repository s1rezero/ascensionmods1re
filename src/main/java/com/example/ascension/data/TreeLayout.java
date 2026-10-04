package com.example.ascension.data;

import java.util.Random;

/**
 * Deterministic, organic layout of the four skill branches. Pure math (no Minecraft classes) so the server can use
 * the same parent links the client draws. Positions are in "tree units"; the screen multiplies by a pixel scale.
 */
public final class TreeLayout {
    public static final int BRANCHES = 4;
    public static final int PER_BRANCH = 35;
    public static final int TOTAL = BRANCHES * PER_BRANCH;
    public static final double ROOT_DIST = 2.3;

    /** Branch axes in screen space (y points down): up-right, down-right, down-left, up-left. */
    public static final double[] AXIS = {
            Math.toRadians(-45), Math.toRadians(45), Math.toRadians(135), Math.toRadians(225)
    };

    private static final double CONE = Math.toRadians(39);
    private static final double MIN_NODE_DIST = 1.0;

    public final int[] parent = new int[TOTAL];   // global parent id, or -1 (attached to the core)
    public final double[] x = new double[TOTAL];
    public final double[] y = new double[TOTAL];
    public final double[] bend = new double[TOTAL]; // curvature of the link to the parent (fraction of its length)

    private static final class Holder {
        static final TreeLayout INSTANCE = new TreeLayout();
    }

    public static TreeLayout get() {
        return Holder.INSTANCE;
    }

    private TreeLayout() {
        for (int b = 0; b < BRANCHES; b++) {
            growBranch(b);
        }
    }

    /** Nodes per tier (distance from the core), summing to {@link #PER_BRANCH}. */
    private static final int[] TIERS = {1, 2, 3, 4, 5, 5, 6, 5, 4};
    private static final double TIER_STEP = 1.45;
    private static final double SPIRAL = 0.045; // radians of twist per tier, gives each arm a galaxy-like sweep

    private void growBranch(int b) {
        Random rng = new Random(90210L + b * 7919L);
        double axis = AXIS[b];

        int[] prevIds = null;       // global ids of the previous tier, sorted by angle
        int next = b * PER_BRANCH;  // next global id to hand out

        for (int d = 0; d < TIERS.length; d++) {
            int n = TIERS[d];
            double cone = Math.min(CONE, 0.16 + 0.085 * (d + 1));
            double base = axis + SPIRAL * d;
            int[] ids = new int[n];

            // --- angles: evenly spread over the cone with jitter, kept in order so links never cross
            double[] ang = new double[n];
            for (int i = 0; i < n; i++) {
                double t = n == 1 ? 0.5 : (double) i / (n - 1);
                double gap = n == 1 ? 0 : (2 * cone * 0.92) / (n - 1);
                ang[i] = base + (t * 2 - 1) * cone * 0.92 + (rng.nextDouble() - 0.5) * gap * 0.55;
            }

            // --- place nodes, re-rolling the radial jitter if one lands too close to a neighbour
            for (int i = 0; i < n; i++) {
                int g = next++;
                ids[i] = g;
                double bestX = 0, bestY = 0;
                for (int attempt = 0; attempt < 120; attempt++) {
                    double r = ROOT_DIST + d * TIER_STEP + (rng.nextDouble() - 0.5) * (attempt < 60 ? 0.9 : 0.5);
                    double px = Math.cos(ang[i]) * r;
                    double py = Math.sin(ang[i]) * r;
                    bestX = px;
                    bestY = py;
                    if (clearOfOthers(g, px, py)) {
                        break;
                    }
                }
                x[g] = bestX;
                y[g] = bestY;
            }

            // --- parents: order-preserving matching onto the previous tier
            if (prevIds == null) {
                parent[ids[0]] = -1;
            } else {
                assignParents(ids, prevIds, rng);
            }
            prevIds = ids;
        }

        for (int i = 0; i < PER_BRANCH; i++) {
            int g = b * PER_BRANCH + i;
            Random br = new Random(31L * g + 17);
            double mag = 0.10 + br.nextDouble() * 0.24;
            bend[g] = (br.nextBoolean() ? 1 : -1) * mag;
        }
    }

    private boolean clearOfOthers(int self, double px, double py) {
        for (int g = 0; g < self; g++) {
            if (Math.hypot(x[g] - px, y[g] - py) < MIN_NODE_DIST) {
                return false;
            }
        }
        return true;
    }

    private void assignParents(int[] kids, int[] parents, Random rng) {
        int n = kids.length;
        int m = parents.length;
        int[] count = new int[m];
        if (n >= m) {
            java.util.Arrays.fill(count, 1);
            int extra = n - m;
            int guard = 0;
            while (extra > 0 && guard++ < 500) {
                int j = rng.nextInt(m);
                if (count[j] < 3) {
                    count[j]++;
                    extra--;
                }
            }
        } else {
            // some parents end here (leaves): choose which ones, never all the outer ones
            int drop = m - n;
            boolean[] dead = new boolean[m];
            int guard = 0;
            while (drop > 0 && guard++ < 500) {
                int j = rng.nextInt(m);
                if (!dead[j]) {
                    dead[j] = true;
                    drop--;
                }
            }
            for (int j = 0; j < m; j++) {
                count[j] = dead[j] ? 0 : 1;
            }
        }
        int k = 0;
        for (int j = 0; j < m; j++) {
            for (int c = 0; c < count[j] && k < n; c++) {
                parent[kids[k++]] = parents[j];
            }
        }
    }

}
