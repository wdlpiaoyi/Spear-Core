package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraModuleGateMapping;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;
import se.mickelus.tetra.module.schematic.ConfigSchematic;
import se.mickelus.tetra.module.schematic.OutcomeDefinition;
import se.mickelus.tetra.module.schematic.SchematicDefinition;

/**
 * 第三方「只加改进、不换模块」的剑刃配方（RevelationFix 的 longinus / aeglos 等）
 * 在矛上同样成立：槽位按角色映射到 {@code spear/head} 等，不改第三方数据文件。
 *
 * <p>只放行 improvement-only 配方；任何会替换模块的配方仍然只作用于剑，避免把
 * 剑刃模块塞进矛头槽。</p>
 */
@Mixin(targets = "se.mickelus.tetra.module.schematic.ConfigSchematic", remap = false)
public abstract class ConfigSchematicSpearMixin {

    @Inject(method = "isRelevant", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$spearSchematicIsRelevant(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (spearcore$isForbiddenGuardSchematic(stack)) {
            cir.setReturnValue(false);
            return;
        }
        // Tetra 自带的打磨配方在「打磨」面板里是**按物品**列出来的（不看当前槽位），
        // 同一个改进在 Tetra 里可能有多套来源（single/handle 与 double/basic_handle 都给
        // handle_hone/speed，single/head 与 double/shared_head 都给 head_hone/*），
        // 不在这里过滤就会看到两条一样的「打磨：速度」。
        if (spearcore$isHiddenNativeSpearHone((ConfigSchematic) (Object) this, stack)) {
            cir.setReturnValue(false);
            return;
        }
        if (spearcore$isThirdPartySpearImprovement(stack)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 本模组的矛上，只保留 {@link #NATIVE_SPEAR_HONES} 里点名的那几套 Tetra 打磨；
     * 其余 Tetra 原生的 hone 配方（double/basic_handle、double/shared_head、shield/* 等）
     * 在矛的「打磨」面板里隐藏，避免同一改进重复出现。
     */
    private boolean spearcore$isHiddenNativeSpearHone(ConfigSchematic schematic, ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()
                    || !(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return false;
            }
            SchematicDefinition definition = spearcore$definition(schematic);
            if (definition == null || !definition.hone) {
                return false;
            }
            // 本模组自己的打磨（spear/head 尖锐、spear/handle 稳定）不在隐藏范围。
            if (definition.slots != null && definition.slots.length == 1
                    && definition.slots[0].startsWith("spear/")) {
                return false;
            }
            if (definition.slots == null || definition.slots.length != 1 || definition.outcomes == null) {
                return true;
            }
            String sourceSlot = definition.slots[0];
            for (OutcomeDefinition outcome : definition.outcomes) {
                if (outcome == null || outcome.moduleKey != null || outcome.improvements == null
                        || outcome.improvements.isEmpty()) {
                    return true;
                }
                for (String improvement : outcome.improvements.keySet()) {
                    if (!NATIVE_SPEAR_HONES.containsKey(sourceSlot + "|" + improvement)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Inject(method = "isApplicableForSlot", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$spearSchematicAppliesToSlot(String slot, ItemStack stack,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (slot == null || (!slot.startsWith("spear/") && !"sword/guard".equals(slot))) {
            return;
        }
        try {
            if (spearcore$allowsNativeSpearHone((ConfigSchematic) (Object) this, slot, stack)) {
                cir.setReturnValue(true);
                return;
            }
            if (spearcore$isForbiddenGuardSchematic(stack)) {
                cir.setReturnValue(false);
                return;
            }
            if (!spearcore$isThirdPartySpearImprovement(stack)) {
                return;
            }
            SchematicDefinition definition = spearcore$definition((ConfigSchematic) (Object) this);
            if (definition == null || definition.slots == null) {
                return;
            }
            String role = TetraModuleGateMapping.roleOfSlot(slot);
            if (role == null) {
                return;
            }
            for (String candidate : definition.slots) {
                if (role.equals(TetraModuleGateMapping.roleOfSlot(candidate))) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // 兼容层失败时退回 Tetra 原判定。
        }
    }

    /**
     * Tetra 自带、允许直接打磨矛的配方（源槽位 + 改进 key → 矛槽位角色）。
     *
     * <p>同一个改进在 Tetra 里可能有多套配方（例如 {@code head_hone/durability} 同时有
     * {@code single/head} 和 {@code double/shared_head} 两个版本），所以这里**按源槽位**精确点名，
     * 只放行其中一套；否则工作台会把同一改进列成好几条重复的「打磨：X」。</p>
     *
     * <ul>
     *   <li>{@code single/head}：单头工具的头部耐久/速度（{@code head_hone/durability|speed}）；</li>
     *   <li>{@code sword/blade}：剑刃伤害打磨（{@code blade_hone/damage}）；</li>
     *   <li>{@code single/handle}：单手柄速度（{@code handle_hone/speed}）。</li>
     * </ul>
     *
     * <p>Tetra **没有手柄耐久**打磨（只有单头/盾握把的耐久），所以矛杆耐久线已直接移除，
     * 不再自造；本模组自己的打磨只剩「尖锐」与「稳定」。</p>
     */
    @Unique
    private static final java.util.Map<String, String> NATIVE_SPEAR_HONES = java.util.Map.of(
            "single/head|head_hone/durability", "head",
            "single/head|head_hone/speed", "head",
            "sword/blade|blade_hone/damage", "head",
            "sword/hilt|hilt_hone/speed", "handle");

    private boolean spearcore$allowsNativeSpearHone(ConfigSchematic schematic, String slot, ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()
                    || !(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return false;
            }
            SchematicDefinition definition = spearcore$definition(schematic);
            if (definition == null || !definition.hone
                    || definition.slots == null || definition.slots.length != 1
                    || definition.outcomes == null || definition.outcomes.length == 0) {
                return false;
            }
            String targetRole = TetraModuleGateMapping.roleOfSlot(slot);
            if (targetRole == null || !slot.startsWith("spear/")) {
                return false;
            }
            String sourceSlot = definition.slots[0];
            for (OutcomeDefinition outcome : definition.outcomes) {
                if (outcome == null || outcome.moduleKey != null || outcome.improvements == null
                        || outcome.improvements.isEmpty()) {
                    return false;
                }
                for (String improvement : outcome.improvements.keySet()) {
                    if (!targetRole.equals(NATIVE_SPEAR_HONES.get(sourceSlot + "|" + improvement))) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 矛的 sword/guard 槽只允许 Tetra 的 binding / socket；Tetra 剑的 basic_guard、
     * wide_guard、decorative_guard 等护手模块不应该出现在矛上。
     */
    private boolean spearcore$isForbiddenGuardSchematic(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()
                    || !(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return false;
            }
            SchematicDefinition definition = spearcore$definition((ConfigSchematic) (Object) this);
            if (definition == null || definition.slots == null || definition.outcomes == null) {
                return false;
            }
            boolean guardSlot = false;
            for (String slot : definition.slots) {
                if ("sword/guard".equals(slot)) {
                    guardSlot = true;
                    break;
                }
            }
            if (!guardSlot) {
                return false;
            }
            for (OutcomeDefinition outcome : definition.outcomes) {
                if (outcome == null || outcome.moduleKey == null) {
                    continue;
                }
                if (!"sword/binding".equals(outcome.moduleKey) && !"sword/socket".equals(outcome.moduleKey)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 字段不可用时退回原判定。
        }
        return false;
    }

    private boolean spearcore$isThirdPartySpearImprovement(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()
                    || !(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return false;
            }
            SchematicDefinition definition = spearcore$definition((ConfigSchematic) (Object) this);
            if (definition == null || definition.slots == null || definition.outcomes == null
                    || definition.outcomes.length == 0) {
                return false;
            }
            // 第三方数据也放在 data/tetra/ 下，key 本身不带命名空间，无法靠 key 区分。
            // Tetra 自带的剑刃改进用 sword/* / single/* / double/* 本地化 key；第三方
            // （RevelationFix 的 longinus/aeglos）用自己模组的命名空间。只放行后者，
            // 避免把 Tetra 自带的剑刃打磨/锯齿再复制一份到矛头。
            boolean hiltSlot = false;
            for (String slot : definition.slots) {
                if ("sword/hilt".equals(slot)) {
                    hiltSlot = true;
                    break;
                }
            }
            String localizationKey = definition.localizationKey;
            if (!hiltSlot && (localizationKey == null || localizationKey.startsWith("sword/")
                    || localizationKey.startsWith("single/") || localizationKey.startsWith("double/"))) {
                return false;
            }
            for (OutcomeDefinition outcome : definition.outcomes) {
                if (outcome == null || outcome.moduleKey != null || outcome.improvements == null
                        || outcome.improvements.isEmpty()) {
                    return false;
                }
            }
            for (String slot : definition.slots) {
                if (TetraModuleGateMapping.roleOfSlot(slot) != null) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 字段不可用时退回原判定。
        }
        return false;
    }

    /** 反射字段只查一次；Tetra 改结构时只 warn 一次，而不是每个配方静默失败。 */
    @Unique
    private static java.lang.reflect.Field definitionField;
    @Unique
    private static boolean definitionFieldMissing;
    @Unique
    private static boolean definitionFieldWarning;

    private static SchematicDefinition spearcore$definition(ConfigSchematic schematic) throws Exception {
        if (definitionFieldMissing) {
            throw new NoSuchFieldException("ConfigSchematic.definition");
        }
        if (definitionField == null) {
            try {
                java.lang.reflect.Field field = ConfigSchematic.class.getDeclaredField("definition");
                field.setAccessible(true);
                definitionField = field;
            } catch (Exception exception) {
                definitionFieldMissing = true;
                if (!definitionFieldWarning) {
                    definitionFieldWarning = true;
                    SpearcoreMod.LOGGER.warn(
                            "[spearcore] 读取 Tetra ConfigSchematic.definition 失败，矛的第三方配方门禁将退回原判定",
                            exception);
                }
                throw exception;
            }
        }
        return (SchematicDefinition) definitionField.get(schematic);
    }
}
