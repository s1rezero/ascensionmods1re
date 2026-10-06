package com.example.ascension.data;

/**
 * The 12 milestone orbs. Index = branch * 3 + k, matching {@link Nodes#MILESTONE_LOCAL}.
 * "Passive" milestones are not put on a key (the Sort Button lives in your inventory screens).
 */
public enum Ability {
    WHIRLING_EDGE(Branch.COMBAT, 0, "Whirling Edge", 160, false),
    TIME_FRACTURE(Branch.COMBAT, 1, "Time Fracture", 500, false),
    BLADE_MAELSTROM(Branch.COMBAT, 2, "Blade Maelstrom", 1400, false),

    HASTE_SURGE(Branch.MINING, 0, "Haste Surge", 700, false),
    VEIN_BURST(Branch.MINING, 1, "Vein Burst", 300, false),
    SEISMIC_PULSE(Branch.MINING, 2, "Seismic Pulse", 900, false),

    BLINK(Branch.EXPLORATION, 0, "Blink", 120, false),
    SKY_LEAP(Branch.EXPLORATION, 1, "Sky Leap", 260, false),
    STARWALK(Branch.EXPLORATION, 2, "Starwalk", 1800, false),

    SORT_BUTTON(Branch.INVENTORY, 0, "Sort Button", 0, true),
    ITEM_MAGNET(Branch.INVENTORY, 1, "Item Magnet", 400, false),
    POCKET_DIMENSION(Branch.INVENTORY, 2, "Pocket Dimension", 100, false);

    public static final int COUNT = values().length;

    public final Branch branch;
    public final int milestone;
    public final String label;
    public final int cooldownTicks;
    public final boolean passive;

    Ability(Branch branch, int milestone, String label, int cooldownTicks, boolean passive) {
        this.branch = branch;
        this.milestone = milestone;
        this.label = label;
        this.cooldownTicks = cooldownTicks;
        this.passive = passive;
    }

    /** Cooldown in ticks after the "Ability Cooldown" orbs (never below half). */
    public int cooldownFor(PlayerProgress progress) {
        double mult = Math.max(0.5, 1.0 + progress.sum(Effect.COOLDOWN));
        return (int) Math.round(cooldownTicks * mult);
    }

    /** Global id of the orb that unlocks this ability. */
    public int nodeId() {
        return branch.ordinal() * TreeLayout.PER_BRANCH + Nodes.MILESTONE_LOCAL[milestone];
    }

    public static Ability of(int index) {
        return index >= 0 && index < COUNT ? values()[index] : null;
    }

    /** The ability a node unlocks, or null if the node is an ordinary upgrade. */
    public static Ability ofNode(int nodeId) {
        for (Ability a : values()) {
            if (a.nodeId() == nodeId) return a;
        }
        return null;
    }
}
