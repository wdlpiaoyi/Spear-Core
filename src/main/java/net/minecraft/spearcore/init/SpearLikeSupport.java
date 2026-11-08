package net.minecraft.spearcore.init;

import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * 从物品堆解析出「长矛行为」的统一入口。
 *
 * <p>本模组长矛直接返回自身；Tetra 工具走 {@link TetraSpearProfile}（没装 Tetra 时恒为 null，
 * 不会触发任何 Tetra 类加载）。所有战斗 / 渲染逻辑都应该通过这里判断，而不是继续
 * {@code instanceof SpearItem}。</p>
 */
public final class SpearLikeSupport {

    private SpearLikeSupport() {
    }

    @Nullable
    public static SpearLike of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof SpearItem spear) {
            return spear;
        }
        return TetraSpearProfile.of(stack);
    }

    public static boolean isSpearLike(ItemStack stack) {
        return of(stack) != null;
    }

    /**
     * 挥砍横扫：由 Tetra 的 {@code SweepingEffect} 实现。
     *
     * <p>{@code TetraSpearHooks} 会 import Tetra 类，所以这里先确认 Tetra 已加载；方法体里的
     * 类引用只在真正执行到时才解析，未装 Tetra 的客户端不会因此加载 Tetra。</p>
     */
    public static void tetraSlashSweep(Player player, ItemStack stack, LivingEntity target, int level) {
        if (!isTetraLoaded()) {
            return;
        }
        try {
            net.minecraft.spearcore.compat.tetra.TetraSpearHooks.slashSweep(player, stack, target, level);
        } catch (Throwable ignored) {
            // 兼容层失败只影响横扫，不影响本体重击。
        }
    }

    /** Tetra 的 {@code tetra:on_use} item_effects；仅模块化长矛使用。 */
    public static void tetraOnUse(ItemStack stack, LivingEntity user) {
        if (!isTetraLoaded()) {
            return;
        }
        try {
            net.minecraft.spearcore.compat.tetra.TetraSpearHooks.onUse(stack, user);
        } catch (Throwable ignored) {
        }
    }

    /** Tetra 的 {@code tetra:on_hit} item_effects；仅模块化长矛使用。 */
    public static void tetraOnHit(ItemStack stack, LivingEntity attacker, LivingEntity target) {
        if (!isTetraLoaded()) {
            return;
        }
        try {
            net.minecraft.spearcore.compat.tetra.TetraSpearHooks.onHit(stack, attacker, target);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isTetraLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded("tetra");
    }
}
