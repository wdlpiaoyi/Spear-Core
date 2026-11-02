package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.init.SpearEnchantments;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Tetra 单头长矛接受本模组允许的附魔（原版白名单 + 模组武器附魔）。
 *
 * <p>Tetra 的 {@code ModularItem#canApplyAtEnchantingTable} 按 aspect 过滤附魔，矛头只有
 * {@code pointy_weapon}，武器类附魔会被挡掉。这个方法由 Forge 添加、名字不带 SRG，
 * 因此可以安全地用 {@code remap = false} 注入；普通 Tetra 剑/镐不会走到这个分支。</p>
 */
@Mixin(targets = "se.mickelus.tetra.items.modular.ModularItem", remap = false)
public abstract class ModularItemTetraEnchantMixin {

    @Inject(method = "canApplyAtEnchantingTable", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$allowWeaponEnchantments(ItemStack stack, Enchantment enchantment,
                                                   CallbackInfoReturnable<Boolean> cir) {
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear != null && spear.style() == SpearStyle.MODULAR_3D
                && SpearEnchantments.supportsSpearEnchantment(enchantment)) {
            cir.setReturnValue(true);
        }
    }
}
