package net.minecraft.spearcore.compat.tetra;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.spearcore.SpearcoreMod;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.ItemUpgradeRegistry;
import se.mickelus.tetra.module.SchematicRegistry;
import se.mickelus.tetra.module.data.VariantData;
import se.mickelus.tetra.module.schematic.ConfigSchematic;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;
import se.mickelus.tetra.module.schematic.SchematicDefinition;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

/**
 * 通用「强化附层」变体导入。
 *
 * <p>Tetra 的特殊 fuller 效果（例如不洁之血）不是材料属性，而是**模块变体**的数据；
 * 只接受 {@code tetra:fuller/} 材料只能拿到基础数值，拿不到特效。这里在数据加载完成后，
 * 把第三方「/fuller」模块的变体与对应配方 outcome 通用导入到本模组的
 * {@code spear/fuller} 模块/配方里：不认 modid、不写死材料，任何模组新增的 fuller 变体都会自动可用。</p>
 *
 * <p>本类 import Tetra 类型，只能通过反射从事件订阅类加载；未装 Tetra 时不得触碰。</p>
 */
public final class TetraFullerImportImpl {

    private static final String FULLER_MODULE_KEY = "spear/fuller";
    private static final String FULLER_SCHEMATIC_KEY = "tetra:spear/fuller";

    private static boolean failed;

    private TetraFullerImportImpl() {
    }

    public static void importAll() {
        try {
            ItemModule targetModule = findModule(FULLER_MODULE_KEY);
            UpgradeSchematic targetSchematic = SchematicRegistry.getSchematic(FULLER_SCHEMATIC_KEY);
            if (targetModule == null || !(targetSchematic instanceof ConfigSchematic configSchematic)) {
                return;
            }

            Field variantField = ItemModule.class.getDeclaredField("variantData");
            variantField.setAccessible(true);
            List<VariantData> variants = new ArrayList<>(Arrays.asList((VariantData[]) variantField.get(targetModule)));

            Field definitionField = ConfigSchematic.class.getDeclaredField("definition");
            definitionField.setAccessible(true);
            SchematicDefinition targetDefinition = (SchematicDefinition) definitionField.get(configSchematic);
            List<OutcomeDefinition> outcomes = new ArrayList<>(Arrays.asList(targetDefinition.outcomes));

            int imported = 0;
            for (UpgradeSchematic schematic : SchematicRegistry.getAllSchematics()) {
                if (!(schematic instanceof ConfigSchematic other) || other == configSchematic) {
                    continue;
                }
                SchematicDefinition definition = (SchematicDefinition) definitionField.get(other);
                if (definition == null || definition.slots == null
                        || Arrays.stream(definition.slots).noneMatch(slot -> slot != null && slot.endsWith("/fuller"))) {
                    continue;
                }
                if (other.getKey().equals(FULLER_SCHEMATIC_KEY)) {
                    continue;
                }
                for (OutcomeDefinition outcome : definition.outcomes) {
                    if (outcome == null || outcome.material == null || outcome.moduleKey == null
                            || outcome.moduleVariant == null || outcome.moduleKey.startsWith("spear/")) {
                        continue;
                    }
                    ItemModule foreignModule = findModule(outcome.moduleKey);
                    if (foreignModule == null) {
                        continue;
                    }
                    VariantData foreignVariant = resolveVariant(foreignModule, outcome.moduleVariant);
                    if (foreignVariant == null) {
                        continue;
                    }
                    // 直接复用第三方的 VariantData 对象，但要按它自己的 key 安装；
                    // outcome 的 moduleVariant 可能是前缀/拼接过导致对不上，用自己的 key 最稳。
                    String variantKey = foreignVariant.key != null ? foreignVariant.key : outcome.moduleVariant;
                    if (!hasVariant(variants, variantKey)) {
                        variants.add(foreignVariant);
                    }
                    if (!hasOutcome(outcomes, variantKey)) {
                        OutcomeDefinition importedOutcome = new OutcomeDefinition();
                        importedOutcome.material = outcome.material;
                        importedOutcome.requiredTools = outcome.requiredTools;
                        importedOutcome.moduleKey = FULLER_MODULE_KEY;
                        importedOutcome.moduleVariant = variantKey;
                        importedOutcome.hidden = outcome.hidden;
                        importedOutcome.materialSlot = outcome.materialSlot;
                        importedOutcome.experienceCost = outcome.experienceCost;
                        importedOutcome.improvements = outcome.improvements;
                        // 特殊材料 outcome 放在通用 tetra:fuller/ outcome 前面，否则通用材料前缀会先吃掉它。
                        outcomes.add(0, importedOutcome);
                        imported++;
                    }
                }
            }

            variantField.set(targetModule, variants.toArray(new VariantData[0]));
            targetDefinition.outcomes = outcomes.toArray(new OutcomeDefinition[0]);
            if (imported > 0) {
                SpearcoreMod.LOGGER.info("[spearcore] 已导入 {} 个第三方强化附层变体", imported);
            }
        } catch (Throwable throwable) {
            if (!failed) {
                failed = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 导入第三方强化附层变体失败，附层将只有基础材料属性", throwable);
            }
        }
    }

    private static ItemModule findModule(String key) {
        ItemModule module = ItemUpgradeRegistry.instance.getModule(key);
        if (module == null && key != null && !key.startsWith("tetra:")) {
            module = ItemUpgradeRegistry.instance.getModule("tetra:" + key);
        }
        return module;
    }

    private static VariantData resolveVariant(ItemModule module, String key) {
        for (VariantData variant : module.getVariantData()) {
            if (variant != null && key.equals(variant.key)) {
                return variant;
            }
        }
        for (VariantData variant : module.getVariantData()) {
            if (variant != null && variant.key != null
                    && (variant.key.startsWith(key) || key.startsWith(variant.key))) {
                return variant;
            }
        }
        return module.getVariantData().length == 1 ? module.getVariantData()[0] : null;
    }

    private static boolean hasVariant(List<VariantData> variants, String key) {
        return variants.stream().anyMatch(variant -> variant != null && key.equals(variant.key));
    }

    private static boolean hasOutcome(List<OutcomeDefinition> outcomes, String variantKey) {
        return outcomes.stream().anyMatch(outcome -> outcome != null && variantKey.equals(outcome.moduleVariant));
    }
}
