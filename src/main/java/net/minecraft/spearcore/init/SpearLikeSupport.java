package net.minecraft.spearcore.init;

import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 从物品堆解析出「长矛行为」的统一入口。
 *
 * <p>本模组长矛直接返回自身；Tetra 工具走 {@link TetraSpearProfile}（没装 Tetra 时恒为 null，
 * 不会触发任何 Tetra 类加载）。所有战斗 / 渲染逻辑都应该通过这里判断，而不是继续
 * {@code instanceof SpearItem}。</p>
 */
public final class SpearLikeSupport {

    private SpearLikeSupport() {
    }

    @Nullable
    public static SpearLike of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof SpearItem spear) {
            return spear;
        }
        return TetraSpearProfile.of(stack);
    }

    public static boolean isSpearLike(ItemStack stack) {
        return of(stack) != null;
    }
}
