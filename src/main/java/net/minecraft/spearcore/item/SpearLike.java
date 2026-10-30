package net.minecraft.spearcore.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * 「像长矛一样工作」的物品协议。
 *
 * <p>本模组自己的长矛由 {@link SpearItem} 实现；Tetra 单头工具（长手柄 + 矛头）由
 * {@code TetraSpearProfile} 在运行时按材料数值现算一份实现。战斗逻辑统一放在
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

    /** 表现风格：本模组贴图矛 / Tetra 3D 模块化工具。 */
    default SpearStyle style() {
        return SpearStyle.VANILLA_SPEAR;
    }

    default float effectiveMinRange(Entity entity) {
        return SpearCombat.effectiveMinRange(this, entity);
    }

    default float effectiveMaxRange(Entity entity) {
        return SpearCombat.effectiveMaxRange(this, entity);
    }
}
