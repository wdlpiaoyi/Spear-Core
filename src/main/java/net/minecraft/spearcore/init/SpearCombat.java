package net.minecraft.spearcore.init;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.spearcore.event.SpearChargePhaseEvent;
import net.minecraft.spearcore.event.SpearDamageEvent;
import net.minecraft.spearcore.event.SpearHitEvent;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.spearcore.util.SpearCooldownAccessor;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.spearcore.util.UseSpearSpecialEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 长矛的战斗流程（戳刺 / 蓄力）。
 *
 * <p>原先这些逻辑写在 {@link SpearItem} 的实例方法里，只有本模组自己的长矛能用。
 * 为了让 Tetra 的模块化长矛（archetype modular_spear）复用同一套手感，这里改成静态实现，
 * 通过 {@link SpearLike} 读取数值 —— 两边走的是同一份代码，而不是复制出来的近似版本。</p>
 */
public final class SpearCombat {

    /** 蓄力阶段缓存，用于阶段变更检测。 */
    private static final Map<LivingEntity, SpearChargePhaseEvent.Phase> PHASE_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * 「虚拟原版攻击」的重入闸门。
     *
     * <p>蓄力命中后我们会补一次 {@code Player#attack(target)}，让只挂在原版攻击上的 mod
     * （例如 fantasy_ending 的 Void Collapsing）能看到这次攻击；本闸门防止我们自己的
     * {@code SpearAttackHandler} 在这条路径上再跑一遍戳刺、造成双倍伤害。</p>
     */
    private static final ThreadLocal<Boolean> VIRTUAL_ATTACK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 每次蓄力只补一次虚拟攻击；起手时置位，第一次命中后消费掉。 */
    private static final Map<LivingEntity, Boolean> VIRTUAL_PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 同一 tick 只允许一次横扫，避免一次攻击命中多个目标时叠出多道剑气。 */
    private static final Map<Player, Long> LAST_SWEEP_TICK =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean virtualAttackFailureLogged;

    private SpearCombat() {
    }

    /**
     * 把多部件实体（如末影龙的头/颈/身/尾/翅）解析回母 {@link LivingEntity}。
     *
     * <p>Forge 会把 {@code Entity#getParts()} 注册进 {@code ServerLevel.dragonParts}，
     * 所以 {@code Level.getEntities} 扫到的是部件本身；部件不是 LivingEntity，
     * 附魔/免疫/击退/仇恨等逻辑必须先回到母实体，伤害则仍落在具体部件上，
     * 这样原版对龙头的满伤、对其他部件的打折才不会被我们改掉。</p>
     */
    public static LivingEntity resolveLivingTarget(Entity target) {
        if (target instanceof LivingEntity living) return living;
        if (target instanceof PartEntity<?> part && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    public static Entity rootTarget(Entity target) {
        LivingEntity living = resolveLivingTarget(target);
        return living != null ? living : target;
    }

    /**
     * 同一次射线扫描里，对同一只母实体只保留最近的一个部件。
     * 不这么做的话，一次蓄力会同时打中龙头、颈、身，结算好几遍。
     * 输入已按距离从近到远排序，输出保持该顺序。
     */
    public static List<EntityHitResult> dedupeByRoot(List<EntityHitResult> hits) {
        if (hits.size() < 2) return hits;
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<EntityHitResult> out = new ArrayList<>(hits.size());
        for (EntityHitResult hit : hits) {
            if (seen.add(rootTarget(hit.getEntity()))) out.add(hit);
        }
        return out;
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
            LivingEntity living = resolveLivingTarget(target);
            if (living == null) continue;

            // 蓄力重复命中：JAVA 每个目标每次蓄力只结算一次；BEDROCK 固定接触冷却；MODDED/Tetra 按攻速缩放。
            if (user instanceof SpearCooldownAccessor accessor
                    && accessor.WasRecentlyStabbed(living, spear.chargeRepeatCooldownTicks(user))) {
                continue;
            }

            double targetSpeed = look.dot(SpearItem.getMotion(living));
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
            if (applied) {
                if (user instanceof SpearCooldownAccessor accessor) {
                    accessor.RememberStabbedEntity(living);
                }
                if (spear.style() == SpearStyle.MODULAR_3D) {
                    SpearLikeSupport.tetraOnHit(stack, user, living);
                }
                if (user instanceof Player player && VIRTUAL_PENDING.remove(player) != null) {
                    virtualAttack(player, target);
                }
            }
            hitSomething |= applied;
        }

        if (hitSomething) {
            user.level().broadcastEntityEvent(user, (byte) 2);
        }
    }

    // ========== 起手蓄力 ==========

    /**
     * 矛盾共持：潜行 + 副手盾时，右键是否应该让给副手盾牌。
     *
     * <p>{@code BEDROCK} 与 {@code MODDED} 都在潜行时把右键让给盾（{@code BEDROCK} 还会自动举盾），
     * 两者的区别只在「要不要右键」，见 {@link #autoRaisesOffhandShield}。返回 {@code true} 时
     * {@link #tryStartCharging} 返回 {@code PASS}，客户端 {@code Minecraft#startUseItem} 会继续尝试副手，
     * 由原版 {@code ShieldItem#use} 进入格挡（减伤/击退/音效/动画全部走原版）。</p>
     */
    public static boolean offhandShieldTakesPriority(boolean sneaking, boolean offhandShield) {
        return SpearRules.shieldCoWield() != SpearRules.ShieldCoWield.JAVA && sneaking && offhandShield;
    }

    /**
     * 矛盾共持 {@code BEDROCK} 档：潜行即自动举盾，不需要右键。
     *
     * <p>对应原版基岩版的盾牌机制（潜行/下蹲会激活盾牌）。{@code MODDED} 档不自动举盾，需要右键。</p>
     */
    public static boolean autoRaisesOffhandShield(boolean sneaking, boolean offhandShield) {
        return SpearRules.shieldCoWield() == SpearRules.ShieldCoWield.BEDROCK && sneaking && offhandShield;
    }

    /**
     * 矛盾共持 {@code BEDROCK} 档：潜行自动举盾时，客户端不要原版「没按右键就松手」那套把盾放下。
     *
     * <p>自动举盾只在服务端 {@code startUsingItem}，客户端没按右键；{@code Minecraft#handleKeybinds}
     * 每 tick 都会对「正在使用物品但右键没按住」的状态调用 {@code releaseUsingItem}，
     * 于是盾牌举起又放下、一闪一闪。客户端 mixin 用这里拦掉 release。</p>
     */
    public static boolean shouldKeepAutoRaisedShield(Player player) {
        if (player == null || SpearRules.shieldCoWield() != SpearRules.ShieldCoWield.BEDROCK) {
            return false;
        }
        if (!player.isShiftKeyDown() || SpearLikeSupport.of(player.getMainHandItem()) == null
                || !(player.getOffhandItem().getItem() instanceof ShieldItem)) {
            return false;
        }
        return player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
                && player.getUseItem().getItem() instanceof ShieldItem;
    }

    /**
     * 矛盾共持：起身收盾（{@code BEDROCK} / {@code MODDED} 都适用）。
     *
     * <p>玩家用副手盾格挡时直接站起来（不再潜行），原版不会自动收盾；这里判定为应当中断，
     * 由 {@code SpearShieldStanceHandler} 在主手仍是矛时调用 {@code stopUsingItem}。</p>
     */
    public static boolean shouldReleaseOffhandShield(boolean sneaking, boolean usingOffhandShield,
                                                     boolean mainHandSpear) {
        return SpearRules.shieldCoWield() != SpearRules.ShieldCoWield.JAVA
                && mainHandSpear && usingOffhandShield && !sneaking;
    }

    /**
     * 右键起手蓄力。本模组贴图矛与 Tetra 模块化长矛共用同一份判定，避免两边手感漂移。
     *
     * <p>Tetra 的 {@code use()} 只有在它自己算出 {@code getUseDuration > 0} 时才会
     * {@code startUsingItem}；矛头 JSON 的 {@code aspects.throwable} 并不保证产生
     * {@code ItemEffect.throwable}，所以不能指望 Tetra 自己进入 using。这里直接按本模组语义起手。</p>
     */
    public static InteractionResultHolder<ItemStack> tryStartCharging(Level level, Player player,
                                                                     InteractionHand hand, ItemStack stack,
                                                                     SpearLike spear) {
        // 矛盾共持 BEDROCK：潜行 + 副手盾时把右键让给盾牌（返回 PASS，客户端会继续尝试副手）。
        if (hand == InteractionHand.MAIN_HAND
                && offhandShieldTakesPriority(player.isShiftKeyDown(),
                        player.getOffhandItem().getItem() instanceof ShieldItem)) {
            return InteractionResultHolder.pass(stack);
        }
        // 冷却期间不能起手蓄力（基岩版戳刺后的后摇，以及基岩版的使用冷却本身）。
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResultHolder.pass(stack);
        }
        // 基岩版不能用副手举矛蓄力。
        if (hand == InteractionHand.OFF_HAND && SpearRules.blocksOffhandCharging()) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        playUseSound(player, spear);
        if (!level.isClientSide) {
            // 客户端不跑命中结算；只允许服务端线程写这个缓存。
            VIRTUAL_PENDING.put(player, Boolean.TRUE);
        }
        if (spear.style() == SpearStyle.MODULAR_3D) {
            SpearLikeSupport.tetraOnUse(stack, player);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** 举矛音效；玩家与非玩家分开播，保持与原实现一致。 */
    public static void playUseSound(net.minecraft.world.entity.Entity entity, SpearLike spear) {
        if (entity instanceof Player player) {
            entity.level().playSound(player, entity.getX(), entity.getY(), entity.getZ(),
                    spear.getUseSound(), entity.getSoundSource(), 1.0f, 1.0f);
        } else {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    spear.getUseSound(), entity.getSoundSource(), 1.0f, 1.0f);
        }
    }

    // ========== 左键戳刺 ==========

    public static void attack(LivingEntity attacker, ItemStack stack, EquipmentSlot slot, SpearLike spear) {
        float baseDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0f;

        for (EntityHitResult hit : SpearItem.getHitEntitiesAlong(attacker, spear, spear.getHitboxMargin2(),
                entity -> SpearItem.canHitEntity(attacker, entity))) {
            stabAttack(slot, hit.getEntity(), baseDamage, true,
                    spear.dealsKnockback(), spear.dismounts(), attacker,
                    SpearDamageEvent.AttackType.STAB, spear);
        }

        if (attacker instanceof ServerPlayer serverPlayer) {
            serverPlayer.resetAttackStrengthTicker();
        }
        SpearItem.jerotesLungeForwardMaybe(attacker);

        // 命中音效由 stabAttack 在目标处播放，这里只补攻击音，避免一次命中两声。
        attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                spear.getAttackSound(), attacker.getSoundSource(), 1.0f, 1.0f);
        attacker.swing(net.minecraft.world.InteractionHand.MAIN_HAND, false);
    }

    // ========== performStabAttack（兼容 SpearStabAttackPacket） ==========

    public static boolean performStabAttack(LivingEntity attacker, ItemStack stack, EquipmentSlot slot,
                                            boolean playSound) {
        if (attacker.level().isClientSide || stack.isEmpty()) return false;
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null || spear.slashing()) return false;
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

        boolean hitSomething = false;
        if (playSound) {
            attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                    spear.getAttackSound(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        double minRange = spear.effectiveMinRange(attacker);
        for (EntityHitResult hit : SpearItem.getHitEntitiesAlong(attacker, spear, spear.getHitboxMargin2(),
                entity -> SpearItem.canHitEntity(attacker, entity))) {
            Entity target = hit.getEntity();
            // 近距限制：射线起点已经在 minRange，但目标判定箱可能伸进起点里；这里再按实体中心补一道，
            // 避免贴脸也能戳中（用户实测 Tetra 矛 jab 没有这个限制）。
            if (attacker.distanceToSqr(target) < minRange * minRange) {
                continue;
            }
            LivingEntity living = resolveLivingTarget(target);
            if (living == null) continue;
            if (SpearRules.hasJabRepeatCooldown()
                    && attacker instanceof SpearCooldownAccessor accessor
                    && accessor.WasRecentlyStabbed(living, spear.getContactCooldownTicks())) {
                continue;
            }
            // 戳刺一次可能扫到多个目标，事件按目标逐个发：以前只在循环外发一次、
            // 且 target 填 null，监听方既分不清打的是谁，也没法按目标改伤害。
            SpearDamageEvent event = new SpearDamageEvent(attacker, target,
                    damage, SpearDamageEvent.AttackType.STAB);
            MinecraftForge.EVENT_BUS.post(event);
            float stabDamage = damage * (float) (attrMult * event.getMultiplier());

            // 与 26.1.2 原版一致：左键穿刺也要过原版附魔伤害修正。
            // 1.20.1 的 API 是 getDamageBonus(ItemStack, MobType)。
            DamageSource spearSource = SpearDamageTypes.spear(attacker.level(), attacker);
            float finalDamage = stabDamage;
            if (living != null) {
                finalDamage += EnchantmentHelper.getDamageBonus(stack, living.getMobType());
                // 同一 tick 里别的模组（如 fantasy_ending）可能已经先打了一段，
                // 会留下受伤免疫；不清掉的话长矛本体这次的伤害会被吞。
                clearSameAttackerHurtImmunity(attacker, living);
            }
            if (target.hurt(spearSource, finalDamage)) {
                if (living != null) {
                    applyOnHitEnchantments(attacker, living);
                    if (spear.style() == SpearStyle.MODULAR_3D) {
                        SpearLikeSupport.tetraOnHit(stack, attacker, living);
                    }
                }
                // 与蓄力路径一致，命中后把效果转发给 hurtEnemy 钩子
                if (living != null && attacker instanceof Player player) {
                    stack.hurtEnemy(living, player);
                }
                hitSomething = true;
                stack.hurtAndBreak(1, attacker, e -> e.broadcastBreakEvent(slot));
                if (spear.dealsKnockback()) {
                    // 与原版一致：0.4 基础击退 + 附魔击退加成
                    causeExtraKnockback(attacker, living != null ? living : target,
                            0.4F + EnchantmentHelper.getKnockbackBonus(attacker), target.getDeltaMovement());
                }
                if (attacker instanceof Player player) {
                    player.setLastHurtMob(living != null ? living : target);
                }
                MinecraftForge.EVENT_BUS.post(new SpearHitEvent(attacker, target, finalDamage,
                        SpearDamageEvent.AttackType.STAB));
                if (attacker instanceof SpearCooldownAccessor accessor) {
                    accessor.RememberStabbedEntity(living != null ? living : target);
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
                    spear.getHitSound(), attacker.getSoundSource(), 1.0F, 1.0F);
        }
        SpearItem.jerotesLungeForwardMaybe(attacker);
        return hitSomething;
    }

    /**
     * 横扫半径：命中实体周围 {@code 0.3 × 有效攻击距离} 格。
     *
     * <p>原版剑固定 1.0；这里按长矛射程等比放大（4.5 射程 → 1.35，创造 6.5 → 1.95）。
     * 不要再夹上下限——夹住之后 0.3 的系数会被吃掉，长剑气的范围又变回固定值。</p>
     */
    public static float sweepRadius(float effectiveMaxRange) {
        return effectiveMaxRange * 0.3F;
    }

    /**
     * 原版剑式横扫的触发条件：攻击强度、疾跑与地面限制。
     *
     * <p>槊头 / 三尖两刃刀通过 {@link SpearLike#sweepsWhileAirborne()} 允许空中横扫；
     * 普通矛头仍要求站在地面。两者都保留攻击强度与非疾跑限制。</p>
     */
    public static boolean canVanillaSlashSweep(Player player, SpearLike spear) {
        if (player == null || spear == null) return false;
        return player.getAttackStrengthScale(0.5F) > 0.9F
                && (player.onGround() || spear.sweepsWhileAirborne())
                && !player.isSprinting();
    }

    /**
     * 横扫候选过滤：只按**命中实体**周围的判定箱筛选，不再按玩家距离筛。
     *
     * <p>长矛射程 4.5 时，玩家到命中实体就有 4 格多；再用「玩家距离 ≤ 半径+2」过滤，
     * 命中实体旁边的怪会被误杀。这里只判断目标周围的判定箱（与
     * {@code getEntitiesOfClass} 用的盒子一致），并排除自己 / 主目标 / 友军 / 标记盔甲架。</p>
     */
    public static boolean isSweptBy(Player player, Entity target, LivingEntity living, float radius) {
        if (living == player || living == target || player.isAlliedTo(living)) {
            return false;
        }
        if (living instanceof net.minecraft.world.entity.decoration.ArmorStand armorStand
                && armorStand.isMarker()) {
            return false;
        }
        return living.getBoundingBox().intersects(target.getBoundingBox().inflate(radius, 0.35D, radius));
    }

    /**
     * 原版剑式横扫：满蓄力、没有疾跑时触发；普通矛头要求站在地面，槊头 / 三尖两刃刀
     * 允许空中触发。伤害按原版 {@code 1 + 横扫之刃加成} 计算，并补原版横扫粒子和音效。
     *
     * <p>不能用 Tetra 的 {@code sweeping} 效果代替：那套按攻击力全额结算，会绕开原版
     * 蓄力/冷却缩放，0 蓄力也能打出高伤。这里只复刻原版 {@code Player#attack} 的横扫分支，
     * 主目标仍由原版攻击结算，横扫只打周围的其它目标。</p>
     */
    public static void vanillaSlashSweep(Player player, Entity target, SpearLike spear) {
        if (player == null || target == null || spear == null || player.level().isClientSide) {
            return;
        }
        if (!canVanillaSlashSweep(player, spear)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_SWEEP_TICK.get(player);
        if (last != null && last == now) {
            return;
        }
        LAST_SWEEP_TICK.put(player, now);
        // 剑气以**命中实体**为中心，半径 = 0.3 × 长矛有效攻击距离（4.5 射程 → 1.35 格）。
        // 之前用 0.6 且夹在 2~4，还按玩家距离过滤，结果长手命中时剑气只覆盖自己脚边。
        float radius = sweepRadius((float) spear.effectiveMaxRange(player));
        float ratio = 1.0F + EnchantmentHelper.getSweepingDamageRatio(player);
        double yaw = Math.toRadians(player.getYRot());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);
        for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(radius, 0.35D, radius))) {
            if (!isSweptBy(player, target, living, radius)) {
                continue;
            }
            living.knockback(0.4F, dx, dz);
            living.hurt(player.damageSources().playerAttack(player), ratio);
        }
        if (player.level() instanceof ServerLevel server) {
            // 只放一枚剑气，位置在命中实体处；范围由伤害判定半径体现，避免一次横扫出现多道剑气。
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY(0.5D), target.getZ(),
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2F, 1.0F);
    }

    /** 当前是否处于「虚拟原版攻击」里；{@code SpearAttackHandler} 用它避免重复结算。 */
    public static boolean inVirtualAttack() {
        return VIRTUAL_ATTACK.get();
    }

    /**
     * 补一次原版 {@code Player#attack}，只为让挂在原版攻击上的 mod 钩子/事件生效。
     *
     * <p>伤害已经由长矛自己的流程结算，所以 {@code SpearAttackHandler} 会在这条路径上取消 Forge
     * {@code AttackEntityEvent}，原版本体伤害不会重复；其它 mod 在 {@code Player#attack} 头部的
     * mixin（fantasy_ending 等）会照常触发。</p>
     */
    public static void virtualAttack(Player player, Entity target) {
        if (player == null || target == null || player.level().isClientSide) {
            return;
        }
        // 第三方挂在 Player#attack 的 mixin 常按 LivingEntity 处理；多部件实体要传母实体。
        Entity attackTarget = rootTarget(target);
        VIRTUAL_ATTACK.set(Boolean.TRUE);
        try {
            player.attack(attackTarget);
        } catch (Throwable throwable) {
            if (!virtualAttackFailureLogged) {
                virtualAttackFailureLogged = true;
                net.minecraft.spearcore.SpearcoreMod.LOGGER.warn(
                        "[spearcore] 虚拟原版攻击失败，mod 攻击钩子可能收不到蓄力命中", throwable);
            }
        } finally {
            VIRTUAL_ATTACK.set(Boolean.FALSE);
        }
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

    /**
     * 命中后统一补上原版「命中时」附魔效果。
     *
     * <p>原版 {@code Player.attack} 在 {@code hurt} 成功后会处理火焰附加、攻击者武器上的
     * {@code doPostAttack}（节肢杀手减速、霜冻附加等）以及目标护甲上的 {@code doPostHurt}
     * （荆棘等）。长矛走的是自定义攻击流程，必须自己补这几步，否则附魔能上、效果永远不触发。</p>
     *
     * <p>注意两个 helper 的语义不同：{@code doPostDamageEffects(attacker, target)} 遍历
     * <b>攻击者</b>的物品并调用 {@code doPostAttack}；{@code doPostHurtEffects(target, attacker)}
     * 遍历<b>目标</b>的物品（护甲）并调用 {@code doPostHurt}。只调其中一个会漏掉另一侧。</p>
     */
    public static void applyOnHitEnchantments(LivingEntity attacker, LivingEntity target) {
        int fireAspect = EnchantmentHelper.getFireAspect(attacker);
        if (fireAspect > 0) {
            target.setSecondsOnFire(fireAspect * 4);
        }
        EnchantmentHelper.doPostDamageEffects(attacker, target);
        EnchantmentHelper.doPostHurtEffects(target, attacker);
    }

    /**
     * 清掉同一攻击者在同一 tick 里留下的受伤免疫。
     *
     * <p>有些模组（例如 fantasy_ending 的 Void Collapsing）会在原版 {@code Player.attack} 开头
     * 的事件里先对目标打一段伤害；本模组长矛随后再打本体伤害时，会被这段伤害留下的
     * {@code invulnerableTime} 折减甚至整段吞掉。这里只在「目标当前免疫、且上次伤害来自同一个攻击者」
     * 时清零，让两次伤害正确相加，不影响其他玩家/其他来源的攻击。</p>
     */
    public static void clearSameAttackerHurtImmunity(LivingEntity attacker, LivingEntity target) {
        if (target.invulnerableTime > 0 && target.getLastHurtByMob() == attacker) {
            target.invulnerableTime = 0;
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
        LivingEntity living = resolveLivingTarget(target);

        float finalDamage = damage;
        Vec3 preMotion = target.getDeltaMovement();
        boolean hitSomething = canKnockback;

        boolean damaged = false;
        if (canDamage) {
            // 与 26.1.2 原版矛一致：伤害要过原版附魔修正（锋利/亡灵杀手/节肢杀手等）。
            // 1.20.1 的 API 是 getDamageBonus(ItemStack, MobType)。
            if (living != null) {
                finalDamage += EnchantmentHelper.getDamageBonus(stack, living.getMobType());
                clearSameAttackerHurtImmunity(attacker, living);
            }
            damaged = target.hurt(damageSource, finalDamage);
            if (damaged && living != null) {
                applyOnHitEnchantments(attacker, living);
            }
            hitSomething |= damaged;
        }

        if (canKnockback) {
            // 与原版一致：0.4 基础击退 + 附魔击退加成（1.20.1 用 getKnockbackBonus）
            causeExtraKnockback(attacker, living != null ? living : target,
                    0.4f + EnchantmentHelper.getKnockbackBonus(attacker), preMotion);
        }

        if (canDismount && target.isPassenger()) {
            hitSomething = true;
            target.stopRiding();
        }

        if (living != null && attacker instanceof Player player) {
            stack.hurtEnemy(living, player);
        }

        if (!hitSomething) return false;
        MinecraftForge.EVENT_BUS.post(new SpearHitEvent(attacker, target, finalDamage, attackType));
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                spear.getHitSound(), attacker.getSoundSource(), 1.0F, 1.0F);
        attacker.setLastHurtMob(living != null ? living : target);
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

    /**
     * 蓄力伤害的基线。
     *
     * <p>JAVA：取裸武器攻击力，力量/虚弱等修饰符不算（原版 MC-302778）。
     * 但长矛<b>自己</b>提供的 {@code ADDITION} 攻击力必须算进来 —— 武器加成是装备期才挂上的，
     * 不进 {@code getAttributeBaseValue}，只取基础值会让七种材质的蓄力基线全是玩家的 1.0
     * （铁矛应该比木矛高 2 点）。</p>
     * <p>BEDROCK：取包含修饰符的攻击力，力量药水会加进蓄力伤害。</p>
     */
    public static float chargeBaseDamage(LivingEntity user, SpearLike spear) {
        if (user.getAttribute(Attributes.ATTACK_DAMAGE) == null) return 0.0F;
        double weaponBonus = weaponAttackDamage(user);
        if (SpearRules.chargeGainsAttributeModifiers()) {
            // 1.20.1 的装备修饰符会在实体 tick 时进属性（LivingEntity#collectEquipmentChanges），
            // 玩家和怪物都一样。只有副手蓄力时这把矛不在 MAINHAND 槽，属性里没有它的 ADDITION：
            // 此时把副手矛的加成补上，同时扣掉主手武器已经算进去的那份。
            double value = user.getAttributeValue(Attributes.ATTACK_DAMAGE);
            if (user.getUsedItemHand() == InteractionHand.OFF_HAND) {
                value += weaponBonus - weaponAttackDamage(user.getMainHandItem());
            }
            return (float) value;
        }
        return (float) (user.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + weaponBonus);
    }

    /**
     * 使用者手里那根长矛在 {@code ADDITION} 上贡献的攻击力。
     *
     * <p>只统计 {@code ADDITION}：{@code MULTIPLY_BASE} / {@code MULTIPLY_TOTAL} 属于
     * 全局乘区，JAVA 档的语义是「不算 buff」，把它们算进来会连带把力量药水挪回蓄力伤害里。</p>
     */
    private static double weaponAttackDamage(LivingEntity user) {
        ItemStack stack = user.getUseItem();
        if (stack.isEmpty()) stack = user.getMainHandItem();
        return weaponAttackDamage(stack);
    }

    /** 指定物品堆在 MAINHAND 上贡献的 {@code ADDITION} 攻击力。 */
    private static double weaponAttackDamage(ItemStack stack) {
        if (stack.isEmpty()) return 0.0D;
        double sum = 0.0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += modifier.getAmount();
            }
        }
        return sum;
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
        return effectiveMaxRange(spear, entity, 0.0D, 1.0D);
    }

    /**
     * 带来源缩放的射程。
     *
     * <p>reach 超出原版 3.0 的部分里，{@code sourceBonus} 是「指定来源」贡献的那段，
     * 只对这段乘 {@code scale}；其他来源（整合包加成、其他模组修饰符）仍全额计入。</p>
     *
     * @param sourceBonus 指定来源贡献的额外距离（如 Tetra 的 entity_reach 加成）
     * @param scale       该来源的计入比例，0~1
     */
    public static float effectiveMaxRange(SpearLike spear, Entity entity, double sourceBonus, double scale) {
        double extraReach = reachOverVanilla(entity);
        double weakened = weakenExtraReach(extraReach, sourceBonus, scale);
        if (entity instanceof Player player) {
            return (player.isCreative() ? spear.getMaxCreativeRange() : spear.getMaxRange()) + (float) weakened;
        }
        return spear.getMaxRange() * spear.getMobFactor() + (float) weakened;
    }

    /**
     * 只削弱指定来源的那部分额外距离，其他来源原样保留。
     *
     * @param extraReach  超出原版射程的总距离
     * @param sourceBonus 指定来源贡献的距离
     * @param scale       该来源的计入比例，0~1（超出范围会被夹紧）
     * @return 削弱后的总额外距离
     */
    public static double weakenExtraReach(double extraReach, double sourceBonus, double scale) {
        double extra = Math.max(0.0D, extraReach);
        double bonus = Math.max(0.0D, Math.min(extra, sourceBonus));
        double clampedScale = Math.max(0.0D, Math.min(1.0D, scale));
        return extra - bonus * (1.0D - clampedScale);
    }

    /**
     * 带来源缩放的射程；当来源加成没有被统计到时，把玩家当前的额外距离整体视为该来源。
     *
     * <p>这是给 Tetra 这类「属性修饰符不在 {@code ItemStack} 上」的模组兜底：宁可按配置比例削弱
     * 全部额外距离，也不要让 {@code tetraExtraReachScale} 静默失效。</p>
     */
    public static float effectiveMaxRangeWithFallback(SpearLike spear, Entity entity,
                                                      double sourceBonus, double scale) {
        double extra = reachOverVanilla(entity);
        double bonus = Math.max(0.0D, Math.min(extra, sourceBonus));
        if (extra > 0.0D && bonus <= 0.0D) {
            bonus = extra;
        }
        return effectiveMaxRange(spear, entity, bonus, scale);
    }

    /** 玩家/实体超出原版 3.0 的额外攻击距离；供调试命令与兜底逻辑使用。 */
    public static double extraReach(Entity entity) {
        return reachOverVanilla(entity);
    }

    private static double reachOverVanilla(Entity entity) {
        if (entity instanceof LivingEntity living && living.getAttribute(ForgeMod.ENTITY_REACH.get()) != null) {
            return Math.max(0.0D, living.getAttributeValue(ForgeMod.ENTITY_REACH.get()) - 3.0D);
        }
        return 0.0D;
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
