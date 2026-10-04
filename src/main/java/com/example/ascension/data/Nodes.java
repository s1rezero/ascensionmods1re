package com.example.ascension.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.ascension.AscensionMod;

import static com.example.ascension.data.Effect.*;

/** The full list of 140 upgrades (35 per branch). Index order inside a branch = how far from the core it sits. */
public final class Nodes {
    /** Local index of the three major milestones in every branch, and the points that must be spent first. */
    public static final int[] MILESTONE_LOCAL = {11, 23, 34};
    public static final int[] MILESTONE_REQUIRED = {8, 18, 30};
    public static final int MILESTONE_COST = 3;

    public static final List<NodeDef> ALL;
    public static final int COUNT = TreeLayout.TOTAL;

    private record Spec(String name, Effect effect, double value, String desc) {}

    private static Spec p(String name, Effect effect, double value) {
        return new Spec(name, effect, value, null);
    }

    private static Spec m(String name, String desc) {
        return new Spec(name, ABILITY, 0, desc);
    }

    // -------------------------------------------------------------------------------------------- COMBAT
    private static final Spec[] COMBAT = {
            p("Awakening Strike", ATTACK_DAMAGE, 0.02),
            p("Quick Wrists", ATTACK_SPEED, 0.02),
            p("Sharpened Will", ATTACK_DAMAGE, 0.02),
            p("Hardened Skin", ARMOR, 0.5),
            p("Light Footing", MOVE_SPEED, 0.01),
            p("Steady Grip", ATTACK_SPEED, 0.02),
            p("Reach of Blades", ENTITY_REACH, 0.1),
            p("Vital Spark", MAX_HEALTH, 1),
            p("Keen Edge", ATTACK_DAMAGE, 0.02),
            p("Rooted Stance", KNOCKBACK_RES, 0.02),
            p("Rapid Cuts", ATTACK_SPEED, 0.03),
            m("Whirling Edge", "Ability: spin on the spot, striking every enemy around you."),
            p("Battle Rhythm", ATTACK_SPEED, 0.03),
            p("Crushing Force", ATTACK_DAMAGE, 0.03),
            p("Hunter's Pulse", MOVE_SPEED, 0.01),
            p("Thick Blood", MAX_HEALTH, 2),
            p("Sweeping Arc", SWEEP, 0.05),
            p("Iron Resolve", ARMOR, 1),
            p("Predator's Reach", ENTITY_REACH, 0.15),
            p("Killing Intent", CRIT_CHANCE, 0.02),
            p("Warrior's Heart", MAX_HEALTH, 2),
            p("Unyielding", KNOCKBACK_RES, 0.03),
            p("Fury", ATTACK_DAMAGE, 0.03),
            m("Time Fracture", "Ability: slow every nearby enemy to a crawl for a few seconds."),
            p("Blood Siphon", LIFESTEAL, 0.01),
            p("Overdrive", ATTACK_SPEED, 0.04),
            p("Titan's Might", ATTACK_DAMAGE, 0.04),
            p("Fortified", ARMOR, 1.5),
            p("War Cry", COOLDOWN, -0.03),
            p("Lethal Precision", CRIT_DAMAGE, 0.05),
            p("Ascendant Body", MAX_HEALTH, 3),
            p("Duelist's Grace", MOVE_SPEED, 0.02),
            p("Cataclysm Edge", ATTACK_DAMAGE, 0.05),
            p("Final Form", ATTACK_SPEED, 0.05),
            m("Blade Maelstrom", "Ability: a storm of spectral blades shreds everything in an area."),
    };

