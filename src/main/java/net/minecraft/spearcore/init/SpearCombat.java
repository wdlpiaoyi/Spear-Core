package net.minecraft.spearcore.init;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.spearcore.event.SpearChargePhaseEvent;
import net.minecraft.spearcore.event.SpearDamageEvent;
import net.minecraft.spearcore.event.SpearHitEvent;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.util.SpearCooldownAccessor;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.spearcore.util.UseSpearSpecialEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;

import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * 长矛的战斗流程（戳刺 / 蓄力）。
 *
 * <p>原先这些逻辑写在 {@link SpearItem} 的实例方法里，只有本模组自己的长矛能用。
 * 为了让 Tetra 的单头工具（长手柄 + 矛头）复用同一套手感，这里改成静态实现，
 * 通过 {@link SpearLike} 读取数值 —— 两边走的是同一份代码，而不是复制出来的近似版本。</p>
 */
public final class SpearCombat {

    /** 蓄力阶段缓存，用于阶段变更检测。 */
    private static final WeakHashMap<LivingEntity, SpearChargePhaseEvent.Phase> PHASE_CACHE =
            new WeakHashMap<>();

    private SpearCombat() {
    }

    // ========== 蓄力 tick ==========

    public static void tickCharge(LivingEntity user, ItemStack stack, SpearLike spear) {
        Level level = user.level();
        if (level.isClientSide) return;

        if (user instanceof Player player && player.getCooldowns().isOnCooldown(stack.getItem())) {
            player.stopUsingItem();
            return;
        }

        int usedTicks = stack.getUseDuration() - user.getUseItemRemainingTicks();
        SpearChargePhaseEvent.Phase phase = currentPhase(spear, usedTicks);
        if (PHASE_CACHE.get(user) != phase) {
            PHASE_CACHE.put(user, phase);
            MinecraftForge.EVENT_BUS.post(new SpearChargePhaseEvent(user, phase));
        }
        if (usedTicks < spear.getDelayTicks()) return;
        int effectiveTicks = usedTicks - spear.getDelayTicks();

        if (usedTicks >= spear.getDamageEndTick()) {
            user.stopUsingItem();
            return;
        }

        Vec3 look = user.getLookAngle();
        double attackerSpeed = look.dot(SpearItem.getMotion(user));

        // J库 needSpeed 逻辑
        float needSpeed = user instanceof Player ? 1.0f : 0.2f;
        if (user instanceof UseSpearSpecialEntity u) {
            needSpeed = u.getJerotesSpearNeedSpeed();
        }

        double baseDamage = chargeBaseDamage(user, spear);

        boolean hitSomething = false;

        for (EntityHitResult hit : SpearItem.getHitEntitiesAlong(user, spear, spear.getHitboxMargin(),
                entity -> SpearItem.canHitEntity(user, entity))) {
            Entity target = hit.getEntity();
            if (!(target instanceof LivingEntity)) continue;

            // 蓄力重复命中：JAVA 每个目标每次蓄力只结算一次；BEDROCK 用 10 tick 接触冷却。
            if (user instanceof SpearCooldownAccessor accessor
                    && accessor.WasRecentlyStabbed(target,
                    SpearRules.chargeRepeatCooldownTicks(spear.getContactCooldownTicks()))) {
                continue;
            }

            double targetSpeed = look.dot(SpearItem.getMotion(target));
            double relSpeed = Math.max(0.0, attackerSpeed - targetSpeed);
            if (user instanceof UseSpearSpecialEntity u) {
                relSpeed *= u.getJerotesSpearDamageMultiple();
            }

            boolean canDismount = spear.dismounts() && spear.getDismountConditions().isPresent()
                    && spear.getDismountConditions().get().test(effectiveTicks, attackerSpeed, relSpeed, needSpeed);
            boolean canKnockback = spear.dealsKnockback() && spear.getKnockbackConditions().isPresent()
                    && spear.getKnockbackConditions().get().test(effectiveTicks, attackerSpeed, relSpeed, needSpeed);
            boolean canDamage = spear.getDamageConditions().isPresent()
                    && spear.getDamageConditions().get().test(effectiveTicks, attackerSpeed, relSpeed, needSpeed);

            if (!canDismount && !canKnockback && !canDamage) continue;

            float damage = (float) baseDamage + (float) Mth.floor(relSpeed * (double) spear.getDamageMultiplier());
            double attrMult = getSpearMultiplier(user, SpearAttributes.SPEAR_CHARGE_MULTIPLIER.get());
            SpearDamageEvent event = new SpearDamageEvent(user, target,
                    damage, SpearDamageEvent.AttackType.CHARGE);
            MinecraftForge.EVENT_BUS.post(event);
            damage *= (float) (attrMult * event.getMultiplier());
            boolean applied = stabAttack(
                    SpearItem.SlotUtil.slotForHand(user, user.getUsedItemHand()),
                    target, damage, canDamage, canKnockback, canDismount, user,
                    SpearDamageEvent.AttackType.CHARGE, spear);
            if (applied && user instanceof SpearCooldownAccessor accessor) {
                accessor.RememberStabbedEntity(target);
            }
            hitSomething |= applied;
        }

        if (hitSomething) {
            user.level().broadcastEntityEvent(user, (byte) 2);
        }
    }

