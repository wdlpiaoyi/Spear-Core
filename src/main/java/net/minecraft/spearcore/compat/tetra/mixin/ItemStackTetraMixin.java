package net.minecraft.spearcore.compat.tetra.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.spearcore.compat.tetra.TetraDamageScaling;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 MC 侧把 Tetra 的「投掷」语义改成本模组的「蓄力」语义。
 *
 * <p>为什么不直接 mixin {@code ItemModularHandheld}：发行版 jar 里它的 override 是 SRG 名
 * （{@code m_7203_} 等），Mixin 的 refmap 只覆盖 MC 类，映射不到模组自己类的 override，
 * 生产环境注入不可靠。而 {@link ItemStack} 的这几个方法都是 MC 方法，refmap 正常工作。</p>
 *
 * <p>只对 archetype 为 modular_spear 的模块化长矛生效；Tetra 自带的工具仍走 Tetra 自己的逻辑。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTetraMixin {

    /**
     * 属性查询的重入闸门。
     *
     * <p>TetraSpearBridge 取不到 {@code ENTITY_REACH} 时会回退去扫
     * {@code ItemStack#getAttributeModifiers(MAINHAND)} 的修饰符，而本类的打折逻辑正好注入在
     * 同一个方法的 RETURN：没有这个闸门就会无限递归直到 StackOverflowError，异常被兼容层
     * 的 {@code catch (Throwable)} 吞掉后，模块化长矛会直接失去蓄力/戳刺等全部行为。</p>
     */
    @Unique
    private static final ThreadLocal<Boolean> spearcore$resolvingAttributes =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 右键起手蓄力。
     *
     * <p>Tetra 自己的 {@code use()} 只有在 {@code getUseDuration() > 0} 时才会进入 using；
     * 它的矛头只提供 {@code throwable} aspect，不保证产生 {@code ItemEffect.throwable}，
     * 因此右键会落到「无动作」。这里按本模组语义直接起手，并取消 Tetra 的原逻辑。</p>
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraUse(Level level, Player player, InteractionHand hand,
                                    CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        SpearLike spear = spearLike();
        if (spear == null) {
            return;
        }
        cir.setReturnValue(SpearCombat.tryStartCharging(level, player, hand, (ItemStack) (Object) this, spear));
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraUseDuration(CallbackInfoReturnable<Integer> cir) {
        if (spearLike() != null) {
            cir.setReturnValue(72000);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraUseAnimation(CallbackInfoReturnable<UseAnim> cir) {
        if (spearLike() != null) {
            cir.setReturnValue(UseAnim.NONE);
        }
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraOnUseTick(Level level, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        SpearLike spear = spearLike();
        if (spear == null) {
            return;
        }
        if (!level.isClientSide) {
            SpearCombat.tickCharge(user, (ItemStack) (Object) this, spear);
        }
        ci.cancel();
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraReleaseUsing(Level level, LivingEntity user, int remainingUseTicks, CallbackInfo ci) {
        if (spearLike() != null) {
            ci.cancel();
        }
    }

    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraFinishUsingItem(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (spearLike() != null) {
            cir.setReturnValue((ItemStack) (Object) this);
        }
    }

    /**
     * Tetra 武器攻击力打折：只缩放 {@code ATTACK_DAMAGE} 的 {@code ADDITION} 修饰符，
     * tooltip 与伤害结算读同一份属性，因此面板与实际伤害一致。
     */
    @Inject(method = "getAttributeModifiers(Lnet/minecraft/world/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;", at = @At("RETURN"), cancellable = true)
    private void spearcore$tetraAttackDamage(EquipmentSlot slot,
                                             CallbackInfoReturnable<Multimap<Attribute, AttributeModifier>> cir) {
        if (spearcore$resolvingAttributes.get()) {
            // 重入：这次是兼容层自己在扫修饰符，跳过，避免自递归。
            return;
        }
        spearcore$resolvingAttributes.set(Boolean.TRUE);
        try {
            if (spearLike() == null) {
                return;
            }
            double scale = TetraDamageScaling.damageScale();
            if (scale >= 1.0D) {
                return;
            }
            cir.setReturnValue(TetraDamageScaling.scaleAttackDamage(cir.getReturnValue(), scale));
        } finally {
            spearcore$resolvingAttributes.set(Boolean.FALSE);
        }
    }

    /** 仅本模组的 Tetra 模块化长矛走这里；贴图长矛继续走 {@code SpearItem} 的原逻辑。 */
    private SpearLike spearLike() {
        SpearLike spear = SpearLikeSupport.of((ItemStack) (Object) this);
        return spear != null && spear.style() == SpearStyle.MODULAR_3D ? spear : null;
    }
}
