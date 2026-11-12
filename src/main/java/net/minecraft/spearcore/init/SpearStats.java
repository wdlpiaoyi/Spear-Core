package net.minecraft.spearcore.init;


import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SpearStats {

    public record Stats(
            int durability,
            float attackDuration,
            float damageMultiplier,
            float attackDamageBonus,
            int enchantmentValue,
            Rarity rarity,
            Ingredient repairIngredient,
            boolean fireResistant,
            String materialName,
            Supplier<SoundEvent> useSound,
            Supplier<SoundEvent> hitSound,
            Supplier<SoundEvent> attackSound,
            float swingTimes,
            float hitboxMargin,
            int contactCooldownTicks,
            int delayTicks,
            Optional<SpearCondition> dismountConditions,
            Optional<SpearCondition> knockbackConditions,
            Optional<SpearCondition> damageConditions,
            float forwardMovement,
            float minRange,
            float maxRange,
            float minCreativeRange,
            float maxCreativeRange,
            float hitboxMargin2,
            float mobFactor,
            boolean dealsKnockback,
            boolean dismounts
    ) {
        public float getAttackSpeedModifier() {
            return 1.0F / attackDuration - 4.0F;
        }

        // ========== 完全工厂方法 ==========
        public static Stats of(
                int durability, float attackDuration, float damageMultiplier,
                float attackDamageBonus, int enchantmentValue,
                Rarity rarity, Ingredient repairIngredient, boolean fireResistant,
                String materialName,
                Supplier<SoundEvent> useSound, Supplier<SoundEvent> hitSound, Supplier<SoundEvent> attackSound,
                float swingTimes, float hitboxMargin, int contactCooldownTicks,
                int delayTicks, Optional<SpearCondition> dismountConditions,
                Optional<SpearCondition> knockbackConditions, Optional<SpearCondition> damageConditions,
                float forwardMovement, float minRange, float maxRange,
                float minCreativeRange, float maxCreativeRange,
                float hitboxMargin2, float mobFactor,
                boolean dealsKnockback, boolean dismounts
        ) {
            return new Stats(durability, attackDuration, damageMultiplier, attackDamageBonus,
                    enchantmentValue, rarity, repairIngredient, fireResistant, materialName,
                    useSound, hitSound, attackSound,
                    swingTimes, hitboxMargin, contactCooldownTicks,
                    delayTicks, dismountConditions, knockbackConditions, damageConditions,
                    forwardMovement, minRange, maxRange,
                    minCreativeRange, maxCreativeRange,
                    hitboxMargin2, mobFactor,
                    dealsKnockback, dismounts);
        }

        /** 链式构造器：给 KubeJS / 外部注册用，避免 28 个位置参数错位。 */
        public static final class Builder {
            private int durability;
            private float attackDuration;
            private float damageMultiplier;
            private float attackDamageBonus;
            private int enchantmentValue;
            private Rarity rarity = Rarity.COMMON;
            private Ingredient repairIngredient = Ingredient.EMPTY;
            private boolean fireResistant;
            private String materialName = "custom";
            private Supplier<SoundEvent> useSound = SpearSounds.ITEM_SPEAR_USE;
            private Supplier<SoundEvent> hitSound = SpearSounds.ITEM_SPEAR_HIT;
            private Supplier<SoundEvent> attackSound = SpearSounds.ITEM_SPEAR_ATTACK;
            private float swingTimes;
            private float hitboxMargin = 0.25F;
            private int contactCooldownTicks = 10;
            private int delayTicks;
            private Optional<SpearCondition> dismountConditions = Optional.empty();
            private Optional<SpearCondition> knockbackConditions = Optional.empty();
            private Optional<SpearCondition> damageConditions = Optional.empty();
            private float forwardMovement;
            private float minRange;
            private float maxRange;
            private float minCreativeRange;
            private float maxCreativeRange;
            private float hitboxMargin2 = 0.25F;
            private float mobFactor = 0.5F;
            private boolean dealsKnockback;
            private boolean dismounts;

            public Builder durability(int v) { this.durability = v; return this; }
            public Builder attackDuration(float v) { this.attackDuration = v; return this; }
            public Builder damageMultiplier(float v) { this.damageMultiplier = v; return this; }
            public Builder attackDamageBonus(float v) { this.attackDamageBonus = v; return this; }
            public Builder enchantmentValue(int v) { this.enchantmentValue = v; return this; }
            public Builder rarity(Rarity v) { this.rarity = v; return this; }
            public Builder repairIngredient(Ingredient v) { this.repairIngredient = v; return this; }
            public Builder fireResistant(boolean v) { this.fireResistant = v; return this; }
            public Builder materialName(String v) { this.materialName = v; return this; }
            public Builder useSound(Supplier<SoundEvent> v) { this.useSound = v; return this; }
            public Builder hitSound(Supplier<SoundEvent> v) { this.hitSound = v; return this; }
            public Builder attackSound(Supplier<SoundEvent> v) { this.attackSound = v; return this; }
            public Builder swingTimes(float v) { this.swingTimes = v; return this; }
            public Builder hitboxMargin(float v) { this.hitboxMargin = v; return this; }
            public Builder contactCooldownTicks(int v) { this.contactCooldownTicks = v; return this; }
            public Builder delayTicks(int v) { this.delayTicks = v; return this; }
            public Builder dismountConditions(Optional<SpearCondition> v) { this.dismountConditions = v; return this; }
            public Builder knockbackConditions(Optional<SpearCondition> v) { this.knockbackConditions = v; return this; }
            public Builder damageConditions(Optional<SpearCondition> v) { this.damageConditions = v; return this; }
            public Builder forwardMovement(float v) { this.forwardMovement = v; return this; }
            public Builder minRange(float v) { this.minRange = v; return this; }
            public Builder maxRange(float v) { this.maxRange = v; return this; }
            public Builder minCreativeRange(float v) { this.minCreativeRange = v; return this; }
            public Builder maxCreativeRange(float v) { this.maxCreativeRange = v; return this; }
            public Builder hitboxMargin2(float v) { this.hitboxMargin2 = v; return this; }
            public Builder mobFactor(float v) { this.mobFactor = v; return this; }
            public Builder dealsKnockback(boolean v) { this.dealsKnockback = v; return this; }
            public Builder dismounts(boolean v) { this.dismounts = v; return this; }

            public Stats build() {
                return new Stats(durability, attackDuration, damageMultiplier, attackDamageBonus,
                        enchantmentValue, rarity, repairIngredient, fireResistant, materialName,
                        useSound, hitSound, attackSound, swingTimes, hitboxMargin, contactCooldownTicks,
                        delayTicks, dismountConditions, knockbackConditions, damageConditions,
                        forwardMovement, minRange, maxRange, minCreativeRange, maxCreativeRange,
                        hitboxMargin2, mobFactor, dealsKnockback, dismounts);
            }
        }

    }

    // ========== Jerotes 原版风格的简化工厂 ==========
    // 根据 attackDuration 自动计算所有阶段参数
    public static Stats spearOf(
            int durability, float attackDuration, float damageMultiplier,
            float attackDamageBonus, int enchantmentValue,
            Rarity rarity, Ingredient repairIngredient, boolean fireResistant,
            String materialName,
            Supplier<SoundEvent> useSound, Supplier<SoundEvent> hitSound, Supplier<SoundEvent> attackSound,
            float delaySec, float dismountSec, float knockbackSec, float damageSec
    ) {
        return Stats.of(
                durability, attackDuration, damageMultiplier, attackDamageBonus,
                enchantmentValue, rarity, repairIngredient, fireResistant,
                materialName, useSound, hitSound, attackSound,
                attackDuration,
                0.25F, 10,
                (int)(delaySec * 20.0F),
                dismountConditionFor(materialName, (int)(dismountSec * 20.0F)),
                SpearCondition.ofAttackerSpeed((int)(knockbackSec * 20.0F), 5.1F),
                SpearCondition.ofRelativeSpeed((int)(damageSec * 20.0F), 4.6F),
                0.38F,
                2.0F, 4.5F, 2.0F, 6.5F,
                // 戳刺判定箱：原版 PIERCING_WEAPON 的 hitbox_margin 是 0.25F（蓄力的 KINETIC_WEAPON 是 0.125F）。
                0.25F, 0.5F,
                true, true
        );
    }

    // ========== 击落骑手的速度阈值（写死为 Java 版数据，不随任何配置项变化） ==========

    /**
     * 原版 Java 版击落骑手所需的攻击者速度阈值（格/秒，按材质）。
     *
     * <p>顺序与官方数据一致：木 14、金 13、石 13、铜 12、铁 11、钻石 10、下界合金 9。
     * 怪物使用这些条件时会再乘以 {@code needSpeed}（玩家 1.0 / 怪物 0.2）。</p>
     */
    private static final Map<String, Float> JE_DISMOUNT_SPEED = Map.of(
            "wood", 14.0F,
            "gold", 13.0F,
            "stone", 13.0F,
            "copper", 12.0F,
            "iron", 11.0F,
            "diamond", 10.0F,
            "netherite", 9.0F
    );

    /** 某材质的击落速度阈值；未知材质按铁矛的 11.0 处理。 */
    public static float dismountSpeedFor(String materialName) {
        return JE_DISMOUNT_SPEED.getOrDefault(materialName, 11.0F);
    }

    /**
     * 按材质造一个「可击落骑手」的蓄力条件。
     *
     * @param materialName 材质名（wood / stone / copper / iron / gold / diamond / netherite）
     * @param windowTicks  可击落窗口的长度（tick）
     *
     * <p>KubeJS 建造器也必须走这里：自己写死一个阈值会跟内置预设差一个数量级，
     * 历史上就是 0.3 格/秒 —— 等于任何移动速度都能把人从坐骑上打下来。</p>
     */
    public static Optional<SpearCondition> dismountConditionFor(String materialName, int windowTicks) {
        return SpearCondition.ofAttackerSpeed(windowTicks, dismountSpeedFor(materialName));
    }

    // 无防火
    public static Stats spearOf(
            int durability, float attackDuration, float damageMultiplier,
            float attackDamageBonus, int enchantmentValue,
            Rarity rarity, Ingredient repairIngredient, String materialName,
            Supplier<SoundEvent> useSound, Supplier<SoundEvent> hitSound, Supplier<SoundEvent> attackSound,
            float delaySec, float dismountSec, float knockbackSec, float damageSec
    ) {
        return spearOf(durability, attackDuration, damageMultiplier, attackDamageBonus,
                enchantmentValue, rarity, repairIngredient, false, materialName,
                useSound, hitSound, attackSound,
                delaySec, dismountSec, knockbackSec, damageSec);
    }

    // ========== 音效 ==========

    private static final Supplier<SoundEvent> WOOD_USE = SpearSounds.ITEM_SPEAR_WOOD_USE;
    private static final Supplier<SoundEvent> WOOD_HIT = SpearSounds.ITEM_SPEAR_WOOD_HIT;
    private static final Supplier<SoundEvent> WOOD_ATTACK = SpearSounds.ITEM_SPEAR_WOOD_ATTACK;

    private static final Supplier<SoundEvent> SPEAR_USE = SpearSounds.ITEM_SPEAR_USE;
    private static final Supplier<SoundEvent> SPEAR_HIT = SpearSounds.ITEM_SPEAR_HIT;
    private static final Supplier<SoundEvent> SPEAR_ATTACK = SpearSounds.ITEM_SPEAR_ATTACK;

    // ========== 预设常量 ==========


    public static final Stats WOOD = spearOf(
            59, 0.65f, 0.7f, 0.0f, 15,
            Rarity.COMMON, Ingredient.EMPTY, "wood",
            WOOD_USE, WOOD_HIT, WOOD_ATTACK,
            0.75f, 5.0f, 10.0f, 15.0f
    );

    public static final Stats STONE = spearOf(
            131, 0.75f, 0.82f, 1.0f, 5,
            Rarity.COMMON, Ingredient.of(Items.COBBLESTONE), "stone",
            SPEAR_USE, SPEAR_HIT, SPEAR_ATTACK,
            0.7f, 4.5f, 9.0f, 13.75f
    );

    public static final Stats COPPER = spearOf(
            195, 0.85f, 0.82f, 1.0f, 13,
            Rarity.COMMON, Ingredient.of(Items.COPPER_INGOT), "copper",
            SPEAR_USE, SPEAR_HIT, SPEAR_ATTACK,
            0.65f, 4.0f, 8.25f, 12.5f
    );

    public static final Stats IRON = spearOf(
            250, 0.95f, 0.95f, 2.0f, 14,
            Rarity.COMMON, Ingredient.of(Items.IRON_INGOT), "iron",
            SPEAR_USE, SPEAR_HIT, SPEAR_ATTACK,
            0.6f, 2.5f, 6.75f, 11.25f
    );

    public static final Stats GOLD = spearOf(
            59, 0.95f, 0.7f, 0.0f, 22,
            Rarity.COMMON, Ingredient.of(Items.GOLD_INGOT), "gold",
            SPEAR_USE, SPEAR_HIT, SPEAR_ATTACK,
            0.7f, 3.5f, 8.5f, 13.75f
    );

    public static final Stats DIAMOND = spearOf(
            1561, 1.05f, 1.075f, 3.0f, 10,
            Rarity.COMMON, Ingredient.of(Items.DIAMOND), "diamond",
            SPEAR_USE, SPEAR_HIT, SPEAR_ATTACK,
            0.5f, 3.0f, 6.5f, 10.0f
    );

    public static final Stats NETHERITE = spearOf(
            2031, 1.15f, 1.2f, 4.0f, 15,
            Rarity.COMMON, Ingredient.of(Items.NETHERITE_INGOT), true, "netherite",
            SPEAR_USE, SPEAR_HIT, SPEAR_ATTACK,
            0.4f, 2.5f, 5.5f, 8.75f
    );

    public static final Stats DEFAULT = IRON;

    // ========== Map ==========
    private static final Map<Item, Stats> STATS_MAP = new HashMap<>();

    public static void register(Item spear, Stats stats) {
        STATS_MAP.put(spear, stats);
    }

    public static Stats get(Item spear) {
        return STATS_MAP.getOrDefault(spear, DEFAULT);
    }

    // ========== 便捷方法 ==========
    public static int durability(Item spear) { return get(spear).durability(); }
    public static float attackDuration(Item spear) { return get(spear).attackDuration(); }
    public static float damageMultiplier(Item spear) { return get(spear).damageMultiplier(); }
    public static float attackDamageBonus(Item spear) { return get(spear).attackDamageBonus(); }
    public static float getAttackSpeedModifier(Item spear) { return get(spear).getAttackSpeedModifier(); }
    public static int enchantmentValue(Item spear) { return get(spear).enchantmentValue(); }
    public static Rarity rarity(Item spear) { return get(spear).rarity(); }
    public static Ingredient repairIngredient(Item spear) { return get(spear).repairIngredient(); }
    public static boolean fireResistant(Item spear) { return get(spear).fireResistant(); }
    public static String materialName(Item spear) { return get(spear).materialName(); }
    public static SoundEvent useSound(Item spear) { return get(spear).useSound().get(); }
    public static SoundEvent hitSound(Item spear) { return get(spear).hitSound().get(); }
    public static SoundEvent attackSound(Item spear) { return get(spear).attackSound().get(); }
    public static float swingTimes(Item spear) { return get(spear).swingTimes(); }
    public static float hitboxMargin(Item spear) { return get(spear).hitboxMargin(); }
    public static int contactCooldownTicks(Item spear) { return get(spear).contactCooldownTicks(); }
    public static int delayTicks(Item spear) { return get(spear).delayTicks(); }
    public static Optional<SpearCondition> dismountConditions(Item spear) { return get(spear).dismountConditions(); }
    public static Optional<SpearCondition> knockbackConditions(Item spear) { return get(spear).knockbackConditions(); }
    public static Optional<SpearCondition> damageConditions(Item spear) { return get(spear).damageConditions(); }
    public static float forwardMovement(Item spear) { return get(spear).forwardMovement(); }
    public static float minRange(Item spear) { return get(spear).minRange(); }
    public static float maxRange(Item spear) { return get(spear).maxRange(); }
    public static float minCreativeRange(Item spear) { return get(spear).minCreativeRange(); }
    public static float maxCreativeRange(Item spear) { return get(spear).maxCreativeRange(); }
    public static float hitboxMargin2(Item spear) { return get(spear).hitboxMargin2(); }
    public static float mobFactor(Item spear) { return get(spear).mobFactor(); }
    public static boolean dealsKnockback(Item spear) { return get(spear).dealsKnockback(); }
    public static boolean dismounts(Item spear) { return get(spear).dismounts(); }
}