    // ========== 左键戳刺 ==========

    public static void attack(LivingEntity attacker, ItemStack stack, EquipmentSlot slot, SpearLike spear) {
        float baseDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0f;
        boolean hitSomething = false;

        for (EntityHitResult hit : SpearItem.getHitEntitiesAlong(attacker, spear, spear.getHitboxMargin2(),
                entity -> SpearItem.canHitEntity(attacker, entity))) {
            hitSomething |= stabAttack(slot, hit.getEntity(), baseDamage, true,
                    spear.dealsKnockback(), spear.dismounts(), attacker,
                    SpearDamageEvent.AttackType.STAB, spear);
        }

        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.resetAttackStrengthTicker();
        }
        SpearItem.jerotesLungeForwardMaybe(attacker);

        if (hitSomething) {
            attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    spear.getHitSound(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                spear.getAttackSound(), SoundSource.PLAYERS, 1.0f, 1.0f);
        attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, false);
    }

    // ========== performStabAttack（兼容 SpearStabAttackPacket） ==========

    public static boolean performStabAttack(LivingEntity attacker, ItemStack stack, EquipmentSlot slot,
                                            boolean playSound) {
        if (attacker.level().isClientSide || stack.isEmpty()) return false;
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null) return false;
        if (attacker instanceof Player player && !canPlayerPerformStab(player, stack, spear)) return false;

        Vec3 look = attacker.getLookAngle();
        double attackerSpeed = Math.max(0.0, look.dot(SpearItem.getMotion(attacker)));

        float baseDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage = baseDamage;
        // 速度加成是 MODDED 专有行为；Java / 基岩版戳刺只看武器攻击力。
        if (SpearRules.hasJabSpeedBonus()) {
            damage += (float) Mth.floor(attackerSpeed * spear.getDamageMultiplier());
        }

        double attrMult = getSpearMultiplier(attacker, SpearAttributes.SPEAR_STAB_MULTIPLIER.get());
        SpearDamageEvent event = new SpearDamageEvent(attacker, /* 首个目标暂填 null */ null,
                damage, SpearDamageEvent.AttackType.STAB);
        MinecraftForge.EVENT_BUS.post(event);
        damage *= (float) (attrMult * event.getMultiplier());

        boolean hitSomething = false;
        if (playSound) {
            attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    spear.getAttackSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        for (EntityHitResult hit : SpearItem.getHitEntitiesAlong(attacker, spear, spear.getHitboxMargin2(),
                entity -> SpearItem.canHitEntity(attacker, entity))) {
            Entity target = hit.getEntity();
            if (SpearRules.hasJabRepeatCooldown()
                    && attacker instanceof SpearCooldownAccessor accessor
                    && accessor.WasRecentlyStabbed(target, spear.getContactCooldownTicks())) {
                continue;
            }
            // 与 26.1.2 原版一致：左键穿刺也要过原版附魔伤害修正。
            // 1.20.1 的 API 是 getDamageBonus(ItemStack, MobType)。
            DamageSource spearSource = SpearDamageTypes.spear(attacker.level(), attacker);
            float finalDamage = damage;
            if (target instanceof LivingEntity livingTarget) {
                finalDamage += EnchantmentHelper.getDamageBonus(stack, livingTarget.getMobType());
            }
            if (target.hurt(spearSource, finalDamage)) {
                // 与蓄力路径一致，命中后把效果转发给 hurtEnemy 钩子
                if (target instanceof LivingEntity livingTarget && attacker instanceof Player player) {
                    stack.hurtEnemy(livingTarget, player);
                }
                hitSomething = true;
                stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(slot));
                if (spear.dealsKnockback()) {
                    // 与原版一致：0.4 基础击退 + 附魔击退加成
                    causeExtraKnockback(attacker, target,
                            0.4F + EnchantmentHelper.getKnockbackBonus(attacker), target.getDeltaMovement());
                }
                if (attacker instanceof Player player) {
                    player.setLastHurtMob(target);
                }
                MinecraftForge.EVENT_BUS.post(new SpearHitEvent(attacker, target, damage,
                        SpearDamageEvent.AttackType.STAB));
                if (attacker instanceof SpearCooldownAccessor accessor) {
                    accessor.RememberStabbedEntity(target);
                }
            }
        }

        // 基岩版：戳中之后进入使用冷却，且背包里所有长矛共用同一个冷却。
        if (hitSomething && attacker instanceof Player player) {
            applyUseCooldown(player, spear);
            // 基岩版还有一个"戳完不能马上蓄力"的后摇；JAVA 冷却模型下用物品冷却单独挂上。
            int delay = spear.jabToChargeDelayTicks(spear.getUseCooldownTicks());
            if (delay > 0 && !spear.usesJabCooldown()) {
                player.getCooldowns().addCooldown(stack.getItem(), delay);
            }
        }

        if (hitSomething) {
            attacker.level().broadcastEntityEvent(attacker, (byte) 2);
            attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    spear.getHitSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        SpearItem.jerotesLungeForwardMaybe(attacker);
        return hitSomething;
    }

    public static boolean canPlayerPerformStab(Player player, ItemStack stack, SpearLike spear) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        // 只有基岩版的使用冷却会挡住下一次戳刺；JAVA 冷却模型下物品冷却只用来挡蓄力（戳刺后摇）。
        if (spear.usesJabCooldown() && player.getCooldowns().isOnCooldown(stack.getItem())) {
            return false;
        }
        // 基岩版用使用冷却；Java 版必须等原版攻击蓄力条回满。
        return spear.usesJabCooldown() || player.getAttackStrengthScale(0.0F) >= 0.95F;
    }

    /** 把一个时长按这个长矛的攻击速度折算成使用冷却，并给背包里所有长矛都挂上。 */
    private static void applyUseCooldown(Player player, SpearLike spear) {
        if (!spear.usesJabCooldown()) {
            return;
        }
        int ticks = spear.getUseCooldownTicks();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof SpearItem other) {
                player.getCooldowns().addCooldown(other, ticks);
            }
        }
        if (player.getOffhandItem().getItem() instanceof SpearItem other) {
            player.getCooldowns().addCooldown(other, ticks);
        }
    }

    // ========== stabAttack（J库核心） ==========

    private static boolean stabAttack(EquipmentSlot slot, Entity target, float damage, boolean canDamage,
                                      boolean canKnockback, boolean canDismount, LivingEntity attacker,
                                      SpearDamageEvent.AttackType attackType, SpearLike spear) {
        Level level = attacker.level();
        if (!(level instanceof ServerLevel)) return false;

        ItemStack stack = attacker.getItemBySlot(slot);
        DamageSource damageSource = SpearDamageTypes.spear(level, attacker);

        float finalDamage = damage;
        Vec3 preMotion = target.getDeltaMovement();
        boolean hitSomething = canKnockback;

        boolean damaged = false;
        if (canDamage) {
            // 与 26.1.2 原版矛一致：伤害要过原版附魔修正（锋利/亡灵杀手/节肢杀手等）。
            // 1.20.1 的 API 是 getDamageBonus(ItemStack, MobType)。
            if (target instanceof LivingEntity livingTarget) {
                finalDamage += EnchantmentHelper.getDamageBonus(stack, livingTarget.getMobType());
            }
            damaged = target.hurt(damageSource, finalDamage);
            hitSomething |= damaged;
        }

        if (canKnockback) {
            // 与原版一致：0.4 基础击退 + 附魔击退加成（1.20.1 用 getKnockbackBonus）
            causeExtraKnockback(attacker, target,
                    0.4f + EnchantmentHelper.getKnockbackBonus(attacker), preMotion);
        }

        if (canDismount && target.isPassenger()) {
            hitSomething = true;
            target.stopRiding();
        }

        if (target instanceof LivingEntity livingTarget && attacker instanceof Player player) {
            stack.hurtEnemy(livingTarget, player);
        }

        if (!hitSomething) return false;
        MinecraftForge.EVENT_BUS.post(new SpearHitEvent(attacker, target, damage, attackType));
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                spear.getHitSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
        attacker.setLastHurtMob(target);
        stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(slot));
        return true;
    }

    // ========== causeExtraKnockback（J库） ==========

    private static void causeExtraKnockback(LivingEntity attacker, Entity target, float strength, Vec3 preMotion) {
        boolean asPlayer = attacker instanceof Player
                || (attacker instanceof UseSpearSpecialEntity u && u.isJerotesSpearCauseExtraKnockbackPlayer());

        // 命中后是否把攻击者自己也减速（水平速度 ×0.6）并取消奔跑。
        // JAVA（默认）保留原版这股"撞完得重新起跑"的手感；MODDED 则保持速度与奔跑状态。
        boolean slowDownAttacker = SpearRules.slowsDownAttackerOnHit();

        if (asPlayer) {
            if (strength > 0.0f) {
                if (target instanceof LivingEntity living) {
                    living.knockback(strength, Mth.sin(attacker.getYRot() * ((float) Math.PI / 180)),
                            -Mth.cos(attacker.getYRot() * ((float) Math.PI / 180)));
                } else {
                    target.push(-Mth.sin(attacker.getYRot() * ((float) Math.PI / 180)) * strength, 0.1,
                            Mth.cos(attacker.getYRot() * ((float) Math.PI / 180)) * strength);
                }
                if (slowDownAttacker) {
                    attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                    attacker.setSprinting(false);
                }
            }
            if (target instanceof ServerPlayer serverPlayer && target.hurtMarked) {
                serverPlayer.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(target));
                target.hurtMarked = false;
                target.setDeltaMovement(preMotion);
            }
        } else {
            if (strength > 0.0f && target instanceof LivingEntity living) {
                living.knockback(strength, Mth.sin(attacker.getYRot() * ((float) Math.PI / 180)),
                        -Mth.cos(attacker.getYRot() * ((float) Math.PI / 180)));
                if (slowDownAttacker) {
                    attacker.setDeltaMovement(attacker.getDeltaMovement().multiply(0.6, 1.0, 0.6));
                }
            }
        }
    }

    // ========== 数值 ==========

    public static int computeDamageUseDuration(SpearLike spear) {
        return spear.getDelayTicks() + spear.getDamageConditions()
                .map(SpearCondition::maxDurationTicks).orElse(0);
    }

    /**
     * 蓄力伤害的基线。
     *
     * <p>JAVA：取裸武器攻击力，力量/虚弱等修饰符不算（原版 MC-302778）。</p>
     * <p>BEDROCK：取包含修饰符的攻击力，力量药水会加进蓄力伤害。</p>
     */
    public static float chargeBaseDamage(LivingEntity user, SpearLike spear) {
        if (user.getAttribute(Attributes.ATTACK_DAMAGE) == null) return 0.0F;
        double value = SpearRules.chargeGainsAttributeModifiers()
                ? user.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : user.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        return (float) value;
    }

    public static float effectiveMinRange(SpearLike spear, Entity entity) {
        // MODDED：最小距离归零，贴脸也能戳刺/蓄力。
        if (SpearRules.ignoresMinRange()) return 0.0f;
        if (entity instanceof Player player) {
            if (player.isSpectator()) return 0.0f;
            return player.isCreative() ? spear.getMinCreativeRange() : spear.getMinRange();
        }
        return spear.getMinRange() * spear.getMobFactor();
    }

    public static float effectiveMaxRange(SpearLike spear, Entity entity) {
        if (entity instanceof Player player) {
            return (player.isCreative() ? spear.getMaxCreativeRange() : spear.getMaxRange())
                    + (player.getAttribute(ForgeMod.ENTITY_REACH.get()) != null
                    ? (float) Math.max(0, player.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - 3) : 0);
        }
        return spear.getMaxRange() * spear.getMobFactor()
                + (entity instanceof LivingEntity le && le.getAttribute(ForgeMod.ENTITY_REACH.get()) != null
                ? (float) Math.max(0, le.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - 3) : 0);
    }

    private static double getSpearMultiplier(LivingEntity entity, Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance != null ? instance.getValue() : 1.0;
    }

    private static SpearChargePhaseEvent.Phase currentPhase(SpearLike spear, int usedTicks) {
        if (usedTicks < spear.getDelayTicks()) return SpearChargePhaseEvent.Phase.DELAY;
        if (usedTicks < spear.getDismountEndTick()) return SpearChargePhaseEvent.Phase.DISMOUNT;
        if (usedTicks < spear.getKnockbackEndTick()) return SpearChargePhaseEvent.Phase.KNOCKBACK;
        return SpearChargePhaseEvent.Phase.DAMAGE;
    }
}
