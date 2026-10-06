package com.example.ascension.client;

import org.lwjgl.glfw.GLFW;

import com.example.ascension.AscensionMod;
import com.example.ascension.network.UseAbilityPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AscensionMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static final String CATEGORY = "key.categories.ascension";

    /** Opens the upgrade tree. Default: period (.) - rebindable in Controls. */
    public static final KeyMapping OPEN_KEY = new KeyMapping(
            "key.ascension.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_PERIOD, CATEGORY);

    /** The four ability keys (Z X C V by default). Which ability sits on which key is chosen in the tree screen. */
    public static final KeyMapping[] ABILITY_KEYS = {
            new KeyMapping("key.ascension.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.ascension.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.ascension.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY),
            new KeyMapping("key.ascension.ability_4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };

    private ClientEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_KEY);
        for (KeyMapping key : ABILITY_KEYS) event.register(key);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new AscensionScreen());
            }
        }
        for (int i = 0; i < ABILITY_KEYS.length; i++) {
            while (ABILITY_KEYS[i].consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    PacketDistributor.sendToServer(new UseAbilityPayload(i));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientProgress.clear();
    }
}
