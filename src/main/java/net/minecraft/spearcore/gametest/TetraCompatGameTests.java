package net.minecraft.spearcore.gametest;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraDamageScaling;
import net.minecraft.spearcore.compat.tetra.TetraMaterialKey;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.compat.tetra.TetraSpearStats;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearCoreItems;
import net.minecraft.spearcore.init.SpearEnchantments;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.spearcore.init.SpearDamageTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tetra 兼容层的纯公式测试。
 *
 * <p>Tetra 本身不是 GameTest 依赖（GameTest 服务端不加载 Tetra），所以这里只验证不依赖 Tetra API 的
 * 部分：材料属性 → 长矛数值的拟合公式、边界与放缓、以及「非长矛物品不会被误判」。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TetraCompatGameTests {

    private static final String TEMPLATE = "empty5x5x5";

    private TetraCompatGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(TetraCompatGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void fitDurationClampsAndGrowsWithSpeed(GameTestHelper helper) {
        float previous = -1.0F;
        for (double speed = -4.0D; speed <= 4.0D; speed += 0.25D) {
            float duration = TetraSpearStats.fit(4.0D, speed).getAttackDuration();
            helper.assertTrue(duration >= 0.60F - 1.0E-4F && duration <= 1.30F + 1.0E-4F,
                    "攻击速度 " + speed + " 拟合出的 duration 越界: " + duration);
            helper.assertTrue(duration >= previous - 1.0E-4F,
                    "duration 应随攻击速度单调不减: " + previous + " -> " + duration);
            previous = duration;
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void fitMultiplierGrowsButTapersAtHighDamage(GameTestHelper helper) {
        float low = TetraSpearStats.fit(2.0D, 1.5D).getDamageMultiplier();
        float mid = TetraSpearStats.fit(4.0D, 1.5D).getDamageMultiplier();
        float high = TetraSpearStats.fit(8.0D, 1.5D).getDamageMultiplier();
        float extreme = TetraSpearStats.fit(12.0D, 1.5D).getDamageMultiplier();

        helper.assertTrue(low < mid && mid < high, "倍率应随攻击力单调上升: " + low + " / " + mid + " / " + high);
        helper.assertTrue(extreme > high, "高数值区间仍应缓慢上升: " + high + " -> " + extreme);
        helper.assertTrue((extreme - high) < (high - mid),
                "高攻击力区间必须放缓（衰减）: mid->high=" + (high - mid) + ", high->extreme=" + (extreme - high));
        helper.assertTrue(low >= 0.60F - 1.0E-4F && extreme <= 1.45F + 1.0E-4F,
                "倍率应被夹在 0.60~1.45: " + low + " / " + extreme);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void nonSpearItemsAreNotClassified(GameTestHelper helper) {
        helper.assertTrue(SpearLikeSupport.of(ItemStack.EMPTY) == null, "空物品堆不应被识别为长矛");
        helper.assertTrue(SpearLikeSupport.of(new ItemStack(Items.STICK)) == null, "普通物品不应被识别为长矛");

        TetraSpearStats stats = TetraSpearStats.fit(5.0D, 1.6D);
        helper.assertTrue(stats.style() == SpearStyle.MODULAR_3D, "Tetra 拟合结果应标记为 3D 模块化风格");
        helper.assertTrue(!stats.usesJabCooldown(), "Tetra 工具固定按 Java 语义，不应有全背包共享冷却");
        helper.assertTrue(stats.getDismountConditions().isPresent()
                        && stats.getKnockbackConditions().isPresent()
                        && stats.getDamageConditions().isPresent(),
                "蓄力阶段条件（击落 / 击退 / 伤害）应全部存在");
        helper.assertTrue(stats.getDamageEndTick() > stats.getDelayTicks(), "伤害窗口必须晚于前摇");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraExtraReachIsWeakened(GameTestHelper helper) {
        // 全部额外距离都来自 Tetra 时，按系数保留：4 * 0.3 = 1.2。
        assertReach(helper, 4.0D, 4.0D, 0.3D, 1.2D);
        // 系数边界：0 = 完全不计，1 = 原样计入。
        assertReach(helper, 4.0D, 4.0D, 0.0D, 0.0D);
        assertReach(helper, 4.0D, 4.0D, 1.0D, 4.0D);
        // 来源加成超过 extra 时按 extra 截断，避免出现负数。
        assertReach(helper, 3.0D, 5.0D, 0.3D, 0.9D);
        // 非 Tetra 来源的距离不缩放。
        assertReach(helper, 2.0D, 0.0D, 0.3D, 2.0D);
        // 没有超出原版射程时保持 0。
        assertReach(helper, 0.0D, 4.0D, 0.3D, 0.0D);

        helper.assertTrue(TetraSpearStats.fit(10.0D, 0.0D, 4.0D).style() == SpearStyle.MODULAR_3D,
                "带额外距离的 Tetra 拟合结果仍应是 3D 模块化风格");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void nonModularItemsAreNotClassified(GameTestHelper helper) {
        // 兼容层只认本模组自己的 archetype：普通剑、原版三叉戟都不该被当成长矛。
        // GameTest 服务端不加载 Tetra，所以这里同时验证「没装 Tetra 时完全静默」这条路径。
        helper.assertTrue(SpearLikeSupport.of(new ItemStack(Items.IRON_SWORD)) == null,
                "普通剑不应被识别为长矛");
        helper.assertTrue(SpearLikeSupport.of(new ItemStack(Items.TRIDENT)) == null,
                "原版三叉戟不应被识别为长矛");
        SpearItem ironSpear = (SpearItem) SpearCoreItems.IRON_SPEAR.get();
        helper.assertTrue(SpearLikeSupport.of(new ItemStack(ironSpear)) == ironSpear,
                "本模组自带的长矛仍应直接返回自身");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraReachFallbackAppliesWhenBonusUnknown(GameTestHelper helper) {
        // Tetra 的 reach 有时只体现在玩家的属性上，ItemStack 侧统计不到（bonus=0）。
        // 这时兜底逻辑应把全部额外距离当成 Tetra 来源，而不是让 tetraExtraReachScale 静默失效。
        Player player = helper.makeMockPlayer();
        var reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        helper.assertTrue(reach != null, "测试玩家应带有 ENTITY_REACH 属性");
        reach.addTransientModifier(new AttributeModifier(
                java.util.UUID.fromString("9f0d3a5e-1c42-4a13-9d1e-0b6f9a2c7d11"),
                "spearcore:test_reach", 2.0D, AttributeModifier.Operation.ADDITION));

        SpearLike spear = TetraSpearStats.fit(4.0D, 0.0D, 0.0D);
        float fallback = SpearCombat.effectiveMaxRangeWithFallback(spear, player, 0.0D, 0.3D);
        float explicitBonus = SpearCombat.effectiveMaxRange(spear, player, 2.0D, 0.3D);
        float withoutScaling = SpearCombat.effectiveMaxRange(spear, player, 0.0D, 1.0D);

        helper.assertTrue(Math.abs(fallback - explicitBonus) < 1.0E-4F,
                "来源未知时应按全部额外距离兜底: fallback=" + fallback + ", explicit=" + explicitBonus);
        helper.assertTrue(fallback < withoutScaling - 1.0E-4F,
                "兜底后必须真的削弱，而不是原样计入: fallback=" + fallback + ", full=" + withoutScaling);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraAttackDamageIsScaled(GameTestHelper helper) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                java.util.UUID.fromString("3c1f0d7a-5b2e-4c8f-9a61-7d4e2b0c8f13"),
                "spearcore:test_damage", 6.0D, AttributeModifier.Operation.ADDITION));
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                java.util.UUID.fromString("8b7a6c5d-4e3f-2a1b-9c8d-7e6f5a4b3c2d"),
                "spearcore:test_reach", 0.5D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                java.util.UUID.fromString("1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
                "spearcore:test_speed", -0.1D, AttributeModifier.Operation.MULTIPLY_BASE));

        Multimap<Attribute, AttributeModifier> scaled = TetraDamageScaling.scaleAttackDamage(builder.build(), 0.5D);
        double damage = scaled.get(Attributes.ATTACK_DAMAGE).stream()
                .mapToDouble(AttributeModifier::getAmount).sum();
        double reach = scaled.get(ForgeMod.ENTITY_REACH.get()).stream()
                .mapToDouble(AttributeModifier::getAmount).sum();
        double speed = scaled.get(Attributes.ATTACK_SPEED).stream()
                .mapToDouble(AttributeModifier::getAmount).sum();

        helper.assertTrue(Math.abs(damage - 3.0D) < 1.0E-6D, "攻击伤害应按 0.5 打折: " + damage);
        helper.assertTrue(Math.abs(reach - 0.5D) < 1.0E-6D, "reach 不应被伤害折扣影响: " + reach);
        helper.assertTrue(Math.abs(speed + 0.1D) < 1.0E-6D, "攻速的百分比修饰符不应被缩放: " + speed);
        helper.succeed();
    }


    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void lungeAppliesToSpearLikeStacks(GameTestHelper helper) {
        Enchantment lunge = SpearEnchantments.LUNGE.get();
        helper.assertTrue(lunge.canApplyAtEnchantingTable(new ItemStack(SpearCoreItems.IRON_SPEAR.get())),
                "突进应能附在本模组的内建长矛上");
        helper.assertTrue(!lunge.canApplyAtEnchantingTable(new ItemStack(Items.STICK)),
                "突进不应能附在木棍上");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraWhitelistMatchesVanillaSpearWhitelist(GameTestHelper helper) {
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.SHARPNESS), "应支持锋利");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.KNOCKBACK), "应支持击退");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.SMITE), "应支持亡灵杀手");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.BANE_OF_ARTHROPODS), "应支持节肢杀手");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.FIRE_ASPECT), "应支持火焰附加");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.MOB_LOOTING), "应支持抢夺");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.UNBREAKING), "应支持耐久");
        helper.assertTrue(SpearEnchantments.supportsVanilla(Enchantments.MENDING), "应支持经验修补");
        helper.assertTrue(!SpearEnchantments.supportsVanilla(Enchantments.ALL_DAMAGE_PROTECTION), "不应支持保护");

        helper.assertTrue(!Enchantments.SHARPNESS.canApplyAtEnchantingTable(new ItemStack(Items.STICK)),
                "木棍不应能附锋利（白名单只作用于矛）");
        helper.succeed();
    }


    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void lungeGateMatchesSpearLikeStacks(GameTestHelper helper) {
        helper.assertTrue(SpearItem.isLungeApplicable(new ItemStack(SpearCoreItems.IRON_SPEAR.get())),
                "内建长矛应通过突进门禁");
        helper.assertTrue(!SpearItem.isLungeApplicable(new ItemStack(Items.STICK)),
                "木棍不应通过突进门禁");
        helper.succeed();
    }


    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void weaponProxyDetectsSwordCompatibleEnchantments(GameTestHelper helper) {
        helper.assertTrue(SpearEnchantments.isWeaponCompatible(Enchantments.SWEEPING_EDGE),
                "横扫之刃属于剑类武器附魔，探针应判定为适用");
        helper.assertTrue(!SpearEnchantments.isWeaponCompatible(Enchantments.ALL_DAMAGE_PROTECTION),
                "保护不是武器附魔，探针不应判定为适用");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void minecraftNamespaceEnchantsStayOnWhitelist(GameTestHelper helper) {
        helper.assertTrue(!SpearEnchantments.supportsSpearEnchantment(Enchantments.SWEEPING_EDGE),
                "原版非白名单附魔不应被模组放行");
        helper.assertTrue(SpearEnchantments.supportsSpearEnchantment(Enchantments.SHARPNESS),
                "锋利在白名单内");
        helper.assertTrue(!SpearEnchantments.supportsSpearEnchantment(Enchantments.ALL_DAMAGE_PROTECTION),
                "保护不在白名单内");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void enchantmentProbeIsHiddenAndNotCraftable(GameTestHelper helper) {
        var probe = SpearCoreItems.ENCHANTMENT_PROBE.get();
        helper.assertTrue(probe instanceof SwordItem, "探针必须是剑，才能通过原版 WEAPON 类别判定");
        boolean craftable = helper.getLevel().getRecipeManager().getRecipes().stream()
                .anyMatch(recipe -> recipe.getResultItem(helper.getLevel().registryAccess()).is(probe));
        helper.assertTrue(!craftable, "探针不应有任何配方，避免被玩家正常获取");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void fireAspectIgnitesStabTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack spear = new ItemStack(SpearCoreItems.IRON_SPEAR.get());
        spear.enchant(Enchantments.FIRE_ASPECT, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        SpearCombat.applyOnHitEnchantments(player, zombie);
        helper.assertTrue(zombie.getRemainingFireTicks() > 0, "火焰附加命中后应点燃目标");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void baneSlowsArthropodTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        ItemStack spear = new ItemStack(SpearCoreItems.IRON_SPEAR.get());
        spear.enchant(Enchantments.BANE_OF_ARTHROPODS, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);

        Spider spider = helper.spawn(EntityType.SPIDER, 1, 2, 1);
        SpearCombat.applyOnHitEnchantments(player, spider);
        helper.assertTrue(spider.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "节肢杀手命中节肢生物后应施加缓慢（走 doPostHurtEffects）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void playerSpearDamageUsesPlayerAttackType(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);

        helper.assertTrue(SpearDamageTypes.spear(helper.getLevel(), player).is(DamageTypes.PLAYER_ATTACK),
                "玩家持矛必须使用 minecraft:player_attack 类型，模组的玩家攻击钩子才会进入");
        helper.assertTrue(SpearDamageTypes.spear(helper.getLevel(), zombie).is(SpearDamageTypes.SPEAR),
                "怪物持矛仍应使用 spearcore:spear 类型");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void sameAttackerHurtImmunityIsCleared(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(zombie.invulnerableTime > 0, "前置伤害应留下受伤免疫");

        SpearCombat.clearSameAttackerHurtImmunity(player, zombie);
        helper.assertTrue(zombie.invulnerableTime == 0,
                "同一攻击者的受伤免疫应被清掉，长矛本体伤害才能落地");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearArchetypeKeyMatching(GameTestHelper helper) {
        helper.assertTrue(TetraSpearProfile.isModularSpearArchetype(TetraSpearProfile.SPEAR_ARCHETYPE_KEY),
                "modular_spear 应被识别为本模组的模块化长矛");
        String[] notSpears = {"modular_spear_alt", "modular_sword", "spear", "MODULAR_SPEAR"};
        for (String key : notSpears) {
            helper.assertTrue(!TetraSpearProfile.isModularSpearArchetype(key),
                    "不应把该 archetype 当成模块化长矛: " + key);
        }
        helper.assertTrue(!TetraSpearProfile.isModularSpearArchetype(null), "null archetype 不应被识别为长矛");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraMaterialKeySuffixMatches(GameTestHelper helper) {
        // 材料色靠 variant key 的最后一段匹配材质表（basic_blade/iron → iron），必须按 / 边界匹配，
        // 否则 ironwood 会被 iron 误判、矛头染成铁色。
        helper.assertTrue(TetraMaterialKey.matches("basic_blade/iron", "iron"), "应匹配 basic_blade/iron 与 iron");
        helper.assertTrue(TetraMaterialKey.matches("iron", "iron"), "应匹配完全相同的 key");
        helper.assertTrue(TetraMaterialKey.matches("addon/tools/long_handle/iron", "iron"), "应匹配多级前缀");
        helper.assertTrue(!TetraMaterialKey.matches("basic_blade/ironwood", "iron"), "ironwood 不能被 iron 误匹配");
        helper.assertTrue(!TetraMaterialKey.matches(null, "iron"), "null key 不应匹配");
        helper.assertTrue(!TetraMaterialKey.matches("basic_blade/iron", null), "null 材料不应匹配");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearArchetypeDataIsValid(GameTestHelper helper) {
        JsonElement root = spearcore$readJson(helper, "tetra", "archetypes/modular_spear.json");
        if (root == null) {
            return;
        }
        try {
            JsonObject json = root.getAsJsonObject();
            helper.assertTrue("modular_spear".equals(json.get("id").getAsString()),
                    "archetype 的 id 必须是 modular_spear");
            helper.assertTrue(json.has("honeable") && json.has("honeBase") && json.has("honeIntegrityMultiplier"),
                    "archetype 必须写全 honeable/honeBase/honeIntegrityMultiplier（Tetra 用 record 反序列化）");

            JsonArray slots = json.getAsJsonArray("slots");
            helper.assertTrue(slots != null && slots.size() == 4, "模块化长矛应有 4 个槽位");
            Set<String> keys = new HashSet<>();
            boolean bladeMajor = false;
            boolean hiltMajor = false;
            // 工作台里的模块框位置来自 GuiModuleOffsets(ArchetypeSlotDefinition[])：x/y 会被原样当成像素偏移。
            // 只允许 Tetra 默认布局量级（2 major 用 (4,0)/(4,18)，2 minor 用 (-18,5)/(-18,18)），
            // 写成 0/40/80 这种大数会让模块框散到物品外面。
            Map<String, Integer> expectedX = Map.of(
                    "sword/blade", 4, "sword/hilt", 4, "sword/guard", -18, "sword/pommel", -18);
            Map<String, Integer> expectedY = Map.of(
                    "sword/blade", 0, "sword/hilt", 18, "sword/guard", 5, "sword/pommel", 18);
            for (JsonElement element : slots) {
                JsonObject slot = element.getAsJsonObject();
                String key = slot.get("key").getAsString();
                keys.add(key);
                boolean major = slot.has("major") && slot.get("major").getAsBoolean();
                if ("sword/blade".equals(key)) {
                    bladeMajor = major;
                }
                if ("sword/hilt".equals(key)) {
                    hiltMajor = major;
                }
                int x = slot.has("x") ? slot.get("x").getAsInt() : 0;
                int y = slot.has("y") ? slot.get("y").getAsInt() : 0;
                helper.assertTrue(expectedX.getOrDefault(key, x) == x,
                        "槽位 " + key + " 的 x 应与 Tetra 默认布局一致，实际: " + x);
                helper.assertTrue(expectedY.getOrDefault(key, y) == y,
                        "槽位 " + key + " 的 y 应与 Tetra 默认布局一致，实际: " + y);
            }
            helper.assertTrue(keys.equals(Set.of("sword/blade", "sword/hilt", "sword/guard", "sword/pommel")),
                    "槽位必须是剑的四个槽: " + keys);
            helper.assertTrue(bladeMajor && hiltMajor, "刀身与手柄必须是 major 槽位");
        } catch (RuntimeException exception) {
            helper.fail("archetype 数据格式不符: " + exception);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearReplacementsCoverAllSpears(GameTestHelper helper) {
        JsonElement root = spearcore$readJson(helper, "tetra", "replacements/spearcore_spears.json");
        if (root == null) {
            return;
        }
        Map<String, String> expectedMaterials = Map.of(
                "spearcore:wooden_spear", "oak",
                "spearcore:stone_spear", "stone",
                "spearcore:copper_spear", "copper",
                "spearcore:iron_spear", "iron",
                "spearcore:golden_spear", "gold",
                "spearcore:diamond_spear", "diamond",
                "spearcore:netherite_spear", "netherite");
        try {
            JsonArray array = root.getAsJsonArray();
            helper.assertTrue(array.size() == expectedMaterials.size(),
                    "替换规则应正好覆盖七把矛，实际: " + array.size());
            Set<String> covered = new HashSet<>();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                helper.assertTrue("tetra:dynamic_handheld".equals(entry.get("item").getAsString()),
                        "替换目标必须是 tetra:dynamic_handheld");
                helper.assertTrue(TetraSpearProfile.SPEAR_ARCHETYPE_KEY.equals(entry.get("archetype").getAsString()),
                        "替换必须写入 archetype=modular_spear");

                JsonObject modules = entry.getAsJsonObject("modules");
                helper.assertTrue(modules != null, "替换规则必须带 modules");
                for (String slot : new String[]{"sword/blade", "sword/hilt", "sword/guard", "sword/pommel"}) {
                    helper.assertTrue(modules.has(slot), "缺少槽位模块: " + slot);
                }

                JsonArray items = entry.getAsJsonObject("predicate").getAsJsonArray("items");
                helper.assertTrue(items != null && items.size() == 1, "每条规则只应匹配一个物品");
                String itemId = items.get(0).getAsString();
                covered.add(itemId);
                helper.assertTrue(ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId)),
                        "替换规则引用了不存在的物品: " + itemId);

                String material = expectedMaterials.get(itemId);
                helper.assertTrue(material != null, "替换规则里出现未知物品: " + itemId);
                helper.assertTrue(("basic_blade/" + material)
                                .equals(modules.getAsJsonArray("sword/blade").get(1).getAsString()),
                        "刀身材质映射不符: " + itemId);
                helper.assertTrue(("makeshift_guard/" + material)
                                .equals(modules.getAsJsonArray("sword/guard").get(1).getAsString()),
                        "护手材质映射不符: " + itemId);
                helper.assertTrue(("decorative_pommel/" + material)
                                .equals(modules.getAsJsonArray("sword/pommel").get(1).getAsString()),
                        "配重材质映射不符: " + itemId);
                helper.assertTrue("basic_hilt/stick".equals(modules.getAsJsonArray("sword/hilt").get(1).getAsString()),
                        "手柄固定用 basic_hilt/stick");
            }
            helper.assertTrue(covered.equals(expectedMaterials.keySet()),
                    "替换规则必须覆盖七把矛，实际覆盖: " + covered);
        } catch (RuntimeException exception) {
            helper.fail("替换数据格式不符: " + exception);
        }
        helper.succeed();
    }

    /**
     * 读取数据包里的 JSON：GameTest 服务端不加载 Tetra，这里只验证本模组自己 ship 的数据文件是否合法。
     */
    private static JsonElement spearcore$readJson(GameTestHelper helper, String namespace, String path) {
        ResourceLocation location = new ResourceLocation(namespace, path);
        ResourceManager resources = helper.getLevel().getServer().getResourceManager();
        try (BufferedReader reader = resources.getResource(location).orElseThrow().openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (Exception exception) {
            helper.fail("读取 " + location + " 失败（文件缺失或不是合法 JSON）: " + exception);
            return null;
        }
    }

    private static void assertReach(GameTestHelper helper, double extraReach, double sourceBonus,
                                    double scale, double expected) {
        double actual = SpearCombat.weakenExtraReach(extraReach, sourceBonus, scale);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6D,
                "额外距离削弱不符: extra=" + extraReach + ", bonus=" + sourceBonus
                        + ", scale=" + scale + ", expected=" + expected + ", actual=" + actual);
    }
}
