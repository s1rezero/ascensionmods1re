package com.example.ascension.data;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Everything the mod stores per player: total mod XP and which orbs are unlocked. */
public final class PlayerProgress {
    public enum Check { OK, ALREADY, NEED_PARENT, NEED_BRANCH, NEED_POINTS }

    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(p -> p.totalXp),
            Codec.LONG.listOf().optionalFieldOf("unlocked", List.of()).forGetter(PlayerProgress::wordList)
    ).apply(i, PlayerProgress::fromData));

    public long totalXp;
    public BitSet unlocked = new BitSet(TreeLayout.TOTAL);

    public PlayerProgress() {}

    private static PlayerProgress fromData(long xp, List<Long> words) {
        PlayerProgress p = new PlayerProgress();
        p.totalXp = Math.max(0, xp);
        long[] arr = new long[words.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = words.get(i);
        p.unlocked = BitSet.valueOf(arr);
        p.unlocked.clear(TreeLayout.TOTAL, Math.max(TreeLayout.TOTAL, p.unlocked.length()));
        return p;
    }

    private List<Long> wordList() {
        List<Long> out = new ArrayList<>();
        for (long w : unlocked.toLongArray()) out.add(w);
        return out;
    }

    public int level() {
        return Progression.levelFor(totalXp);
    }

    public int earnedPoints() {
        return Progression.pointsForLevel(level());
    }

    public int spentPoints() {
        int s = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0; i = unlocked.nextSetBit(i + 1)) {
            s += Nodes.get(i).cost();
        }
        return s;
    }

    public int availablePoints() {
        return Math.max(0, earnedPoints() - spentPoints());
    }

    public int spentInBranch(Branch branch) {
        int s = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0; i = unlocked.nextSetBit(i + 1)) {
            NodeDef n = Nodes.get(i);
            if (n.branch() == branch) s += n.cost();
        }
        return s;
    }

    public int unlockedInBranch(Branch branch) {
        int c = 0;
        for (int i = unlocked.nextSetBit(0); i >= 0; i = unlocked.nextSetBit(i + 1)) {
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
