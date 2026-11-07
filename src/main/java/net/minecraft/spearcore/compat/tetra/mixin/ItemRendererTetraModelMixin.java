package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.spearcore.client.TetraSpearModel;
import net.minecraft.spearcore.compat.tetra.TetraSpearColors;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把本模组的 Tetra 模块化长矛换成 assets 里的贴图长矛模型。
 *
 * <p>按矛头槽当前的变体 key 选模型：普通矛头用 {@code spearcore:tetra_spear}，槊头用
 * {@code spearcore:tetra_spear_shuo}，三尖两刃刀用 {@code spearcore:tetra_spear_three}。
 * 三个模型只有头部那一层贴图不同，杆贴图共用（头部贴图盖住杆，所以换头不需要重画杆）。
 * 只替换 {@link SpearStyle#MODULAR_3D} 的栈；材质颜色由物品颜色处理器（tint 0/1）提供，
 * 因此仍会按 Tetra 材料换色。Tetra 自带的工具（包括单头工具）不满足 archetype 判定，保持原样。</p>
 *
 * <p>注入点必须带 {@code require = 0}：Tetra 版本差异或其它模组的 ItemRenderer mixin 可能改签名，
 * 失败时保留原模型而不是让游戏崩。</p>
 */
@Mixin(ItemRenderer.class)
public class ItemRendererTetraModelMixin {

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearcore$tetraSpearModel(ItemStack stack, Level level, LivingEntity entity, int seed,
                                           CallbackInfoReturnable<BakedModel> cir) {
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null || spear.style() != SpearStyle.MODULAR_3D) {
            return;
        }
        String headVariant = TetraSpearColors.headVariantKey(stack);
        BakedModel replacement;
        if (headVariant != null && headVariant.startsWith("spear_shuo_head/")) {
            replacement = TetraSpearModel.getShuo();
        } else if (headVariant != null && headVariant.startsWith("spear_three_point/")) {
            replacement = TetraSpearModel.getThree();
        } else {
            replacement = TetraSpearModel.getDefault();
        }
        if (replacement != null) {
            cir.setReturnValue(replacement);
        }
    }
}
