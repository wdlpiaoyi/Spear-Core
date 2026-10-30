package net.minecraft.spearcore.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.spearcore.event.SpearChargePhaseEvent;
import net.minecraft.spearcore.event.SpearDamageEvent;
import net.minecraft.spearcore.event.SpearHitEvent;
import net.minecraft.spearcore.init.SpearAttributes;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearSounds;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.spearcore.init.SpearDamageTypes;
import net.minecraft.spearcore.util.KnownMovementAccessor;
import net.minecraft.spearcore.util.SpearCollision;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.spearcore.util.SpearCooldownAccessor;
import net.minecraft.spearcore.util.UseSpearSpecialEntity;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;
import java.util.function.Predicate;
import net.minecraftforge.common.ForgeMod;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.spearcore.init.SpearEnchantments;
import net.minecraft.spearcore.util.SpearRules;

/**
 * SpearItem — 矛的核心类，严格基于 J库 ItemToolBaseSpearBase 移植。
 */
public abstract class SpearItem extends Item implements SpearLike {

    public SpearItem(Properties properties) {
        super(properties);
    }

    // ========== 基础属性 ==========
    public abstract float getAttackDuration();
    public abstract float getDamageMultiplier();
    public abstract SoundEvent getUseSound();
    public abstract SoundEvent getHitSound();
    public abstract SoundEvent getAttackSound();

    // ========== 蓄力阶段相关 ==========
    public abstract int getDelayTicks();
    public abstract int getDismountEndTick();
    public abstract int getKnockbackEndTick();
    public abstract int getDamageEndTick();
    public abstract Optional<SpearCondition> getDismountConditions();
    public abstract Optional<SpearCondition> getKnockbackConditions();
    public abstract Optional<SpearCondition> getDamageConditions();
    public abstract float getForwardMovement();
    public abstract float getMinRange();
    public abstract float getMaxRange();
    public abstract float getHitboxMargin();
    public abstract float getHitboxMargin2();
    public abstract int getContactCooldownTicks();
    public abstract float getSwingTimes();
    public abstract float getMinCreativeRange();
    public abstract float getMaxCreativeRange();
    public abstract float getMobFactor();
    public abstract boolean dealsKnockback();
    public abstract boolean dismounts();

    protected int getSpearEnchantmentValue() { return 0; }
    protected boolean canRepair(ItemStack stack, ItemStack repairCandidate) { return false; }
    /** 该实体是否正在蓄力持矛 */
    public static boolean isChargingSpear(LivingEntity entity) {
        return entity.isUsingItem()
                && !entity.getUseItem().isEmpty()
                && entity.getUseItem().getItem() instanceof SpearItem;
    }

    /** 蓄力进度 0~1 */
    public static float getSpearChargeProgress(LivingEntity entity) {
        if (!isChargingSpear(entity)
                || !(entity.getUseItem().getItem() instanceof SpearItem spear)) return 0.0F;
        ItemStack stack = entity.getUseItem();
        int usedTicks = stack.getUseDuration() - entity.getUseItemRemainingTicks();
        return Mth.clamp(usedTicks / (float) spear.getDamageEndTick(), 0.0F, 1.0F);
    }
    protected static double getSpearMultiplier(LivingEntity entity,
                                               net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance != null ? instance.getValue() : 1.0;
    }
    // ========== getKnownMovement / getMotion（J库 static） ==========

    private static Vec3 getKnownMovement(Entity entity) {
        if (entity instanceof KnownMovementAccessor accessor && entity instanceof ServerPlayer && entity.isAlive()) {
            return accessor.GetKnownMovement();
        }
        LivingEntity rider = entity.getControllingPassenger();
        if (rider instanceof Player && entity.isAlive()) {
            return getKnownMovement(rider);
        }
        return entity.getDeltaMovement();
    }

    public static Vec3 getMotion(Entity entity) {
        if (!(entity instanceof Player)
                && !(entity instanceof UseSpearSpecialEntity u && u.isJerotesSpearGetMotionLikePlayer())
                && entity.isPassenger()) {
            entity = entity.getRootVehicle();
        }
        Vec3 vec3 = getKnownMovement(entity).scale(20.0);
        if (entity.onGround()) {
            return vec3.with(Direction.Axis.Y, 0.0);
        }
        return vec3;
    }

