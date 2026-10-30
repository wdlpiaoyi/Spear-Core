package net.minecraft.spearcore.compat.tetra;

import net.minecraft.world.item.Item;
import se.mickelus.tetra.items.modular.IModularItem;

/**
 * 客户端初始化用的 Tetra 类型判断。
 *
 * <p>调用方必须先确认 Tetra 已加载（{@code ModList.isLoaded("tetra")}）再调用本类，
 * 这样未安装 Tetra 时本类不会被加载，也就不会在常量池里解析 Tetra 类型。</p>
 */
public final class TetraClientCompat {

    private TetraClientCompat() {
    }

    public static boolean isModularItem(Item item) {
        return item instanceof IModularItem;
    }
}
