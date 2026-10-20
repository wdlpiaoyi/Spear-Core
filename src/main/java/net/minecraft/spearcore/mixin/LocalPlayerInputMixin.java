package net.minecraft.spearcore.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.spearcore.item.SpearItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 举矛时取消原版"正在使用物品 → 移动输入 ×0.2"的减速。
 *
 * <p><b>为什么 priority 是 2000：</b>疾跑类/使用物品类模组常常也改
 * LocalPlayer.aiStep 里的这个 0.2F。Mixin 对同一个常量的多个
 * {@code @ModifyConstant} 是按 priority 排序链式执行的，最后执行的那个决定最终值。
 * 默认 priority 是 1000，这里提到 2000，保证我们的 1.0F 最后落地，不会被别人的
 * 0.2F/固定值覆盖。</p>
 *
 * <p><b>注意：</b>如果冲突方用 {@code @Overwrite} 整个重写 aiStep，本 mixin 会失效，
 * 那种情况下由 {@code SpearSprintHandler}（ClientTickEvent）兜底，保证举矛仍能疾跑。</p>
 *
 * <p>原先"疾跑断言"的 {@code @Inject(at = @At("TAIL"))} 已迁到
 * {@link net.minecraft.spearcore.client.SpearSprintHandler}，原因同上：不依赖 aiStep 就不会被同目标模组搞掉。</p>
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
