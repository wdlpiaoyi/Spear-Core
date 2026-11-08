package net.minecraft.spearcore.compat.tetra;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.data.VariantData;

/**
 * {@code /spearcore tetra} 的调试输出：把模块化长矛的属性来源摊开，用于调攻速/伤害数值。
 *
 * <p>只在装了 Tetra 且手持模块化长矛时被调用；未装 Tetra 时这个类不会被加载。</p>
 */
public final class TetraSpearDebug {

    private TetraSpearDebug() {
    }

    public static List<String> describe(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        try {
            if (!(stack.getItem() instanceof DynamicModularItem)) {
                lines.add("主手不是模块化长矛（tetra:dynamic_handheld）");
                return lines;
            }
            IModularItem modular = (IModularItem) stack.getItem();
            String archetype = DynamicModularItem.getArchetypeKey(stack);
            TetraSpearHead head = TetraSpearHead.of(TetraSpearColors.headVariantKey(stack));
            lines.add("原型(archetype)=" + archetype);
            lines.add("矛头形态=" + head.nameKey() + "；左键=" + (head.slashing() ? "挥砍(自带横扫 III)" : "戳刺"));
            lines.add("Tetra 属性: 攻击力=" + modular.getAttributeValue(stack, Attributes.ATTACK_DAMAGE)
                    + "，攻速=" + modular.getAttributeValue(stack, Attributes.ATTACK_SPEED)
                    + "，实体距离=" + modular.getAttributeValue(stack, ForgeMod.ENTITY_REACH.get()));
            SpearLike fitted = TetraSpearBridge.of(stack);
            if (fitted instanceof TetraSpearStats stats) {
                lines.add("长矛拟合: 出手时长=" + stats.getAttackDuration()
                        + " tick，伤害系数=" + stats.getDamageMultiplier()
                        + "，基础射程=" + stats.getMaxRange()
                        + "，戳刺判定箱=" + stats.getHitboxMargin2()
                        + "，挥砍=" + stats.slashing());
            }
            lines.add("槽位与模块:");
            for (String slot : new String[]{"spear/head", "spear/handle", "spear/binding",
                    "spear/butt", "spear/fuller"}) {
                ItemModule module = modular.getModuleFromSlot(stack, slot);
                if (module == null) {
                    lines.add(slot + " -> (empty)");
                    continue;
                }
                VariantData variant = module.getVariantData(stack);
                lines.add("  " + slot + " -> " + module.getKey() + " / " + (variant == null ? "?" : variant.key));
                if (variant != null && variant.attributes != null) {
                    lines.add("    变体属性=" + variant.attributes);
                }
                lines.add("    模块属性=" + module.getAttributeModifiers(stack));
            }
            boolean anyImprovement = false;
            for (var improvement : modular.getImprovements(stack)) {
                if (improvement != null) {
                    anyImprovement = true;
                    lines.add("改进: " + improvement.key + " 等级 " + improvement.level);
                }
            }
            if (!anyImprovement) {
                lines.add("改进: 无");
            }
        } catch (Throwable throwable) {
            lines.add("dump 失败: " + throwable);
        }
        return lines;
    }
}