    // -------------------------------------------------------------------------------------------- MINING
    private static final Spec[] MINING = {
            p("Prospector's Eye", MINING_SPEED, 0.03),
            p("Steady Pickaxe", MINING_SPEED, 0.03),
            p("Stone Whisperer", MINING_EFFICIENCY, 0.25),
            p("Deep Breath", UNDERWATER_MINING, 0.04),
            p("Long Arm", BLOCK_REACH, 0.15),
            p("Rock Breaker", MINING_SPEED, 0.03),
            p("Fortunate Hands", LUCK, 0.1),
            p("Tiny Fortune", FORTUNE, 0.1),
            p("Tool Mender", MENDING, 0.1),
            p("Swift Hands", MINING_SPEED, 0.04),
            p("Strong Back", BLOCK_REACH, 0.15),
            m("Haste Surge", "Ability: a burst of Haste for a short time, then a cooldown."),
            p("Granite Grip", MINING_SPEED, 0.04),
            p("Fortune Whisper", FORTUNE, 0.1),
            p("Efficient Swings", MINING_EFFICIENCY, 0.25),
            p("Mending Dust", MENDING, 0.1),
            p("Safe Footing", SAFE_FALL, 0.5),
            p("Tunnel Vision", MINING_SPEED, 0.05),
            p("Gem Sense", LUCK, 0.15),
            p("Deep Delver", UNDERWATER_MINING, 0.04),
            p("Fortune Echo", FORTUNE, 0.1),
            p("Obsidian Will", MINING_SPEED, 0.05),
            p("Mender's Touch", MENDING, 0.1),
            m("Vein Burst", "Ability: break an entire connected ore vein in one swing."),
            p("Core Resonance", MINING_EFFICIENCY, 0.35),
            p("Fortune's Favor", FORTUNE, 0.1),
            p("Titanic Swing", MINING_SPEED, 0.06),
            p("Far Reach", BLOCK_REACH, 0.25),
            p("Resonant Rhythm", COOLDOWN, -0.03),
            p("Ore Sense", LUCK, 0.15),
            p("Earthshaker", MINING_SPEED, 0.06),
            p("Fortune's Grace", FORTUNE, 0.1),
            p("Eternal Pick", MENDING, 0.15),
            p("Planet Breaker", MINING_SPEED, 0.08),
            m("Seismic Pulse", "Ability: shatter the blocks around you in a shockwave."),
    };

    // ---------------------------------------------------------------------------------------- EXPLORATION
    private static final Spec[] EXPLORATION = {
            p("First Step", MOVE_SPEED, 0.02),
            p("Light Heart", GRAVITY, -0.03),
            p("Spring Legs", JUMP, 0.03),
            p("Soft Landing", SAFE_FALL, 0.5),
            p("Open Road", MOVE_SPEED, 0.02),
            p("Stepping Stones", STEP_HEIGHT, 0.1),
            p("Featherfall", GRAVITY, -0.03),
            p("Trailblazer", MOVE_SPEED, 0.02),
            p("Hop Hop", JUMP, 0.04),
            p("Cushioned", SAFE_FALL, 0.5),
            p("Float", GRAVITY, -0.04),
            m("Blink", "Ability: dash instantly in the direction you are looking."),
            p("Swift Current", MOVE_SPEED, 0.03),
            p("Rising Wind", JUMP, 0.04),
            p("Stride", STEP_HEIGHT, 0.1),
            p("Weightless Heart", GRAVITY, -0.04),
            p("Skyward", SAFE_FALL, 1),
            p("Nimble", SNEAK_SPEED, 0.05),
            p("Windrunner", MOVE_SPEED, 0.03),
            p("Starfall", FALL_DAMAGE, -0.05),
            p("Cloud Hopper", JUMP, 0.05),
            p("Drift", GRAVITY, -0.05),
            p("Pathfinder", MOVE_SPEED, 0.03),
            m("Sky Leap", "Ability: launch skyward, then drift gently back down."),
            p("Horizon Chaser", MOVE_SPEED, 0.04),
            p("Feather Soul", GRAVITY, -0.05),
            p("Meteor Landing", FALL_DAMAGE, -0.1),
            p("Orbit Leap", JUMP, 0.05),
            p("Cosmic Stride", STEP_HEIGHT, 0.2),
            p("Slipstream", COOLDOWN, -0.03),
            p("Zero-G Heart", GRAVITY, -0.06),
            p("Comet Speed", MOVE_SPEED, 0.05),
            p("Skybound", SAFE_FALL, 2),
            p("Astral Wanderer", JUMP, 0.06),
            m("Starwalk", "Ability: briefly walk on thin air."),
    };