    // ========== effectiveMinRange / effectiveMaxRange（J库） ==========

    public float effectiveMinRange(Entity entity) {
        return SpearCombat.effectiveMinRange(this, entity);
    }

    public float effectiveMaxRange(Entity entity) {
        return SpearCombat.effectiveMaxRange(this, entity);
    }

    // ========== use（J库） ==========

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 冷却期间不能起手蓄力（基岩版戳刺后的后摇，以及基岩版的使用冷却本身）。
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        // 基岩版不能用副手举矛蓄力。
        if (hand == InteractionHand.OFF_HAND && SpearRules.blocksOffhandCharging()) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        makeSound(player);
        return InteractionResultHolder.consume(stack);
    }

    private void makeSound(Entity entity) {
        if (entity instanceof Player player) {
            entity.level().playSound(player, entity.getX(), entity.getY(), entity.getZ(),
                    getUseSound(), entity.getSoundSource(), 1.0f, 1.0f);
        } else {
            entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    getUseSound(), entity.getSoundSource(), 1.0f, 1.0f);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    public int computeDamageUseDuration() {
        return getDelayTicks() + getDamageConditions().map(SpearCondition::maxDurationTicks).orElse(0);
    }

    /** 按这个长矛的攻击速度折算出来的使用冷却 tick 数（基岩版戳刺冷却 / 戳刺后摇都用它）。 */
    public int getUseCooldownTicks() {
        return Math.round(getAttackDuration() * 20.0F);
    }

    /**
     * 蓄力伤害的基线。
     *
     * <p>JAVA：取裸武器攻击力，力量/虚弱等修饰符不算（原版 MC-302778）。</p>
     * <p>BEDROCK：取包含修饰符的攻击力，力量药水会加进蓄力伤害。</p>
     */
    public float chargeBaseDamage(LivingEntity user) {
        return SpearCombat.chargeBaseDamage(user, this);
    }
    // ========== onUseTick — 等价于 J库 damageEntities ==========

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingTicks) {
        SpearCombat.tickCharge(user, stack, this);
    }

    // ========== attack（J库） ==========

    public void attack(LivingEntity attacker, EquipmentSlot slot) {
        SpearCombat.attack(attacker, attacker.getItemBySlot(slot), slot, this);
    }

    // ========== performStabAttack（兼容 SpearStabAttackPacket） ==========

    public boolean performStabAttack(LivingEntity attacker, ItemStack stack, EquipmentSlot slot, boolean playSound) {
        return SpearCombat.performStabAttack(attacker, stack, slot, playSound);
    }

    // ========== stabAttack（J库核心） ==========

    // ========== canHitEntity（J库 static） ==========

    public static boolean canHitEntity(Entity attacker, Entity target) {
        if (!target.canBeHitByProjectile()) return false;
        if (!target.isAlive() || target == attacker) return false;
        if (target == attacker.getVehicle()) return false;
        if (attacker.isPassengerOfSameVehicle(target)) return false;
        if (attacker instanceof Player p1 && target instanceof Player p2 && !p1.canHarmPlayer(p2)) return false;
        return hasLineOfSight(attacker, target);
    }

    public static boolean hasLineOfSight(Entity from, Entity to) {
        if (to.level() != from.level()) return false;
        Vec3 fromEye = from.getEyePosition();
        Vec3 toCenter = to.position().add(0, to.getBbHeight() / 2, 0);
        BlockHitResult hit = from.level().clip(
                new ClipContext(fromEye, toCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ========== getHitEntitiesAlong（J库） ==========

    /** 客户端渲染实体比服务器晚约 3 tick；移动靶补偿按这个 tick 数回退。 */
    private static final double MOVING_TARGET_COMPENSATION_TICKS = 3.0;

    /**
     * 兼容旧签名：AvaritiaSpear 1.4.0 的 mixin 按
     * {@code (LivingEntity, SpearItem, float, Predicate)List} 这个描述符注入（remap=false）。
     * 不要删除、也不要改参数类型——只能新增重载。
     */
    public static List<EntityHitResult> getHitEntitiesAlong(LivingEntity user, SpearItem spear,
                                                             float hitboxMargin, Predicate<Entity> predicate) {
        return getHitEntitiesAlongInternal(user, spear, hitboxMargin, predicate);
    }

    /**
     * 新的通用签名（SpearItem 与 Tetra 长矛共用入口）。
     * SpearItem 走旧签名重载，保证第三方注入（如 AvaritiaSpear 的秒杀）真的作用在执行路径上。
     */
    public static List<EntityHitResult> getHitEntitiesAlong(LivingEntity user, SpearLike spear,
                                                             float hitboxMargin, Predicate<Entity> predicate) {
        if (spear instanceof SpearItem item) {
            return getHitEntitiesAlong(user, item, hitboxMargin, predicate);
        }
        return getHitEntitiesAlongInternal(user, spear, hitboxMargin, predicate);
    }

    private static List<EntityHitResult> getHitEntitiesAlongInternal(LivingEntity user, SpearLike spear,
                                                                     float hitboxMargin, Predicate<Entity> predicate) {
        Vec3 look = getHeadLookAngle(user);
        Vec3 eyePos = user.getEyePosition();
        Vec3 start = eyePos.add(look.scale(spear.effectiveMinRange(user)));
        double speedBonus = getKnownMovement(user).dot(look);
        Vec3 end = eyePos.add(look.scale((double) spear.effectiveMaxRange(user) + Math.max(0.0, speedBonus)));

        Level level = user.level();
        BlockHitResult blockHit = clipIncludingBorder(
                new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user), level);
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        AABB searchBox = AABB.ofSize(start, hitboxMargin, hitboxMargin, hitboxMargin)
                .expandTowards(end.subtract(start)).inflate(1.0 + movingTargetCompensation(user));

        List<EntityHitResult> results = new ArrayList<>();
        for (Entity target : level.getEntities(user, searchBox, predicate)) {
            AABB targetBox = compensatedTargetBox(user, target, hitboxMargin);
            if (targetBox.contains(start)) {
                results.add(new EntityHitResult(target, start));
                continue;
            }
            Optional<Vec3> hit = targetBox.clip(start, end);
            hit.ifPresent(vec3 -> results.add(new EntityHitResult(target, vec3)));
        }

        results.sort((a, b) -> Double.compare(
                a.getLocation().distanceToSqr(start),
                b.getLocation().distanceToSqr(start)));
        return results;
    }

    /**
     * 目标判定箱。
     *
     * <p>MODDED 的移动靶补偿：服务器按目标当前位置判定，而客户端把实体延迟约 3 tick 渲染，
     * 打横向移动的目标时准星对的是"看到的位置"，会差出近一个身位（MC-310857）。
     * 这里把判定箱按目标最近 3 tick 的位移往回挪，等价于"打你看到的位置"。
     * 只对玩家出手生效，怪物 AI 不受影响。</p>
     */
    public static AABB compensatedTargetBox(Entity attacker, Entity target, float hitboxMargin) {
        AABB box = target.getBoundingBox();
        if (SpearRules.compensatesMovingTargets() && attacker instanceof Player) {
            box = box.move(getKnownMovement(target).scale(-MOVING_TARGET_COMPENSATION_TICKS));
        }
        return box.inflate(hitboxMargin);
    }

    /** 候选实体搜索框要多留的余量（补偿位移可能超出目标当前位置）。 */
    public static double movingTargetCompensation(Entity attacker) {
        return SpearRules.compensatesMovingTargets() && attacker instanceof Player
                ? MOVING_TARGET_COMPENSATION_TICKS : 0.0;
    }

    private static Vec3 getHeadLookAngle(Entity entity) {
        return calculateViewVector(entity.getXRot(), entity.getYHeadRot());
    }

    private static Vec3 calculateViewVector(float xRot, float yRot) {
        float f = xRot * ((float) Math.PI / 180F);
        float f1 = -yRot * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3(f3 * f4, -f5, f2 * f4);
    }

    private static BlockHitResult clipIncludingBorder(ClipContext ctx, Level level) {
        BlockHitResult result = level.clip(ctx);
        WorldBorder border = level.getWorldBorder();
        if (border.isWithinBounds(BlockPos.containing(ctx.getFrom()))
                && !border.isWithinBounds(BlockPos.containing(result.getLocation()))) {
            Vec3 diff = result.getLocation().subtract(ctx.getFrom());
            Direction dir = getApproximateNearest((float) diff.x, (float) diff.y, (float) diff.z);
            Vec3 clamped = new Vec3(
                    Mth.clamp(result.getLocation().x, border.getMinX(), border.getMaxX() - 1.0E-5f),
                    result.getLocation().y,
                    Mth.clamp(result.getLocation().z, border.getMinZ(), border.getMaxZ() - 1.0E-5f));
            return new BlockHitResult(clamped, dir, BlockPos.containing(clamped), false);
        }
        return result;
    }

    private static Direction getApproximateNearest(float x, float y, float z) {
        Direction best = Direction.NORTH;
        float bestVal = Float.MIN_VALUE;
        for (Direction dir : Direction.values()) {
            float val = x * dir.getStepX() + y * dir.getStepY() + z * dir.getStepZ();
            if (val > bestVal) {
                bestVal = val;
                best = dir;
            }
        }
        return best;
    }

    // ========== Lunge ==========

    public static void jerotesLungeForwardMaybe(LivingEntity user) {
        if (!(user.getMainHandItem().getItem() instanceof SpearItem)) {
            return;
        }
        ItemStack stack = user.getMainHandItem();
        int lungeLevel = EnchantmentHelper.getItemEnchantmentLevel(SpearEnchantments.LUNGE.get(), stack);

        if (lungeLevel <= 0) {
            return;
        }
        // 饥饿门槛：JAVA（原版）<6 就禁用；MODDED 饥饿值 ≤7 禁用。
        if (user instanceof Player player && SpearRules.lungeBlockedByHunger(player.getFoodData().getFoodLevel())) {
            return;
        }
        if (user.isPassenger() || user.isFallFlying() || user.isInWater()) {
            return;
        }

        if (stack.isDamageableItem() && user instanceof Player) {
            stack.hurtAndBreak(1, user, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        if (user instanceof Player player) {
            player.causeFoodExhaustion(4.0f * lungeLevel);
        }

        rushAttack(user, SpearRules.lungeStrength() * lungeLevel);

        SoundEvent sound = SpearSounds.ITEM_SPEAR_LUNGE_1.get();
        if (lungeLevel > 1) {
            sound = SpearSounds.ITEM_SPEAR_LUNGE_2.get();
        }
        if (lungeLevel > 2) {
            sound = SpearSounds.ITEM_SPEAR_LUNGE_3.get();
        }
        user.level().playSound(null, user.getX(), user.getY(), user.getZ(),
                sound, user.getSoundSource(), 1.0F, 1.0F);
    }

    public static boolean rushAttack(LivingEntity user, float strength) {
        Vec3 look = user.getLookAngle();
        user.push(look.x * strength, 0.0D, look.z * strength);
        return true;
    }

    // ========== 附魔 ==========

    @Override
    public boolean isEnchantable(ItemStack stack) { return true; }

    /**
     * 复刻 1.21 数据包里的可附魔范围。
     *
     * <p>1.21 上矛能被锋利/亡灵杀手/节肢杀手/击退/火焰附加/抢夺/耐久/经验修补附上，
     * 靠的是 {@code data/minecraft/tags/item/enchantable/*.json}；1.20.1 没有这套标签，
     * 适用范围硬编码在 {@link net.minecraft.world.item.enchantment.EnchantmentCategory} 里
     * （原版 WEAPON 只认 SwordItem），所以这里用 Forge 的物品侧钩子补回来 ——
     * 附魔台与铁砧都会经过它。</p>
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return SpearEnchantments.supportsVanilla(enchantment)
                || super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public int getEnchantmentValue() { return getSpearEnchantmentValue(); }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return canRepair(stack, repairCandidate);
    }

    // ========== 工具类 ==========

    public static final class SlotUtil {
        public static EquipmentSlot slotForHand(LivingEntity user, InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        }
    }
}
