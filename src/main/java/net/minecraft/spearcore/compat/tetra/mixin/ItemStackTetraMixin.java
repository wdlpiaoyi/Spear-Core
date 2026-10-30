package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
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
 * <p>只对「长手柄 + 矛头」的 Tetra 单头工具生效；普通 Tetra 工具仍走 Tetra 自己的逻辑。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTetraMixin {

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

    /** 仅 Tetra 的 3D 单头工具走这里；本模组自己的贴图矛继续走 {@code SpearItem} 的原逻辑。 */
    private SpearLike spearLike() {
        SpearLike spear = SpearLikeSupport.of((ItemStack) (Object) this);
        return spear != null && spear.style() == SpearStyle.MODULAR_3D ? spear : null;
    }
}
