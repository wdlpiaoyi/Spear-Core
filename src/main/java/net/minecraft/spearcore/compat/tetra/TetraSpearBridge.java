package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;

/**
 * Tetra 运行时桥接：模块检测 + 属性读取。
 *
 * <p>只有 {@link TetraSpearProfile} 确认 Tetra 已加载、配置开启后才会调用这里，因此未安装 Tetra 时
 * 这个类不会被加载，也就不会出现 ClassNotFound。方法内任何意外都由调用方兜底成「不是长矛」。</p>
 */
final class TetraSpearBridge {

    /** 单头工具的槽位：矛头 + 手柄。 */
    private static final String HEAD_SLOT = "single/head";
    private static final String HANDLE_SLOT = "single/handle";

    private TetraSpearBridge() {
    }

    static SpearLike of(ItemStack stack) {
        if (!(stack.getItem() instanceof IModularItem modular)) {
            return null;
        }
        ItemModule head = modular.getModuleFromSlot(stack, HEAD_SLOT);
        ItemModule handle = modular.getModuleFromSlot(stack, HANDLE_SLOT);
        if (head == null || handle == null) {
            return null;
        }
        String headKey = head.getKey();
        String handleKey = handle.getKey();
        if (headKey == null || handleKey == null) {
            return null;
        }
        if (!TetraSpearProfile.isSpearheadModuleKey(headKey)
                || !TetraSpearProfile.isLongHandleModuleKey(handleKey)) {
            return null;
        }

        double attackDamage = modular.getAttributeValue(stack, Attributes.ATTACK_DAMAGE);
        double attackSpeed = modular.getAttributeValue(stack, Attributes.ATTACK_SPEED);

        // Tetra 的攻击距离（forge:attack_range，Forge 别名到 forge:entity_reach）会叠得很高：
        // 长手柄 + 紫水晶插槽可轻松超过 2 格。只把 Tetra 自己贡献的 ADDITION 部分交给拟合，
        // 由 TetraSpearStats 按配置比例计入。MULTIPLY_TOTAL 型 reach 修饰符不纳入（Tetra 当前不用）。
        double tetraReachBonus = 0.0D;
        for (var entry : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).entries()) {
            if (entry.getKey() == ForgeMod.ENTITY_REACH.get()
                    && entry.getValue().getOperation() == AttributeModifier.Operation.ADDITION) {
                tetraReachBonus += entry.getValue().getAmount();
            }
        }
        return TetraSpearStats.fit(attackDamage, attackSpeed, tetraReachBonus);
    }
}
