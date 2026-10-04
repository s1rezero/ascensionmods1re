package com.example.ascension.data;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What an upgrade does. Effects with an attribute are live in this build; the others (attribute == null) are
 * stored and shown but get their behaviour in later stages.
 */
public enum Effect {
    // ---- live now (vanilla attributes)
    ATTACK_DAMAGE("Attack Damage", Fmt.PERCENT, () -> Attributes.ATTACK_DAMAGE, Operation.ADD_MULTIPLIED_BASE),
    ATTACK_SPEED("Attack Speed", Fmt.PERCENT, () -> Attributes.ATTACK_SPEED, Operation.ADD_MULTIPLIED_BASE),
    MOVE_SPEED("Movement Speed", Fmt.PERCENT, () -> Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE),
    MAX_HEALTH("Max Health", Fmt.FLAT, () -> Attributes.MAX_HEALTH, Operation.ADD_VALUE),
    ARMOR("Armor", Fmt.FLAT, () -> Attributes.ARMOR, Operation.ADD_VALUE),
    KNOCKBACK_RES("Knockback Resistance", Fmt.PERCENT, () -> Attributes.KNOCKBACK_RESISTANCE, Operation.ADD_VALUE),
    ENTITY_REACH("Attack Reach", Fmt.FLAT, () -> Attributes.ENTITY_INTERACTION_RANGE, Operation.ADD_VALUE),
    BLOCK_REACH("Block Reach", Fmt.FLAT, () -> Attributes.BLOCK_INTERACTION_RANGE, Operation.ADD_VALUE),
    SWEEP("Sweeping Damage", Fmt.PERCENT, () -> Attributes.SWEEPING_DAMAGE_RATIO, Operation.ADD_VALUE),
    MINING_SPEED("Mining Speed", Fmt.PERCENT, () -> Attributes.BLOCK_BREAK_SPEED, Operation.ADD_MULTIPLIED_BASE),
    MINING_EFFICIENCY("Mining Efficiency", Fmt.FLAT, () -> Attributes.MINING_EFFICIENCY, Operation.ADD_VALUE),
    UNDERWATER_MINING("Underwater Mining", Fmt.PERCENT, () -> Attributes.SUBMERGED_MINING_SPEED, Operation.ADD_VALUE),
    LUCK("Luck", Fmt.FLAT, () -> Attributes.LUCK, Operation.ADD_VALUE),
    GRAVITY("Gravity", Fmt.PERCENT, () -> Attributes.GRAVITY, Operation.ADD_MULTIPLIED_BASE),
    JUMP("Jump Height", Fmt.PERCENT, () -> Attributes.JUMP_STRENGTH, Operation.ADD_MULTIPLIED_BASE),
    STEP_HEIGHT("Step Height", Fmt.FLAT, () -> Attributes.STEP_HEIGHT, Operation.ADD_VALUE),
    SAFE_FALL("Safe Fall Distance", Fmt.FLAT, () -> Attributes.SAFE_FALL_DISTANCE, Operation.ADD_VALUE),
    FALL_DAMAGE("Fall Damage", Fmt.PERCENT, () -> Attributes.FALL_DAMAGE_MULTIPLIER, Operation.ADD_MULTIPLIED_BASE),
    SNEAK_SPEED("Sneak Speed", Fmt.PERCENT, () -> Attributes.SNEAKING_SPEED, Operation.ADD_VALUE),

    // ---- stored now, behaviour comes in later stages
    FORTUNE("Fortune", Fmt.FLAT, null, null),
    MENDING("Mending", Fmt.FLAT, null, null),
    CRIT_CHANCE("Crit Chance", Fmt.PERCENT, null, null),
    CRIT_DAMAGE("Crit Damage", Fmt.PERCENT, null, null),
    LIFESTEAL("Lifesteal", Fmt.PERCENT, null, null),
    COOLDOWN("Ability Cooldown", Fmt.PERCENT, null, null),
    STACK_SIZE("Max Stack Size", Fmt.PERCENT, null, null),
    PICKUP_RANGE("Pickup Range", Fmt.FLAT, null, null),
    ABILITY("Ability", Fmt.FLAT, null, null);

    public enum Fmt { PERCENT, FLAT }

    public final String label;
    private final Fmt fmt;
    private final Supplier<Holder<Attribute>> attribute;
    private final Operation operation;

    Effect(String label, Fmt fmt, Supplier<Holder<Attribute>> attribute, Operation operation) {
        this.label = label;
        this.fmt = fmt;
        this.attribute = attribute;
        this.operation = operation;
    }

    /** True if this effect already does something in the current build. */
    public boolean implemented() {
        return attribute != null;
    }

    public Holder<Attribute> attribute() {
        return attribute == null ? null : attribute.get();
    }

    public Operation operation() {
        return operation;
    }

    public String describe(double value) {
        String number = fmt == Fmt.PERCENT ? trim(value * 100) + "%" : trim(value);
        return (value > 0 ? "+" : "") + number + " " + label;
    }

    private static String trim(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            return Long.toString((long) Math.rint(d));
        }
        String s = String.format(Locale.ROOT, "%.2f", d);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }
}
