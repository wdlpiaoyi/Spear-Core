package net.minecraft.spearcore.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.config.SpearConfig;
import net.minecraft.spearcore.event.SpearDamageEvent;
import net.minecraft.spearcore.event.SpearHitEvent;
import net.minecraft.spearcore.event.ThreePointBladeHandler;
import net.minecraft.spearcore.compat.tetra.TetraSpearStats;
import net.minecraft.spearcore.event.SpearMobEquipHandler;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearCoreItems;
import net.minecraft.spearcore.init.SpearDamageTypes;
import net.minecraft.spearcore.init.SpearStats;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.util.SpearCooldownAccessor;
import net.minecraft.spearcore.util.SpearCollision;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTest：由游戏本体自己跑、自己判定、自己报告失败数。
 *
 * <p>判定就是退出码与日志里的 "All N required tests passed :)" /
 * "N required tests failed :("；模板结构由 gameteststructures/ 提供，
 * Gradle 的 stageGameTestStructures 任务负责拷进 run/。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpearGameTests {

    private static final String TEMPLATE = "empty5x5x5";

    private SpearGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(SpearGameTests.class);
    }

    /** Stats.Builder 的关键字段不能因为链式调用/参数顺序错位。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void statsBuilderKeepsValues(GameTestHelper helper) {
        SpearStats.Stats stats = new SpearStats.Stats.Builder()
                .durability(123)
                .attackDuration(0.9F)
                .damageMultiplier(1.1F)
                .attackDamageBonus(3.0F)
                .enchantmentValue(15)
                .rarity(Rarity.RARE)
                .repairIngredient(Ingredient.of(Items.IRON_INGOT))
                .fireResistant(true)
                .materialName("test_spear")
                .swingTimes(0.9F)
                .hitboxMargin(0.2F)
                .contactCooldownTicks(9)
                .delayTicks(11)
                .forwardMovement(0.38F)
                .minRange(2.0F)
                .maxRange(4.5F)
                .minCreativeRange(2.0F)
                .maxCreativeRange(6.5F)
                .hitboxMargin2(0.25F)
                .mobFactor(0.5F)
                .dealsKnockback(true)
                .dismounts(true)
                .build();
        helper.assertTrue(stats.durability() == 123 && stats.attackDuration() == 0.9F
                        && stats.damageMultiplier() == 1.1F && stats.attackDamageBonus() == 3.0F
                        && stats.enchantmentValue() == 15 && stats.rarity() == Rarity.RARE
                        && stats.fireResistant() && "test_spear".equals(stats.materialName())
                        && stats.swingTimes() == 0.9F && stats.hitboxMargin() == 0.2F
                        && stats.contactCooldownTicks() == 9 && stats.delayTicks() == 11
                        && stats.hitboxMargin2() == 0.25F && stats.mobFactor() == 0.5F
                        && stats.dealsKnockback() && stats.dismounts(),
                "Stats.Builder 字段顺序错位");
        helper.succeed();
    }

    /** 七把矛的实例必须都是 SpearItem —— 所有行为判定的前提。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void allSpearsAreSpearItems(GameTestHelper helper) {
        Item[] items = {
                SpearCoreItems.WOODEN_SPEAR.get(),
                SpearCoreItems.STONE_SPEAR.get(),
                SpearCoreItems.COPPER_SPEAR.get(),
                SpearCoreItems.IRON_SPEAR.get(),
                SpearCoreItems.GOLDEN_SPEAR.get(),
                SpearCoreItems.DIAMOND_SPEAR.get(),
                SpearCoreItems.NETHERITE_SPEAR.get(),
        };
        for (Item item : items) {
            helper.assertTrue(item instanceof SpearItem, "有物品不是 SpearItem 子类");
        }
        helper.succeed();
    }

    /** 构造时写入 SpearStats 表的数值必须取得到。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearStatsAreRegistered(GameTestHelper helper) {
        helper.assertTrue(SpearStats.get(SpearCoreItems.IRON_SPEAR.get()).durability() == 250,
                "铁矛耐久应为 250，实际 " + SpearStats.get(SpearCoreItems.IRON_SPEAR.get()).durability());
        helper.assertTrue(SpearStats.get(SpearCoreItems.NETHERITE_SPEAR.get()).fireResistant(),
                "下界合金矛应当防火");
        helper.assertFalse(SpearStats.get(SpearCoreItems.IRON_SPEAR.get()).fireResistant(),
                "铁矛不应防火");
        helper.succeed();
    }

    /** 属性修饰符只该有攻击力/攻击速度：射程由 stats 的 2/4.5 加上持有者 reach 超出 3.0 的部分换算，物品上不能再挂一份。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearAttributesAreRegistered(GameTestHelper helper) {
        ItemStack stack = new ItemStack(SpearCoreItems.IRON_SPEAR.get());
        var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        helper.assertTrue(modifiers.containsKey(Attributes.ATTACK_DAMAGE), "缺少攻击力修饰符");
        helper.assertTrue(modifiers.containsKey(Attributes.ATTACK_SPEED), "缺少攻击速度修饰符");
        helper.assertFalse(modifiers.containsKey(ForgeMod.ENTITY_REACH.get()),
                "长矛不应再挂 ENTITY_REACH 修饰符（射程已由 stats + 持有者 reach 换算，重复计入会变成 6 格）");
        helper.succeed();
    }

    /** 攻击距离必须等于原版：玩家 2~4.5、创造 6.5、怪物 2.25。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void attackRangeFollowsVanilla(GameTestHelper helper) {
        for (Item item : spearcore$allSpears()) {
            SpearItem spear = (SpearItem) item;
            helper.assertTrue(spear.getMinRange() == 2.0F, "最小攻击距离应为 2，实际 " + spear.getMinRange());
            helper.assertTrue(spear.getMaxRange() == 4.5F, "最大攻击距离应为 4.5，实际 " + spear.getMaxRange());
            helper.assertTrue(spear.getMaxCreativeRange() == 6.5F,
                    "创造模式最大攻击距离应为 6.5，实际 " + spear.getMaxCreativeRange());
            helper.assertTrue(spear.getMobFactor() == 0.5F, "怪物射程系数应为 0.5，实际 " + spear.getMobFactor());
        }
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        helper.assertTrue(Math.abs(iron.effectiveMaxRange(mob) - 2.25F) < 1.0e-4F,
                "怪物最大攻击距离应为 2.25，实际 " + iron.effectiveMaxRange(mob));
        helper.succeed();
    }

    /** 整合包给持有者加 reach 时，长矛射程必须跟着变长（不能被上面的修复一刀切掉）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void reachBonusStillApplies(GameTestHelper helper) {
        Player player = helper.makeMockSurvivalPlayer();
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        helper.assertTrue(Math.abs(iron.effectiveMaxRange(player) - 4.5F) < 1.0e-4F,
                "无加成时玩家最大攻击距离应为 4.5，实际 " + iron.effectiveMaxRange(player));
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        helper.assertTrue(reach != null, "测试玩家没有 ENTITY_REACH 属性");
        AttributeModifier bonus = new AttributeModifier(UUID.randomUUID(), "game test reach",
                2.0D, AttributeModifier.Operation.ADDITION);
        reach.addTransientModifier(bonus);
        try {
            helper.assertTrue(Math.abs(iron.effectiveMaxRange(player) - 6.5F) < 1.0e-4F,
                    "reach +2 没有计入长矛射程，实际 " + iron.effectiveMaxRange(player));
        } finally {
            reach.removeModifier(bonus);
        }
        helper.succeed();
    }

    /** 判定箱对齐原版：蓄力 0.125、戳刺 0.25。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void jabHitboxMatchesVanilla(GameTestHelper helper) {
        for (Item item : spearcore$allSpears()) {
            SpearItem spear = (SpearItem) item;
            helper.assertTrue(spear.getHitboxMargin() == 0.125F,
                    "蓄力判定箱应为 0.125，实际 " + spear.getHitboxMargin());
            helper.assertTrue(spear.getHitboxMargin2() == 0.25F,
                    "戳刺判定箱应为 0.25，实际 " + spear.getHitboxMargin2());
        }
        helper.succeed();
    }

    /**
     * 真正打一次：持矛实体戳中正前方的目标，目标掉血。
     *
     * <p>这条同时是"世界刚开时戳刺会被静默跳过"那个 bug 的回归测试——
     * GameTest 跑在全新世界里，gameTime 很小，正好命中该边界。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void stabDamagesTarget(GameTestHelper helper) {
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        ItemStack spear = new ItemStack(SpearCoreItems.IRON_SPEAR.get());
        attacker.setItemSlot(EquipmentSlot.MAINHAND, spear);
        attacker.setYRot(0.0F);
        attacker.setXRot(0.0F);
        attacker.setYHeadRot(0.0F);

        // 关键：怪物有 mobFactor（默认 0.5）缩减，有效射程只有玩家的一半，
        // 不能按玩家的 minRange/maxRange 摆目标，否则永远打不中。
        SpearItem probe = (SpearItem) spear.getItem();
        double minRange = probe.effectiveMinRange(attacker);
        double maxRange = probe.effectiveMaxRange(attacker);
        helper.assertTrue(maxRange > minRange && minRange >= 0.0,
                "怪物有效攻击距离不合法：" + minRange + ".." + maxRange);
        int targetZ = 1 + Math.max(1, (int) Math.round((minRange + maxRange) / 2.0));

        Mob target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, targetZ);
        float before = target.getHealth();

        SpearItem spearItem = (SpearItem) spear.getItem();
        boolean hit = spearItem.performStabAttack(attacker, spear, EquipmentSlot.MAINHAND, false);

        helper.assertTrue(hit, "戳刺没有命中目标（怪物射程 " + spearItem.effectiveMinRange(attacker)
                + ".." + spearItem.effectiveMaxRange(attacker) + "，目标在 " + (targetZ - 1) + " 格外）");
        helper.assertTrue(target.getHealth() < before,
                "戳刺没有造成伤害（" + before + " -> " + target.getHealth() + "）");
        helper.succeed();
    }

    /**
     * 命中目标后给攻击者自己减速（水平速度 ×0.6）并由 setSprinting(false) 取消奔跑，
     * 这个"撞完得重新起跑"的手感由 {@code behavior.hitSlowDown} 控制：
     * JAVA（默认）有、MODDED 没有。
     *
     * <p>两段都在同一个测试里跑，避免翻配置影响到并行/同批次的其他测试。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void hitMomentumFollowsConfig(GameTestHelper helper) {
        // 测试不依赖本地配置文件：整段都在 try/finally 里，断言失败也不会把配置泄漏给后续测试
        SpearRules.HitSlowDown original = SpearConfig.HIT_SLOW_DOWN.get();
        double vanillaSpeed;
        double moddedSpeed;
        try {
            SpearConfig.HIT_SLOW_DOWN.set(SpearRules.HitSlowDown.JAVA);
            helper.assertTrue(SpearRules.slowsDownAttackerOnHit(),
                    "hitSlowDown 默认应为 JAVA（测试内已强制复位）");

            // 第一段：JAVA —— 命中后自己也被削到 0.3 × 0.6 = 0.18
            vanillaSpeed = spearcore$stabAndReadSpeed(helper, 1, 1);
            helper.assertTrue(Math.abs(vanillaSpeed - 0.18D) < 1.0E-6D,
                    "JAVA 模式命中后自己应减速到 0.18，实际 x 速度 " + vanillaSpeed);

            // 第二段：MODDED —— 命中后速度不变，不用重新起跑
            SpearConfig.HIT_SLOW_DOWN.set(SpearRules.HitSlowDown.MODDED);
            moddedSpeed = spearcore$stabAndReadSpeed(helper, 3, 1);
        } finally {
            SpearConfig.HIT_SLOW_DOWN.set(original);
        }
        helper.assertTrue(Math.abs(moddedSpeed - 0.3D) < 1.0E-6D,
                "MODDED 模式命中不该减速，x 速度变成了 " + moddedSpeed);

        helper.succeed();
    }

    /** 耐久按 behavior.durability 切换：JAVA 金 32 / BEDROCK 33 / MODDED 59。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void durabilityFollowsConfig(GameTestHelper helper) {
        SpearRules.Durability original = SpearConfig.DURABILITY.get();
        try {
            SpearConfig.DURABILITY.set(SpearRules.Durability.JAVA);
            helper.assertTrue(spearcore$maxDamage(SpearCoreItems.GOLDEN_SPEAR.get()) == 32,
                    "JAVA 金矛耐久应为 32，实际 " + spearcore$maxDamage(SpearCoreItems.GOLDEN_SPEAR.get()));
            helper.assertTrue(spearcore$maxDamage(SpearCoreItems.IRON_SPEAR.get()) == 250,
                    "JAVA 铁矛耐久应为 250，实际 " + spearcore$maxDamage(SpearCoreItems.IRON_SPEAR.get()));

            SpearConfig.DURABILITY.set(SpearRules.Durability.BEDROCK);
            helper.assertTrue(spearcore$maxDamage(SpearCoreItems.GOLDEN_SPEAR.get()) == 33,
                    "BEDROCK 金矛耐久应为 33，实际 " + spearcore$maxDamage(SpearCoreItems.GOLDEN_SPEAR.get()));
            helper.assertTrue(spearcore$maxDamage(SpearCoreItems.IRON_SPEAR.get()) == 251,
                    "BEDROCK 铁矛耐久应为 251，实际 " + spearcore$maxDamage(SpearCoreItems.IRON_SPEAR.get()));

            SpearConfig.DURABILITY.set(SpearRules.Durability.MODDED);
            helper.assertTrue(spearcore$maxDamage(SpearCoreItems.GOLDEN_SPEAR.get()) == 59,
                    "MODDED 金矛耐久应为 59，实际 " + spearcore$maxDamage(SpearCoreItems.GOLDEN_SPEAR.get()));
        } finally {
            SpearConfig.DURABILITY.set(original);
        }
        helper.succeed();
    }

    /**
     * 矛盾共持（behavior.shieldCoWield）：
     * <ul>
     *   <li>{@code JAVA}：右键永远蓄力、副手盾不格挡（原版 Java 版）；</li>
     *   <li>{@code BEDROCK}：潜行即自动举盾，起身收盾（原版基岩版的盾牌机制）；</li>
     *   <li>{@code MODDED}：潜行之外还要按右键才举盾，起身收盾。</li>
     * </ul>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void shieldCoWieldFollowsConfig(GameTestHelper helper) {
        SpearRules.ShieldCoWield original = SpearConfig.SHIELD_CO_WIELD.get();
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        ItemStack stack = new ItemStack(iron);
        try {
            Player player = helper.makeMockPlayer();
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.setShiftKeyDown(true);

            // JAVA：老行为不变——潜行 + 副手盾时右键仍是蓄力，也不会自动举盾/收盾。
            SpearConfig.SHIELD_CO_WIELD.set(SpearRules.ShieldCoWield.JAVA);
            helper.assertTrue(!SpearCombat.offhandShieldTakesPriority(true, true),
                    "JAVA 档不能让右键给副手盾");
            helper.assertTrue(!SpearCombat.autoRaisesOffhandShield(true, true),
                    "JAVA 档不能自动举盾");
            helper.assertTrue(!SpearCombat.shouldReleaseOffhandShield(false, true, true),
                    "JAVA 档不能打断原版盾牌");
            helper.assertTrue(SpearCombat.tryStartCharging(player.level(), player,
                            InteractionHand.MAIN_HAND, stack, iron).getResult().consumesAction(),
                    "JAVA 档潜行时右键仍要起手蓄力");
            player.stopUsingItem();

            // MODDED：潜行 + 右键才举盾（不自动举），起身收盾。
            SpearConfig.SHIELD_CO_WIELD.set(SpearRules.ShieldCoWield.MODDED);
            helper.assertTrue(SpearCombat.offhandShieldTakesPriority(true, true),
                    "MODDED 档潜行时右键要让给副手盾");
            helper.assertTrue(!SpearCombat.offhandShieldTakesPriority(false, true),
                    "MODDED 档不潜行时右键仍是蓄力");
            helper.assertTrue(!SpearCombat.offhandShieldTakesPriority(true, false),
                    "MODDED 档没有副手盾时右键仍是蓄力");
            helper.assertTrue(!SpearCombat.autoRaisesOffhandShield(true, true),
                    "MODDED 档不能自动举盾");
            helper.assertTrue(SpearCombat.shouldReleaseOffhandShield(false, true, true),
                    "MODDED 档起身要收盾");
            helper.assertTrue(!SpearCombat.shouldReleaseOffhandShield(true, true, true),
                    "MODDED 档蹲着不能收盾");
            helper.assertTrue(!SpearCombat.shouldReleaseOffhandShield(false, false, true),
                    "MODDED 档没在用盾时不用收");
            helper.assertTrue(!SpearCombat.shouldReleaseOffhandShield(false, true, false),
                    "MODDED 档主手不是矛时不影响原版盾牌");
            helper.assertTrue(!SpearCombat.tryStartCharging(player.level(), player,
                            InteractionHand.MAIN_HAND, stack, iron).getResult().consumesAction(),
                    "MODDED 档潜行 + 副手盾时右键必须让给盾牌");
            helper.assertTrue(!player.isUsingItem(), "让给盾牌时不能启动长矛蓄力");

            // MODDED：模拟右键举起副手盾，再站起来，tick 后应自动收盾。
            player.startUsingItem(InteractionHand.OFF_HAND);
            helper.assertTrue(player.isUsingItem(), "测试前置：副手盾应处于使用中");
            player.setShiftKeyDown(false);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(!player.isUsingItem(), "MODDED 档起身收盾必须生效");

            // BEDROCK：潜行即自动举盾（不用右键），起身自动收盾。
            SpearConfig.SHIELD_CO_WIELD.set(SpearRules.ShieldCoWield.BEDROCK);
            helper.assertTrue(SpearCombat.offhandShieldTakesPriority(true, true),
                    "BEDROCK 档潜行时右键要让给副手盾");
            helper.assertTrue(SpearCombat.autoRaisesOffhandShield(true, true),
                    "BEDROCK 档潜行要自动举盾");
            helper.assertTrue(!SpearCombat.autoRaisesOffhandShield(false, true),
                    "BEDROCK 档不潜行时不能自动举盾");
            helper.assertTrue(!SpearCombat.autoRaisesOffhandShield(true, false),
                    "BEDROCK 档没有副手盾时不能自动举盾");
            player.setShiftKeyDown(true);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND,
                    "BEDROCK 档潜行 tick 后必须自动举起副手盾");
            player.setShiftKeyDown(false);
            MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            helper.assertTrue(!player.isUsingItem(), "BEDROCK 档起身收盾必须生效");
        } finally {
            SpearConfig.SHIELD_CO_WIELD.set(original);
        }
        helper.succeed();
    }

    /**
     * 横扫：半径 0.3×射程，判定只按**命中实体**周围的判定箱。
     *
     * <p>旧实现夹了 2~4 的下限、还按玩家距离过滤，长手命中（4.5 格）时剑气只覆盖自己脚边。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void sweepIsCenteredOnHitTarget(GameTestHelper helper) {
        helper.assertTrue(Math.abs(SpearCombat.sweepRadius(4.5F) - 1.35F) < 1.0E-6F,
                "4.5 射程的横扫半径应为 1.35，实际 " + SpearCombat.sweepRadius(4.5F));
        helper.assertTrue(Math.abs(SpearCombat.sweepRadius(6.5F) - 1.95F) < 1.0E-6F,
                "创造 6.5 射程的横扫半径应为 1.95，实际 " + SpearCombat.sweepRadius(6.5F));

        Mob target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 3);
        Mob nearTarget = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 1, 2);
        Mob nearPlayer = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 1, 4);
        // 把「离玩家很近」的那只挪到玩家身边（命中实体在 4 格外，正好是长矛的典型命中距离）。
        nearPlayer.moveTo(target.getX() + 4.0D, target.getY(), target.getZ());
        Player player = helper.makeMockPlayer();
        player.moveTo(nearPlayer.getX(), nearPlayer.getY(), nearPlayer.getZ());

        float radius = SpearCombat.sweepRadius(4.5F);
        helper.assertTrue(SpearCombat.isSweptBy(player, target, nearTarget, radius),
                "命中实体旁边的目标必须被横扫到（哪怕离玩家 4 格以上）");
        helper.assertTrue(!SpearCombat.isSweptBy(player, target, nearPlayer, radius),
                "贴着玩家、但离命中实体很远的目标不能被横扫到（判定只按命中实体）");
        helper.assertTrue(!SpearCombat.isSweptBy(player, target, target, radius),
                "主目标不参与横扫，避免重复结算");
        helper.assertTrue(!SpearCombat.isSweptBy(player, target, player, radius),
                "攻击者自己不能被自己的横扫打到");
        helper.succeed();
    }

    /** 蓄力判定箱：JAVA 0.125 / MODDED 0.25。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void hitboxMarginFollowsConfig(GameTestHelper helper) {        SpearRules.ChargeHitboxMargin original = SpearConfig.CHARGE_HITBOX_MARGIN.get();
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        try {
            SpearConfig.CHARGE_HITBOX_MARGIN.set(SpearRules.ChargeHitboxMargin.JAVA);
            helper.assertTrue(Math.abs(iron.getHitboxMargin() - 0.125F) < 1.0E-6F,
                    "JAVA 判定箱膨胀应为 0.125，实际 " + iron.getHitboxMargin());
            SpearConfig.CHARGE_HITBOX_MARGIN.set(SpearRules.ChargeHitboxMargin.MODDED);
            helper.assertTrue(Math.abs(iron.getHitboxMargin() - 0.25F) < 1.0E-6F,
                    "MODDED 判定箱膨胀应为 0.25，实际 " + iron.getHitboxMargin());
        } finally {
            SpearConfig.CHARGE_HITBOX_MARGIN.set(original);
        }
        helper.succeed();
    }

    /** 蓄力判定箱开关对内建矛与 Tetra 拟合矛都要生效。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraChargeHitboxFollowsConfig(GameTestHelper helper) {
        SpearRules.ChargeHitboxMargin original = SpearConfig.CHARGE_HITBOX_MARGIN.get();
        try {
            SpearConfig.CHARGE_HITBOX_MARGIN.set(SpearRules.ChargeHitboxMargin.JAVA);
            helper.assertTrue(Math.abs(TetraSpearStats.fit(4.0D, -2.0D).getHitboxMargin() - 0.125F) < 1.0E-6F,
                    "Tetra 蓄力判定箱在 JAVA 档应为 0.125");
            SpearConfig.CHARGE_HITBOX_MARGIN.set(SpearRules.ChargeHitboxMargin.MODDED);
            helper.assertTrue(Math.abs(TetraSpearStats.fit(4.0D, -2.0D).getHitboxMargin() - 0.25F) < 1.0E-6F,
                    "Tetra 蓄力判定箱在 MODDED 档应为 0.25");
        } finally {
            SpearConfig.CHARGE_HITBOX_MARGIN.set(original);
        }
        helper.succeed();
    }

    /** 子类 finalizeSpawn 的 populateDefaultEquipmentSlots 不能把矛覆盖掉。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void mobEquipmentSurvivesSubclassOverrides(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ZombifiedPiglin piglin = EntityType.ZOMBIFIED_PIGLIN.create(level);
        if (piglin == null) {
            helper.fail("无法创建僵尸猪灵");
            return;
        }
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        piglin.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        piglin.finalizeSpawn(level, level.getCurrentDifficultyAt(piglin.blockPosition()),
                MobSpawnType.COMMAND, null, null);
        helper.assertTrue(piglin.getMainHandItem().is(Items.GOLDEN_SWORD),
                "前置：僵尸猪灵的 finalizeSpawn 应已把主手设成金剑，实际 " + piglin.getMainHandItem());
        SpearMobEquipHandler.queueForTest(piglin,
                new ItemStack(SpearCoreItems.GOLDEN_SPEAR.get()), 0.085F);
        level.addFreshEntity(piglin);
        helper.assertTrue(piglin.getMainHandItem().getItem() instanceof SpearItem,
                "加入世界后应被 EntityJoinLevelEvent 换成金矛，实际 " + piglin.getMainHandItem());
        helper.succeed();
    }

    /** 创造模式攻击距离：JAVA 6.5 / BEDROCK 7.5。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void creativeRangeFollowsConfig(GameTestHelper helper) {
        SpearRules.CreativeMaxRange original = SpearConfig.CREATIVE_MAX_RANGE.get();
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        try {
            SpearConfig.CREATIVE_MAX_RANGE.set(SpearRules.CreativeMaxRange.JAVA);
            helper.assertTrue(Math.abs(iron.getMaxCreativeRange() - 6.5F) < 1.0E-6F,
                    "JAVA 创造距离上限应为 6.5，实际 " + iron.getMaxCreativeRange());
            SpearConfig.CREATIVE_MAX_RANGE.set(SpearRules.CreativeMaxRange.BEDROCK);
            helper.assertTrue(Math.abs(iron.getMaxCreativeRange() - 7.5F) < 1.0E-6F,
                    "BEDROCK 创造距离上限应为 7.5，实际 " + iron.getMaxCreativeRange());
        } finally {
            SpearConfig.CREATIVE_MAX_RANGE.set(original);
        }
        helper.succeed();
    }

    /** 戳刺伤害：JAVA 铁矛 +2 攻击力 / BEDROCK +3（每级 +1 点）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void jabDamageFollowsConfig(GameTestHelper helper) {
        SpearRules.JabDamage original = SpearConfig.JAB_DAMAGE.get();
        try {
            SpearConfig.JAB_DAMAGE.set(SpearRules.JabDamage.JAVA);
            helper.assertTrue(Math.abs(spearcore$attackDamageBonus(SpearCoreItems.IRON_SPEAR.get()) - 2.0F) < 1.0E-6F,
                    "JAVA 铁矛攻击力加成应为 2，实际 " + spearcore$attackDamageBonus(SpearCoreItems.IRON_SPEAR.get()));
            SpearConfig.JAB_DAMAGE.set(SpearRules.JabDamage.BEDROCK);
            helper.assertTrue(Math.abs(spearcore$attackDamageBonus(SpearCoreItems.IRON_SPEAR.get()) - 3.0F) < 1.0E-6F,
                    "BEDROCK 铁矛攻击力加成应为 3，实际 " + spearcore$attackDamageBonus(SpearCoreItems.IRON_SPEAR.get()));
        } finally {
            SpearConfig.JAB_DAMAGE.set(original);
        }
        helper.succeed();
    }

    /** 最小攻击距离：JAVA 有下限（怪物按 mobFactor 减半）/ MODDED 归零，贴脸也能出手。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void minAttackRangeFollowsConfig(GameTestHelper helper) {
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        SpearRules.MinAttackRange original = SpearConfig.MIN_ATTACK_RANGE.get();
        try {
            SpearConfig.MIN_ATTACK_RANGE.set(SpearRules.MinAttackRange.JAVA);
            helper.assertTrue(Math.abs(iron.effectiveMinRange(attacker) - 1.0F) < 1.0E-6F,
                    "JAVA 怪物最小攻击距离应为 2×0.5=1.0，实际 " + iron.effectiveMinRange(attacker));
            SpearConfig.MIN_ATTACK_RANGE.set(SpearRules.MinAttackRange.MODDED);
            helper.assertTrue(iron.effectiveMinRange(attacker) == 0.0F,
                    "MODDED 最小攻击距离应为 0，实际 " + iron.effectiveMinRange(attacker));
        } finally {
            SpearConfig.MIN_ATTACK_RANGE.set(original);
        }
        helper.succeed();
    }

    /**
     * 蓄力基线：JAVA 只取裸值（力量药水无效，原版 MC-302778）/ BEDROCK 吃修饰符，
     * 但两者都必须把<b>手持长矛自己的攻击力加成</b>算进去。
     *
     * <p>这是回归测试：以前 JAVA 只取 {@code getAttributeBaseValue}，而武器加成是装备期
     * 才挂上的 ADDITION，于是七种材质的蓄力基线全是玩家的 1.0，铁矛与木矛同样疼。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void chargeStrengthFollowsConfig(GameTestHelper helper) {
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        Mob user = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        user.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(iron));
        // 装备属性是在实体 tick 时通过 LivingEntity#collectEquipmentChanges 同步进属性的；
        // 不 tick 就断言会把「怪物属性不含武器加成」这个错误前提固化下来。
        user.tick();
        float weaponBonus = spearcore$attackDamageBonus(iron);
        helper.assertTrue(weaponBonus > 0.0F, "铁矛的攻击力加成不应为 0（木矛才是 0）");
        user.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(
                new AttributeModifier("spearcore_test_strength", 5.0D, AttributeModifier.Operation.ADDITION));
        SpearRules.ChargeStrength original = SpearConfig.CHARGE_STRENGTH.get();
        try {
            SpearConfig.CHARGE_STRENGTH.set(SpearRules.ChargeStrength.JAVA);
            float javaBase = iron.chargeBaseDamage(user);
            float expectedJava = (float) user.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + weaponBonus;
            helper.assertTrue(Math.abs(javaBase - expectedJava) < 1.0E-6F,
                    "JAVA 蓄力基线应为「裸攻击力 " + (float) user.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                            + " + 武器加成 " + weaponBonus + "」，实际 " + javaBase);
            SpearConfig.CHARGE_STRENGTH.set(SpearRules.ChargeStrength.BEDROCK);
            float bedrockBase = iron.chargeBaseDamage(user);
            helper.assertTrue(Math.abs(bedrockBase - (javaBase + 5.0F)) < 1.0E-6F,
                    "BEDROCK 蓄力基线应包含 +5 力量，实际 " + bedrockBase + "（JAVA " + javaBase + "）");
        } finally {
            SpearConfig.CHARGE_STRENGTH.set(original);
        }
        helper.succeed();
    }

    /** 蓄力重复命中：JAVA 一次 / BEDROCK 固定 10t / MODDED 按攻击时长缩放。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void chargeRepeatModdedFollowsConfig(GameTestHelper helper) {
        SpearRules.ChargeRepeatHit original = SpearConfig.CHARGE_REPEAT_HIT.get();
        SpearRules.ChargeRepeatHitThirdParty originalThirdParty =
                SpearConfig.CHARGE_REPEAT_HIT_THIRD_PARTY.get();
        try {
            SpearConfig.CHARGE_REPEAT_HIT.set(SpearRules.ChargeRepeatHit.JAVA);
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F) == Integer.MAX_VALUE,
                    "JAVA 蓄力对同一目标只能结算一次");

            SpearConfig.CHARGE_REPEAT_HIT.set(SpearRules.ChargeRepeatHit.BEDROCK);
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F) == 10,
                    "BEDROCK 应固定使用 contactCooldownTicks=10");

            SpearConfig.CHARGE_REPEAT_HIT.set(SpearRules.ChargeRepeatHit.MODDED);
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F) == 6,
                    "MODDED 0.6 秒应折成 6 tick，实际 " + SpearRules.chargeRepeatCooldownTicks(10, 0.6F));
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F, true) == 6,
                    "本模组内建矛路径应允许按攻速缩放");

            // 第三方矛有独立配置，不受 chargeRepeatHit=MODDED 连带影响。
            SpearConfig.CHARGE_REPEAT_HIT_THIRD_PARTY.set(SpearRules.ChargeRepeatHitThirdParty.JAVA);
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F, false) == Integer.MAX_VALUE,
                    "第三方默认 JAVA 应保持每次蓄力一次");
            SpearConfig.CHARGE_REPEAT_HIT_THIRD_PARTY.set(SpearRules.ChargeRepeatHitThirdParty.BEDROCK);
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F, false) == 10,
                    "第三方 BEDROCK 应使用它自己的 contactCooldownTicks");
            SpearConfig.CHARGE_REPEAT_HIT_THIRD_PARTY.set(SpearRules.ChargeRepeatHitThirdParty.MODDED);
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.6F, false) == 6,
                    "第三方 MODDED 才按攻速缩放");

            SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
            helper.assertTrue(iron.chargeRepeatCooldownTicks()
                            == SpearRules.speedScaledChargeRepeatTicks(iron.getAttackDuration()),
                    "内建矛必须在 MODDED 档按自身攻击时长缩放");

            // 实际攻速修饰符（haste / 装备）必须参与，而不是只按武器 attackDuration。
            Player player = helper.makeMockPlayer();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(iron));
            player.tick();
            int beforeHaste = iron.chargeRepeatCooldownTicks(player);
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1));
            int withHaste = iron.chargeRepeatCooldownTicks(player);
            helper.assertTrue(withHaste < beforeHaste,
                    "Haste 必须缩短蓄力重复间隔: " + beforeHaste + " -> " + withHaste);
            player.removeEffect(MobEffects.DIG_SPEED);

            AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
            helper.assertTrue(attackSpeed != null, "测试玩家必须带 ATTACK_SPEED 属性");
            if (attackSpeed != null) {
                AttributeModifier haste = new AttributeModifier(UUID.randomUUID(), "game test haste",
                        2.0D, AttributeModifier.Operation.ADDITION);
                attackSpeed.addTransientModifier(haste);
                try {
                    int afterHaste = iron.chargeRepeatCooldownTicks(player);
                    helper.assertTrue(afterHaste < beforeHaste,
                            "haste/攻速修饰符必须缩短蓄力重复间隔: " + beforeHaste + " -> " + afterHaste);
                } finally {
                    attackSpeed.removeModifier(haste);
                }
            }
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 0.95F) == 10,
                    "MODDED 0.95 秒应折成 10 tick");
            helper.assertTrue(SpearRules.chargeRepeatCooldownTicks(10, 1.3F) == 13,
                    "MODDED 1.3 秒应折成 13 tick");
            helper.assertTrue(SpearRules.speedScaledChargeRepeatTicks(0.1F) == 1,
                    "极短攻击时长也应至少保留 1 tick");
        } finally {
            SpearConfig.CHARGE_REPEAT_HIT.set(original);
            SpearConfig.CHARGE_REPEAT_HIT_THIRD_PARTY.set(originalThirdParty);
        }
        helper.succeed();
    }

    /** 三尖两刃刀：20% out_of_world + 20% 魔法 + 10% 直接扣血，最大生命 3%，最低 1 点。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void threePointDamageSharesAndMaxHealth(GameTestHelper helper) {
        helper.assertTrue(Math.abs(ThreePointBladeHandler.NORMAL_SHARE - 0.50F) < 1.0E-6F
                        && Math.abs(ThreePointBladeHandler.VOID_SHARE - 0.20F) < 1.0E-6F
                        && Math.abs(ThreePointBladeHandler.MAGIC_SHARE - 0.20F) < 1.0E-6F
                        && Math.abs(ThreePointBladeHandler.DIRECT_SHARE - 0.10F) < 1.0E-6F
                        && Math.abs(ThreePointBladeHandler.MAX_HEALTH_SHARE - 0.03F) < 1.0E-6F,
                "三尖两刃刀拆分比例必须为 50/20/20/10，最大生命 3%");

        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        double initial = zombie.getMaxHealth();
        ThreePointBladeHandler.reduceMaxHealth(zombie, 10.0F);
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - (initial - 0.3D)) < 1.0E-5D,
                "10 点伤害应扣 0.3 最大生命，实际 " + zombie.getMaxHealth());
        ThreePointBladeHandler.reduceMaxHealth(zombie, 10.0F);
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - (initial - 0.6D)) < 1.0E-5D,
                "最大生命削减必须可叠加，实际 " + zombie.getMaxHealth());
        ThreePointBladeHandler.reduceMaxHealth(zombie, 10_000.0F);
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - 1.0D) < 1.0E-5D,
                "最大生命最低只能到 1.0，实际 " + zombie.getMaxHealth());

        var cow = helper.spawnWithNoFreeWill(EntityType.COW, 3, 1, 3);
        double cowMax = cow.getMaxHealth();
        ThreePointBladeHandler.reduceMaxHealth(cow, 10.0F);
        helper.assertTrue(Math.abs(cow.getMaxHealth() - cowMax) < 1.0E-5D,
                "非敌对生物不应被削减最大生命");
        helper.succeed();
    }

    /** 虚空/魔法的伤害来源必须是原版 out_of_world / magic，并保留攻击者归属。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void threePointSourcesUseVanillaTypes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        DamageSource voidSource = SpearDamageTypes.outOfWorld(player.level(), player);
        DamageSource magicSource = SpearDamageTypes.magic(player.level(), player);
        helper.assertTrue(voidSource.typeHolder().is(DamageTypes.FELL_OUT_OF_WORLD),
                "三尖两刃刀的虚空伤害必须是 minecraft:out_of_world，实际 " + voidSource.getMsgId());
        helper.assertTrue(magicSource.typeHolder().is(DamageTypes.MAGIC),
                "三尖两刃刀的魔法伤害必须是 minecraft:magic，实际 " + magicSource.getMsgId());
        helper.assertTrue(voidSource.getEntity() == player && magicSource.getEntity() == player,
                "两种伤害来源都必须保留攻击者归属");
        helper.succeed();
    }

    /** 戳刺后重新蓄力的间隔：JAVA 0 tick / BEDROCK 该矛的使用冷却（铁 19t、木 13t）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void jabToChargeDelayFollowsConfig(GameTestHelper helper) {
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        SpearItem wood = (SpearItem) SpearCoreItems.WOODEN_SPEAR.get();
        helper.assertTrue(iron.getUseCooldownTicks() == 19,
                "铁矛使用冷却应为 19t，实际 " + iron.getUseCooldownTicks());
        helper.assertTrue(wood.getUseCooldownTicks() == 13,
                "木矛使用冷却应为 13t，实际 " + wood.getUseCooldownTicks());
        SpearRules.JabToChargeDelay original = SpearConfig.JAB_TO_CHARGE_DELAY.get();
        try {
            SpearConfig.JAB_TO_CHARGE_DELAY.set(SpearRules.JabToChargeDelay.JAVA);
            helper.assertTrue(SpearRules.jabToChargeDelayTicks(19) == 0, "JAVA 戳刺后不应有间隔");
            SpearConfig.JAB_TO_CHARGE_DELAY.set(SpearRules.JabToChargeDelay.BEDROCK);
            helper.assertTrue(SpearRules.jabToChargeDelayTicks(19) == 19, "BEDROCK 铁矛戳刺后应冷却 19t");
            helper.assertTrue(SpearRules.jabToChargeDelayTicks(13) == 13, "BEDROCK 木矛戳刺后应冷却 13t");
        } finally {
            SpearConfig.JAB_TO_CHARGE_DELAY.set(original);
        }
        helper.succeed();
    }

    /** 移动靶补偿（MC-310857）：JAVA 按服务器真实位置判定 / MODDED 按客户端看到的位置回退 3 tick。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void movingTargetAssistFollowsConfig(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Mob moving = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 3);
        moving.setDeltaMovement(0.28D, 0.0D, 0.0D);
        SpearRules.MovingTargetAssist original = SpearConfig.MOVING_TARGET_ASSIST.get();
        try {
            SpearConfig.MOVING_TARGET_ASSIST.set(SpearRules.MovingTargetAssist.JAVA);
            helper.assertTrue(SpearItem.movingTargetCompensation(player) == 0.0D, "JAVA 不应有移动靶补偿");
            double javaShift = SpearItem.compensatedTargetBox(player, moving, 0.0F).minX
                    - moving.getBoundingBox().minX;
            helper.assertTrue(Math.abs(javaShift) < 1.0E-6D, "JAVA 判定箱不应被移动，实际偏移 " + javaShift);

            SpearConfig.MOVING_TARGET_ASSIST.set(SpearRules.MovingTargetAssist.MODDED);
            helper.assertTrue(Math.abs(SpearItem.movingTargetCompensation(player) - 3.0D) < 1.0E-6D,
                    "MODDED 应回退 3 tick，实际 " + SpearItem.movingTargetCompensation(player));
            double moddedShift = SpearItem.compensatedTargetBox(player, moving, 0.0F).minX
                    - moving.getBoundingBox().minX;
            helper.assertTrue(Math.abs(moddedShift - (-0.28D * 3.0D)) < 1.0E-6D,
                    "MODDED 判定箱应按 0.28 格/tick × 3 回退，实际偏移 " + moddedShift);
        } finally {
            SpearConfig.MOVING_TARGET_ASSIST.set(original);
        }
        helper.succeed();
    }

    /** 击落骑手的速度阈值写死 Java 版数据，与任何配置项无关。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void dismountThresholdIsVanillaData(GameTestHelper helper) {
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("wood") - 14.0F) < 1.0E-6F, "木矛击落阈值应为 14");
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("gold") - 13.0F) < 1.0E-6F, "金矛击落阈值应为 13");
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("stone") - 13.0F) < 1.0E-6F, "石矛击落阈值应为 13");
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("copper") - 12.0F) < 1.0E-6F, "铜矛击落阈值应为 12");
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("iron") - 11.0F) < 1.0E-6F, "铁矛击落阈值应为 11");
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("diamond") - 10.0F) < 1.0E-6F, "钻矛击落阈值应为 10");
        helper.assertTrue(Math.abs(SpearStats.dismountSpeedFor("netherite") - 9.0F) < 1.0E-6F, "下界合金矛击落阈值应为 9");

        var ironStats = SpearStats.get(SpearCoreItems.IRON_SPEAR.get());
        helper.assertTrue(ironStats.dismounts(), "铁矛应当能击落骑手");
        helper.assertTrue(ironStats.dismountConditions().isPresent(), "铁矛应当带击落条件");
        helper.assertTrue(Math.abs(ironStats.dismountConditions().get().minSpeed() - 11.0F) < 1.0E-6F,
                "铁矛击落条件里的速度阈值应为 11，实际 "
                        + ironStats.dismountConditions().get().minSpeed());
        helper.succeed();
    }

    /** 突进力度：JAVA 0.458 / MODDED 0.65。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void lungeStrengthFollowsConfig(GameTestHelper helper) {
        SpearRules.Lunge original = SpearConfig.LUNGE.get();
        try {
            SpearConfig.LUNGE.set(SpearRules.Lunge.JAVA);
            helper.assertTrue(Math.abs(SpearRules.lungeStrength() - 0.458F) < 1.0E-6F,
                    "JAVA 突进力度应为 0.458，实际 " + SpearRules.lungeStrength());
            SpearConfig.LUNGE.set(SpearRules.Lunge.MODDED);
            helper.assertTrue(Math.abs(SpearRules.lungeStrength() - 0.65F) < 1.0E-6F,
                    "MODDED 突进力度应为 0.65，实际 " + SpearRules.lungeStrength());
        } finally {
            SpearConfig.LUNGE.set(original);
        }
        helper.succeed();
    }

    /**
     * 附魔伤害必须真的参与结算（对齐 26.1.2 原版）。
     *
     * <p>这条是回归测试：曾经 stabAttack 直接 target.hurt，没走原版
     * EnchantmentHelper 的附魔伤害修正，导致锋利/亡灵杀手挂在物品上却不加伤害。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void sharpnessIncreasesStabDamage(GameTestHelper helper) {
        float plain = spearcore$stabDamage(helper, 1, 1, null, 0);
        float sharp = spearcore$stabDamage(helper, 3, 1, Enchantments.SHARPNESS, 5);
        helper.assertTrue(sharp > plain, "锋利附魔没有提高戳刺伤害：" + plain + " -> " + sharp);
        helper.succeed();
    }

    /** 击退附魔必须真的加大击退（对齐 26.1.2 原版：0.4 + 附魔加成）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void knockbackEnchantmentPushesFarther(GameTestHelper helper) {
        double plain = spearcore$stabAndReadTargetPush(helper, 1, 1, null, 0);
        double pushed = spearcore$stabAndReadTargetPush(helper, 3, 1, Enchantments.KNOCKBACK, 2);
        helper.assertTrue(pushed > plain, "击退附魔没有加大击退：" + plain + " -> " + pushed);
        helper.succeed();
    }

    /**
     * 怪物 AI 的攻击距离必须和真正结算的射程一致（mobFactor 减半）。
     *
     * <p>这是回归测试：{@code SpearCollision#getHitEntitiesAlong} 以前直接用
     * {@code getMaxRange()}（4.5），怪物会在 3 格外就起手，而它实际只够到 2.25 格。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void mobAttackRangeMatchesMobFactor(GameTestHelper helper) {
        SpearItem iron = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        attacker.setYRot(0.0F);
        attacker.setXRot(0.0F);
        attacker.setYHeadRot(0.0F);

        float expectedMax = iron.getMaxRange() * iron.getMobFactor();
        helper.assertTrue(Math.abs(iron.effectiveMaxRange(attacker) - expectedMax) < 1.0E-6F,
                "怪物有效射程应为 " + expectedMax + "，实际 " + iron.effectiveMaxRange(attacker));

        Mob far = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 4);   // 3 格外
        helper.assertTrue(SpearCollision.getHitEntitiesAlong(attacker, iron, iron.getHitboxMargin(),
                        e -> e == far).isEmpty(),
                "3 格外（怪物射程 " + expectedMax + "）的目标不应被 AI 判定为命中");

        Mob near = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 2);   // 1 格外
        helper.assertTrue(!SpearCollision.getHitEntitiesAlong(attacker, iron, iron.getHitboxMargin(),
                        e -> e == near).isEmpty(),
                "1 格外的目标在怪物射程内，应被 AI 判定为命中");
        helper.succeed();
    }

    /**
     * 第一次戳刺也要把目标记进「最近戳过谁」的表。
     *
     * <p>回归测试：那张表以前只在 {@code startUsingItem}（起手蓄力）时才 new，
     * 于是首次戳刺的 {@code RememberStabbedEntity} 被静默丢弃。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void stabRecordSurvivesFirstHit(GameTestHelper helper) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 1);
        Mob target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, 1, 1, 2);
        SpearCooldownAccessor accessor = (SpearCooldownAccessor) mob;

        helper.assertFalse(accessor.WasRecentlyStabbed(target, 10), "没记录过就不该算刚被戳过");
        accessor.RememberStabbedEntity(target);
        helper.assertTrue(accessor.WasRecentlyStabbed(target, 10),
                "第一次 RememberStabbedEntity 必须生效（表应懒初始化）");
        helper.succeed();
    }

    /**
     * 戳刺的 {@link SpearDamageEvent} 必须带上被命中的目标。
     *
     * <p>回归测试：以前事件在循环外只发一次、target 填 null，监听方分不清打的是谁。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void stabDamageEventCarriesTarget(GameTestHelper helper) {
        SpearEventRecorder recorder = new SpearEventRecorder();
        MinecraftForge.EVENT_BUS.register(recorder);
        try {
            StabResult result = spearcore$stabOnce(helper, 1, 1, null, 0);
            List<SpearDamageEvent> stabs = recorder.stabEvents();
            helper.assertTrue(stabs.size() == 1,
                    "一次戳刺应恰好发一次 STAB 的 SpearDamageEvent，实际 " + stabs.size() + " 次");
            helper.assertTrue(stabs.get(0).getTarget() == result.target(),
                    "SpearDamageEvent 必须带上被戳中的目标");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
        }
        helper.succeed();
    }

    /**
     * {@link SpearHitEvent} 报的伤害必须是实际造成的那一份（含附魔修正）。
     *
     * <p>回归测试：以前传的是附魔修正之前的 {@code damage}。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void hitEventReportsActualDamage(GameTestHelper helper) {
        SpearEventRecorder recorder = new SpearEventRecorder();
        MinecraftForge.EVENT_BUS.register(recorder);
        try {
            StabResult result = spearcore$stabOnce(helper, 1, 1, Enchantments.SHARPNESS, 5);
            helper.assertTrue(recorder.hits().size() == 1,
                    "一次戳刺应恰好发一次 SpearHitEvent，实际 " + recorder.hits().size() + " 次");
            float reported = recorder.hits().get(0).getDamageDealt();
            // 事件报的是"护甲减免前"的结算伤害：攻击力（含武器加成）+ 附魔伤害修正。
            // 旧实现传的是附魔修正之前的数，所以锋利附魔下这条会失败。
            float expected = (float) result.attacker().getAttributeValue(Attributes.ATTACK_DAMAGE)
                    + EnchantmentHelper.getDamageBonus(result.spear(), result.target().getMobType());
            helper.assertTrue(Math.abs(reported - expected) < 1.0E-3F,
                    "SpearHitEvent 应报附魔修正后的伤害 " + expected + "，实际报 " + reported);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(recorder);
        }
        helper.succeed();
    }

    /**
     * 击落骑手的速度阈值按材质取 Java 版数据（KubeJS 建造器也走同一条）。
     *
     * <p>回归测试：建造器曾写死 0.3 格/秒，等于任何速度都能把人打下坐骑。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void dismountConditionFollowsMaterial(GameTestHelper helper) {
        SpearCondition wood = SpearStats.dismountConditionFor("wood", 100).orElseThrow();
        helper.assertTrue(Math.abs(wood.minSpeed() - 14.0F) < 1.0E-6F, "木矛击落阈值应为 14，实际 " + wood.minSpeed());
        helper.assertTrue(wood.maxDurationTicks() == 100, "窗口长度应原样保留");
        helper.assertTrue(Math.abs(SpearStats.dismountConditionFor("copper", 80).orElseThrow().minSpeed() - 12.0F) < 1.0E-6F,
                "铜矛击落阈值应为 12");
        helper.assertTrue(Math.abs(SpearStats.dismountConditionFor("netherite", 50).orElseThrow().minSpeed() - 9.0F) < 1.0E-6F,
                "下界合金矛击落阈值应为 9");
        helper.assertTrue(Math.abs(SpearStats.dismountConditionFor("custom", 50).orElseThrow().minSpeed() - 11.0F) < 1.0E-6F,
                "未知材质应回落铁矛的 11");

        // 与内置预设同源：任意材质都必须等于内置预设里的那一份阈值。
        helper.assertTrue(Math.abs(SpearStats.WOOD.dismountConditions().orElseThrow().minSpeed()
                        - SpearStats.dismountConditionFor("wood", 1).orElseThrow().minSpeed()) < 1.0E-6F,
                "建造器路径与内置预设的击落阈值必须一致");
        helper.succeed();
    }

    /** 记录长矛事件（注册/注销都包在 try/finally 里）。 */
    private static final class SpearEventRecorder {
        private final List<SpearDamageEvent> damageEvents = new ArrayList<>();
        private final List<SpearHitEvent> hitEvents = new ArrayList<>();

        @SubscribeEvent
        public void onSpearDamage(SpearDamageEvent event) {
            this.damageEvents.add(event);
        }

        @SubscribeEvent
        public void onSpearHit(SpearHitEvent event) {
            this.hitEvents.add(event);
        }

        private List<SpearDamageEvent> stabEvents() {
            return this.damageEvents.stream()
                    .filter(e -> e.getAttackType() == SpearDamageEvent.AttackType.STAB)
                    .toList();
        }

        private List<SpearHitEvent> hits() {
            return this.hitEvents;
        }
    }

    /** 读一件长矛当前配置下的最大耐久。 */
    private static int spearcore$maxDamage(Item item) {
        return new ItemStack(item).getMaxDamage();
    }

    /** 读一件长矛主手时的攻击力修饰符数值（ADDITION 的 amount）。 */
    private static float spearcore$attackDamageBonus(Item item) {
        var modifiers = new ItemStack(item).getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE);
        if (modifiers == null || modifiers.isEmpty()) {
            return 0.0F;
        }
        return (float) modifiers.iterator().next().getAmount();
    }

    /** 摆一对"持矛攻击者 + 目标"，戳一次，返回目标掉的血量。 */
    private static float spearcore$stabDamage(GameTestHelper helper, int attackerX, int attackerZ,
                                             Enchantment enchantment, int level) {
        return spearcore$stabOnce(helper, attackerX, attackerZ, enchantment, level).damageDealt();
    }

    /** 摆一对"持矛攻击者 + 目标"，戳一次，返回目标的水平速度（击退量）。 */
    private static double spearcore$stabAndReadTargetPush(GameTestHelper helper, int attackerX, int attackerZ,
                                                          Enchantment enchantment, int level) {
        Mob target = spearcore$stabOnce(helper, attackerX, attackerZ, enchantment, level).target();
        net.minecraft.world.phys.Vec3 motion = target.getDeltaMovement();
        return Math.sqrt(motion.x * motion.x + motion.z * motion.z);
    }

    private record StabResult(Mob attacker, Mob target, ItemStack spear, float damageDealt) {
    }

    /** 摆一对"持矛攻击者 + 目标"并戳一次；矛按需附魔。 */
    private static StabResult spearcore$stabOnce(GameTestHelper helper, int attackerX, int attackerZ,
                                                 Enchantment enchantment, int level) {
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, attackerX, 1, attackerZ);
        ItemStack spear = new ItemStack(SpearCoreItems.IRON_SPEAR.get());
        if (enchantment != null) {
            spear.enchant(enchantment, level);
        }
        attacker.setItemSlot(EquipmentSlot.MAINHAND, spear);
        attacker.setYRot(0.0F);
        attacker.setXRot(0.0F);
        attacker.setYHeadRot(0.0F);

        SpearItem spearItem = (SpearItem) spear.getItem();
        double minRange = spearItem.effectiveMinRange(attacker);
        double maxRange = spearItem.effectiveMaxRange(attacker);
        helper.assertTrue(maxRange > minRange && minRange >= 0.0,
                "怪物有效攻击距离不合法：" + minRange + ".." + maxRange);
        int targetZ = attackerZ + Math.max(1, (int) Math.round((minRange + maxRange) / 2.0));

        Mob target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, attackerX, 1, targetZ);
        float before = target.getHealth();
        boolean hit = spearItem.performStabAttack(attacker, spear, EquipmentSlot.MAINHAND, false);
        helper.assertTrue(hit, "戳刺没有命中目标（车道 x=" + attackerX + "）");
        return new StabResult(attacker, target, spear, before - target.getHealth());
    }

    /**
     * 长矛必须挂进合适的物品 tag —— 这是和其他模组/数据包互通的前提。
     *
     * <p>1.20.1 的 tag 目录是 tags/items（复数），写成 1.21 的 tags/item 会被静默忽略，
     * 原仓库就是踩了这个坑（所有 tag 实际都没生效）。这条测试同时验证本模组 tag
     * 与原版 / Forge 的归类 tag，防止再犯。</p>
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearsCarryItemTags(GameTestHelper helper) {
        TagKey<Item> spearsTag = TagKey.create(Registries.ITEM, new ResourceLocation(SpearcoreMod.MODID, "spears"));
        TagKey<Item> spearTag = TagKey.create(Registries.ITEM, new ResourceLocation(SpearcoreMod.MODID, "spear"));
        TagKey<Item> copperTag = TagKey.create(Registries.ITEM,
                new ResourceLocation(SpearcoreMod.MODID, "copper_spear"));
        TagKey<Item> swordsTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "swords"));
        TagKey<Item> toolsTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "tools"));
        TagKey<Item> minecraftSpearsTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "spears"));
        TagKey<Item> forgeToolsTag = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "tools"));
        TagKey<Item> piglinLovedTag = TagKey.create(Registries.ITEM, new ResourceLocation("minecraft", "piglin_loved"));

        // #spearcore:spears 按上游约定只含「原版材质」的六把（铜是模组自加的，单独一个 tag）
        Item[] vanillaMaterialSpears = {
                SpearCoreItems.WOODEN_SPEAR.get(),
                SpearCoreItems.STONE_SPEAR.get(),
                SpearCoreItems.IRON_SPEAR.get(),
                SpearCoreItems.GOLDEN_SPEAR.get(),
                SpearCoreItems.DIAMOND_SPEAR.get(),
                SpearCoreItems.NETHERITE_SPEAR.get(),
        };
        Item[] allSpears = {
                SpearCoreItems.WOODEN_SPEAR.get(),
                SpearCoreItems.STONE_SPEAR.get(),
                SpearCoreItems.COPPER_SPEAR.get(),
                SpearCoreItems.IRON_SPEAR.get(),
                SpearCoreItems.GOLDEN_SPEAR.get(),
                SpearCoreItems.DIAMOND_SPEAR.get(),
                SpearCoreItems.NETHERITE_SPEAR.get(),
        };
        for (Item item : vanillaMaterialSpears) {
            helper.assertTrue(new ItemStack(item).is(spearsTag), "长矛没有进入 #spearcore:spears：" + item);
        }
        for (Item item : allSpears) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(stack.is(spearTag), "长矛没有进入 #spearcore:spear：" + item);
            helper.assertTrue(stack.is(minecraftSpearsTag), "长矛没有进入 #minecraft:spears：" + item);
            helper.assertTrue(stack.is(swordsTag), "长矛没有进入 #minecraft:swords：" + item);
            helper.assertTrue(stack.is(toolsTag), "长矛没有进入 #minecraft:tools：" + item);
            helper.assertTrue(stack.is(forgeToolsTag), "长矛没有进入 #forge:tools：" + item);
        }
        helper.assertTrue(new ItemStack(SpearCoreItems.GOLDEN_SPEAR.get()).is(piglinLovedTag),
                "金矛没有进入 #minecraft:piglin_loved");
        // 负断言：六把原版材质矛不含铜；铜 tag 只含铜；猪灵喜爱只含金矛。
        for (Item item : vanillaMaterialSpears) {
            helper.assertTrue(!new ItemStack(item).is(copperTag), "#spearcore:copper_spear 只应含铜矛：" + item);
        }
        helper.assertTrue(new ItemStack(SpearCoreItems.COPPER_SPEAR.get()).is(copperTag),
                "铜矛没有进入 #spearcore:copper_spear");
        helper.assertTrue(!new ItemStack(SpearCoreItems.COPPER_SPEAR.get()).is(spearsTag),
                "#spearcore:spears 按上游约定不含铜矛");
        for (Item item : allSpears) {
            if (item != SpearCoreItems.GOLDEN_SPEAR.get()) {
                helper.assertTrue(!new ItemStack(item).is(piglinLovedTag),
                        "#minecraft:piglin_loved 只应含金矛：" + item);
            }
        }
        helper.succeed();
    }

    /** 在指定 x 车道摆一对"持矛攻击者 + 目标"，戳一次，返回攻击者戳完的水平速度。 */
    private static double spearcore$stabAndReadSpeed(GameTestHelper helper, int attackerX, int attackerZ) {
        Mob attacker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, attackerX, 1, attackerZ);
        ItemStack spear = new ItemStack(SpearCoreItems.IRON_SPEAR.get());
        attacker.setItemSlot(EquipmentSlot.MAINHAND, spear);
        attacker.setYRot(0.0F);
        attacker.setXRot(0.0F);
        attacker.setYHeadRot(0.0F);

        SpearItem spearItem = (SpearItem) spear.getItem();
        // 怪物有 mobFactor（默认 0.5）缩减，目标要按怪物自己的有效射程摆
        double minRange = spearItem.effectiveMinRange(attacker);
        double maxRange = spearItem.effectiveMaxRange(attacker);
        int targetZ = attackerZ + Math.max(1, (int) Math.round((minRange + maxRange) / 2.0));
        helper.spawnWithNoFreeWill(EntityType.ZOMBIE, attackerX, 1, targetZ);

        attacker.setDeltaMovement(0.3D, 0.0D, 0.0D);
        boolean hit = spearItem.performStabAttack(attacker, spear, EquipmentSlot.MAINHAND, false);
        helper.assertTrue(hit, "戳刺没有命中目标（车道 x=" + attackerX + "）");
        return attacker.getDeltaMovement().x;
    }

    /** 七把长矛的实例，顺序固定（木/石/铜/铁/金/钻/下界）。 */
    private static Item[] spearcore$allSpears() {
        return new Item[]{
                SpearCoreItems.WOODEN_SPEAR.get(),
                SpearCoreItems.STONE_SPEAR.get(),
                SpearCoreItems.COPPER_SPEAR.get(),
                SpearCoreItems.IRON_SPEAR.get(),
                SpearCoreItems.GOLDEN_SPEAR.get(),
                SpearCoreItems.DIAMOND_SPEAR.get(),
                SpearCoreItems.NETHERITE_SPEAR.get(),
        };
    }
}