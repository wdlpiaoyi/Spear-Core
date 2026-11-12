package net.minecraft.spearcore.init;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.world.entity.LivingEntity;
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

    /** 每个线程最近一次 Tetra 解析结果；渲染/护盾 tick 会重复查同一个栈。 */
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);

    private static final class Cache {
        private ItemStack stack;
        private int tagHash;
        private int damage;
        private float hitboxMargin;
        private SpearLike spear;
    }

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
        // Tetra 的数值完全由 NBT 模块决定；用「同一个栈对象 + NBT 哈希 + 耐久」做键，
        // NBT 原地修改后哈希会变，避免返回过期数值。线程内缓存，不跨客户端/服务端共享。
        Cache cache = CACHE.get();
        int tagHash = stack.getTag() == null ? 0 : stack.getTag().hashCode();
        int damage = stack.getDamageValue();
        // chargeHitboxMargin 是运行期配置，TetraSpearStats 在解析时就会读它；配置热重载后必须失效。
        float hitboxMargin = SpearRules.hitboxMargin(0.25F);
        if (cache.stack == stack && cache.tagHash == tagHash && cache.damage == damage
                && cache.hitboxMargin == hitboxMargin) {
            return cache.spear;
        }
        SpearLike spear = TetraSpearProfile.of(stack);
        cache.stack = stack;
        cache.tagHash = tagHash;
        cache.damage = damage;
        cache.hitboxMargin = hitboxMargin;
        cache.spear = spear;
        return spear;
    }

    public static boolean isSpearLike(ItemStack stack) {
        return of(stack) != null;
    }

    /** Tetra 的 {@code tetra:on_use} item_effects；仅模块化长矛使用。 */
    public static void tetraOnUse(ItemStack stack, LivingEntity user) {
        if (!isTetraLoaded()) {
            return;
        }
        try {
            net.minecraft.spearcore.compat.tetra.TetraSpearHooks.onUse(stack, user);
        } catch (Throwable throwable) {
            warnTetraHookFailure(throwable);
        }
    }

    /** Tetra 的 {@code tetra:on_hit} item_effects；仅模块化长矛使用。 */
    public static void tetraOnHit(ItemStack stack, LivingEntity attacker, LivingEntity target) {
        if (!isTetraLoaded()) {
            return;
        }
        try {
            net.minecraft.spearcore.compat.tetra.TetraSpearHooks.onHit(stack, attacker, target);
        } catch (Throwable throwable) {
            warnTetraHookFailure(throwable);
        }
    }

    private static boolean tetraHookFailureLogged;

    private static void warnTetraHookFailure(Throwable throwable) {
        if (!tetraHookFailureLogged) {
            tetraHookFailureLogged = true;
            SpearcoreMod.LOGGER.warn("[spearcore] Tetra 效果钩子调用失败，本次启动将跳过这些效果", throwable);
        }
    }

    private static boolean isTetraLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded("tetra");
    }
}
