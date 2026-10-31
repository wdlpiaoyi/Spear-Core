package net.minecraft.spearcore.compat.tetra;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.spearcore.config.SpearConfig;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Tetra 长矛攻击力打折。
 *
 * <p>Tetra 的武器攻击力普遍高于本模组同级材质，直接当作蓄力基数会明显 overpowered。这里只对
 * Tetra 工具自身的 {@code ATTACK_DAMAGE}（{@code ADDITION} 修饰符）打折，玩家基础攻击力、力量效果
 * 以及其他来源（附魔、药水、其他模组）都不受影响。</p>
 *
 * <p>之所以做在属性修饰符层而不是伤害结算里：物品 tooltip、原版攻击力和本模组的蓄力/戳刺都会读同一份
 * 属性，折扣后三处显示与实际伤害一致，不会出现「面板 6、打出来 4」的割裂。</p>
 *
 * <p>本类刻意不 import 任何 Tetra 类型，配置未加载时回落默认值，保证 GameTest 与早期调用安全。</p>
 */
public final class TetraDamageScaling {

    /** 配置未加载时的兜底值（与 {@link SpearConfig#TETRA_DAMAGE_SCALE} 的默认值一致）。 */
    public static final double DEFAULT_DAMAGE_SCALE = 0.5D;

    private TetraDamageScaling() {
    }

    /** 跟随配置 {@code tetraDamageScale}（0~1，默认 0.5）；配置未加载时回落默认值。 */
    public static double damageScale() {
        try {
            return SpearConfig.TETRA_DAMAGE_SCALE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_DAMAGE_SCALE;
        }
    }

    /**
     * 把 {@code ATTACK_DAMAGE} 的 {@code ADDITION} 修饰符按 {@code scale} 打折，其他属性/操作原样保留。
     *
     * @param modifiers 原始修饰符表（通常是 {@code ItemStack.getAttributeModifiers(slot)} 的返回值）
     * @param scale     打折比例，限制在 0~1；&gt;=1 时直接返回原表
     * @return 新的不可变表；没有任何 {@code ATTACK_DAMAGE ADDITION} 时返回原表
     */
    public static Multimap<Attribute, AttributeModifier> scaleAttackDamage(
            Multimap<Attribute, AttributeModifier> modifiers, double scale) {
        double clamped = Math.max(0.0D, Math.min(1.0D, scale));
        if (clamped >= 1.0D || modifiers.isEmpty()) {
            return modifiers;
        }

        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        boolean changed = false;
        for (var entry : modifiers.entries()) {
            Attribute attribute = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            if (attribute == Attributes.ATTACK_DAMAGE
                    && modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                builder.put(attribute, new AttributeModifier(modifier.getId(), modifier.getName(),
                        modifier.getAmount() * clamped, modifier.getOperation()));
                changed = true;
            } else {
                builder.put(attribute, modifier);
            }
        }
        return changed ? builder.build() : modifiers;
    }
}
