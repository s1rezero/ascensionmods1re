package com.example.ascension.server;

import java.util.ArrayList;
import java.util.List;

import com.example.ascension.AscensionMod;
import com.example.ascension.data.Effect;
import com.example.ascension.data.PlayerProgress;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Orb effects that are not plain attribute modifiers. */
@EventBusSubscriber(modid = AscensionMod.MODID)
public final class PassiveEvents {
    private PassiveEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbilityManager.tick(player);

        int t = player.tickCount;
        if (t % 4 == 0) {
            PlayerProgress progress = ProgressService.get(player);
            pickupRange(player, progress.sum(Effect.PICKUP_RANGE));
            if (t % 12 == 0) {
                StackSizes.apply(player, progress.sum(Effect.STACK_SIZE));
            }
        }
    }

    /** Pickup Range orbs: loose items and XP within range drift toward you. */
    private static void pickupRange(ServerPlayer player, double range) {
        if (range <= 0) return;
        ServerLevel level = player.serverLevel();
        Vec3 target = player.position().add(0, 0.6, 0);
        var box = player.getBoundingBox().inflate(range);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, i -> i.isAlive() && !i.hasPickUpDelay())) {
            nudge(item, target, range);
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive)) {
            nudge(orb, target, range);
        }
    }

    private static void nudge(net.minecraft.world.entity.Entity e, Vec3 target, double range) {
        Vec3 d = target.subtract(e.position());
        double len = d.length();
        if (len < 1.0 || len > range) return;
        e.setDeltaMovement(e.getDeltaMovement().scale(0.4).add(d.normalize().scale(0.4)));
        e.hurtMarked = true;
    }

    /** Fortune orbs: ores occasionally drop double. 1.0 Fortune here would equal the chance of Fortune I, split in ten. */
    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) return;
        if (!event.getState().is(Tags.Blocks.ORES)) return;
        double fortune = ProgressService.get(player).sum(Effect.FORTUNE);
        if (fortune <= 0 || player.getRandom().nextDouble() >= fortune / 3.0) return;

        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (stack.getItem() == event.getState().getBlock().asItem()) continue; // silk touch: the block itself
            extra.add(new ItemEntity(event.getLevel(), drop.getX(), drop.getY(), drop.getZ(), stack.copy()));
        }
        if (extra.isEmpty()) return;
        event.getDrops().addAll(extra);
        event.getLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, event.getPos().getX() + 0.5,
                event.getPos().getY() + 0.5, event.getPos().getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
    }

    /** Mending orbs: breaking blocks sometimes repairs your tool a little. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        double mending = ProgressService.get(player).sum(Effect.MENDING);
        if (mending <= 0) return;
        ItemStack tool = player.getMainHandItem();
        if (tool.isDamaged() && player.getRandom().nextDouble() < mending) {
            tool.setDamageValue(tool.getDamageValue() - 1);
        }
    }

    /** Crit orbs: melee hits sometimes land harder. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getDirectEntity() != player) return;
        PlayerProgress progress = ProgressService.get(player);
        double chance = progress.sum(Effect.CRIT_CHANCE);
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) return;

        event.setAmount(event.getAmount() * (float) (1.5 + progress.sum(Effect.CRIT_DAMAGE)));
        var target = event.getEntity();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                12, 0.3, 0.3, 0.3, 0.3);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    /** Lifesteal orbs: a slice of the damage you deal comes back as health. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        double lifesteal = ProgressService.get(player).sum(Effect.LIFESTEAL);
        if (lifesteal > 0 && event.getNewDamage() > 0) {
            player.heal((float) (event.getNewDamage() * lifesteal));
        }
    }
}
