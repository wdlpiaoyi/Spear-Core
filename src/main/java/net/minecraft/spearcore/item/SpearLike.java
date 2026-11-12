package net.minecraft.spearcore.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * 「像长矛一样工作」的物品协议。
 *
 * <p>本模组自己的长矛由 {@link SpearItem} 实现；Tetra 的模块化长矛（archetype modular_spear）由
 * {@code TetraSpearProfile} 在运行时按 Tetra 属性现算一份实现。战斗逻辑统一放在
 * {@link SpearCombat}，两边共用同一套戳刺 / 蓄力流程。</p>
 */
public interface SpearLike {

    float getAttackDuration();

    float getDamageMultiplier();

    SoundEvent getUseSound();

    SoundEvent getHitSound();

    SoundEvent getAttackSound();

    int getDelayTicks();

    int getDismountEndTick();

    int getKnockbackEndTick();

    int getDamageEndTick();

    Optional<SpearCondition> getDismountConditions();

    Optional<SpearCondition> getKnockbackConditions();

    Optional<SpearCondition> getDamageConditions();

    float getForwardMovement();

    float getMinRange();

    float getMaxRange();

    float getHitboxMargin();

    float getHitboxMargin2();

    int getContactCooldownTicks();

    float getSwingTimes();

    float getMinCreativeRange();

    float getMaxCreativeRange();

    float getMobFactor();

    boolean dealsKnockback();

    boolean dismounts();

    /** 使用冷却模型：本模组长矛跟随 {@code jabCooldown} 配置，Tetra 固定按 Java 版语义处理。 */
    default boolean usesJabCooldown() {
        return SpearRules.usesUseCooldown();
    }

    /** 戳刺后禁止立刻蓄力的后摇；Tetra 不挂这个后摇。 */
    default int jabToChargeDelayTicks(int cooldownTicks) {
        return SpearRules.jabToChargeDelayTicks(cooldownTicks);
    }

    /** 按攻击速度折算的使用冷却 tick 数。 */
    default int getUseCooldownTicks() {
        return Math.round(getAttackDuration() * 20.0F);
    }

    /**
     * 蓄力命中同一目标后的重复结算间隔。
     *
     * <p>默认走 {@link SpearRules} 的 {@code chargeRepeatHit}，但不会按攻速缩放；本模组内建矛
     * 由 {@code BaseSpearItem} 打开 {@link #speedScalesChargeRepeat()}，Tetra 模块化长矛则直接
     * override 成本身攻击时长。第三方 {@code SpearItem}（例如 Avaritia Spear）保持默认 false，
     * 不受本模组新选项影响。</p>
     */
    default int chargeRepeatCooldownTicks(LivingEntity user) {
        return SpearRules.chargeRepeatCooldownTicks(user, getContactCooldownTicks(),
                getAttackDuration(), speedScalesChargeRepeat());
    }

    /** 无持有者上下文时的回退（测试/工具查询用）。 */
    default int chargeRepeatCooldownTicks() {
        return chargeRepeatCooldownTicks(null);
    }

    /** 是否走本模组的 chargeRepeatHit；第三方矛默认 false，走独立配置。 */
    default boolean speedScalesChargeRepeat() {
        return false;
    }

    /** 表现风格：本模组贴图矛 / Tetra 3D 模块化工具。 */
    default SpearStyle style() {
        return SpearStyle.VANILLA_SPEAR;
    }

    /**
     * 左键是否走「挥砍 + 横扫」而不是戳刺。
     *
     * <p>只有特定矛头（槊头、三尖两刃刀）为 true；贴图矛与普通矛头仍按 Java 版长矛的戳刺处理。</p>
     */
    default boolean slashing() {
        return false;
    }

    /**
     * 是否要求左键必须满蓄力（槊头）。三尖两刃刀不要求，保持高上限。
     */
    default boolean requiresFullCharge() {
        return false;
    }

    /** 空中是否允许触发横扫；目前只有槊头、三尖两刃刀为 true。 */
    default boolean sweepsWhileAirborne() {
        return false;
    }

    /** 是否为三尖两刃刀（伤害拆分与最大生命削减只对它生效）。 */
    default boolean threePointBlade() {
        return false;
    }

    default float effectiveMinRange(Entity entity) {
        return SpearCombat.effectiveMinRange(this, entity);
    }

    default float effectiveMaxRange(Entity entity) {
        return SpearCombat.effectiveMaxRange(this, entity);
    }
}
