package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;

/**
 * Tetra 运行时桥接：识别本模组的模块化长矛 + 读取属性。
 *
 * <p>只有 {@link TetraSpearProfile} 确认 Tetra 已加载、配置开启后才会调用这里，因此未安装 Tetra 时
 * 这个类不会被加载，也就不会出现 ClassNotFound。方法内任何意外都由调用方兜底成「不是长矛」。</p>
 *
 * <p>判定只看 archetype：Tetra 自带的单头工具（矛头 + 长手柄）不再被当成长矛。本模组的模块化矛是
 * 一个独立的 {@code DynamicModularItem} 栈，archetype 为 {@code modular_spear}，由数据包里的
 * replacement 把七把贴图长矛转换得到。</p>
 */
final class TetraSpearBridge {

    private TetraSpearBridge() {
    }

    static SpearLike of(ItemStack stack) {
        if (!(stack.getItem() instanceof DynamicModularItem)) {
            return null;
        }
        // Tetra 把 archetype 存成 Stack NBT 里的纯 path，读取时固定拼 tetra 命名空间。
        if (!TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
            return null;
        }

        IModularItem modular = (IModularItem) stack.getItem();
        double attackDamage = modular.getAttributeValue(stack, Attributes.ATTACK_DAMAGE);
        double attackSpeed = modular.getAttributeValue(stack, Attributes.ATTACK_SPEED);

        // Tetra 的额外攻击距离（forge:entity_reach，Forge 内部名 ForgeMod.ENTITY_REACH）会叠得很高：
        // 长手柄 + 紫水晶插槽可轻松超过 2 格。优先取 Tetra 自己合并后的 reach 总量（与属性面板一致），
        // 取不到（例如修饰符只存在于 NBT / 别的路径）再退回遍历 ItemStack 修饰符。
        // 交由 TetraSpearStats 按配置比例计入；玩家其他来源的 reach 不受影响。
        double tetraReachBonus = modular.getAttributeValue(stack, ForgeMod.ENTITY_REACH.get());
        if (!(tetraReachBonus > 0.0D)) {
            tetraReachBonus = reachFromItemModifiers(stack);
        }
        return TetraSpearStats.fit(attackDamage, attackSpeed, tetraReachBonus);
    }

    /**
     * 从 {@code ItemStack} 自身的属性修饰符里折算额外 reach。
     *
     * <p>{@code ADDITION} 直接相加；{@code MULTIPLY_BASE} 以原版 3.0 为基准折算成额外距离；
     * {@code MULTIPLY_TOTAL} 不参与（Tetra 当前不用）。</p>
     */
    private static double reachFromItemModifiers(ItemStack stack) {
        double addition = 0.0D;
        double multiplyBase = 0.0D;
        for (var entry : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
            if (entry.getKey() != ForgeMod.ENTITY_REACH.get()) continue;
            AttributeModifier modifier = entry.getValue();
            switch (modifier.getOperation()) {
                case ADDITION -> addition += modifier.getAmount();
                case MULTIPLY_BASE -> multiplyBase += modifier.getAmount();
                default -> {
                    // MULTIPLY_TOTAL 与 Tetra 的 reach 无关，忽略。
                }
            }
        }
        return Math.max(0.0D, addition + 3.0D * multiplyBase);
    }
}
