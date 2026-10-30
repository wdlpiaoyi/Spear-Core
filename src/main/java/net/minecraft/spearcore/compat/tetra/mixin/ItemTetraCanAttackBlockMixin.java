package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Tetra 长矛拥有原版剑的「创造模式不可破坏方块」语义。
 *
 * <p>创造模式下，{@code MultiPlayerGameMode} 在起手挖方块前会问
 * {@code Item#canAttackBlock}，返回 false 就不发送挖掘动作。原版 {@code SwordItem}
 * 这里返回 {@code !player.isCreative()}，而 Tetra 的模块化工具返回 true，于是 Tetra 长矛
 * 会在创造模式左键时瞬破方块，还会触发 WieldYourPower 的「强制破坏」排队。</p>
 *
 * <p>给本模组的目标（长手柄 + 矛头）补上同一条剑类语义即可，不需要全局取消左键。</p>
 */
@Mixin(Item.class)
public abstract class ItemTetraCanAttackBlockMixin {

    @Inject(method = "canAttackBlock", at = @At("HEAD"), cancellable = true)
    private void spearcore$tetraCanAttackBlock(BlockState state, Level level, BlockPos pos, Player player,
                                               CallbackInfoReturnable<Boolean> cir) {
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() != (Item) (Object) this) {
            return;
        }
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null || spear.style() != SpearStyle.MODULAR_3D) {
            return;
        }
        cir.setReturnValue(!player.isCreative());
    }
}
