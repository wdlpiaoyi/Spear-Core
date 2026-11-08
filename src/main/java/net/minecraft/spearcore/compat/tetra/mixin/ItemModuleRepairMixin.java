package net.minecraft.spearcore.compat.tetra.mixin;

import java.util.Collection;
import java.util.List;
import net.minecraft.spearcore.compat.tetra.TetraSpearRepairDefinitions;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.data.VariantData;
import se.mickelus.tetra.module.schematic.RepairDefinition;

/**
 * 给本模组矛模块现算维修定义：Tetra 只按变体 key 查 data/tetra/repairs，本模组模块接受任意材料，
 * 无法穷举，所以这里按当前变体的材料生成。
 */
@Mixin(targets = "se.mickelus.tetra.module.ItemModule", remap = false)
public abstract class ItemModuleRepairMixin {

    @Inject(method = "getRepairDefinitions", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$spearRepairDefinitions(ItemStack stack,
                                                  CallbackInfoReturnable<Collection<RepairDefinition>> cir) {
        try {
            ItemModule self = (ItemModule) (Object) this;
            String moduleKey = self.getKey();
            if (moduleKey == null || !moduleKey.startsWith("spear/")) {
                return;
            }
            VariantData variant = self.getVariantData(stack);
            if (variant == null || variant.key == null) {
                return;
            }
            RepairDefinition definition = TetraSpearRepairDefinitions.get(moduleKey, variant.key);
            if (definition != null) {
                cir.setReturnValue(List.of(definition));
            }
        } catch (Throwable ignored) {
            // 兼容层失败时退回 Tetra 原行为（没有维修定义）。
        }
    }
}
