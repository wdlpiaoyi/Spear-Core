package net.minecraft.spearcore.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.spearcore.item.SpearItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 举矛时取消原版"正在使用物品 → 移动输入 ×0.2"的减速。
 *
 * <p><b>为什么这里只能用 @ModifyConstant（不是不想换）：</b>
 * 1.21.1 分支用 MixinExtras 的 {@code @WrapOperation} 包住 PUTFIELD，不会和别的模组抢常量。
 * 1.20.1 走不通：
 * <ul>
 *   <li>{@code @Redirect} 也能包 PUTFIELD，但 FG 的 Mixin 注解处理器<b>不生成</b> {@code @At} 字段目标的
 *       refmap 映射（实测：refmap 里只有 aiStep 的方法映射，没有 f_108566_/f_108567_）。
 *       正式环境跑的是 SRG 名，没有映射就等于注入失败 —— 比 @ModifyConstant 更糟。</li>
 *   <li>{@code @WrapOperation} 需要 MixinExtras 在运行时存在，而 Forge 1.20.1 不自带，
 *       内嵌方案（jarJar / shading）在 FG6 下还没跑通。</li>
 * </ul>
 * 所以这里保留"改常量 + 提高 priority"，并接受 issue #3 描述的风险。</p>
 *
 * <p><b>issue #3 的风险（未解决，需与玩家说明）：</b>
 * {@code @ModifyConstant} 对同一条常量只允许一个模组生效。若别的模组也对 aiStep 里的 0.2F 做
 * {@code @ModifyConstant}（例如 Tensura 的 useItemFirst），对方会扫不到注入目标，抛
 * {@code InjectionError: Critical injection failure ... (0/1) succeeded} 导致启动崩溃。
 * 这个和 priority 无关 —— 常量被重写后第二个模组就是找不到目标。</p>
 *
 * <p><b>require = 0：</b>万一常量被别人改掉，我们静默失效、不崩；此时举矛仍能疾跑由
 * {@code SpearSprintHandler}（客户端 tick 事件）兜底。</p>
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
