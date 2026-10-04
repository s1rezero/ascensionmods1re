package com.example.ascension;

import java.util.function.Supplier;

import com.example.ascension.data.PlayerProgress;
import com.example.ascension.network.ClientHandlers;
import com.example.ascension.network.ProgressSyncPayload;
import com.example.ascension.network.ServerHandlers;
import com.example.ascension.network.UnlockNodePayload;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@Mod(AscensionMod.MODID)
public class AscensionMod {
    public static final String MODID = "ascension";

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    /** Per-player progress (XP + unlocked orbs). Saved with the player and kept through death. */
    public static final Supplier<AttachmentType<PlayerProgress>> PROGRESS = ATTACHMENTS.register("progress",
            () -> AttachmentType.builder(() -> new PlayerProgress())
                    .serialize(PlayerProgress.CODEC)
                    .copyOnDeath()
                    .build());

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    /** The Japanese intro line (used by the opening cinematic in the next stage). */
    public static final Supplier<SoundEvent> INTRO_VOICE = SOUNDS.register("intro_voice",
            () -> SoundEvent.createVariableRangeEvent(id("intro_voice")));

    public AscensionMod(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(ProgressSyncPayload.TYPE, ProgressSyncPayload.STREAM_CODEC, ClientHandlers::onSync);
        registrar.playToServer(UnlockNodePayload.TYPE, UnlockNodePayload.STREAM_CODEC, ServerHandlers::onUnlock);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
