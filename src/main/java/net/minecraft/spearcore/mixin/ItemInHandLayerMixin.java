package net.minecraft.spearcore.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.spearcore.client.animation.SpearAnimations;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 长矛第三人称手持/蓄力/挥砍姿势。
 *
 * <p>不再 HEAD + cancel 重写整段渲染：只在原版 {@code renderArmWithItem} 调用
 * {@code ItemInHandRenderer#renderItem} 之前，把额外的攻击/使用姿态叠到原版已经摆好的
 * 手部 PoseStack 上。这样其他 mod 对 {@code renderArmWithItem} 的 HEAD/TAIL 注入仍会执行。</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {

    public ItemInHandLayerMixin(RenderLayerParent<T, M> renderLayerParent) {
        super(renderLayerParent);
    }

    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
                    shift = At.Shift.BEFORE))
    private void spearcore$applySpearArmPose(LivingEntity livingEntity, ItemStack itemStack,
                                             ItemDisplayContext displayContext, HumanoidArm humanoidArm,
                                             PoseStack poseStack, MultiBufferSource buffer, int light,
                                             CallbackInfo ci) {
        SpearLike spear = SpearLikeSupport.of(itemStack);
        if (spear == null) return;

        // 挥砍攻击交给原版物品层渲染（普通剑的挥动）；蓄力/格挡等姿态仍走自定义。
        if (spear.slashing() && this.getParentModel().attackTime > 0.0F) {
            return;
        }

        float attackTime = this.getParentModel().attackTime;
        if (attackTime > 0.0F && livingEntity.getMainArm() == humanoidArm) {
            SpearAnimations.thirdPersonAttackItem(attackTime, poseStack, livingEntity);
        }

        float useTicks = livingEntity.getTicksUsingItem();
        if (useTicks != 0.0F && isUsedArm(livingEntity, humanoidArm)) {
            SpearAnimations.thirdPersonUseItem(attackTime, poseStack, useTicks,
                    humanoidArm, itemStack, livingEntity, Minecraft.getInstance().getPartialTick());
        }
    }

    private static boolean isUsedArm(LivingEntity livingEntity, HumanoidArm humanoidArm) {
        InteractionHand usedHand = livingEntity.getUsedItemHand();
        HumanoidArm usedArm = usedHand == InteractionHand.MAIN_HAND
                ? livingEntity.getMainArm()
                : livingEntity.getMainArm().getOpposite();
        return usedArm == humanoidArm;
    }
}
