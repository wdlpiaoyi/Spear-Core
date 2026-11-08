package net.minecraft.spearcore.gametest;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraDamageScaling;
import net.minecraft.spearcore.compat.tetra.TetraMaterialKey;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.compat.tetra.TetraModuleGateMapping;
import net.minecraft.spearcore.compat.tetra.TetraSpearHead;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            helper.assertTrue(slots != null && slots.size() == 5, "模块化长矛应有 5 个槽位（矛头/矛杆/绑定/枪纂/强化附层）");
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
                    "spear/head", 4, "spear/handle", 4, "spear/binding", -18, "spear/butt", -18,
                    "spear/fuller", -18);
            Map<String, Integer> expectedY = Map.of(
                    "spear/head", 0, "spear/handle", 18, "spear/binding", 0, "spear/butt", 18,
                    "spear/fuller", -18);
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
            helper.assertTrue(keys.equals(Set.of("spear/head", "spear/handle", "spear/binding",
                            "spear/butt", "spear/fuller")),
                    "槽位必须是矛的五个槽: " + keys);
            for (JsonElement element : slots) {
                JsonObject slot = element.getAsJsonObject();
                if ("spear/fuller".equals(slot.get("key").getAsString())) {
                    helper.assertFalse(slot.has("major") && slot.get("major").getAsBoolean(),
                            "强化附层必须是 minor 槽位");
                    helper.assertFalse(slot.has("required") && slot.get("required").getAsBoolean(),
                            "强化附层不能是 required，否则旧矛转换后会缺部件");
                }
            }
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
                // 槽位, 改进 key, 文件名前缀, localizationKey 前缀, 是否允许模块 gate（稳定线挂在 3/3 加固等级上）
                {"spear/head", "spearhead_hone/damage", "head/hone_damage_", "sword/shared_blade/hone_damage_", "0"},
                {"spear/head", "head_hone/durability", "head/hone_durability_", "double/shared_head/hone_durability_", "0"},
                {"spear/head", "head_hone/speed", "head/hone_speed_", "double/shared_head/hone_speed_", "0"},
                {"spear/handle", "handle_hone/speed", "handle/hone_speed_", "single/handle/hone_speed_", "0"},
                {"spear/handle", "handle_hone/durability", "handle/hone_durability_", "double/shared_head/hone_durability_", "0"},
                {"spear/handle", "spear_hone/stable", "handle/hone_stable_", "", "1"},
        };
        for (String[] hone : hones) {
            String slot = hone[0];
            String improvementKey = hone[1];
            String filePrefix = hone[2];
            String localizationPrefix = hone[3];
            boolean moduleGated = "1".equals(hone[4]);
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
                    if (!moduleGated) {
                        helper.assertTrue(localizationPrefix.concat(Integer.toString(level))
                                        .equals(json.get("localizationKey").getAsString()),
                                file + " 必须复用 Tetra 的 localizationKey，才会跟着 Tetra 的翻译走");
                    }
                    JsonArray slots = json.getAsJsonArray("slots");
                    helper.assertTrue(slots != null && slots.size() == 1 && slot.equals(slots.get(0).getAsString()),
                            file + " 必须只作用于槽位 " + slot);
                    // 级别锁：1 级要求「还没有这条改进」，2~5 级要求上一级已经打磨过。
                    String requirement = json.get("requirement").toString();
                    if (moduleGated) {
                        helper.assertTrue(requirement.contains("spear/composite_shaft")
                                        && requirement.contains("spear_composite/stage"),
                                file + " 的「稳定」线应只作用于已到 3/3（stage 2）的复合矛杆");
                    } else {
                        helper.assertFalse(requirement.contains("tetra:module"),
                                file + " 的 requirement 不能按模块 key 过滤，否则换到新矛头后整条打磨线会消失");
                    }
                    String compact = requirement.replaceAll("\\s+", "");
                    if (level == 1) {
                        helper.assertTrue(requirement.contains("tetra:not")
                                        && requirement.contains(improvementKey),
                                file + " 的 requirement 应该是「还没有 " + improvementKey + "」");
                    } else if (moduleGated) {
                        // 稳定线的等级是对象形式 {"min": N}，格式化后带空格，先压掉空白再比。
                        helper.assertTrue(compact.contains("tetra:improvement")
                                        && compact.contains(improvementKey)
                                        && compact.contains("\"min\":" + (level - 1)),
                                file + " 的 requirement 应该是 " + improvementKey + " 的 " + (level - 1) + " 级");
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
        helper.assertTrue(handleAccepted.contains("tetra:spear/handle/"),
                "矛杆必须接受 tetra:spear/handle/（含 handle_hone/durability、spear_hone/stable）");
        // 尖锐：矛头任一条五级打磨后解锁，单级 +5% 蓄力系数
        JsonElement sharpRoot = spearcore$readJson(helper, "tetra", "schematics/spear/head/hone_sharp.json");
        if (sharpRoot == null) {
            return;
        }
        JsonObject sharp = sharpRoot.getAsJsonObject();
        helper.assertTrue(sharp.has("hone") && sharp.get("hone").getAsBoolean(), "尖锐必须是 hone=true");
        String sharpRequirement = sharp.get("requirement").toString();
        for (String key : new String[]{"spearhead_hone/damage", "head_hone/durability", "head_hone/speed"}) {
            helper.assertTrue(sharpRequirement.contains(key),
                    "尖锐必须由「" + key + " 五级」解锁，实际 requirement=" + sharpRequirement);
        }
        helper.assertTrue(sharpRequirement.contains("\"min\":5"),
                "尖锐解锁条件必须是任一条五级（min=5）");
        helper.assertTrue(sharp.getAsJsonArray("outcomes").get(0).getAsJsonObject()
                        .getAsJsonObject("improvements").has("spearhead_hone/sharp"),
                "尖锐的 outcome 必须给 spearhead_hone/sharp");
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
                Map.entry("fuller", new String[]{"tetra:metal/", "tetra:wood/", "tetra:bone/",
                        "tetra:misc/forged_beam", "tetra:fuller/"}),
                Map.entry("shuo_head", new String[]{"tetra:metal/"}),
                Map.entry("three_point_blade",
                        new String[]{"tetra:wood/", "tetra:stone/", "tetra:metal/", "tetra:gem/", "tetra:bone/"}),
                Map.entry("composite_shaft", new String[]{"tetra:wood/"}));
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
            if ("composite_shaft".equals(part)) {
                // 最终形态只由「完成」配方产生，没有自己的换部件配方；阶段配方由
                // modularSpearCompositeStagesAreDataDefined 单独校验。
                continue;
            }
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
                if (!schematicMaterials.isEmpty()) {
                    helper.assertTrue(moduleMaterials.equals(schematicMaterials),
                            part + " 的模块 materials " + moduleMaterials + " 与配方 materials " + schematicMaterials
                                    + " 不一致（Tetra 会静默换不上部件）");
                }
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
                "item.tetra.scroll.spear/three_point_blade.prefix",
                // 本轮新增：名字、螺栓绑定、积竹木柲阶段、尖锐/耐久/稳定、镶嵌描述
                "item.spearcore.modular_spear.named_shuo",
                "item.spearcore.modular_spear.named_blade",
                "tetra.variant.spear_binding/bolt",
                "tetra.variant.spear_binding/bolt.description",
                "tetra.improvement.spear_composite/stage.name",
                "tetra.improvement.spear_composite/stage.description",
                "tetra/schematic/spear/composite_shaft_1.name",
                "tetra/schematic/spear/composite_shaft_1.description",
                "tetra/schematic/spear/composite_shaft_1.slot1",
                "tetra/schematic/spear/composite_shaft_2.name",
                "tetra/schematic/spear/composite_shaft_2.description",
                "tetra/schematic/spear/composite_shaft_2.slot1",
                "tetra/schematic/spear/composite_shaft_3.name",
                "tetra/schematic/spear/composite_shaft_3.description",
                "tetra/schematic/spear/composite_shaft_3.slot1",
                "tetra/schematic/spear/composite_shaft_finish.name",
                "tetra/schematic/spear/composite_shaft_finish.description",
                "tetra/schematic/spear/head/hone_sharp.name",
                "tetra/schematic/spear/head/hone_sharp.description",
                "tetra/schematic/spear/head/hone_sharp.slot1",
                "tetra.improvement.handle_hone/durability.name",
                "tetra.improvement.handle_hone/durability.description",
                "tetra.improvement.spear_hone/stable.name",
                "tetra.improvement.spear_hone/stable.description",
                "tetra.improvement.spearhead_hone/sharp.name",
                "tetra.improvement.spearhead_hone/sharp.description",
                "tetra/schematic/spear/handle/hone_stable_1.name",
                "tetra/schematic/spear/handle/hone_stable_2.name",
                "tetra/schematic/spear/handle/hone_stable_3.name",
                "tetra/schematic/spear/handle/hone_stable_4.name",
                "tetra/schematic/spear/handle/hone_stable_5.name",
                // 本轮新增：强化附层、矛头锯齿/回火的矛头文案（不再复用剑刃 key）
                "tetra.slot.spear/fuller",
                "tetra.module.spear/fuller.name",
                "tetra.module.spear/fuller.material_name",
                "tetra.module.spear/fuller.description",
                "tetra/schematic/spear/fuller.name",
                "tetra/schematic/spear/fuller.description",
                "tetra/schematic/spear/fuller.slot1",
                "tetra/schematic/spear/head/serrated_1.name",
                "tetra/schematic/spear/head/serrated_1.description",
                "tetra/schematic/spear/head/serrated_2.name",
                "tetra/schematic/spear/head/serrated_2.description",
                "tetra/schematic/spear/head/serrated_3.name",
                "tetra/schematic/spear/head/serrated_3.description",
                "tetra/schematic/spear/head/tempered_1.name",
                "tetra/schematic/spear/head/tempered_1.description",
                "tetra/schematic/spear/head/tempered_2.name",
                "tetra/schematic/spear/head/tempered_2.description",
                "tetra/schematic/spear/head/tempered_3.name",
                "tetra/schematic/spear/head/tempered_3.description"};
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
            if ("zh_cn".equals(language)) {
                String handleName = json.get("tetra.module.spear/handle.material_name").getAsString();
                helper.assertFalse(handleName.contains("制"),
                        "矛杆 material_name 模板不能带「制」，否则会显示成「脆弱的制长矛杆」");
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
            helper.assertTrue(requirement != null && requirement.has("type"),
                    schematicId + " 必须有 requirement");
            String requirementType = requirement.get("type").getAsString();
            if ("tetra:locked".equals(requirementType)) {
                helper.assertTrue(schematicId.equals(requirement.get("key").getAsString()),
                        schematicId + " 的 locked key 必须与卷轴声明一致");
            } else {
                // 积竹木柲 1/3 用 or(新卷轴, 旧卷轴)，兼容 3.4.0 之前发出去的旧卷轴。
                helper.assertTrue("tetra:or".equals(requirementType),
                        schematicId + " 的需求必须是 tetra:locked 或 tetra:or，实际: " + requirementType);
                boolean declared = false;
                for (JsonElement element : requirement.getAsJsonArray("requirements")) {
                    JsonObject child = element.getAsJsonObject();
                    declared |= "tetra:locked".equals(child.get("type").getAsString())
                            && schematicId.equals(child.get("key").getAsString());
                }
                helper.assertTrue(declared,
                        schematicId + " 的 or 需求里必须包含与卷轴声明一致的 locked key");
            }
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
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("head", "spear/head");
        map.put("handle", "spear/handle");
        map.put("binding", "spear/binding");
        map.put("butt", "spear/butt");
        map.put("socket", "spear/binding");
        map.put("fuller", "spear/fuller");
        map.put("shuo_head", "spear/head");
        map.put("composite_shaft", "spear/handle");
        map.put("three_point_blade", "spear/head");
        return map;
    }

    /** 部件 → 变体 key 前缀（三尖两刃刀的模块 key 是 spear/three_point，变体前缀也就跟着短）。 */
    private static Map<String, String> spearcore$variantPrefix() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("head", "spear_head/");
        map.put("handle", "spear_handle/");
        map.put("binding", "spear_binding/");
        map.put("butt", "spear_butt/");
        map.put("socket", "spear_socket/");
        map.put("fuller", "spear_fuller/");
        map.put("shuo_head", "spear_shuo_head/");
        map.put("composite_shaft", "spear_composite_shaft/");
        map.put("three_point_blade", "spear_three_point/");
        return map;
    }

    /**
     * 读取数据包里的 JSON：GameTest 服务端不加载 Tetra，这里只验证本模组自己 ship 的数据文件是否合法。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearHeadUpgradesAreDataDefined(GameTestHelper helper) {
        // 锯齿 / 淬火：Tetra 给矛头的两条升级线（流血 / 穿甲）。Tetra 自己的 schematic 写死模块 key
        // single/spearhead，本模组矛头不在其中，所以要自带一份；同样不能按模块 key 过滤，否则
        // 换上槊头 / 三尖两刃刀之后这两条升级就用不了（和打磨配方踩过的是同一个坑）。
        String[][] upgrades = {
                // 文件名前缀, 改进 key
                {"serrated", "spearhead/serrated"},
                {"tempered", "spearhead/tempered"},
        };
        for (String[] upgrade : upgrades) {
            String kind = upgrade[0];
            String improvementKey = upgrade[1];
            for (int level = 1; level <= 3; level++) {
                String file = "schematics/spear/head/" + kind + "_" + level + ".json";
                JsonElement root = spearcore$readJson(helper, "tetra", file);
                if (root == null) {
                    return;
                }
                try {
                    JsonObject json = root.getAsJsonObject();
                    helper.assertTrue("improvement".equals(json.get("displayType").getAsString()),
                            file + " 必须是 displayType=improvement，才会出现在工作台的升级列表");
                    helper.assertFalse(json.has("localizationKey"),
                            file + " 不应复用 Tetra 剑刃的 localizationKey（描述会写成「剑刃」）");
                    String nameKey = "tetra/schematic/spear/head/" + kind + "_" + level + ".name";
                    for (String language : new String[]{"en_us", "zh_cn"}) {
                        JsonElement langRoot = spearcore$readJson(helper, SpearcoreMod.MODID, "lang/" + language + ".json");
                        if (langRoot == null) {
                            return;
                        }
                        helper.assertTrue(langRoot.getAsJsonObject().has(nameKey),
                                language + " 缺少矛头升级翻译 " + nameKey);
                    }
                    JsonArray slots = json.getAsJsonArray("slots");
                    helper.assertTrue(slots != null && slots.size() == 1
                                    && "spear/head".equals(slots.get(0).getAsString()),
                            file + " 必须只作用于矛头槽位 spear/head");
                    String requirement = json.get("requirement").toString();
                    helper.assertFalse(requirement.contains("tetra:module"),
                            file + " 的 requirement 不能按模块 key 过滤，否则新矛头用不了这条升级");
                    if (level > 1) {
                        helper.assertTrue(requirement.contains(improvementKey)
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
        // 矛头模块必须接受 tetra:single/spearhead/ 下的改进，否则这两条升级会被 Tetra 过滤掉。
        JsonElement headModuleRoot = spearcore$readJson(helper, "tetra", "modules/spear/head.json");
        if (headModuleRoot == null) {
            return;
        }
        Set<String> accepted = new HashSet<>();
        for (JsonElement element : headModuleRoot.getAsJsonObject().getAsJsonArray("improvements")) {
            accepted.add(element.getAsString());
        }
        helper.assertTrue(accepted.contains("tetra:single/spearhead/"),
                "矛头必须接受 tetra:single/spearhead/ 下的改进（含 spearhead/serrated、spearhead/tempered）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearHeadTexturesAreWired(GameTestHelper helper) {
        // 三种矛头外观：默认（tetra_spear）、槊头（tetra_spear_shuo）、三尖两刃刀（tetra_spear_three）。
        // 只换头部那一层贴图，杆贴图共用：头部贴图本来就该盖住杆（槊头 / 三尖两刃刀比普通矛头长），
        // 所以以后新增矛头只需要复制一份 tetra_spear_head* 改画，不用为每种矛头重画杆。
        for (String part : new String[]{"shuo", "three"}) {
            JsonElement hand = spearcore$readAssetJson(helper,
                    "models/item/tetra_spear_" + part + "_in_hand.json");
            JsonElement icon = spearcore$readAssetJson(helper,
                    "models/item/tetra_spear_icon_" + part + ".json");
            JsonElement split = spearcore$readAssetJson(helper,
                    "models/item/tetra_spear_" + part + ".json");
            if (hand == null || icon == null || split == null) {
                return;
            }
            String handTextures = hand.getAsJsonObject().getAsJsonObject("textures").toString();
            helper.assertTrue(handTextures.contains("spearcore:item/tetra_spear_handle_in_hand"),
                    part + " 的手持模型必须共用杆贴图 tetra_spear_handle_in_hand");
            helper.assertTrue(handTextures.contains("spearcore:item/tetra_spear_head_" + part + "_in_hand"),
                    part + " 的手持模型必须用各自的头部贴图");
            String iconTextures = icon.getAsJsonObject().getAsJsonObject("textures").toString();
            helper.assertTrue(iconTextures.contains("spearcore:item/tetra_spear_handle"),
                    part + " 的物品栏模型必须共用杆贴图 tetra_spear_handle");
            helper.assertTrue(iconTextures.contains("spearcore:item/tetra_spear_head_" + part + "\""),
                    part + " 的物品栏模型必须用各自的头部贴图");
            helper.assertTrue(("spearcore:item/tetra_spear_" + part + "_in_hand").equals(
                            split.getAsJsonObject().getAsJsonObject("base").get("parent").getAsString()),
                    part + " 的 separate_transforms 模型必须指向自己的手持模型");
            helper.assertTrue(spearcore$assetExists(
                            "textures/item/tetra_spear_head_" + part + ".png"),
                    part + " 缺少物品栏头部贴图 textures/item/tetra_spear_head_" + part + ".png");
            helper.assertTrue(spearcore$assetExists(
                            "textures/item/tetra_spear_head_" + part + "_in_hand.png"),
                    part + " 缺少手持头部贴图 textures/item/tetra_spear_head_" + part + "_in_hand.png");
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearHolosphereEntryExists(GameTestHelper helper) {
        // 全息球「适用于此原理图的材料」按钮按 assets/tetra/holosphere_entries 的条目找物品+archetype；
        // Tetra 只给静态模块物品写了条目，dynamic_handheld 必须自己补一条，否则点进去会回首页。
        JsonElement root = spearcore$readTetraAssetJson(helper, "holosphere_entries/modular_spear.json");
        if (root == null) {
            return;
        }
        JsonObject json = root.getAsJsonObject();
        helper.assertTrue("tetra:dynamic_handheld".equals(json.get("item").getAsString()),
                "全息球条目必须指向 tetra:dynamic_handheld");
        helper.assertTrue(TetraSpearProfile.SPEAR_ARCHETYPE_KEY.equals(json.get("archetype").getAsString()),
                "全息球条目必须限定 archetype=modular_spear");
        helper.assertTrue(json.has("icon") && json.has("position"), "全息球条目必须有 icon 与 position");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearCompositeStagesAreDataDefined(GameTestHelper helper) {
        // 积竹木柲：一个模块 + 三级原地改进（类似剑的缠柄），后续升级不换模块。
        // 1/3 数值故意很低，逼玩家加竹/缠藤；完整度固定 2.5，不随升级变化。
        JsonObject module = spearcore$readJson(helper, "tetra", "modules/spear/composite_shaft.json")
                .getAsJsonObject();
        helper.assertTrue("spear/handle".equals(module.getAsJsonArray("slots").get(0).getAsString()),
                "积竹木柲必须占 spear/handle");
        JsonObject variant = module.getAsJsonArray("variants").get(0).getAsJsonObject();
        helper.assertTrue(variant.getAsJsonArray("materials").size() == 1
                        && "tetra:wood/".equals(variant.getAsJsonArray("materials").get(0).getAsString()),
                "积竹木柲只接受木板（tetra:wood/）");
        double baseSpeed = variant.getAsJsonObject("attributes").get("generic.attack_speed").getAsDouble();
        double baseReach = variant.getAsJsonObject("attributes").get("forge:entity_reach").getAsDouble();
        helper.assertTrue(baseSpeed > -0.5D, "1/3 矛杆攻速必须明显弱于最终形态，实际: " + baseSpeed);
        helper.assertTrue(baseReach < 0.1D, "1/3 矛杆射程必须很低，实际: " + baseReach);
        helper.assertTrue(variant.getAsJsonObject("extract").get("integrity").getAsDouble() == 2.5D,
                "积竹木柲完整度必须固定 2.5（用户要求不动）");

        JsonElement stageRoot = spearcore$readJson(helper, "tetra",
                "improvements/spear/composite/stage.json");
        if (stageRoot == null) {
            return;
        }
        JsonArray levels = stageRoot.getAsJsonArray();
        helper.assertTrue(levels.size() == 3, "积竹木柲应有 3 级原地加固改进");
        double previousSpeed = baseSpeed;
        double previousReach = baseReach;
        for (int i = 0; i < levels.size(); i++) {
            JsonObject entry = levels.get(i).getAsJsonObject();
            helper.assertTrue("spear_composite/stage".equals(entry.get("key").getAsString()),
                    "改进 key 必须是 spear_composite/stage");
            helper.assertTrue(entry.get("level").getAsInt() == i + 1,
                    "改进等级必须依次为 1..3");
            helper.assertFalse(entry.has("integrity"),
                    "加固改进不能改完整度（用户要求不动）");
            double speed = entry.getAsJsonObject("attributes").get("generic.attack_speed").getAsDouble();
            double reach = entry.getAsJsonObject("attributes").get("forge:entity_reach").getAsDouble();
            helper.assertTrue(speed < previousSpeed,
                    "加固 " + (i + 1) + " 的攻速应继续变慢（更重），实际: " + speed);
            helper.assertTrue(reach > previousReach,
                    "加固 " + (i + 1) + " 的射程应继续增加，实际: " + reach);
            previousSpeed = speed;
            previousReach = reach;
        }

        // 2/3、3/3、完成都必须是 improvement 类型，且只给改进、不写 moduleKey/moduleVariant。
        String[][] staged = {
                {"composite_shaft_2", "1", "bamboo"},
                {"composite_shaft_3", "2", "vines"},
                {"composite_shaft_finish", "3", null},
        };
        for (String[] stage : staged) {
            JsonObject schematic = spearcore$readJson(helper, "tetra",
                    "schematics/spear/" + stage[0] + ".json").getAsJsonObject();
            helper.assertTrue("improvement".equals(schematic.get("displayType").getAsString()),
                    stage[0] + " 必须是 displayType=improvement（原地改装）");
            JsonObject outcome = schematic.getAsJsonArray("outcomes").get(0).getAsJsonObject();
            helper.assertFalse(outcome.has("moduleKey"), stage[0] + " 不能换模块（不写 moduleKey）");
            helper.assertFalse(outcome.has("moduleVariant"), stage[0] + " 不能换变体（不写 moduleVariant）");
            helper.assertTrue(outcome.getAsJsonObject("improvements")
                            .get("spear_composite/stage").getAsInt() == Integer.parseInt(stage[1]),
                    stage[0] + " 必须给 spear_composite/stage 等级 " + stage[1]);
            if (stage[2] != null) {
                helper.assertTrue(outcome.getAsJsonObject("material").getAsJsonArray("items")
                                .get(0).getAsString().contains(stage[2]),
                        stage[0] + " 的固定材料不符");
            }
            helper.assertTrue(schematic.get("requirement").toString().contains("spear/composite_shaft"),
                    stage[0] + " 必须要求积竹木柲模块");
        }
        helper.assertTrue(spearcore$readJson(helper, "tetra", "schematics/spear/composite_shaft_2.json")
                        .getAsJsonObject().get("requirement").toString().contains("tetra:not"),
                "2/3 应要求「还没有加固改进」");

        // 稳定 I~V 只作用于已经到 3/3（stage level 2）的积竹木柲。
        for (int level = 1; level <= 5; level++) {
            JsonObject stable = spearcore$readJson(helper, "tetra",
                    "schematics/spear/handle/hone_stable_" + level + ".json").getAsJsonObject();
            String requirement = stable.get("requirement").toString();
            helper.assertTrue(stable.has("hone") && stable.get("hone").getAsBoolean(),
                    "稳定 " + level + " 必须是 hone=true");
            helper.assertTrue(requirement.contains("spear/composite_shaft"),
                    "稳定 " + level + " 必须作用于积竹木柲");
            helper.assertTrue(requirement.contains("spear_composite/stage"),
                    "稳定 " + level + " 必须要求加固等级 2（3/3）");
        }
        JsonObject finish = spearcore$readJson(helper, "tetra",
                "schematics/spear/composite_shaft_finish.json").getAsJsonObject();
        String finishRequirement = finish.get("requirement").toString();
        helper.assertTrue(finishRequirement.contains("spear_hone/stable")
                        && finishRequirement.contains("\"min\":5"),
                "完成必须要求稳定满级");
        helper.assertTrue(finish.getAsJsonArray("outcomes").get(0).getAsJsonObject()
                        .getAsJsonObject("improvements").has("spear_composite/stage"),
                "完成必须给最终加固等级");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearHeadBalanceIsDataDefined(GameTestHelper helper) {
        // 三尖两刃刀必须「全面强于普通矛头，含攻速」；槊只接受金属、攻速更慢。
        JsonObject head = spearcore$readJson(helper, "tetra", "modules/spear/head.json").getAsJsonObject()
                .getAsJsonArray("variants").get(0).getAsJsonObject();
        JsonObject shuo = spearcore$readJson(helper, "tetra", "modules/spear/shuo_head.json").getAsJsonObject()
                .getAsJsonArray("variants").get(0).getAsJsonObject();
        JsonObject three = spearcore$readJson(helper, "tetra", "modules/spear/three_point_blade.json").getAsJsonObject()
                .getAsJsonArray("variants").get(0).getAsJsonObject();
        double headSpeed = head.getAsJsonObject("attributes").get("generic.attack_speed").getAsDouble();
        double threeSpeed = three.getAsJsonObject("attributes").get("generic.attack_speed").getAsDouble();
        double shuoSpeed = shuo.getAsJsonObject("attributes").get("generic.attack_speed").getAsDouble();
        helper.assertTrue(threeSpeed > headSpeed,
                "三尖两刃刀攻速必须高于普通矛头（" + threeSpeed + " > " + headSpeed + "）");
        helper.assertTrue(shuoSpeed < headSpeed,
                "槊攻速必须低于普通矛头（" + shuoSpeed + " < " + headSpeed + "）");
        double headDamage = head.getAsJsonObject("extract").getAsJsonObject("primaryAttributes")
                .get("generic.attack_damage").getAsDouble();
        double threeDamage = three.getAsJsonObject("extract").getAsJsonObject("primaryAttributes")
                .get("generic.attack_damage").getAsDouble();
        helper.assertTrue(threeDamage > headDamage, "三尖两刃刀伤害必须高于普通矛头");
        helper.assertTrue(three.getAsJsonArray("materials").size() >= 5,
                "三尖两刃刀不限制材料（至少五类）");
        helper.assertTrue(shuo.getAsJsonArray("materials").size() == 1
                        && "tetra:metal/".equals(shuo.getAsJsonArray("materials").get(0).getAsString()),
                "槊头只接受金属");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraTuningDefaultsAreFull(GameTestHelper helper) {
        helper.assertTrue(TetraSpearStats.DEFAULT_EXTRA_REACH_SCALE == 1.0D,
                "Tetra 射程缩放默认必须是 1.0（不缩）");
        helper.assertTrue(TetraDamageScaling.DEFAULT_DAMAGE_SCALE == 1.0D,
                "Tetra 伤害缩放默认必须是 1.0（不缩）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearHeadSlashingFlags(GameTestHelper helper) {
        helper.assertTrue(TetraSpearHead.of("spear_head/iron") == TetraSpearHead.PLAIN,
                "普通矛头不是挥砍");
        helper.assertTrue(TetraSpearHead.of("spear_shuo_head/iron") == TetraSpearHead.SHUO
                        && TetraSpearHead.of("spear_shuo_head/iron").slashing(),
                "槊头必须是挥砍");
        helper.assertTrue(TetraSpearHead.of("spear_three_point/iron") == TetraSpearHead.THREE
                        && TetraSpearHead.of("spear_three_point/iron").slashing(),
                "三尖两刃刀必须是挥砍");
        helper.assertTrue(TetraSpearHead.ofVariantAndModule(null, "spear/shuo_head") == TetraSpearHead.SHUO,
                "模块 key spear/shuo_head 必须识别为槊头（不依赖变体前缀）");
        helper.assertTrue(TetraSpearHead.ofVariantAndModule(null, "spear/three_point_blade")
                        == TetraSpearHead.THREE,
                "模块 key spear/three_point_blade 必须识别为三尖两刃刀");
        helper.assertTrue(TetraSpearHead.of(null) == TetraSpearHead.PLAIN,
                "读不到变体时按普通矛头处理");
        helper.assertTrue("item.spearcore.modular_spear.named_shuo".equals(TetraSpearHead.SHUO.nameKey())
                        && "item.spearcore.modular_spear.named_blade".equals(TetraSpearHead.THREE.nameKey()),
                "矛头名字 key 必须区分槊/刃");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraModuleGateMappingMatchesSpearSlots(GameTestHelper helper) {
        helper.assertTrue(TetraModuleGateMapping.matches("spear/head", "sword/basic_blade"),
                "剑刃门禁应映射到矛头槽");
        helper.assertTrue(TetraModuleGateMapping.matches("spear/handle", "sword/basic_hilt"),
                "剑柄门禁应映射到矛杆槽");
        helper.assertTrue(TetraModuleGateMapping.matches("spear/handle", "single/long_handle"),
                "单头工具手柄门禁应映射到矛杆槽");
        helper.assertTrue(TetraModuleGateMapping.matches("spear/binding", "sword/basic_guard"),
                "护手门禁应映射到绑定槽");
        helper.assertTrue(TetraModuleGateMapping.matches("spear/butt", "sword/counterweight"),
                "配重门禁应映射到枪纂槽");
        helper.assertFalse(TetraModuleGateMapping.matches("spear/head", "sword/basic_hilt"),
                "剑柄门禁不能匹配矛头槽");
        helper.assertFalse(TetraModuleGateMapping.matches("spear/head", "tetra:random_module"),
                "非 Tetra 武器模块不参与映射");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearScrollLootNbtIsComplete(GameTestHelper helper) {
        CompoundTag tag = TetraSpearScrolls.buildTag(TetraSpearScrolls.THREE_POINT_BLADE);
        CompoundTag scroll = tag.getCompound("BlockEntityTag").getList("data", 10).getCompound(0);
        helper.assertTrue(scroll.getList("schematics", 8).size() == 1
                        && "tetra:spear/three_point_blade".equals(scroll.getList("schematics", 8).getString(0)),
                "战利品卷轴必须解锁三尖两刃刀原理图");
        helper.assertTrue(scroll.getBoolean("intricate"), "三尖两刃刀卷轴必须是复杂原理图");
        helper.assertTrue(scroll.getList("glyphs", 3).size() == 4, "战利品卷轴必须带 4 个字形");
        try {
            Long.parseLong(scroll.getString("ribbon"), 16);
        } catch (NumberFormatException exception) {
            helper.fail("战利品卷轴的 ribbon 必须是合法 16 进制字符串");
            return;
        }
        helper.assertTrue("tetra:spear/composite_shaft_1".equals(
                        TetraSpearScrolls.COMPOSITE_SHAFT.schematics()[0]),
                "积竹木柲卷轴必须解锁 1/3 阶段配方");
        helper.succeed();
    }

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

    /** 从 classpath 读本模组 assets 里的 JSON（模型这类纯客户端资产不走服务端 ResourceManager）。 */
    private static JsonElement spearcore$readAssetJson(GameTestHelper helper, String path) {
        try (InputStream stream = TetraCompatGameTests.class.getResourceAsStream("/assets/spearcore/" + path)) {
            if (stream == null) {
                helper.fail("缺少 assets/spearcore/" + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            helper.fail("读取 assets/spearcore/" + path + " 失败: " + exception);
            return null;
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void modularSpearFullerIsDataDefined(GameTestHelper helper) {
        // 第五槽「强化附层」（数据抄 Tetra 剑的 reinforced_fuller）：archetype 必须开槽（minor、非 required），模块与配方共用 spear/fuller 槽。
        JsonElement moduleRoot = spearcore$readJson(helper, "tetra", "modules/spear/fuller.json");
        if (moduleRoot == null) {
            return;
        }
        JsonObject module = moduleRoot.getAsJsonObject();
        JsonArray moduleSlots = module.getAsJsonArray("slots");
        helper.assertTrue(moduleSlots != null && moduleSlots.size() == 1
                        && "spear/fuller".equals(moduleSlots.get(0).getAsString()),
                "强化附层模块必须占 spear/fuller");
        JsonObject variant = module.getAsJsonArray("variants").get(0).getAsJsonObject();
        helper.assertTrue("spear_fuller/".equals(variant.get("key").getAsString()),
                "强化附层变体前缀必须是 spear_fuller/");
        Set<String> materials = new HashSet<>();
        for (JsonElement element : variant.getAsJsonArray("materials")) {
            materials.add(element.getAsString());
        }
        for (String material : new String[]{"tetra:metal/", "tetra:wood/", "tetra:bone/",
                "tetra:misc/forged_beam", "tetra:fuller/"}) {
            helper.assertTrue(materials.contains(material),
                    "强化附层必须接受 " + material + "，实际: " + materials);
        }
        JsonElement schematicRoot = spearcore$readJson(helper, "tetra", "schematics/spear/fuller.json");
        if (schematicRoot == null) {
            return;
        }
        JsonObject schematic = schematicRoot.getAsJsonObject();
        helper.assertTrue(schematic.getAsJsonArray("slots").get(0).getAsString().equals("spear/fuller"),
                "强化附层配方必须写 slots=spear/fuller");
        helper.assertTrue(schematic.get("materialSlotCount").getAsInt() == 1,
                "强化附层配方应占用一个材料槽");
        JsonObject outcome = schematic.getAsJsonArray("outcomes").get(0).getAsJsonObject();
        helper.assertTrue("spear/fuller".equals(outcome.get("moduleKey").getAsString()),
                "强化附层 outcome 的 moduleKey 必须是 spear/fuller");
        helper.assertTrue(outcome.get("moduleVariant").getAsString().startsWith("spear_fuller/"),
                "强化附层 outcome 的 moduleVariant 必须是 spear_fuller/ 前缀");
        helper.assertTrue(schematic.getAsJsonArray("outcomes").size() == 1,
                "强化附层只应有一个通用 outcome，不为单个 mod 写专用模块");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearHandleIntegrityRaised(GameTestHelper helper) {
        JsonElement root = spearcore$readJson(helper, "tetra", "modules/spear/handle.json");
        if (root == null) {
            return;
        }
        JsonObject variant = root.getAsJsonObject().getAsJsonArray("variants").get(0).getAsJsonObject();
        helper.assertTrue(variant.getAsJsonObject("extract").get("integrity").getAsDouble() == 1.3D,
                "普通矛杆的完整度应为 1.3（比旧版 1.0 稍高）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearButtAddsAttackDamage(GameTestHelper helper) {
        JsonElement root = spearcore$readJson(helper, "tetra", "modules/spear/butt.json");
        if (root == null) {
            return;
        }
        JsonObject variant = root.getAsJsonObject().getAsJsonArray("variants").get(0).getAsJsonObject();
        JsonObject attributes = variant.getAsJsonObject("attributes");
        helper.assertTrue(attributes != null && attributes.has("generic.attack_damage"),
                "枪纂必须给 generic.attack_damage，才不会只是纯配重");
        double damage = attributes.get("generic.attack_damage").getAsDouble();
        helper.assertTrue(damage == 0.5D, "枪纂攻击力加值应为 +0.5，实际: " + damage);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void tetraCraftingEffectModuleMappingMatchesSpearSlots(GameTestHelper helper) {
        // crafting effect 的 tetra:module 条件与 schematic requirement 是两个类；这里校验纯映射函数，
        // 保证 RevelationFix 的神灵金锭（sword/* 模块条件）能命中矛的刃/柄/护手/配重。
        helper.assertTrue(TetraModuleGateMapping.matches("spear/head", "sword/basic_blade"),
                "剑刃条件应映射到矛头槽");
        helper.assertTrue(TetraModuleGateMapping.matches("spear/handle", "sword/basic_hilt"),
                "剑柄条件应映射到矛杆槽");
        helper.assertTrue(TetraModuleGateMapping.matchesAnySpearSlot("sword/basic_blade"),
                "anySlot 条件下剑刃应命中矛的任一槽位");
        helper.assertTrue(TetraModuleGateMapping.matchesAnySpearSlot("sword/basic_guard"),
                "anySlot 条件下护手应命中矛的任一槽位");
        helper.assertTrue(TetraModuleGateMapping.matchesAnySpearSlot("sword/counterweight"),
                "anySlot 条件下配重应命中矛的任一槽位");
        helper.assertFalse(TetraModuleGateMapping.matchesAnySpearSlot("tetra:random_module"),
                "非 Tetra 武器模块不应被映射");
        helper.assertFalse(TetraModuleGateMapping.matches("spear/head", "sword/basic_hilt"),
                "剑柄条件不能匹配矛头槽");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void spearScrollsTabIsRegistered(GameTestHelper helper) {
        // 卷轴的正常来源是交易/战利品；创造栏那份是验收入口。这里钉住类本身，
        // 防止以后清理时又把「测试时拿不到积竹木柲卷轴」当成数据坏了。
        try {
            Class<?> tab = Class.forName("net.minecraft.spearcore.compat.tetra.TetraSpearScrollsTab");
            boolean found = false;
            for (var method : tab.getDeclaredMethods()) {
                if ("buildContents".equals(method.getName()) && method.getParameterCount() == 1) {
                    found = true;
                }
            }
            helper.assertTrue(found, "TetraSpearScrollsTab 必须保留 BuildCreativeModeTabContentsEvent 订阅方法");
        } catch (ClassNotFoundException exception) {
            helper.fail("缺少 TetraSpearScrollsTab（卷轴验收入口）: " + exception);
            return;
        }
        helper.succeed();
    }

    /** 从 classpath 读 Tetra 命名空间下的 assets（全息球条目等）。 */
    private static JsonElement spearcore$readTetraAssetJson(GameTestHelper helper, String path) {
        try (InputStream stream = TetraCompatGameTests.class
                .getResourceAsStream("/assets/tetra/" + path)) {
            if (stream == null) {
                helper.fail("缺少 assets/tetra/" + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            helper.fail("读取 assets/tetra/" + path + " 失败: " + exception);
            return null;
        }
    }

    /** classpath 里是否存在本模组 assets 下的这个文件（用来钉住贴图别被误删）。 */
    private static boolean spearcore$assetExists(String path) {
        return TetraCompatGameTests.class.getResource("/assets/spearcore/" + path) != null;
    }

    private static void assertReach(GameTestHelper helper, double extraReach, double sourceBonus,
                                    double scale, double expected) {
        double actual = SpearCombat.weakenExtraReach(extraReach, sourceBonus, scale);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6D,
                "额外距离削弱不符: extra=" + extraReach + ", bonus=" + sourceBonus
                        + ", scale=" + scale + ", expected=" + expected + ", actual=" + actual);
    }
}
