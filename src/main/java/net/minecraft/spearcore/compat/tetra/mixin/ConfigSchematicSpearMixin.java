package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.compat.tetra.TetraModuleGateMapping;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
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
        if (spearcore$isThirdPartySpearImprovement(stack)) {
            cir.setReturnValue(true);
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
     * Tetra 自带的打磨配方里，允许直接打磨矛的条目。
     *
     * <p>目前只放行 {@code single/handle/hone_speed_*}（改进 {@code handle_hone/speed}）到矛杆：
     * 本模组以前自造过一份同名同改进的配方，工作台里会出现两条一样的「打磨：速度」，
     * 现在删掉自造那份、直接复用 Tetra 的。矛头打磨（伤害/耐久/速度）、矛杆耐久和稳定
     * 仍用本模组自己的配方（Tetra 没有对应的可复用项，或改进 key 不同）。</p>
     */
    private static final java.util.Set<String> NATIVE_SPEAR_HONES = java.util.Set.of("handle_hone/speed");

    private boolean spearcore$allowsNativeSpearHone(ConfigSchematic schematic, String slot, ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()
                    || !(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return false;
            }
            SchematicDefinition definition = spearcore$definition(schematic);
            if (definition == null || !definition.hone
                    || definition.slots == null || definition.outcomes == null
                    || definition.outcomes.length == 0) {
                return false;
            }
            String targetRole = TetraModuleGateMapping.roleOfSlot(slot);
            if (!"handle".equals(targetRole) || !slot.startsWith("spear/")) {
                return false;
            }
            for (OutcomeDefinition outcome : definition.outcomes) {
                if (outcome == null || outcome.moduleKey != null || outcome.improvements == null
                        || outcome.improvements.isEmpty()) {
                    return false;
                }
                for (String improvement : outcome.improvements.keySet()) {
                    if (!NATIVE_SPEAR_HONES.contains(improvement)) {
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

    private static SchematicDefinition spearcore$definition(ConfigSchematic schematic) throws Exception {
        java.lang.reflect.Field field = ConfigSchematic.class.getDeclaredField("definition");
        field.setAccessible(true);
        return (SchematicDefinition) field.get(schematic);
    }
}
