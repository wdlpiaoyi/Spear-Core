package net.minecraft.spearcore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.spearcore.item.SpearItem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 举矛时取消原版"正在使用物品 → 移动输入 ×0.2"的减速。
 *
 * <p><b>为什么从 @ModifyConstant 换成 MixinExtras @WrapOperation（issue #3）：</b>
 * {@code @ModifyConstant} 对同一条常量只允许一个模组生效。我们抢先改掉 aiStep 里的 0.2F 后，
 * 其它模组（例如 Tensura 的 useItemFirst）对同一常量的 {@code @ModifyConstant} 会找不到注入目标，
 * 抛 {@code InjectionError: Critical injection failure ... (0/1) succeeded} 直接启动崩溃，
 * 且与 priority 无关。</p>
 *
 * <p>{@code @WrapOperation} 包裹的是 {@code input.leftImpulse = ...} 这个写回动作，不碰常量本身：
 * <ul>
 *   <li>别的模组仍可自由 {@code @ModifyConstant} 那条 0.2F；多个 {@code @WrapOperation} 会链式执行；</li>
 *   <li>写回前读到的 {@code input.leftImpulse} 就是未被乘过的旧值，直接写回去即可；</li>
 *   <li>MixinExtras 由 shading 内嵌进 jar（Forge 1.20.1 不自带），初始化 config 在 build.gradle 里注册。</li>
 * </ul></p>
 */
@Mixin(value = LocalPlayer.class, priority = 2000)
public class LocalPlayerInputMixin {

    @WrapOperation(method = "aiStep",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/Input;leftImpulse:F",
                    opcode = Opcodes.PUTFIELD))
    private void spearcore$keepLeftImpulse(Input input, float value, Operation<Void> original) {
        original.call(input, spearcore$isUsingSpear((LocalPlayer) (Object) this) ? input.leftImpulse : value);
    }

    @WrapOperation(method = "aiStep",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/Input;forwardImpulse:F",
                    opcode = Opcodes.PUTFIELD))
    private void spearcore$keepForwardImpulse(Input input, float value, Operation<Void> original) {
        original.call(input, spearcore$isUsingSpear((LocalPlayer) (Object) this) ? input.forwardImpulse : value);
    }

    private static boolean spearcore$isUsingSpear(LocalPlayer player) {
        return player.isUsingItem()
                && !player.getUseItem().isEmpty()
                && player.getUseItem().getItem() instanceof SpearItem;
    }
}
