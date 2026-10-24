package net.minecraft.spearcore.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.spearcore.item.SpearItem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 举矛时取消原版"正在使用物品 → 移动输入 ×0.2"的减速。
 *
 * <p><b>为什么用 {@code @WrapOperation} 而不是 {@code @ModifyConstant}：</b>
 * 原版这段字节码是 {@code getfield leftImpulse; ldc 0.2F; fmul; putfield leftImpulse}
 * （forwardImpulse 同理）。{@code @ModifyConstant} 改的是那个 0.2F 字面量，
 * 而"疾跑类/使用物品类"模组也常盯着它 —— 同一常量多个 handler 只能按 priority
 * 排序链式执行，谁最后谁说了算，属于会互相覆盖的写法。</p>
 *
 * <p>{@code @WrapOperation}（MixinExtras）包裹的是"写回字段"这个动作本身，
 * 多个模组各自的 WrapOperation 会<b>链式</b>执行、互不覆盖。我们在写入前读到
 * {@code input} 里尚未被乘的旧值并原样写回，等于跳过这次减速；没举矛时照常放行。</p>
 *
 * <p>1.21.1 由 NeoForge 自带 MixinExtras；1.20.1 由本模组通过 jarJar 内嵌
 * {@code mixinextras-forge}。</p>
 *
 * <p><b>兜底：</b>如果冲突方用 {@code @Overwrite} 整个重写 aiStep，本 mixin 仍会失效，
 * 那时由 {@code SpearSprintHandler}（客户端 tick 事件）保证举矛能疾跑。</p>
 */
@Mixin(value = LocalPlayer.class, priority = 2000)
public class LocalPlayerInputMixin {

    @WrapOperation(method = "aiStep",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/Input;leftImpulse:F",
                    opcode = Opcodes.PUTFIELD))
    private void spearcore$keepLeftImpulse(Input input, float value, Operation<Void> original) {
        original.call(input, spearcore$unslowed(input.leftImpulse, value));
    }

    @WrapOperation(method = "aiStep",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/Input;forwardImpulse:F",
                    opcode = Opcodes.PUTFIELD))
    private void spearcore$keepForwardImpulse(Input input, float value, Operation<Void> original) {
        original.call(input, spearcore$unslowed(input.forwardImpulse, value));
    }

    /** 举矛时写回"乘 0.2 之前"的原值（此刻 input 字段里还是旧值）；否则用原版算出来的值。 */
    /**
     * 举矛时允许起跑。
     *
     * <p>原版那个"使用物品 → 移动输入 ×0.2"的分支里还有一句 {@code sprintTriggerTime = 0}，
     * 它会让"双击 W / 按冲刺键"都无法起跑。这是在源头放行，而不是每 tick 事后 setSprinting(true)
     * —— 参考 Backported Spears 的做法。</p>
     *
     * <p>{@code require = 0}：万一某个版本没有这个方法，注入静默跳过而不是崩；
     * 那种情况下由 {@code SpearSprintHandler} 兜底。</p>
     */
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true, require = 0)
    private void spearcore$allowSprintWhileUsingSpear(CallbackInfoReturnable<Boolean> cir) {
        if (spearcore$isUsingSpear(Minecraft.getInstance().player)) {
            cir.setReturnValue(true);
        }
    }

    private static float spearcore$unslowed(float previous, float multiplied) {
        return spearcore$isUsingSpear(Minecraft.getInstance().player) ? previous : multiplied;
    }

    private static boolean spearcore$isUsingSpear(LocalPlayer player) {
        return player != null
                && player.isUsingItem()
                && !player.getUseItem().isEmpty()
                && player.getUseItem().getItem() instanceof SpearItem;
    }
}