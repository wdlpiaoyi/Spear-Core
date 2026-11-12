package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;

/**
 * 维修项只对「模块化长矛」显示。
 *
 * <p>Tetra 的 {@code RepairSchematic#isRelevant} 只做 {@code item.getClass().isInstance(stack.getItem())}，
 * 不检查 archetype；而我们在 {@code TetraSpearRepairSetup} 里把 RepairSchematic 注册给了
 * {@code tetra:dynamic_handheld}，所有动态 archetype 都会看到维修动作。这里对非
 * {@code modular_spear} 的 DynamicModularItem 返回 false，其它物品仍走原判定。</p>
 */
@Mixin(targets = "se.mickelus.tetra.module.schematic.RepairSchematic", remap = false)
public abstract class RepairSchematicSpearMixin {

    @Inject(method = "isRelevant", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$onlyModularSpear(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof DynamicModularItem)) {
                return;
            }
            if (!TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 兼容层失败时退回 Tetra 原判定。
        }
    }
}
