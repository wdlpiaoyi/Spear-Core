package net.minecraft.spearcore.gametest;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraDamageScaling;
import net.minecraft.spearcore.compat.tetra.TetraMaterialKey;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.compat.tetra.TetraSpearScrolls;
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
import net.minecraft.nbt.CompoundTag;
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
    public static void tetraMaterialKeyExtractsMaterial(GameTestHelper helper) {
        // 模块化长矛的名字要用矛头材料拼「材料 + 矛」，材料段就是 variant key 的最后一个 / 之后的部分。
        helper.assertTrue("iron".equals(TetraMaterialKey.materialOf("basic_blade/iron")),
                "basic_blade/iron 的材料段应是 iron");
        helper.assertTrue("iron".equals(TetraMaterialKey.materialOf("iron")),
                "没有前缀的 key 本身就是材料 key");
        helper.assertTrue("netherite".equals(TetraMaterialKey.materialOf("addon/tools/basic_blade/netherite")),
                "多级前缀应取最后一段");
        helper.assertTrue(TetraMaterialKey.materialOf("basic_blade/") == null,
                "以 / 结尾的 key 没有材料段，应返回 null");
        helper.assertTrue(TetraMaterialKey.materialOf(null) == null, "null key 应返回 null");
        helper.assertTrue(TetraMaterialKey.materialOf("") == null, "空 key 应返回 null");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearNameKeysTranslated(GameTestHelper helper) {
        // 名字 = 「材料前缀 + 矛」，lang key 与回退名必须在三种语言里都有；占位符丢了会显示成裸 %s。
        // 这里不能用 TetraSpearNaming.NAMED_KEY 引用常量：GameTest 服务端没有 Tetra，加载那个类会 NoClassDefFoundError。
        String namedKey = "item.spearcore.modular_spear.named";
        String fallbackKey = "item.spearcore.modular_spear";
        // 只维护中英两份语言文件（ja_jp 已删除，日文回退英文），所以这里不校验日文。
        for (String language : new String[]{"en_us", "zh_cn"}) {
            JsonElement root = spearcore$readJson(helper, SpearcoreMod.MODID, "lang/" + language + ".json");
            if (root == null) {
                return;
            }
            try {
                JsonObject json = root.getAsJsonObject();
                helper.assertTrue(json.has(fallbackKey), language + " 缺少模块化长矛的回退名 " + fallbackKey);
                helper.assertTrue(json.has(namedKey), language + " 缺少模块化长矛的带材料名 " + namedKey);
                String named = json.get(namedKey).getAsString();
                helper.assertTrue(named.contains("%s"),
                        language + " 的带材料名必须留一个材料占位符，实际: " + named);
            } catch (RuntimeException exception) {
                helper.fail(language + " 语言文件格式不符: " + exception);
                return;
            }
        }
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
            // 打磨：honeable 关掉的话工作台里不会出现 hone 配方，矛头永远停在初始数值。
            // 数值参考 Tetra 自己的 tetra.toml（module_progression.hone_double_base / hone_double_integrity_multiplier
            // = 140 / 75，双手重武器的档位；剑是 110 / 65，长矛按重武器算）。
            helper.assertTrue(json.get("honeable").getAsBoolean(), "archetype 必须开启 honeable，否则矛头不能打磨");
            helper.assertTrue(json.get("honeBase").getAsInt() > 0, "honeBase 必须大于 0，否则第一次打磨要的击杀数没有基数");
            helper.assertTrue(json.get("honeIntegrityMultiplier").getAsInt() > 0,
                    "honeIntegrityMultiplier 必须大于 0，否则刚性成本不参与打磨进度");

            JsonArray slots = json.getAsJsonArray("slots");
            helper.assertTrue(slots != null && slots.size() == 4, "模块化长矛应有 4 个槽位");
            Set<String> keys = new HashSet<>();
            boolean headMajor = false;
            boolean handleMajor = false;
            boolean headRequired = false;
            boolean handleRequired = false;
            // 工作台里的模块框位置来自 GuiModuleOffsets(ArchetypeSlotDefinition[])：x/y 会被原样当成像素偏移。
            // 只允许 Tetra 默认布局量级（2 major 用 (4,0)/(4,18)，2 minor 用 (-18,0)/(-18,18)），
            // 写成 0/40/80 这种大数会让模块框散到物品外面。
            // 方位按设计稿：↗矛头、↘矛杆、↖绑定/涂层、↙枪尾（配重）。
            Map<String, Integer> expectedX = Map.of(
                    "spear/head", 4, "spear/handle", 4, "spear/binding", -18, "spear/butt", -18);
            Map<String, Integer> expectedY = Map.of(
                    "spear/head", 0, "spear/handle", 18, "spear/binding", 0, "spear/butt", 18);
            for (JsonElement element : slots) {
                JsonObject slot = element.getAsJsonObject();
                String key = slot.get("key").getAsString();
                keys.add(key);
                boolean major = slot.has("major") && slot.get("major").getAsBoolean();
                boolean required = slot.has("required") && slot.get("required").getAsBoolean();
                if ("spear/head".equals(key)) {
                    headMajor = major;
                    headRequired = required;
                }
                if ("spear/handle".equals(key)) {
                    handleMajor = major;
                    handleRequired = required;
                }
                int x = slot.has("x") ? slot.get("x").getAsInt() : 0;
                int y = slot.has("y") ? slot.get("y").getAsInt() : 0;
                helper.assertTrue(expectedX.getOrDefault(key, x) == x,
                        "槽位 " + key + " 的 x 应与 Tetra 默认布局一致，实际: " + x);
                helper.assertTrue(expectedY.getOrDefault(key, y) == y,
                        "槽位 " + key + " 的 y 应与 Tetra 默认布局一致，实际: " + y);
            }
            helper.assertTrue(keys.equals(Set.of("spear/head", "spear/handle", "spear/binding", "spear/butt")),
                    "槽位必须是矛的四个槽: " + keys);
            helper.assertTrue(headMajor && handleMajor, "矛头与矛杆必须是 major 槽位");
            helper.assertTrue(headRequired && handleRequired, "矛头与矛杆必须是 required 槽位");
        } catch (RuntimeException exception) {
            helper.fail("archetype 数据格式不符: " + exception);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearHoneSchematicsAreDataDefined(GameTestHelper helper) {
        // 打磨配方：Tetra 的 hone 面板只列 hone=true 且 slots 命中当前物品的 schematic，
        // 所以矛头（spear/head）与矛杆（spear/handle）必须各自自带一套，不能指望 Tetra 的那份还会出现。
        // 改进 key 与翻译都复用 Tetra 自带的（矛头：spearhead_hone/damage、head_hone/durability、
        // head_hone/speed；矛杆：handle_hone/speed），对应目录必须写在模块的 improvements 里。
        // requirement 里**不能**出现 tetra:module：那个需求比对的是模块 key（不是槽位），
        // 换成 spear/shuo_head / spear/three_point_blade 之后整条打磨线会静默消失（3.4.0 修过的坑）。
        String[][] hones = {
                // 槽位, 改进 key, 文件名前缀, localizationKey 前缀
                {"spear/head", "spearhead_hone/damage", "head/hone_damage_", "sword/shared_blade/hone_damage_"},
                {"spear/head", "head_hone/durability", "head/hone_durability_", "double/shared_head/hone_durability_"},
                {"spear/head", "head_hone/speed", "head/hone_speed_", "double/shared_head/hone_speed_"},
                {"spear/handle", "handle_hone/speed", "handle/hone_speed_", "single/handle/hone_speed_"},
        };
        for (String[] hone : hones) {
            String slot = hone[0];
            String improvementKey = hone[1];
            String filePrefix = hone[2];
            String localizationPrefix = hone[3];
            for (int level = 1; level <= 5; level++) {
                String file = "schematics/spear/" + filePrefix + level + ".json";
                JsonElement root = spearcore$readJson(helper, "tetra", file);
                if (root == null) {
                    return;
                }
                try {
                    JsonObject json = root.getAsJsonObject();
                    helper.assertTrue(json.has("hone") && json.get("hone").getAsBoolean(),
                            file + " 必须标 hone=true，否则不会出现在打磨面板");
                    helper.assertTrue(localizationPrefix.concat(Integer.toString(level))
                                    .equals(json.get("localizationKey").getAsString()),
                            file + " 必须复用 Tetra 的 localizationKey，才会跟着 Tetra 的翻译走");
                    JsonArray slots = json.getAsJsonArray("slots");
                    helper.assertTrue(slots != null && slots.size() == 1 && slot.equals(slots.get(0).getAsString()),
                            file + " 必须只作用于槽位 " + slot);
                    // 级别锁：1 级要求「还没有这条改进」，2~5 级要求上一级已经打磨过。
                    String requirement = json.get("requirement").toString();
                    helper.assertFalse(requirement.contains("tetra:module"),
                            file + " 的 requirement 不能按模块 key 过滤，否则换到新矛头后整条打磨线会消失");
                    if (level == 1) {
                        helper.assertTrue(requirement.contains("tetra:not")
                                        && requirement.contains(improvementKey),
                                file + " 的 requirement 应该是「还没有 " + improvementKey + "」");
                    } else {
                        helper.assertTrue(requirement.contains("tetra:improvement")
                                        && requirement.contains(improvementKey)
                                        && requirement.contains("\"level\":" + (level - 1)),
                                file + " 的 requirement 应该是 " + improvementKey + " 的 " + (level - 1) + " 级");
                    }
                    JsonArray outcomes = json.getAsJsonArray("outcomes");
                    helper.assertTrue(outcomes != null && !outcomes.isEmpty(), file + " 必须有 outcomes");
                    JsonObject improvements = outcomes.get(0).getAsJsonObject().getAsJsonObject("improvements");
                    helper.assertTrue(improvements != null && improvements.has(improvementKey),
                            file + " 的 outcome 必须给改进 " + improvementKey);
                    helper.assertTrue(improvements.get(improvementKey).getAsInt() == level,
                            file + " 的改进等级必须是 " + level);
                } catch (RuntimeException exception) {
                    helper.fail(file + " 数据格式不符: " + exception);
                    return;
                }
            }
        }
        // 上面复用的改进 key 必须真的被对应模块接受，否则打磨面板照样是空的。
        JsonElement headModuleRoot = spearcore$readJson(helper, "tetra", "modules/spear/head.json");
        if (headModuleRoot == null) {
            return;
        }
        Set<String> headAccepted = new HashSet<>();
        for (JsonElement element : headModuleRoot.getAsJsonObject().getAsJsonArray("improvements")) {
            headAccepted.add(element.getAsString());
        }
        helper.assertTrue(headAccepted.contains("tetra:single/spearhead/"),
                "矛头必须接受 tetra:single/spearhead/ 下的改进（含 spearhead_hone/damage）");
        helper.assertTrue(headAccepted.contains("tetra:single/shared_head/"),
                "矛头必须接受 tetra:single/shared_head/ 下的改进（含 head_hone/durability、head_hone/speed）");
        JsonElement handleModuleRoot = spearcore$readJson(helper, "tetra", "modules/spear/handle.json");
        if (handleModuleRoot == null) {
            return;
        }
        Set<String> handleAccepted = new HashSet<>();
        for (JsonElement element : handleModuleRoot.getAsJsonObject().getAsJsonArray("improvements")) {
            handleAccepted.add(element.getAsString());
        }
        helper.assertTrue(handleAccepted.contains("tetra:single/shared_handle/"),
                "矛杆必须接受 tetra:single/shared_handle/ 下的改进（含 handle_hone/speed）");
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
                for (String slot : new String[]{"spear/head", "spear/handle", "spear/binding", "spear/butt"}) {
                    helper.assertTrue(modules.has(slot), "缺少槽位模块: " + slot);
                    JsonArray pair = modules.getAsJsonArray(slot);
                    helper.assertTrue(pair != null && pair.size() == 2,
                            "每个槽位必须写 [模块 key, 变体 key]: " + slot);
                    helper.assertTrue(slot.equals(pair.get(0).getAsString()),
                            "槽位必须挂本模组同名的矛模块: " + slot + " -> " + pair.get(0).getAsString());
                }

                JsonArray items = entry.getAsJsonObject("predicate").getAsJsonArray("items");
                helper.assertTrue(items != null && items.size() == 1, "每条规则只应匹配一个物品");
                String itemId = items.get(0).getAsString();
                covered.add(itemId);
                helper.assertTrue(ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId)),
                        "替换规则引用了不存在的物品: " + itemId);

                String material = expectedMaterials.get(itemId);
                helper.assertTrue(material != null, "替换规则里出现未知物品: " + itemId);
                helper.assertTrue(("spear_head/" + material)
                                .equals(modules.getAsJsonArray("spear/head").get(1).getAsString()),
                        "矛头材质映射不符: " + itemId);
                helper.assertTrue(("spear_butt/" + material)
                                .equals(modules.getAsJsonArray("spear/butt").get(1).getAsString()),
                        "枪尾材质映射不符: " + itemId);
                helper.assertTrue("spear_handle/stick".equals(modules.getAsJsonArray("spear/handle").get(1).getAsString()),
                        "矛杆固定用 spear_handle/stick");
                helper.assertTrue("spear_binding/string".equals(modules.getAsJsonArray("spear/binding").get(1).getAsString()),
                        "绑定固定用 spear_binding/string");
            }
            helper.assertTrue(covered.equals(expectedMaterials.keySet()),
                    "替换规则必须覆盖七把矛，实际覆盖: " + covered);
        } catch (RuntimeException exception) {
            helper.fail("替换数据格式不符: " + exception);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearModulesAreDataDefined(GameTestHelper helper) {
        // 四个槽位各挂本模组自己的模块（data/tetra/modules/spear/*），不再借用 Tetra 剑的模块。
        // 镶座（socket）与绑定共用 spear/binding 槽：Tetra 剑也是把 socket 装在 guard 槽上的。
        // GameTest 服务端不加载 Tetra，这里只校验数据形状，注册结果要靠进游戏验收。
        Map<String, String> partToSlot = spearcore$partToSlot();
        Map<String, String> variantPrefix = spearcore$variantPrefix();
        Map<String, String[]> requiredMaterialPrefixes = Map.ofEntries(
                Map.entry("head", new String[]{"tetra:wood/", "tetra:stone/", "tetra:metal/", "tetra:gem/"}),
                Map.entry("butt", new String[]{"tetra:wood/", "tetra:stone/", "tetra:metal/"}),
                Map.entry("handle", new String[]{"tetra:rod/"}),
                Map.entry("binding", new String[]{"tetra:fibre/", "tetra:fabric/"}),
                Map.entry("socket", new String[]{"tetra:socket/"}),
                Map.entry("shuo_head", new String[]{"tetra:metal/"}),
                Map.entry("composite_shaft", new String[]{"tetra:wood/"}),
                Map.entry("three_point_blade", new String[]{"tetra:metal/", "tetra:gem/"}));
        for (Map.Entry<String, String> entry : partToSlot.entrySet()) {
            String part = entry.getKey();
            String slot = entry.getValue();
            JsonElement root = spearcore$readJson(helper, "tetra", "modules/spear/" + part + ".json");
            if (root == null) {
                return;
            }
            try {
                JsonObject json = root.getAsJsonObject();
                JsonArray slots = json.getAsJsonArray("slots");
                helper.assertTrue(slots != null && slots.size() == 1 && slot.equals(slots.get(0).getAsString()),
                        part + " 模块应只占用槽位 " + slot);
                JsonArray variants = json.getAsJsonArray("variants");
                helper.assertTrue(variants != null && !variants.isEmpty(), part + " 模块必须有变体");
                Set<String> prefixes = new HashSet<>();
                for (JsonElement element : variants) {
                    JsonObject variant = element.getAsJsonObject();
                    String key = variant.get("key").getAsString();
                    helper.assertTrue(key.startsWith(variantPrefix.get(part)),
                            part + " 的变体 key 必须以 " + variantPrefix.get(part) + " 开头，实际: " + key);
                    // models 可以写在 extract 里（跟随抽取），也可以直接写在变体上（Tetra 的 bolt 变体就是这么写的）。
                    JsonObject extract = variant.getAsJsonObject("extract");
                    helper.assertTrue(variant.has("models") || (extract != null && extract.has("models")),
                            part + " 的变体必须给出 models，否则工作台预览没有模型");
                }
                for (JsonElement element : variants.get(0).getAsJsonObject().getAsJsonArray("materials")) {
                    prefixes.add(element.getAsString());
                }
                for (String prefix : requiredMaterialPrefixes.get(part)) {
                    helper.assertTrue(prefixes.contains(prefix),
                            part + " 模块的 materials 缺少前缀 " + prefix + "，七把矛的材料必须都能生成变体");
                }
            } catch (RuntimeException exception) {
                helper.fail("模块数据格式不符: " + exception);
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearPartSchematicsAreDataDefined(GameTestHelper helper) {
        // 换部件的配方：工作台只会显示 slots 命中当前物品的 schematic，槽名换成 spear/* 后必须自带。
        Map<String, String> slotByPart = spearcore$partToSlot();
        Map<String, String> variantPrefix = spearcore$variantPrefix();
        for (Map.Entry<String, String> entry : slotByPart.entrySet()) {
            String part = entry.getKey();
            String slot = entry.getValue();
            JsonElement root = spearcore$readJson(helper, "tetra", "schematics/spear/" + part + ".json");
            if (root == null) {
                return;
            }
            try {
                JsonObject json = root.getAsJsonObject();
                JsonArray slots = json.getAsJsonArray("slots");
                helper.assertTrue(slots != null && slots.size() == 1 && slot.equals(slots.get(0).getAsString()),
                        part + " 的 schematic 应只作用于槽位 " + slot);
                JsonArray outcomes = json.getAsJsonArray("outcomes");
                helper.assertTrue(outcomes != null && !outcomes.isEmpty(), part + " 的 schematic 必须有 outcomes");
                for (JsonElement element : outcomes) {
                    JsonObject outcome = element.getAsJsonObject();
                    helper.assertTrue(("spear/" + part).equals(outcome.get("moduleKey").getAsString()),
                            part + " 的 outcome 必须给本模组模块 spear/" + part);
                    helper.assertTrue(outcome.has("materials") || outcome.has("material"),
                            part + " 的 outcome 必须说明材料来源");
                    if (outcome.has("moduleVariant")) {
                        String variant = outcome.get("moduleVariant").getAsString();
                        helper.assertTrue(variant.startsWith(variantPrefix.get(part)),
                                part + " 的 outcome 变体前缀不符: " + variant);
                    }
                }
                // 模块变体的 materials 必须与配方 outcome 的 materials 完全一致：
                // Tetra 只按材料前缀找 outcome，对不上就是「配方看得到、却换不上部件」。
                JsonElement moduleRoot = spearcore$readJson(helper, "tetra", "modules/spear/" + part + ".json");
                if (moduleRoot == null) {
                    return;
                }
                Set<String> moduleMaterials = new HashSet<>();
                for (JsonElement element : moduleRoot.getAsJsonObject().getAsJsonArray("variants")) {
                    JsonObject variant = element.getAsJsonObject();
                    // 特殊变体（例如绑定槽的 forged_bolt）用 material 物品而不是材料前缀，不参与材料对比。
                    if (!variant.has("materials")) {
                        continue;
                    }
                    for (JsonElement material : variant.getAsJsonArray("materials")) {
                        moduleMaterials.add(material.getAsString());
                    }
                }
                Set<String> schematicMaterials = new HashSet<>();
                for (JsonElement element : outcomes) {
                    JsonObject outcome = element.getAsJsonObject();
                    if (outcome.has("materials")) {
                        for (JsonElement material : outcome.getAsJsonArray("materials")) {
                            schematicMaterials.add(material.getAsString());
                        }
                    }
                }
                helper.assertTrue(moduleMaterials.equals(schematicMaterials),
                        part + " 的模块 materials " + moduleMaterials + " 与配方 materials " + schematicMaterials
                                + " 不一致（Tetra 会静默换不上部件）");
            } catch (RuntimeException exception) {
                helper.fail("schematic 数据格式不符: " + exception);
                return;
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearPartKeysTranslated(GameTestHelper helper) {
        // 槽位名、模块名、换部件配方名都走 Tetra 的 I18n key（注意配方用的是 tetra/schematic/ 斜杠前缀），
        // 缺一个就会在工具提示里直接显示原始 key。
        String[] keys = {
                "tetra.slot.spear/head", "tetra.slot.spear/handle",
                "tetra.slot.spear/binding", "tetra.slot.spear/butt",
                "tetra.module.spear/head.name", "tetra.module.spear/handle.name",
                "tetra.module.spear/binding.name", "tetra.module.spear/butt.name",
                "tetra.module.spear/socket.name", "tetra.module.spear/socket.material_name",
                "tetra/schematic/spear/head.name", "tetra/schematic/spear/handle.name",
                "tetra/schematic/spear/binding.name", "tetra/schematic/spear/butt.name",
                "tetra/schematic/spear/socket.name", "tetra/schematic/spear/socket.slot1",
                // 卷轴解锁的三件新部件：模块名、配方名、解锁提示、卷轴显示名
                "tetra.module.spear/shuo_head.name", "tetra.module.spear/shuo_head.material_name",
                "tetra.module.spear/composite_shaft.name",
                "tetra.module.spear/composite_shaft.material_name",
                "tetra.module.spear/three_point_blade.name",
                "tetra.module.spear/three_point_blade.material_name",
                "tetra/schematic/spear/shuo_head.name", "tetra/schematic/spear/shuo_head.slot1",
                "tetra/schematic/spear/composite_shaft.name", "tetra/schematic/spear/composite_shaft.slot1",
                "tetra/schematic/spear/three_point_blade.name",
                "tetra/schematic/spear/three_point_blade.slot1",
                "tetra.unlock.tetra.spear/shuo_head.description",
                "tetra.unlock.tetra.spear/composite_shaft.description",
                "tetra.unlock.tetra.spear/three_point_blade.description",
                "item.tetra.scroll.spear/shuo_head.name", "item.tetra.scroll.spear/shuo_head.prefix",
                "item.tetra.scroll.spear/composite_shaft.name",
                "item.tetra.scroll.spear/composite_shaft.prefix",
                "item.tetra.scroll.spear/three_point_blade.name",
                "item.tetra.scroll.spear/three_point_blade.prefix"};
        // 只维护中英两份语言文件（ja_jp 已删除、日文回退英文，见 AGENTS.md），只校验中英。
        for (String language : new String[]{"en_us", "zh_cn"}) {
            JsonElement root = spearcore$readJson(helper, SpearcoreMod.MODID, "lang/" + language + ".json");
            if (root == null) {
                return;
            }
            JsonObject json = root.getAsJsonObject();
            for (String key : keys) {
                helper.assertTrue(json.has(key), language + " 缺少 Tetra 部件翻译 " + key);
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearScrollsUnlockTheirSchematics(GameTestHelper helper) {
        // 卷轴 = tetra:scroll_rolled + BlockEntityTag.data[0]；工作台会把附近卷轴声明的 schematics
        // 塞进 CraftingContext.unlocks，配方里 {"type":"tetra:locked","key":...} 才通过。
        // 两边各写一遍 key，很容易对不上，所以这里交叉校验，顺带校验 NBT 结构（ribbon 必须是 16 进制字符串）。
        int intricateCount = 0;
        for (TetraSpearScrolls.Definition definition : TetraSpearScrolls.ALL) {
            helper.assertTrue(definition.schematics().length == 1,
                    definition.key() + " 卷轴应只解锁一份 schematic");
            String schematicId = definition.schematics()[0];
            helper.assertTrue(schematicId.startsWith("tetra:spear/"),
                    definition.key() + " 卷轴解锁的 schematic 应在 tetra:spear/ 下，实际: " + schematicId);
            JsonElement root = spearcore$readJson(helper, "tetra",
                    "schematics/" + schematicId.substring("tetra:".length()) + ".json");
            if (root == null) {
                return;
            }
            JsonObject requirement = root.getAsJsonObject().getAsJsonObject("requirement");
            helper.assertTrue(requirement != null && requirement.has("type")
                            && "tetra:locked".equals(requirement.get("type").getAsString()),
                    schematicId + " 必须用 tetra:locked 需求，否则卷轴不起作用");
            helper.assertTrue(requirement != null && requirement.has("key")
                            && schematicId.equals(requirement.get("key").getAsString()),
                    schematicId + " 的 locked key 必须与卷轴声明一致");
            if (definition.intricate()) {
                intricateCount++;
            }

            CompoundTag tag = TetraSpearScrolls.buildTag(definition);
            CompoundTag scroll = tag.getCompound("BlockEntityTag").getList("data", 10).getCompound(0);
            helper.assertTrue(definition.key().equals(scroll.getString("key")), "卷轴 NBT 的 key 不符");
            // 原理图卷轴不该带 details（那是右键打开长卷的画面 id，只有 GoetyRevelation 那类联动才用）
            helper.assertFalse(scroll.contains("details"), definition.key() + " 卷轴不应写 details");
            helper.assertTrue(definition.intricate() == scroll.getBoolean("intricate"), "卷轴 NBT 的 intricate 不符");
            helper.assertTrue(definition.material() == scroll.getInt("material"), "卷轴 NBT 的 material 不符");
            helper.assertTrue(definition.ribbon().equals(scroll.getString("ribbon")),
                    "卷轴 NBT 的 ribbon 必须写成字符串（Tetra 按 16 进制解析）");
            try {
                Long.parseLong(scroll.getString("ribbon"), 16);
            } catch (NumberFormatException exception) {
                helper.fail(definition.key() + " 的 ribbon 不是合法的 16 进制颜色: " + scroll.getString("ribbon"));
                return;
            }
            helper.assertTrue(scroll.getList("glyphs", 3).size() == definition.glyphs().length,
                    "卷轴 NBT 的 glyphs 数量不符");
            boolean declared = false;
            for (int i = 0; i < scroll.getList("schematics", 8).size(); i++) {
                declared |= schematicId.equals(scroll.getList("schematics", 8).getString(i));
            }
            helper.assertTrue(declared, "卷轴 NBT 没有声明 schematic " + schematicId);
        }
        // 三尖两刃刀是「深奥的记录」：必须直接摆在加工台上，其余两件摆在附近即可。
        helper.assertTrue(intricateCount == 1 && TetraSpearScrolls.THREE_POINT_BLADE.intricate(),
                "只有三尖两刃刀卷轴应为复杂原理图（intricate）");
        helper.succeed();
    }

    /** 部件 → 槽位：镶座与绑定共用 spear/binding。 */
    private static Map<String, String> spearcore$partToSlot() {
        return Map.of(
                "head", "spear/head", "handle", "spear/handle",
                "binding", "spear/binding", "butt", "spear/butt",
                "socket", "spear/binding",
                "shuo_head", "spear/head", "composite_shaft", "spear/handle",
                "three_point_blade", "spear/head");
    }

    /** 部件 → 变体 key 前缀（三尖两刃刀的模块 key 是 spear/three_point，变体前缀也就跟着短）。 */
    private static Map<String, String> spearcore$variantPrefix() {
        return Map.of(
                "head", "spear_head/", "handle", "spear_handle/",
                "binding", "spear_binding/", "butt", "spear_butt/",
                "socket", "spear_socket/",
                "shuo_head", "spear_shuo_head/", "composite_shaft", "spear_composite_shaft/",
                "three_point_blade", "spear_three_point/");
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
