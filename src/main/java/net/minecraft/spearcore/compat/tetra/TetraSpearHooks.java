package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.effect.SweepingEffect;
import se.mickelus.tetra.effect.data.DataEffectsHandler;

/**
 * 模块化长矛与 Tetra 效果系统的桥：挥砍横扫、item_effects 触发。
 *
 * <p>这个类会 import Tetra 类，因此只在确认物品是模块化长矛（{@code SpearStyle.MODULAR_3D}）之后
 * 由 {@code SpearLikeSupport} 懒加载调用；未装 Tetra 时永远不会被加载。</p>
 */
public final class TetraSpearHooks {

    private static boolean loggedFailure;

    private TetraSpearHooks() {
    }

    /**
     * 挥砍命中后的横扫（自带横扫之刃 III）。
     *
     * @param level Tetra 的横扫等级；3 对应原版横扫之刃 III 的伤害/范围
     */
    public static void slashSweep(Player player, ItemStack stack, LivingEntity target, int level) {
        try {
            SweepingEffect.sweepAttack(stack, player, target, level);
        } catch (Throwable throwable) {
            warn(throwable);
        }
    }

    /** 右键起手时触发 Tetra 的 {@code tetra:on_use} 类 item_effects。 */
    public static void onUse(ItemStack stack, LivingEntity user) {
        try {
            DataEffectsHandler.applyOnUseEffects(stack, user);
        } catch (Throwable throwable) {
            warn(throwable);
        }
    }

    /** 命中后触发 Tetra 的 {@code tetra:on_hit} 类 item_effects。 */
    public static void onHit(ItemStack stack, LivingEntity attacker, LivingEntity target) {
        try {
            DataEffectsHandler.applyOnHitEffects(stack, attacker, target);
        } catch (Throwable throwable) {
            warn(throwable);
        }
    }

    private static void warn(Throwable throwable) {
        if (!loggedFailure) {
            loggedFailure = true;
            SpearcoreMod.LOGGER.warn("[spearcore] Tetra 效果钩子调用失败，本次启动将跳过这些效果", throwable);
        }
    }
}
