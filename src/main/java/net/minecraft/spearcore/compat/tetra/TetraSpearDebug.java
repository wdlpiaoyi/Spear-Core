package net.minecraft.spearcore.compat.tetra;

import java.util.ArrayList;
import java.util.List;
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
                lines.add("主手不是 tetra:dynamic_handheld");
                return lines;
            }
            IModularItem modular = (IModularItem) stack.getItem();
            String archetype = DynamicModularItem.getArchetypeKey(stack);
            TetraSpearHead head = TetraSpearHead.of(TetraSpearColors.headVariantKey(stack));
            lines.add("archetype=" + archetype + " head=" + head + " slashing=" + head.slashing());
            lines.add("ATTACK_DAMAGE=" + modular.getAttributeValue(stack, Attributes.ATTACK_DAMAGE));
            lines.add("ATTACK_SPEED=" + modular.getAttributeValue(stack, Attributes.ATTACK_SPEED));
            lines.add("ENTITY_REACH=" + modular.getAttributeValue(stack, ForgeMod.ENTITY_REACH.get()));
            for (String slot : new String[]{"spear/head", "spear/handle", "spear/binding", "spear/butt"}) {
                ItemModule module = modular.getModuleFromSlot(stack, slot);
                if (module == null) {
                    lines.add(slot + " -> (empty)");
                    continue;
                }
                VariantData variant = module.getVariantData(stack);
                lines.add(slot + " -> " + module.getKey() + " / " + (variant == null ? "?" : variant.key));
                if (variant != null && variant.attributes != null) {
                    lines.add("  variant.attributes=" + variant.attributes);
                }
                lines.add("  module.attributes=" + module.getAttributeModifiers(stack));
            }
            for (var improvement : modular.getImprovements(stack)) {
                if (improvement != null) {
                    lines.add("improvement " + improvement.key + " level " + improvement.level);
                }
            }
        } catch (Throwable throwable) {
            lines.add("dump 失败: " + throwable);
        }
        return lines;
    }
}
