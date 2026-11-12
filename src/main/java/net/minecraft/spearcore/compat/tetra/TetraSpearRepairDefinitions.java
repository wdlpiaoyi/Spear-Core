package net.minecraft.spearcore.compat.tetra;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.spearcore.SpearcoreMod;
import se.mickelus.tetra.module.data.MaterialData;
import se.mickelus.tetra.module.data.ToolData;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;
import se.mickelus.tetra.module.schematic.OutcomeMaterial;
import se.mickelus.tetra.module.schematic.RepairDefinition;

/**
 * 给本模组的矛模块现算维修定义（Tetra 工作台维修用）。
 *
 * <p>Tetra 的维修不是按材料自动匹配：{@code ItemModule#getRepairDefinitions} 用**变体 key**
 * 去 {@code data/tetra/repairs/} 精确查找。本模组模块接受任意 Tetra 材料（含第三方），无法穷举数据，
 * 所以这里按当前变体的材料现算一条：修耐久就消耗该材料自己的物品。</p>
 */
public final class TetraSpearRepairDefinitions {

    private static final Map<String, Optional<RepairDefinition>> CACHE = new ConcurrentHashMap<>();
    private static boolean loggedFailure;

    private TetraSpearRepairDefinitions() {
    }

    /** 数据重载后材料表可能换对象；由 reload 监听器调用。 */
    public static void clearCache() {
        CACHE.clear();
    }

    /** 当前变体的维修定义；无法解析材料时返回 {@code null}（等于没有维修项）。 */
    public static RepairDefinition get(String moduleKey, String variantKey) {
        if (moduleKey == null || variantKey == null) {
            return null;
        }
        String cacheKey = moduleKey + "|" + variantKey;
        Optional<RepairDefinition> cached = CACHE.get(cacheKey);
        if (cached != null) {
            return cached.orElse(null);
        }
        // 只缓存成功结果：材料表/数据尚未就绪时 build 返回 null，不能把失败永久钉住。
        RepairDefinition built = build(moduleKey, variantKey);
        if (built != null) {
            CACHE.put(cacheKey, Optional.of(built));
        }
        return built;
    }

    private static RepairDefinition build(String moduleKey, String variantKey) {
        try {
            String materialKey = TetraMaterialKey.materialOf(variantKey);
            if (materialKey == null) {
                return null;
            }
            OutcomeMaterial material = findMaterial(materialKey);
            if (material == null) {
                return null;
            }
            OutcomeDefinition outcome = new OutcomeDefinition();
            outcome.material = material;
            outcome.moduleKey = moduleKey;
            outcome.moduleVariant = variantKey;
            outcome.requiredTools = emptyTools();
            return new RepairDefinition(outcome);
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 生成模块化长矛维修定义失败，工作台维修将不可用", throwable);
            }
            return null;
        }
    }

    /** 在 Tetra 材料表里按材料 key 找它自己的 {@code material}（物品/标签），拿来当维修材料。 */
    private static OutcomeMaterial findMaterial(String materialKey) {
        try {
            for (Object value : materialDataValues()) {
                if (value instanceof MaterialData data && materialKey.equals(data.key) && data.material != null) {
                    return data.material;
                }
            }
        } catch (Throwable ignored) {
            // 数据未就绪时按没有维修材料处理。
        }
        return null;
    }

    /** 反射读 Tetra 材料表的值集合（mutil 的 DataStore 不是本模组的编译期依赖）。 */
    private static java.util.Collection<?> materialDataValues() throws ReflectiveOperationException {
        Class<?> dataManagerClass = Class.forName("se.mickelus.tetra.data.DataManager");
        Object dataManager = dataManagerClass.getField("instance").get(null);
        if (dataManager == null) {
            return java.util.List.of();
        }
        Object store = dataManagerClass.getField("materialData").get(dataManager);
        if (store == null) {
            return java.util.List.of();
        }
        Object data = store.getClass().getMethod("getData").invoke(store);
        if (data instanceof java.util.Map<?, ?> map) {
            return map.values();
        }
        return java.util.List.of();
    }

    /** 空工具要求：维修不强制工具等级（与 Tetra 少数维修定义不同，避免卡住流程）。 */
    private static ToolData emptyTools() {
        ToolData tools = new ToolData();
        tools.levelMap = new HashMap<>();
        tools.efficiencyMap = new HashMap<>();
        return tools;
    }
}
