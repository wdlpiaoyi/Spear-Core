package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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

    /** 矛头模块的 key 前缀；长手柄同理。 */
    private static final String SPEARHEAD_PREFIX = "spearhead/";
    private static final String LONG_HANDLE_PREFIX = "long_handle/";

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
        if (!headKey.startsWith(SPEARHEAD_PREFIX) || !handleKey.startsWith(LONG_HANDLE_PREFIX)) {
            return null;
        }

        double attackDamage = modular.getAttributeValue(stack, Attributes.ATTACK_DAMAGE);
        double attackSpeed = modular.getAttributeValue(stack, Attributes.ATTACK_SPEED);
        return TetraSpearStats.fit(attackDamage, attackSpeed);
    }
}
