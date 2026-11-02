package net.minecraft.spearcore.mixin;

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
 * 让 Tetra 单头长矛能附上本模组支持的原版附魔（锋利、击退、亡灵杀手等）。
 *
 * <p>Tetra 的 {@code ModularItem#canApplyAtEnchantingTable} 按 aspect 过滤附魔，矛头只有
 * {@code pointy_weapon}，没有 {@code edged_weapon}，所以原版武器附魔全被挡掉；这是 Tetra 自己的设计。
 * 本模组选择在 {@link Enchantment#canApplyAtEnchantingTable(ItemStack)} 的 HEAD 放行：
 * 只对「本模组白名单附魔 + 识别为 Tetra 长矛的栈」返回 true，其它情况原样交还 Tetra/原版。</p>
 *
 * <p>注意：`canApplyAtEnchantingTable` 是 Forge 后加的方法，没有 SRG 映射，必须 {@code remap = false}。</p>
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentTetraCompatMixin {

    @Inject(method = "canApplyAtEnchantingTable", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$allowVanillaEnchantmentsOnTetraSpears(ItemStack stack,
                                                                 CallbackInfoReturnable<Boolean> cir) {
        // 先确认是本模组识别的 Tetra 长矛栈，再问附魔是否放行：
        // 顺序反过来会让每次附魔台查询（包括非长矛物品）都触发模组探针逻辑。
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null || spear.style() != SpearStyle.MODULAR_3D) {
            return;
        }
        if (SpearEnchantments.supportsSpearEnchantment((Enchantment) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
