package com.example.ascension.server;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The inventory "Sort" button: merge matching stacks, order by item, and refill the slots. */
public final class Sorter {
    private Sorter() {}

    public static void sort(ServerPlayer player, boolean external) {
        AbstractContainerMenu menu = player.containerMenu;
        List<Slot> slots = new ArrayList<>();
        if (external) {
            if (!(menu instanceof ChestMenu chest)) return;
            Container container = chest.getContainer();
            for (Slot s : menu.slots) {
                if (s.container == container) slots.add(s);
            }
        } else {
            Inventory inv = player.getInventory();
            for (Slot s : menu.slots) {
                if (s.container == inv && s.getContainerSlot() >= 9 && s.getContainerSlot() < 36) slots.add(s);
            }
        }
        if (slots.isEmpty()) return;

        List<ItemStack> merged = new ArrayList<>();
        for (Slot s : slots) {
            ItemStack stack = s.getItem().copy();
            if (stack.isEmpty()) continue;
            for (ItemStack m : merged) {
                if (stack.isEmpty()) break;
                if (m.getCount() < m.getMaxStackSize() && ItemStack.isSameItemSameComponents(m, stack)) {
                    int move = Math.min(m.getMaxStackSize() - m.getCount(), stack.getCount());
                    m.grow(move);
                    stack.shrink(move);
                }
            }
            if (!stack.isEmpty()) merged.add(stack);
        }
        merged.sort(Comparator
                .comparing((ItemStack st) -> BuiltInRegistries.ITEM.getKey(st.getItem()).toString())
                .thenComparing((ItemStack st) -> -st.getCount()));

        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).set(i < merged.size() ? merged.get(i) : ItemStack.EMPTY);
        }
        menu.broadcastChanges();
    }
}
