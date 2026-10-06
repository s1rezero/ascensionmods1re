package com.example.ascension.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.ascension.data.Ability;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** What each ability actually does when its key is pressed. */
public final class AbilityEffects {
    private static final String FLIGHT_TAG = "ascension_starwalk";

    private AbilityEffects() {}

    /** @return true if the ability fired (and so should go on cooldown) */
    public static boolean activate(ServerPlayer p, Ability ability) {
        return switch (ability) {
            case WHIRLING_EDGE -> whirlingEdge(p);
            case TIME_FRACTURE -> timeFracture(p);
            case BLADE_MAELSTROM -> bladeMaelstrom(p);
            case HASTE_SURGE -> hasteSurge(p);
            case VEIN_BURST -> veinBurst(p);
            case SEISMIC_PULSE -> seismicPulse(p);
            case BLINK -> blink(p);
            case SKY_LEAP -> skyLeap(p);
            case STARWALK -> starwalk(p);
            case ITEM_MAGNET -> itemMagnet(p);
            case POCKET_DIMENSION -> pocketDimension(p);
            case SORT_BUTTON -> false;
        };
    }

    // ------------------------------------------------------------------ helpers

    private static boolean fail(ServerPlayer p, String message) {
        p.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    private static void sound(ServerLevel level, double x, double y, double z, SoundEvent event, float volume, float pitch) {
        level.playSound(null, x, y, z, event, SoundSource.PLAYERS, volume, pitch);
    }

    private static void particles(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                                  double spread, double speed) {
        level.sendParticles(type, x, y, z, count, spread, spread, spread, speed);
    }

    private static List<LivingEntity> enemiesAround(ServerLevel level, ServerPlayer p, double radius) {
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(radius, radius * 0.6, radius),
                e -> e != p && e.isAlive() && e instanceof Enemy);
        found.removeIf(e -> e.distanceToSqr(p) > radius * radius);
        return found;
    }

    // ------------------------------------------------------------------ COMBAT

    private static boolean whirlingEdge(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double radius = 4.5;
        float damage = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6 + 3.0);
        for (LivingEntity e : enemiesAround(level, p, radius)) {
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(p), damage);
            e.knockback(0.9, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        p.swing(InteractionHand.MAIN_HAND, true);
        for (int i = 0; i < 28; i++) {
            double a = i / 28.0 * Math.PI * 2;
            double r = 1.5 + (i % 3) * 1.2;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + Math.cos(a) * r, p.getY() + 1.0,
                    p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        particles(level, ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 30, 1.8, 0.4);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.1f);
        return true;
    }

