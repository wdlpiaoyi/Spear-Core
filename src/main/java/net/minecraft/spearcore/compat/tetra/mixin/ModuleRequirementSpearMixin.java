package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.compat.tetra.TetraModuleGateMapping;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.requirement.ModuleRequirement;

/**
 * 让第三方 Tetra 数据里写死 {@code sword/*} / {@code single/*} / {@code double/*} 模块的
 * requirement 也能匹配本模组的矛槽位（按角色映射，见 {@link TetraModuleGateMapping}）。
 *
 * <p>典型用途：RevelationFix 的神灵金锭 crafting effect 要求「模块是某把剑的剑刃/剑柄」，
 * 映射后本模组的矛头/矛杆同样能触发，而不需要给每个 mod 抄一份数据。</p>
 */
@Mixin(targets = "se.mickelus.tetra.module.schematic.requirement.ModuleRequirement", remap = false)
public abstract class ModuleRequirementSpearMixin {

    @Shadow
    @Final
    String moduleKey;

    @Inject(method = "test", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$spearModuleEquivalent(CraftingContext context,
                                                 CallbackInfoReturnable<Boolean> cir) {
        try {
            if (context == null) {
                return;
            }
            ItemStack target = context.targetStack;
            if (target == null || target.isEmpty()
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(target))) {
                return;
            }
            if (TetraModuleGateMapping.matches(context.slot, moduleKey)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // 兼容层任何异常都退回 Tetra 原判定。
        }
    }
}
