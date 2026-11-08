package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 模块化长矛（Tetra）的识别与数值层。
 *
 * <p>本类不 import 任何 Tetra 类型：先确认 Tetra 已加载，然后才进
 * {@link TetraSpearBridge} 读 Tetra API；桥接里任何意外都只记一次日志并当作「不是长矛」，
 * 绝不让兼容层把游戏搞崩。</p>
 *
 * <p>判定依据只有一个：物品栈的 Tetra archetype 是不是 {@link #SPEAR_ARCHETYPE_KEY}。Tetra 自带的
 * 单头工具（矛头 + 长手柄）不在兼容范围内，它们按普通 Tetra 工具工作。</p>
 */
public final class TetraSpearProfile {

    private static final String TETRA_MOD_ID = "tetra";

    /**
     * 本模组模块化长矛的 archetype key。
     *
     * <p>Tetra 的 {@code DynamicModularItem} 把 archetype 写成物品栈 NBT 里的纯 path，读取时固定拼
     * {@code tetra} 命名空间，所以这里只比较 path 部分，数据文件也必须放在
     * {@code data/tetra/archetypes/modular_spear.json}。</p>
     */
    public static final String SPEAR_ARCHETYPE_KEY = "modular_spear";

    private static boolean warningLogged;

    private TetraSpearProfile() {
    }

    @Nullable
    public static SpearLike of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!isTetraLoaded()) {
            return null;
        }
        try {
            return TetraSpearBridge.of(stack);
        } catch (Throwable throwable) {
            if (!warningLogged) {
                warningLogged = true;
                SpearcoreMod.LOGGER.warn("[spearcore] Tetra 兼容层读取模块/属性失败，本次启动将按普通 Tetra 工具处理", throwable);
            }
            return null;
        }
    }

    private static boolean isTetraLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(TETRA_MOD_ID);
    }

    /**
     * 是否为「本模组自己的模块化长矛」：唯一判定入口，Tetra 自带的单头工具不在此列。
     */
    public static boolean isModularSpearArchetype(@Nullable String archetypeKey) {
        return SPEAR_ARCHETYPE_KEY.equals(archetypeKey);
    }
}
