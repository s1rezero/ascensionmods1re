package com.example.ascension.client;

import org.lwjgl.glfw.GLFW;

import com.example.ascension.AscensionMod;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = AscensionMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Opens the upgrade tree. Default: period (.) - rebindable in Controls. */
    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.ascension.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, "key.categories.ascension");

    private ClientEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new AscensionScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientProgress.clear();
    }
}
