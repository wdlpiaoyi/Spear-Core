package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.config.SpearConfig;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Tetra 工具的长矛数值层。
 *
 * <p>本类不 import 任何 Tetra 类型：先确认 Tetra 已加载、{@code tetraCompat} 开启，然后才进
 * {@link TetraSpearBridge} 读 Tetra API；桥接里任何意外都只记一次日志并当作「不是长矛」，
 * 绝不让兼容层把游戏搞崩。</p>
 */
public final class TetraSpearProfile {

    private static final String TETRA_MOD_ID = "tetra";

    private static boolean warningLogged;

    private TetraSpearProfile() {
    }

    @Nullable
    public static SpearLike of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!isTetraLoaded() || !enabled()) {
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

    private static boolean enabled() {
        try {
            return SpearConfig.TETRA_COMPAT.get();
        } catch (IllegalStateException exception) {
            // 配置尚未加载（例如数据生成 / 启动早期）时按开启处理。
            return true;
        }
    }

    /**
     * Tetra 的模块 key 是「模块资源路径」（{@code ResourceLocation.getPath()}），不是模块 JSON 里
     * {@code improvements} 的写法：单头工具的矛头模块在运行时是 {@code single/spearhead}。
     *
     * <p>这里同时接受 {@code spearhead} 与 {@code <命名空间>/spearhead}，方便附属模组新增同语义模块。</p>
     */
    public static boolean isSpearheadModuleKey(String key) {
        return key != null && (key.equals("spearhead") || key.endsWith("/spearhead"));
    }

    /**
     * 长手柄模块的运行时 key 是 {@code single/long_handle}；匹配规则同 {@link #isSpearheadModuleKey(String)}。
     */
    public static boolean isLongHandleModuleKey(String key) {
        return key != null && (key.equals("long_handle") || key.endsWith("/long_handle"));
    }
}
