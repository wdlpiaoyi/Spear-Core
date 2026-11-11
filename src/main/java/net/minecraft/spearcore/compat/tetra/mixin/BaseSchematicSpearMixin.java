package net.minecraft.spearcore.compat.tetra.mixin;

import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.ItemModuleMajor;
import se.mickelus.tetra.module.schematic.BaseSchematic;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

/**
 * 「复合矛杆·完成」会被 {@code SchematicRegistrySpearMixin} 补回工作台列表用于提示，
 * 但 {@code BaseSchematic.canApplyUpgrade} 本身不检查 schematic requirement，
 * 所以必须在这里单独禁用：稳定打磨不到 V 时合成按钮不可用。
 */
@Mixin(targets = "se.mickelus.tetra.module.schematic.BaseSchematic", remap = false)
public abstract class BaseSchematicSpearMixin {

    @Inject(method = "canApplyUpgrade", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$blockUnstableFinish(Player player, ItemStack targetStack, ItemStack[] materials,
                                               String slot, Map<ToolAction, Integer> tools,
                                               CallbackInfoReturnable<Boolean> cir) {
        try {
            UpgradeSchematic schematic = (UpgradeSchematic) (Object) this;
            String schematicKey = schematic.getKey();
            if (!"spear/composite_shaft_finish".equals(schematicKey)
                    && !"tetra:spear/composite_shaft_finish".equals(schematicKey)
                    || targetStack == null || targetStack.isEmpty()
                    || !(targetStack.getItem() instanceof IModularItem item)) {
                return;
            }
            ItemModule module = item.getModuleFromSlot(targetStack, "spear/handle");
            if (module instanceof ItemModuleMajor major
                    && major.getImprovementLevel(targetStack, "spear_hone/stable") < 5) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 兼容层失败时退回 Tetra 原判定。
        }
    }
}