    private static boolean timeFracture(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        for (LivingEntity e : enemiesAround(level, p, 12)) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 140, 4));
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 1));
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140, 0));
        }
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 2));
        for (int ring = 1; ring <= 4; ring++) {
            for (int i = 0; i < 20; i++) {
                double a = i / 20.0 * Math.PI * 2 + ring * 0.3;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX() + Math.cos(a) * ring * 2.5, p.getY() + 0.3,
                        p.getZ() + Math.sin(a) * ring * 2.5, 1, 0, 0.2, 0, 0.05);
            }
        }
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.6f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
        return true;
    }

    private static boolean bladeMaelstrom(ServerPlayer p) {
        AbilityManager.start(p, new AbilityManager.Tickable() {
            int ticks = 120;

            @Override
            public boolean tick(ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                double spin = (120 - ticks) * 0.5;
                for (int k = 0; k < 3; k++) {
                    double a = spin + k * 2.094;
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(a) * 3.0,
                            player.getY() + 1.0 + Math.sin(spin * 0.7 + k) * 0.7, player.getZ() + Math.sin(a) * 3.0,
                            1, 0, 0, 0, 0);
                }
                if (ticks % 3 == 0) {
                    List<LivingEntity> targets = enemiesAround(level, player, 10);
                    if (!targets.isEmpty()) {
                        LivingEntity e = targets.get(player.getRandom().nextInt(targets.size()));
                        float damage = (float) (player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9 + 1.5);
                        e.invulnerableTime = 0;
                        e.hurt(level.damageSources().playerAttack(player), damage);
                        double y = e.getY() + e.getBbHeight() * 0.5;
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), y, e.getZ(), 1, 0.2, 0.2, 0.2, 0);
                        level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), y, e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
                        sound(level, e.getX(), y, e.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f,
                                0.9f + player.getRandom().nextFloat() * 0.4f);
                    }
                }
                return --ticks > 0;
            }

            @Override
            public void end(ServerPlayer player) {}
        });
        ServerLevel level = p.serverLevel();
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.6f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 1.2f);
        particles(level, ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 1.2, 0.2);
        return true;
    }

    // ------------------------------------------------------------------ MINING

    private static boolean hasteSurge(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 2));
        particles(level, ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1, p.getZ(), 40, 0.8, 0.3);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.5f);
        return true;
    }

    private static boolean veinBurst(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        HitResult hit = p.pick(8.0, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return fail(p, "Look at an ore vein.");
        }
        BlockPos start = blockHit.getBlockPos();
        BlockState first = level.getBlockState(start);
        if (!first.is(Tags.Blocks.ORES)) {
            return fail(p, "Look at an ore vein.");
        }
        ItemStack tool = p.getMainHandItem();
        if (!tool.isCorrectToolForDrops(first)) {
            return fail(p, "You need the right tool for this ore.");
        }

        Block ore = first.getBlock();
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && vein.size() < 64) {
            BlockPos pos = queue.poll();
            vein.add(pos);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = pos.offset(dx, dy, dz);
                        if (!seen.add(n) || !level.isLoaded(n)) continue;
                        if (level.getBlockState(n).is(ore)) queue.add(n);
                    }
                }
            }
        }
        for (BlockPos pos : vein) {
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
            level.destroyBlock(pos, true, p);
        }
        tool.hurtAndBreak(Math.max(1, vein.size() / 4), p, EquipmentSlot.MAINHAND);
        sound(level, start.getX(), start.getY(), start.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2f, 0.8f);
        sound(level, start.getX(), start.getY(), start.getZ(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.8f);
        return true;
    }

    private static boolean seismicPulse(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos center = p.blockPosition();
        int radius = 4;
        int broken = 0;
        outer:
        for (int x = -radius; x <= radius; x++) {
            for (int y = 0; y <= radius; y++) {          // never the floor under your feet
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > radius * radius) continue;
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) continue;
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0 || hardness > 5.0f) continue;
                    if (broken >= 160) break outer;
                    level.destroyBlock(pos, true, p);
                    broken++;
                }
            }
        }
        for (LivingEntity e : enemiesAround(level, p, 6)) {
            e.hurt(level.damageSources().playerAttack(p), 8.0f);
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
            e.hurtMarked = true;
        }
        for (int ring = 1; ring <= 4; ring++) {
            for (int i = 0; i < 16; i++) {
                double a = i / 16.0 * Math.PI * 2;
                level.sendParticles(ParticleTypes.EXPLOSION, p.getX() + Math.cos(a) * ring, p.getY() + 0.2,
                        p.getZ() + Math.sin(a) * ring, 1, 0, 0, 0, 0);
            }
        }
        particles(level, ParticleTypes.POOF, p.getX(), p.getY() + 0.3, p.getZ(), 60, 2.5, 0.1);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 0.7f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, 0.9f, 0.5f);
        return true;
    }

    // ------------------------------------------------------------------ EXPLORATION

    private static boolean blink(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        double half = p.getBbHeight() * 0.5;
        Vec3 from = p.position().add(0, half, 0);
        Vec3 to = from.add(look.scale(9.0));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation().subtract(look.scale(0.7));
        Vec3 feet = target.subtract(0, half, 0);

        Vec3 safe = null;
        for (int i = 0; i <= 8; i++) {
            Vec3 candidate = feet.add(0, i * 0.25, 0);
            if (level.noCollision(p, p.getBoundingBox().move(candidate.subtract(p.position())))) {
                safe = candidate;
                break;
            }
        }
        if (safe == null || safe.distanceToSqr(p.position()) < 1.5) {
            return fail(p, "No room to blink there.");
        }
        particles(level, ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.4, 0.5);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.4f);
        p.teleportTo(safe.x, safe.y, safe.z);
        p.fallDistance = 0;
        particles(level, ParticleTypes.REVERSE_PORTAL, safe.x, safe.y + 1, safe.z, 40, 0.4, 0.3);
        particles(level, ParticleTypes.END_ROD, safe.x, safe.y + 1, safe.z, 12, 0.5, 0.1);
        sound(level, safe.x, safe.y, safe.z, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.8f);
        return true;
    }

    private static boolean skyLeap(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        p.setDeltaMovement(look.x * 0.6, 1.5, look.z * 0.6);
        p.hurtMarked = true;
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
        particles(level, ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 40, 0.5, 0.15);
        particles(level, ParticleTypes.FIREWORK, p.getX(), p.getY() + 0.2, p.getZ(), 25, 0.3, 0.2);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 1.4f);
        return true;
    }

    private static boolean starwalk(ServerPlayer p) {
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.getPersistentData().putBoolean(FLIGHT_TAG, true);
        AbilityManager.start(p, new AbilityManager.Tickable() {
            int ticks = 160;

            @Override
            public boolean tick(ServerPlayer player) {
                player.getAbilities().mayfly = true;
                if (ticks % 2 == 0) {
                    player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(),
                            2, 0.3, 0.05, 0.3, 0.01);
                }
                if (ticks == 50) {
                    player.displayClientMessage(Component.literal("Starwalk is fading...").withStyle(ChatFormatting.AQUA), true);
                }
                return --ticks > 0;
            }

            @Override
            public void end(ServerPlayer player) {
                revokeFlight(player, true);
            }
        });
        ServerLevel level = p.serverLevel();
        particles(level, ParticleTypes.END_ROD, p.getX(), p.getY() + 0.5, p.getZ(), 40, 0.8, 0.15);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_ACTIVATE, 1.0f, 1.9f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, 1.0f, 1.0f);
        return true;
    }

    /** Takes flight away again (unless the player is in creative/spectator). */
    public static void revokeFlight(ServerPlayer p, boolean softLanding) {
        if (!p.isCreative() && !p.isSpectator()) {
            p.getAbilities().mayfly = false;
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        p.getPersistentData().remove(FLIGHT_TAG);
        if (softLanding) {
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0));
        }
    }

    /** Safety net: if the game was closed mid-Starwalk, flight must not stay on forever. */
    public static void cleanupStaleFlight(ServerPlayer p) {
        if (p.getPersistentData().getBoolean(FLIGHT_TAG) && !AbilityManager.hasActive(p)) {
            revokeFlight(p, false);
        }
    }

    // ------------------------------------------------------------------ INVENTORY

    private static boolean itemMagnet(ServerPlayer p) {
        AbilityManager.start(p, new AbilityManager.Tickable() {
            int ticks = 80;

            @Override
            public boolean tick(ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                Vec3 target = player.position().add(0, 0.6, 0);
                var box = player.getBoundingBox().inflate(14);
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, ItemEntity::isAlive)) {
                    pull(item, target);
                    item.setNoPickUpDelay();
                }
                for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive)) {
                    pull(orb, target);
                }
                if (ticks % 4 == 0) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                            6, 1.5, 0.8, 1.5, 0.3);
                }
                return --ticks > 0;
            }

            private void pull(net.minecraft.world.entity.Entity e, Vec3 target) {
                Vec3 d = target.subtract(e.position());
                double len = d.length();
                if (len < 0.4) return;
                e.setDeltaMovement(d.normalize().scale(Math.min(0.9, 0.2 + len * 0.15)));
                e.hurtMarked = true;
            }

            @Override
            public void end(ServerPlayer player) {}
        });
        ServerLevel level = p.serverLevel();
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.2f);
        sound(level, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, 0.6f, 0.6f);
        return true;
    }

    private static boolean pocketDimension(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider(
                (id, inventory, player) -> ChestMenu.threeRows(id, inventory, player.getEnderChestInventory()),
                Component.literal("Pocket Dimension")));
        sound(p.serverLevel(), p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_CHEST_OPEN, 0.8f, 1.3f);
        return true;
    }
}
