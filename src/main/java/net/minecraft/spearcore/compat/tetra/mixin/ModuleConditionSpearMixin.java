package net.minecraft.spearcore.compat.tetra.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.compat.tetra.TetraModuleGateMapping;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

/**
 * 让第三方 crafting effect 的 {@code tetra:module} 条件也能命中本模组的矛槽位。
 *
 * <p>关键：crafting effect 用的是
 * {@code se.mickelus.tetra.craftingeffect.condition.ModuleCondition}，和
 * {@code schematic.requirement.ModuleRequirement} <b>不是同一个类</b>；只修后者的话，
 * RevelationFix 的神灵金锭效果永远不会对矛生效。</p>
 *
 * <p>映射规则复用 {@link TetraModuleGateMapping}：第三方写死的 {@code sword/*} /
 * {@code single/*} / {@code double/*} 按角色对应到矛的刃/柄/护手/配重槽。</p>
 */
@Mixin(targets = "se.mickelus.tetra.craftingeffect.condition.ModuleCondition", remap = false)
public abstract class ModuleConditionSpearMixin {

    @Shadow
    String module;

    @Shadow
    String variant;

    @Shadow
    boolean anySlot;

    @Inject(method = "test", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$spearCraftingEffectGate(ResourceLocation[] unlocked, ItemStack stack,
                                                   String slot, boolean isReplacing, Player player,
                                                   ItemStack[] materials, Map<ToolAction, Integer> tools,
                                                   UpgradeSchematic schematic, Level level, BlockPos pos,
                                                   BlockState state,
                                                   CallbackInfoReturnable<Boolean> cir) {
        try {
            if (variant != null || module == null || stack == null || stack.isEmpty()) {
                return;
            }
            if (!(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return;
            }
            boolean matched = anySlot
                    ? TetraModuleGateMapping.matchesAnySpearSlot(module)
                    : TetraModuleGateMapping.matches(slot, module);
            if (matched) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
            // 兼容层失败时退回 Tetra 原判定。
        }
    }
}
