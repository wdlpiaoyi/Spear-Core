package net.minecraft.spearcore.compat.tetra.mixin;

import java.util.Map;
import net.minecraft.spearcore.init.SpearEnchantments;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Tetra 模块化长矛接受本模组允许的附魔（原版白名单 + 模组武器附魔）。
 *
 * <p>Tetra 的 {@code ModularItem#canApplyAtEnchantingTable} 按 aspect 过滤附魔，矛头只有
 * {@code pointy_weapon}，武器类附魔会被挡掉。这个方法由 Forge 添加、名字不带 SRG，
 * 因此可以安全地用 {@code remap = false} 注入；普通 Tetra 剑/镐不会走到这个分支。</p>
 *
 * <p>{@code isBookEnchantable} 同理：Tetra 的 {@code ModularItem} 直接返回 {@code false}，
 * 于是所有模块化物品都不能吃附魔书。本模组的贴图长矛是普通物品、本来就能吃，两条矛不该有两种规则，
 * 所以对模块化长矛放行；书上的附魔必须<i>全部</i>通过 {@link SpearEnchantments#supportsSpearEnchantment}，
 * 横扫之刃这类本来就不该给的附魔仍会被挡下。</p>
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

    /** 附魔书可以在铁砧上打给模块化长矛；书里只要混进一条本模组不支持的附魔就保持 Tetra 的原行为。 */
    @Inject(method = "isBookEnchantable", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$allowBookEnchantments(ItemStack stack, ItemStack book,
                                                 CallbackInfoReturnable<Boolean> cir) {
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null || spear.style() != SpearStyle.MODULAR_3D) {
            return;
        }
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(book);
        if (enchantments.isEmpty()) {
            return;
        }
        for (Enchantment enchantment : enchantments.keySet()) {
            if (!SpearEnchantments.supportsSpearEnchantment(enchantment)) {
                return;
            }
        }
        cir.setReturnValue(true);
    }
}
