package net.minecraft.spearcore.compat.tetra;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.spearcore.config.SpearConfig;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearSounds;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * 由 Tetra 材料数值拟合出来的「长矛行为」。
 *
 * <p>Tetra 给每种材料定义了 攻击力 / 攻击速度 等数值（原版材料、模组材料、以及附属新增的材料都走同一套），
 * 但 Tetra 里没有「木矛 / 金矛」这种现成的长矛预设。因此这里不照抄本模组各材质的固定数值（更不复刻金矛那类
 * 反常数值），而是用一条单调的拟合公式从 Tetra 算出来的属性推导：</p>
 *
 * <ul>
 *   <li>攻击速度 → 蓄力全长 {@code attackDuration}（越快越短，夹在 0.60~1.30 秒）。</li>
 *   <li>攻击力 → 蓄力速度倍率 {@code damageMultiplier}（越高越疼；超过 4 点后按
 *       {@code soft = 4 + (d - 4) / (1 + (d - 4) / 8)} 放缓，避免高数值材料滚雪球）。</li>
 *   <li>蓄力阶段（前摇 / 击落 / 击退 / 伤害）以本模组铁矛的秒数为基准，按 duration 等比缩放。</li>
 *   <li>击落骑手的速度阈值同样随 soft 值从 14 往 9 收，最低不低于下界合金的 9。</li>
 *   <li>Tetra 自己贡献的额外攻击距离（长手柄 / 插槽等）只按配置 {@code tetraExtraReachScale}
 *       （默认 0.3）计入本模组长矛射程，其他来源的 reach 不受影响。</li>
 * </ul>
 *
 * <p>这样同一个公式对「原版材料 + 模组材料 + 附属新加材料」都成立，也不会因为某个材料数值特别高而失控。
 * 公式只依赖 Tetra 已经算好的属性，不读本模组的材质表，因此无需为 Tetra 材料单独维护配置。</p>
 *
 * <p>本类刻意不 import 任何 Tetra 类型：只有 {@link TetraSpearProfile} 确认 Tetra 已加载后才会构造它。</p>
 */
public final class TetraSpearStats implements SpearLike {

    /** 蓄力全长的上下限（秒）。 */
    private static final float MIN_DURATION = 0.60F;
    private static final float MAX_DURATION = 1.30F;

    /** 蓄力速度倍率的上下限。 */
    private static final float MIN_MULTIPLIER = 0.60F;
    private static final float MAX_MULTIPLIER = 1.45F;

    /** 攻击力超过该值后开始放缓（拟合公式的拐点）。 */
    private static final float SOFT_CAP = 4.0F;

    /** 以本模组铁矛为基准的阶段秒数：前摇 / 击落窗口 / 击退窗口 / 伤害窗口。 */
    private static final float BASE_DELAY_SEC = 0.60F;
    private static final float BASE_DISMOUNT_SEC = 2.50F;
    private static final float BASE_KNOCKBACK_SEC = 6.75F;
    private static final float BASE_DAMAGE_SEC = 11.25F;
    private static final float BASE_DURATION = 0.95F;

    /** 击落速度阈值的拟合范围。 */
    private static final float DISMOUNT_SPEED_MAX = 14.0F;
    private static final float DISMOUNT_SPEED_MIN = 9.0F;

    /** 配置未加载时的兜底值（与 {@link SpearConfig#TETRA_EXTRA_REACH_SCALE} 的默认值一致）。 */
    private static final double DEFAULT_EXTRA_REACH_SCALE = 0.3D;

    private final float attackDuration;
    private final float damageMultiplier;
    private final int delayTicks;
    private final int dismountEndTick;
    private final int knockbackEndTick;
    private final int damageEndTick;
    private final Optional<SpearCondition> dismountConditions;
    private final Optional<SpearCondition> knockbackConditions;
    private final Optional<SpearCondition> damageConditions;

    /** Tetra 自身贡献的额外攻击距离（forge:entity_reach 的 ADDITION 之和）。 */
    private final double tetraReachBonus;

    private TetraSpearStats(float attackDuration, float damageMultiplier,
                            int delayTicks,
                            Optional<SpearCondition> dismountConditions,
                            Optional<SpearCondition> knockbackConditions,
                            Optional<SpearCondition> damageConditions,
                            double tetraReachBonus) {
        this.attackDuration = attackDuration;
        this.damageMultiplier = damageMultiplier;
        this.delayTicks = delayTicks;
        this.dismountConditions = dismountConditions;
        this.knockbackConditions = knockbackConditions;
        this.damageConditions = damageConditions;
        this.tetraReachBonus = tetraReachBonus;
        this.dismountEndTick = delayTicks + dismountConditions.map(SpearCondition::maxDurationTicks).orElse(0);
        this.knockbackEndTick = delayTicks + knockbackConditions.map(SpearCondition::maxDurationTicks).orElse(0);
        this.damageEndTick = delayTicks + damageConditions.map(SpearCondition::maxDurationTicks).orElse(0);
    }

    /**
     * 从 Tetra 算好的属性拟合一份长矛行为。
     *
     * @param attackDamage Tetra 的 {@code Attributes.ATTACK_DAMAGE} 总值
     * @param attackSpeed  Tetra 的 {@code Attributes.ATTACK_SPEED} 总值
     */
    public static TetraSpearStats fit(double attackDamage, double attackSpeed) {
        return fit(attackDamage, attackSpeed, 0.0D);
    }

