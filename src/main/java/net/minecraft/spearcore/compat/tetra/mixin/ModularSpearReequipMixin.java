package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 模块化长矛命中后不再重播「掏出」动画。
 *
 * <p>命中时 {@code stack.hurtEnemy} / {@code hurtAndBreak} 会改 Tetra 模块耐久（存在 NBT），
 * Forge 的 {@code shouldCauseReequipAnimation} 因此把每次攻击都当成「物品变了」，重播装备动画。
 * 这里对同一个模块化矛栈直接返回 false；换物品/空手仍然正常播放。</p>
 */
@Mixin(targets = "net.minecraftforge.client.ForgeHooksClient", remap = false)
public class ModularSpearReequipMixin {

    @Inject(method = "shouldCauseReequipAnimation", at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void spearcore$skipModularSpearReequip(ItemStack oldStack, ItemStack newStack,
                                                          int slot, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (oldStack == null || newStack == null || oldStack.isEmpty() || newStack.isEmpty()
                    || oldStack.getItem() != newStack.getItem()) {
                return;
            }
            SpearLike spear = SpearLikeSupport.of(newStack);
            if (spear != null && spear.style() == SpearStyle.MODULAR_3D) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 兼容层失败只影响重装动画，不影响游戏。
        }
    }
}
