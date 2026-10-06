package com.example.ascension.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Bigger stacks for players who unlocked the Inventory orbs.
 *
 * Minecraft stores a stack's size limit on the stack itself (the max_stack_size component), and caps it at 99.
 * So every stackable stack in the player's inventory gets the same raised limit, and stacks that picked up
 * different limits are merged again. Without the orbs (or after a respec) limits go back to normal.
 */
public final class StackSizes {
    public static final int HARD_CAP = 99;

    private StackSizes() {}

    public static void apply(ServerPlayer player, double bonus) {
        Inventory inv = player.getInventory();
        boolean changed = false;
        for (int i = 0; i < inv.items.size(); i++) {
            changed |= normalize(player, inv.items.get(i), bonus);
        }
        for (int i = 0; i < inv.offhand.size(); i++) {
            changed |= normalize(player, inv.offhand.get(i), bonus);
        }
        if (bonus > 0 || changed) {
            merge(inv);
        }
    }

    private static boolean normalize(ServerPlayer player, ItemStack stack, double bonus) {
        if (stack.isEmpty()) return false;
        int def = stack.getItem().getDefaultMaxStackSize();
        if (def <= 1) return false;
        int target = Math.max(def, Math.min(HARD_CAP, (int) (def * (1.0 + bonus))));
        if (stack.getMaxStackSize() == target) return false;

        stack.set(DataComponents.MAX_STACK_SIZE, target);   // equal to the default => component is removed again
        if (stack.getCount() > target) {
            ItemStack extra = stack.copy();
            extra.setCount(stack.getCount() - target);
            stack.setCount(target);
            player.getInventory().placeItemBackInInventory(extra);
        }
        return true;
    }

    private static void merge(Inventory inv) {
        int n = inv.items.size();
        for (int i = 0; i < n; i++) {
            ItemStack a = inv.items.get(i);
            if (a.isEmpty() || a.getMaxStackSize() <= 1 || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i + 1; j < n; j++) {
                ItemStack b = inv.items.get(j);
                if (b.isEmpty() || !ItemStack.isSameItemSameComponents(a, b)) continue;
                int move = Math.min(a.getMaxStackSize() - a.getCount(), b.getCount());
                if (move <= 0) break;
                a.grow(move);
                b.shrink(move);
                if (b.isEmpty()) inv.items.set(j, ItemStack.EMPTY);
                if (a.getCount() >= a.getMaxStackSize()) break;
            }
        }
    }
}