    /**
     * 从 Tetra 算好的属性拟合一份长矛行为，并记录 Tetra 自己提供的额外攻击距离。
     *
     * @param attackDamage    Tetra 的 {@code Attributes.ATTACK_DAMAGE} 总值
     * @param attackSpeed     Tetra 的 {@code Attributes.ATTACK_SPEED} 总值
     * @param tetraReachBonus Tetra 自己贡献的 {@code forge:entity_reach} 加成（长手柄 / 插槽等）
     */
    public static TetraSpearStats fit(double attackDamage, double attackSpeed, double tetraReachBonus) {
        float duration = clamp(1.10F + 0.10F * ((float) attackSpeed + 2.0F), MIN_DURATION, MAX_DURATION);

        float damage = (float) attackDamage;
        float soft = damage <= SOFT_CAP
                ? damage
                : SOFT_CAP + (damage - SOFT_CAP) / (1.0F + (damage - SOFT_CAP) / 8.0F);
        float multiplier = clamp(0.55F + 0.09F * soft, MIN_MULTIPLIER, MAX_MULTIPLIER);

        float scale = duration / BASE_DURATION;
        int delay = Math.round(BASE_DELAY_SEC * scale * 20.0F);
        float dismountSpeed = clamp(DISMOUNT_SPEED_MAX - 0.5F * soft, DISMOUNT_SPEED_MIN, DISMOUNT_SPEED_MAX);

        return new TetraSpearStats(
                duration,
                multiplier,
                delay,
                SpearCondition.ofAttackerSpeed(Math.round(BASE_DISMOUNT_SEC * scale * 20.0F), dismountSpeed),
                SpearCondition.ofAttackerSpeed(Math.round(BASE_KNOCKBACK_SEC * scale * 20.0F), 5.1F),
                SpearCondition.ofRelativeSpeed(Math.round(BASE_DAMAGE_SEC * scale * 20.0F), 4.6F),
                Math.max(0.0D, tetraReachBonus)
        );
    }

    /**
     * Tetra 额外攻击距离的计入比例，跟随配置 {@code tetraExtraReachScale}（0~1，默认 0.3）。
     * 配置未加载时回落默认值，避免早期调用抛出。
     */
    static double reachScale() {
        try {
            return SpearConfig.TETRA_EXTRA_REACH_SCALE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_EXTRA_REACH_SCALE;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public float getAttackDuration() {
        return attackDuration;
    }

    @Override
    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    @Override
    public SoundEvent getUseSound() {
        return SpearSounds.ITEM_SPEAR_USE.get();
    }

    @Override
    public SoundEvent getHitSound() {
        return SpearSounds.ITEM_SPEAR_HIT.get();
    }

    @Override
    public SoundEvent getAttackSound() {
        return SpearSounds.ITEM_SPEAR_ATTACK.get();
    }

    @Override
    public int getDelayTicks() {
        return delayTicks;
    }

    @Override
    public int getDismountEndTick() {
        return dismountEndTick;
    }

    @Override
    public int getKnockbackEndTick() {
        return knockbackEndTick;
    }

    @Override
    public int getDamageEndTick() {
        return damageEndTick;
    }

    @Override
    public Optional<SpearCondition> getDismountConditions() {
        return dismountConditions;
    }

    @Override
    public Optional<SpearCondition> getKnockbackConditions() {
        return knockbackConditions;
    }

    @Override
    public Optional<SpearCondition> getDamageConditions() {
        return damageConditions;
    }

    @Override
    public float getForwardMovement() {
        return 0.38F;
    }

    @Override
    public float getMinRange() {
        return 2.0F;
    }

    @Override
    public float getMaxRange() {
        return 4.5F;
    }

    @Override
    public float getHitboxMargin() {
        return 0.25F;
    }

    @Override
    public float getHitboxMargin2() {
        return 0.25F;
    }

    @Override
    public int getContactCooldownTicks() {
        return 10;
    }

    @Override
    public float getSwingTimes() {
        return attackDuration;
    }

    @Override
    public float getMinCreativeRange() {
        return 2.0F;
    }

    @Override
    public float getMaxCreativeRange() {
        return 6.5F;
    }

    @Override
    public float getMobFactor() {
        return 0.5F;
    }

    @Override
    public boolean dealsKnockback() {
        return true;
    }

    @Override
    public boolean dismounts() {
        return true;
    }

    /**
     * Tetra 工具固定按 Java 语义：不挂「全背包共享冷却」，也不挂戳刺后摇。
     * 这两项都属于本模组长矛的可配置行为，Tetra 物品没有稳定的 Item 身份（每个材料组合一个实例），
     * 无法按 Item 共享冷却，因此不受 {@code jabCooldown} / {@code jabToChargeDelay} 控制。
     */
    @Override
    public boolean usesJabCooldown() {
        return false;
    }

    @Override
    public int jabToChargeDelayTicks(int cooldownTicks) {
        return 0;
    }

    @Override
    public float effectiveMaxRange(Entity entity) {
        return SpearCombat.effectiveMaxRange(this, entity, tetraReachBonus, reachScale());
    }

    @Override
    public SpearStyle style() {
        return SpearStyle.MODULAR_3D;
    }
}
