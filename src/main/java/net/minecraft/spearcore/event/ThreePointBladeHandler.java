package net.minecraft.spearcore.event;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearDamageTypes;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 三尖两刃刀的特殊伤害拆分。
 *
 * <p>以 {@link LivingDamageEvent} 的最终伤害（已经过护甲/附魔减免）为总量：
 * 50% 保持原样，20% 转成 {@code minecraft:out_of_world}，20% 转成 {@code minecraft:magic}，
 * 10% 直接扣血；另外对敌对生物按总量 3% 永久削减最大生命。</p>
 *
 * <p>原版伤害先按 50% 正常结算，剩余三种在同一个 tick 结束时补上，避免在
 * {@code LivingDamageEvent} 内递归调用 {@code hurt} 造成重复结算。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class ThreePointBladeHandler {

    public static final float NORMAL_SHARE = 0.50F;
    public static final float VOID_SHARE = 0.20F;
    public static final float MAGIC_SHARE = 0.20F;
    public static final float DIRECT_SHARE = 0.10F;
    public static final float MAX_HEALTH_SHARE = 0.03F;

    /** 同一个实体上的最大生命削减只用一个 modifier，重复命中累加。 */
    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.nameUUIDFromBytes(
            "spearcore:three_point_max_health".getBytes(StandardCharsets.UTF_8));

    /** 处理额外伤害时置位，避免额外伤害再次触发拆分。 */
    private static final ThreadLocal<Boolean> CONVERTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private static final List<Pending> PENDING = new ArrayList<>();

    private ThreePointBladeHandler() {
    }

    private record Pending(Player attacker, Entity target, float total) {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.isCanceled() || CONVERTING.get()) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (event.getSource().getDirectEntity() != player) return;

        SpearLike spear = activeSpear(player);
        if (spear == null || !spear.threePointBlade()) return;

        float total = event.getAmount();
        if (total <= 0.0F) return;

        // 正常伤害先留一半；另外一半在 tick 末尾按类型补。
        event.setAmount(total * NORMAL_SHARE);
        PENDING.add(new Pending(player, event.getEntity(), total));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;

        List<Pending> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        CONVERTING.set(Boolean.TRUE);
        try {
            for (Pending pending : batch) {
                applyPending(pending);
            }
        } finally {
            CONVERTING.set(Boolean.FALSE);
        }
    }

    private static SpearLike activeSpear(Player player) {
        ItemStack useItem = player.getUseItem();
        SpearLike spear = SpearLikeSupport.of(useItem);
        if (spear != null) return spear;
        return SpearLikeSupport.of(player.getMainHandItem());
    }

    private static void applyPending(Pending pending) {
        Player attacker = pending.attacker();
        Entity target = pending.target();
        if (attacker == null || target == null || target.isRemoved() || target.level().isClientSide()) {
            return;
        }
        LivingEntity living = SpearCombat.resolveLivingTarget(target);
        if (living == null || living.isDeadOrDying()) {
            return;
        }

        float total = pending.total();
        DamageSource voidSource = SpearDamageTypes.outOfWorld(target.level(), attacker);
        DamageSource magicSource = SpearDamageTypes.magic(target.level(), attacker);

        // 原版伤害已经结算了 50%；这里补 20% 虚空、20% 魔法。
        clearImmunity(attacker, living);
        target.hurt(voidSource, total * VOID_SHARE);
        clearImmunity(attacker, living);
        target.hurt(magicSource, total * MAGIC_SHARE);

        // 10% 直接扣血，绕过护甲/附魔/吸收。
        if (living.isAlive()) {
            float health = living.getHealth() - total * DIRECT_SHARE;
            living.setHealth(Math.max(0.0F, health));
            if (living.getHealth() <= 0.0F) {
                living.die(SpearDamageTypes.spear(target.level(), attacker));
            }
        }

        // 只有敌对生物削减最大生命；最低保留 1 点。
        if (living.isAlive()) {
            reduceMaxHealth(living, total);
        }
    }

    private static void clearImmunity(Player attacker, LivingEntity target) {
        if (target.invulnerableTime > 0 && target.getLastHurtByMob() == attacker) {
            target.invulnerableTime = 0;
        }
    }

    /**
     * 按本次总伤害的 3% 永久削减最大生命；重复命中累加，最低保留 1 点。
     * 只对 {@link Enemy} 生效。
     */
    public static void reduceMaxHealth(LivingEntity target, float totalDamage) {
        if (!(target instanceof Enemy) || !target.isAlive() || totalDamage <= 0.0F) {
            return;
        }
        AttributeInstance maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        double reduction = totalDamage * MAX_HEALTH_SHARE;
        double current = maxHealth.getValue();
        double allowed = Math.max(0.0D, current - 1.0D);
        double actual = Math.min(reduction, allowed);
        if (actual <= 0.0D) return;

        AttributeModifier old = maxHealth.getModifier(MAX_HEALTH_MODIFIER_ID);
        double oldAmount = old == null ? 0.0D : old.getAmount();
        if (old != null) {
            maxHealth.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
        maxHealth.addPermanentModifier(new AttributeModifier(
                MAX_HEALTH_MODIFIER_ID,
                "spearcore_three_point_max_health",
                oldAmount - actual,
                AttributeModifier.Operation.ADDITION));

        if (target.getHealth() > target.getMaxHealth()) {
            target.setHealth(target.getMaxHealth());
        }
    }
}
