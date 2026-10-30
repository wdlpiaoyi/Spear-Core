package net.minecraft.spearcore.compat.tetra;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.data.GlyphData;
import se.mickelus.tetra.module.data.VariantData;

/**
 * Tetra 单头工具的材料颜色。
 *
 * <p>Tetra 的材料色写在 {@code variants[i].glyph.tint} 或材质 JSON 的 {@code tints.texture} 里，
 * 自绘模型需要拿它给顶点/贴图着色，才能保留「按材料换色」。</p>
 *
 * <p>本类只在确认 Tetra 已加载后才会被加载（调用方先做 {@code ModList.isLoaded("tetra")}
 * 守卫），并且所有查询都包在 {@code try/catch} 里：KubeJS/CrT 改过的材料数据出现半成品时，
 * 回退成白色而不是让渲染线程崩掉。</p>
 */
public final class TetraSpearColors {

    public static final int FALLBACK = 0xFFFFFFFF;
    public static final String HEAD_SLOT = "single/head";
    public static final String HANDLE_SLOT = "single/handle";

    private static final Map<String, Integer> MATERIAL_CACHE = new HashMap<>();
    private static boolean loggedFailure;

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
