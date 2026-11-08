package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让槊/三尖两刃刀这些「劈砍」矛头参与原版剑的横扫。
 *
 * <p>1.20.1 Forge 把 {@code Player#attack} 里的 {@code instanceof SwordItem} 换成了
 * {@code ItemStack#canPerformAction(ToolActions.SWORD_SWEEP)}；Tetra 的
 * {@code DynamicModularItem} 不是 {@code SwordItem}，不返回 true 就永远没有横扫粒子/音效，
 * 满蓄力也只有单体伤害。</p>
 *
 * <p>{@code canPerformAction} 是 {@code IForgeItemStack} 的接口默认方法，Mixin 不支持在接口里
 * 注入，所以改从调用点重定向：只改 {@code Player.attack} 里的那一次调用，其它地方仍走原逻辑。
 * 只对 slashing 矛头放行，普通矛头仍然只戳刺。</p>
 */
@Mixin(Player.class)
public abstract class PlayerSweepTetraMixin {

    @Redirect(method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z"))
    private boolean spearcore$spearSweepAction(ItemStack stack, ToolAction action) {
        if (action == ToolActions.SWORD_SWEEP) {
            SpearLike spear = SpearLikeSupport.of(stack);
            if (spear != null && spear.slashing()) {
                return true;
            }
        }
        return stack.canPerformAction(action);
    }
}