    // ----------------------------------------------------------------------------------------- INVENTORY
    private static final Spec[] INVENTORY = {
            p("Roomy Pockets", STACK_SIZE, 0.04),
            p("Better Folds", STACK_SIZE, 0.04),
            p("Packed Vitality", MAX_HEALTH, 1),
            p("Tidy Habits", STACK_SIZE, 0.04),
            p("Careful Steps", SNEAK_SPEED, 0.04),
            p("Cinched Straps", STACK_SIZE, 0.05),
            p("Sturdy Satchel", ARMOR, 0.5),
            p("Compression", STACK_SIZE, 0.05),
            p("Light Load", MOVE_SPEED, 0.01),
            p("Folded Space", STACK_SIZE, 0.05),
            p("Deep Pockets", STACK_SIZE, 0.05),
            m("Sort Button", "Adds a sort button to your inventory and chests."),
            p("Magnet Seed", PICKUP_RANGE, 1),
            p("Ironbound Pack", STACK_SIZE, 0.06),
            p("Packed Vitality II", MAX_HEALTH, 1),
            p("Dense Packing", STACK_SIZE, 0.06),
            p("Pull", PICKUP_RANGE, 1),
            p("Quiet Packing", SNEAK_SPEED, 0.04),
            p("Space Weave", STACK_SIZE, 0.06),
            p("Hardened Straps", ARMOR, 1),
            p("Wider Pull", PICKUP_RANGE, 1),
            p("Void Lining", STACK_SIZE, 0.07),
            p("Hoarder's Vigor", MAX_HEALTH, 2),
            m("Item Magnet", "Ability: pull every nearby item straight to you."),
            p("Compressed Space", STACK_SIZE, 0.07),
            p("Longer Pull", PICKUP_RANGE, 2),
            p("Fold Reality", STACK_SIZE, 0.08),
            p("Greater Vitality", MAX_HEALTH, 2),
            p("Stack Mastery", STACK_SIZE, 0.08),
            p("Wide Reach", BLOCK_REACH, 0.2),
            p("Dimensional Weave", STACK_SIZE, 0.08),
            p("Pack Mule Heart", MAX_HEALTH, 2),
            p("Infinite Fold", STACK_SIZE, 0.10),
            p("Gravity Well", PICKUP_RANGE, 3),
            m("Pocket Dimension", "Ability: open a private storage space from anywhere."),
    };

    static {
        Spec[][] tables = {COMBAT, MINING, EXPLORATION, INVENTORY};
        TreeLayout layout = TreeLayout.get();
        List<NodeDef> list = new ArrayList<>();
        for (int b = 0; b < TreeLayout.BRANCHES; b++) {
            Spec[] table = tables[b];
            if (table.length != TreeLayout.PER_BRANCH) {
                throw new IllegalStateException("Branch " + b + " has " + table.length + " nodes, expected 35");
            }
            for (int i = 0; i < table.length; i++) {
                Spec s = table[i];
                int milestoneIndex = milestoneIndex(i);
                boolean milestone = milestoneIndex >= 0;
                if (milestone != (s.effect() == ABILITY)) {
                    throw new IllegalStateException("Milestone mismatch at branch " + b + " node " + i);
                }
                int g = b * TreeLayout.PER_BRANCH + i;
                list.add(new NodeDef(g, Branch.of(b), i, s.name(), s.effect(), s.value(), s.desc(), milestone,
                        milestone ? MILESTONE_COST : 1,
                        milestone ? MILESTONE_REQUIRED[milestoneIndex] : 0,
                        layout.parent[g], AscensionMod.id("node_" + g)));
            }
        }
        ALL = Collections.unmodifiableList(list);
    }

    private Nodes() {}

    private static int milestoneIndex(int local) {
        for (int k = 0; k < MILESTONE_LOCAL.length; k++) {
            if (MILESTONE_LOCAL[k] == local) return k;
        }
        return -1;
    }

    public static NodeDef get(int id) {
        return ALL.get(id);
    }
}
