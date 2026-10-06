package com.example.ascension.client;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.Ability;
import com.example.ascension.network.SortPayload;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AscensionMod.MODID, value = Dist.CLIENT)
public final class ClientGuiEvents {
    private ClientGuiEvents() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(AscensionMod.id("abilities"), AbilityHud::render);
    }

    /** Adds the Sort buttons to the inventory and to chest-style containers once the Sort Button orb is unlocked. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        if (!ClientProgress.get().has(Ability.SORT_BUTTON)) return;

        int left = container.getGuiLeft();
        int top = container.getGuiTop();
        int w = container.getXSize();
        int h = container.getYSize();

        if (container.getMenu() instanceof ChestMenu) {
            // one button for the chest itself, one for your own inventory
            event.addListener(sortButton(left + w - 44, top + 3, true));
            event.addListener(sortButton(left + w - 44, top + h - 96, false));
        } else if (screen instanceof InventoryScreen) {
            event.addListener(sortButton(left + w - 44, top + 70, false));
        }
    }

    private static Button sortButton(int x, int y, boolean external) {
        return Button.builder(Component.literal("Sort"),
                        b -> PacketDistributor.sendToServer(new SortPayload(external)))
                .bounds(x, y, 36, 12)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(external ? "Sort this container" : "Sort your inventory")))
                .build();
    }
}
