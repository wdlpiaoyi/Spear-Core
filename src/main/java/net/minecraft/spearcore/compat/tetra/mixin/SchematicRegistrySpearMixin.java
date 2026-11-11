package net.minecraft.spearcore.compat.tetra.mixin;

import java.util.Arrays;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.ItemModuleMajor;
import se.mickelus.tetra.module.SchematicRegistry;
import se.mickelus.tetra.module.schematic.CraftingContext;
import se.mickelus.tetra.module.schematic.UpgradeSchematic;

/**
 * 让「复合矛杆·完成」在 stage 2 但稳定打磨不足时仍然出现在工作台列表里。
 *
 * <p>Tetra 的工作台用 {@code getSchematics(CraftingContext)}，它会过滤掉
 * {@code matchesRequirements} 为 false 的配方，所以玩家看不到「还差稳定 V」的提示。
 * 这里只在 stage 2 的复合矛杆上把完成配方补回列表；{@code canApplyUpgrade} 仍会检查
 * 稳定 V 并禁用合成按钮，因此不会真的提前安装。</p>
 */
@Mixin(targets = "se.mickelus.tetra.module.SchematicRegistry", remap = false)
public abstract class SchematicRegistrySpearMixin {

    @Inject(method = "getSchematics(Lse/mickelus/tetra/module/schematic/CraftingContext;)[Lse/mickelus/tetra/module/schematic/UpgradeSchematic;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void spearcore$showFinishWhenHoneMissing(CraftingContext context,
                                                             CallbackInfoReturnable<UpgradeSchematic[]> cir) {
        try {
            if (context == null || context.targetStack == null || context.targetStack.isEmpty()
                    || context.slot == null || !"spear/handle".equals(context.slot)) {
                return;
            }
            ItemStack stack = context.targetStack;
            if (!(stack.getItem() instanceof DynamicModularItem)
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return;
            }
            IModularItem item = (IModularItem) stack.getItem();
            ItemModule module = item.getModuleFromSlot(stack, "spear/handle");
            if (!(module instanceof ItemModuleMajor major)
                    || !"spear/composite_shaft".equals(module.getKey())
                    || major.getImprovementLevel(stack, "spear_composite/stage") != 2) {
                return;
            }
            UpgradeSchematic finish = SchematicRegistry.getSchematic("spear/composite_shaft_finish");
            if (finish == null) {
                return;
            }
            UpgradeSchematic[] current = cir.getReturnValue();
            if (current == null) {
                cir.setReturnValue(new UpgradeSchematic[]{finish});
                return;
            }
            if (Arrays.stream(current).anyMatch(s -> s == finish)) {
                return;
            }
            UpgradeSchematic[] result = Arrays.copyOf(current, current.length + 1);
            result[current.length] = finish;
            cir.setReturnValue(result);
        } catch (Throwable ignored) {
            // 兼容层失败时保持 Tetra 原列表。
        }
    }
}
