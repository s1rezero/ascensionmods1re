package com.example.ascension.data;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Everything the mod stores per player: total mod XP, unlocked orbs, ability key slots and the intro flag. */
public final class PlayerProgress {
    public enum Check { OK, ALREADY, NEED_PARENT, NEED_BRANCH, NEED_POINTS }

    public static final int SLOTS = 4;

    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(p -> p.totalXp),
            Codec.LONG.listOf().optionalFieldOf("unlocked", List.of()).forGetter(PlayerProgress::wordList),
            Codec.INT.listOf().optionalFieldOf("slots", List.of(-1, -1, -1, -1)).forGetter(PlayerProgress::slotList),
            Codec.BOOL.optionalFieldOf("intro_seen", false).forGetter(p -> p.introSeen)
    ).apply(i, PlayerProgress::fromData));

    public long totalXp;
    public BitSet unlocked = new BitSet(TreeLayout.TOTAL);
    /** Ability index (see {@link Ability}) assigned to each of the four keys, or -1. */
    public int[] slots = {-1, -1, -1, -1};
    public boolean introSeen;

    public PlayerProgress() {}

    private static PlayerProgress fromData(long xp, List<Long> words, List<Integer> slotList, boolean introSeen) {
        PlayerProgress p = new PlayerProgress();
        p.totalXp = Math.max(0, xp);
        long[] arr = new long[words.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = words.get(i);
        p.unlocked = BitSet.valueOf(arr);
        p.unlocked.clear(TreeLayout.TOTAL, Math.max(TreeLayout.TOTAL, p.unlocked.length()));
        for (int i = 0; i < SLOTS && i < slotList.size(); i++) p.slots[i] = slotList.get(i);
        p.introSeen = introSeen;
        p.validateSlots();
        return p;
    }

    private List<Long> wordList() {
        List<Long> out = new ArrayList<>();
        for (long w : unlocked.toLongArray()) out.add(w);
        return out;
    }

    private List<Integer> slotList() {
        List<Integer> out = new ArrayList<>();
        for (int s : slots) out.add(s);
        return out;
    }

    /** Drops slot assignments whose ability orb is not unlocked (or that are duplicated / invalid). */
    public void validateSlots() {
        boolean[] used = new boolean[Ability.COUNT];
        for (int i = 0; i < SLOTS; i++) {
            Ability a = Ability.of(slots[i]);
            if (a == null || a.passive || !isUnlocked(a.nodeId()) || used[a.ordinal()]) {
                slots[i] = -1;
            } else {
                used[a.ordinal()] = true;
            }
        }
    }

    public int slotOf(Ability a) {
        for (int i = 0; i < SLOTS; i++) if (slots[i] == a.ordinal()) return i;
        return -1;
    }

    public boolean has(Ability a) {
        return isUnlocked(a.nodeId());
    }

    /** Sum of the values of every unlocked orb with this effect (e.g. total Fortune from the Mining branch). */
    public double sum(Effect effect) {
        double total = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0 && i < Nodes.COUNT; i = unlocked.nextSetBit(i + 1)) {
            NodeDef n = Nodes.get(i);
            if (n.effect() == effect) total += n.value();
        }
        return total;
    }

    public int level() {
        return Progression.levelFor(totalXp);
    }

    public int earnedPoints() {
        return Progression.pointsForLevel(level());
    }

    public int spentPoints() {
        int s = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0 && i < Nodes.COUNT; i = unlocked.nextSetBit(i + 1)) {
            s += Nodes.get(i).cost();
        }
        return s;
    }

    public int availablePoints() {
        return Math.max(0, earnedPoints() - spentPoints());
    }

    public int spentInBranch(Branch branch) {
        int s = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0 && i < Nodes.COUNT; i = unlocked.nextSetBit(i + 1)) {
            NodeDef n = Nodes.get(i);
            if (n.branch() == branch) s += n.cost();
        }
        return s;
    }

    public int unlockedInBranch(Branch branch) {
        int c = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0 && i < Nodes.COUNT; i = unlocked.nextSetBit(i + 1)) {
            if (Nodes.get(i).branch() == branch) c++;
        }
        return c;
    }

    public boolean isUnlocked(int id) {
        return id >= 0 && unlocked.get(id);
    }

    public Check check(NodeDef n) {
        if (isUnlocked(n.id())) return Check.ALREADY;
        if (n.parent() >= 0 && !isUnlocked(n.parent())) return Check.NEED_PARENT;
        if (n.milestone() && spentInBranch(n.branch()) < n.requiredSpent()) return Check.NEED_BRANCH;
        if (availablePoints() < n.cost()) return Check.NEED_POINTS;
        return Check.OK;
    }
}
