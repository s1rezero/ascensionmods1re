package com.example.ascension.data;

import net.minecraft.resources.ResourceLocation;

/**
 * One orb in the tree.
 *
 * @param requiredSpent for milestones: points that must already be spent in this branch
 * @param parent        global id of the orb that must be unlocked first, or -1 if it hangs off the core
 */
public record NodeDef(int id, Branch branch, int local, String name, Effect effect, double value,
                      String abilityDesc, boolean milestone, int cost, int requiredSpent, int parent,
                      ResourceLocation modifierId) {

    public String description() {
        return milestone ? abilityDesc : effect.describe(value);
    }

    /** Does unlocking this actually change anything in the current build? */
    public boolean live() {
        return effect.implemented();
    }
}
