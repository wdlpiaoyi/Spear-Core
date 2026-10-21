package net.minecraft.spearcore.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.spearcore.item.SpearItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 举矛时取消原版"正在使用物品 → 移动输入 ×0.2"的减速。
 *
 * <p><b>为什么这里是 @ModifyConstant 而不是 MixinExtras 的 @WrapOperation：</b>
 * 1.21.1 那边用的是 @WrapOperation（可叠加、不会与别的模组互相覆盖），但 1.20.1 受两个
 * 现实条件限制：① Forge 1.20.1 不自带 MixinExtras，整合包里可能已有别的模组带了旧版本
 * （实测目标整合包内是 0.2.0-rc.2），运行时版本不由我们控制；② FG6 的 jarJar 在本工程里
 * 不产出合并 jar，手写 shading 又要额外接好 MixinExtras 的初始化配置。
 * 为了不给发布版引入崩溃风险，这里保留"改常量 + 提高 priority"的保守写法。</p>
 *
 * <p><b>priority = 2000：</b>同一个常量的多个 @ModifyConstant 按 priority 排序链式执行，
 * 最后执行的那个决定最终值。默认是 1000，提到 2000 可以保证我们的 1.0F 最后落地，
 * 不会被别的模组改回 0.2 或其它值。</p>
 *
 * <p><b>require = 0：</b>对方把 0.2F 常量重构掉时本注入静默失效、不会崩；
 * 那种情况下由 {@code SpearSprintHandler}（客户端 tick 事件）保证举矛仍能疾跑。</p>
 */
@Mixin(value = LocalPlayer.class, priority = 2000)
public class LocalPlayerInputMixin {

    @ModifyConstant(method = "aiStep", constant = @Constant(floatValue = 0.2F), require = 0)
    private float spearcore$disableUseItemSlowdownForSpear(float original) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        return spearcore$isUsingSpear(self) ? 1.0F : original;
    }

    private static boolean spearcore$isUsingSpear(LocalPlayer player) {
        return player.isUsingItem()
                && !player.getUseItem().isEmpty()
                && player.getUseItem().getItem() instanceof SpearItem;
    }
}
