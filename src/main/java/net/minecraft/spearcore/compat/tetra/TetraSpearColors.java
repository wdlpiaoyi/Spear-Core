package net.minecraft.spearcore.compat.tetra;

import java.util.Map;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.data.GlyphData;
import se.mickelus.tetra.module.data.VariantData;

/**
 * 本模组模块化长矛（archetype {@code modular_spear}）的材料颜色。
 *
 * <p>Tetra 的材料色写在 {@code variants[i].glyph.tint} 或材质 JSON 的 {@code tints.texture} 里，
 * 自绘贴图模型需要拿它给杆/矛头着色，才能保留「按材料换色」。</p>
 *
 * <p>槽位来自 archetype：{@code spear/head} 当矛头（tint 1），{@code spear/handle} 当杆与箍（tint 0）。
 * 这两个槽位挂的是本模组自己的模块（{@code data/tetra/modules/spear/*}），不是 Tetra 剑的模块。</p>
 *
 * <p>本类只在确认 Tetra 已加载后才会被加载（调用方先做 {@code ModList.isLoaded("tetra")}
 * 守卫），并且所有查询都包在 {@code try/catch} 里：KubeJS/CrT 改过的材料数据出现半成品时，
 * 回退成白色而不是让渲染线程崩掉。</p>
 */
public final class TetraSpearColors {

    public static final int FALLBACK = 0xFFFFFFFF;
    public static final String HEAD_SLOT = "spear/head";
    public static final String HANDLE_SLOT = "spear/handle";

    private static final Map<String, Integer> MATERIAL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean loggedFailure;
    private static boolean validatedReflection;

    private TetraSpearColors() {
    }

    /** 矛头颜色。 */
    public static int headColor(ItemStack stack) {
        return colorFor(stack, HEAD_SLOT);
    }

    /** 长手柄颜色（杆与箍用）。 */
    public static int handleColor(ItemStack stack) {
        return colorFor(stack, HANDLE_SLOT);
    }

    /**
     * 矛头槽当前变体 key（例如 {@code spear_head/iron}、{@code spear_shuo_head/stick}）。
     *
     * <p>渲染层用它选矛头外观（{@code ItemRendererTetraModelMixin}）；读不到时返回 {@code null}，
     * 调用方回退到默认模型。</p>
     */
    public static String headVariantKey(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IModularItem modular)) {
                return null;
            }
            ItemModule module = modular.getModuleFromSlot(stack, HEAD_SLOT);
            if (module == null) {
                return null;
            }
            VariantData variant = module.getVariantData(stack);
            return variant == null ? null : variant.key;
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 读取 Tetra 矛头变体失败，长矛将使用默认外观", throwable);
            }
            return null;
        }
    }

    public static void clearCache() {
        MATERIAL_CACHE.clear();
    }

    private static int colorFor(ItemStack stack, String slot) {
        try {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IModularItem modular)) {
                return FALLBACK;
            }
            ItemModule module = modular.getModuleFromSlot(stack, slot);
            if (module == null) {
                return FALLBACK;
            }
            VariantData variant = module.getVariantData(stack);
            if (variant == null) {
                return FALLBACK;
            }
            GlyphData glyph = variant.glyph;
            if (glyph != null && (glyph.tint & 0xFFFFFF) != 0) {
                return 0xFF000000 | (glyph.tint & 0xFFFFFF);
            }
            return materialColor(variant.key);
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 读取 Tetra 材料颜色失败，长矛模型将使用白色杆/头", throwable);
            }
            return FALLBACK;
        }
    }

    /** 遍历 Tetra 已加载的材质数据，按 variant key 的最后一段匹配。 */
    private static int materialColor(String variantKey) {
        if (variantKey == null || variantKey.isEmpty()) {
            return FALLBACK;
        }
        validateReflection();
        Integer cached = MATERIAL_CACHE.get(variantKey);
        if (cached != null) {
            return cached;
        }
        int color = FALLBACK;
        // 反射读取 Tetra 的材料表：直接 import MaterialStore 会把 mutil 的类拉进编译期
        // classpath，而 mutil 并不是本模组的 compileOnly 依赖。
        Object materials = materialDataMap();
        if (materials instanceof java.util.Map<?, ?> map) {
            for (Object value : map.values()) {
                if (value == null) {
                    continue;
                }
                try {
                    Class<?> type = value.getClass();
                    String key = (String) type.getField("key").get(value);
                    if (!TetraMaterialKey.matches(variantKey, key)) {
                        continue;
                    }
                    Object tints = type.getField("tints").get(value);
                    if (tints != null) {
                        color = 0xFF000000 | (tints.getClass().getField("texture").getInt(tints) & 0xFFFFFF);
                    }
                    break;
                } catch (ReflectiveOperationException ignored) {
                    // 单个材质结构不完整时跳过，继续找下一个。
                }
            }
        }
        MATERIAL_CACHE.put(variantKey, color);
        return color;
    }

    /**
     * 启动后第一次取色时确认「反射读取 Tetra 材料表」这条路仍然可用。
     *
     * <p>Tetra 升级后如果 {@code DataManager.materialData} 的结构变了，这里会静默退回白色，
     * 玩家只看到「所有矛都白了」而没有任何线索；所以拿已知材料 {@code iron} 探一次，失败就明确告警。</p>
     */
    private static void validateReflection() {
        if (validatedReflection) {
            return;
        }
        validatedReflection = true;
        Object materials = materialDataMap();
        if (!(materials instanceof java.util.Map<?, ?> map) || map.isEmpty()) {
            SpearcoreMod.LOGGER.warn(
                    "[spearcore] 读不到 Tetra 的材料表（DataManager.materialData 结构可能变了），长矛将退回白色杆/头");
            return;
        }
        for (Object value : map.values()) {
            if ("iron".equals(materialKeyOf(value))) {
                return;
            }
        }
        SpearcoreMod.LOGGER.warn(
                "[spearcore] Tetra 材料表里找不到已知材料 iron，材质色可能全部退回白色（Tetra 版本不匹配？）");
    }

    private static String materialKeyOf(Object material) {
        if (material == null) {
            return null;
        }
        try {
            return (String) material.getClass().getField("key").get(material);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object materialDataMap() {
        try {
            Class<?> dataManager = Class.forName("se.mickelus.tetra.data.DataManager");
            Object instance = dataManager.getField("instance").get(null);
            if (instance == null) {
                return null;
            }
            Object store = dataManager.getField("materialData").get(instance);
            if (store == null) {
                return null;
            }
            return store.getClass().getMethod("getData").invoke(store);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
