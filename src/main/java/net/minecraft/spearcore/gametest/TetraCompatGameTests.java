package net.minecraft.spearcore.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.spearcore.compat.tetra.TetraSpearStats;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
    public static void tetraModuleKeysMatchSingleHeadedLayout(GameTestHelper helper) {
        // Tetra 的模块 key 是资源路径（single/spearhead），不是模块 JSON 里 improvements 的 spearhead/ 写法。
        // 3.3.1 之前按旧写法匹配，导致所有 Tetra 单头工具都被当成普通武器。
        String[] spearheads = {"single/spearhead", "spearhead", "addon/tools/spearhead"};
        for (String key : spearheads) {
            helper.assertTrue(TetraSpearProfile.isSpearheadModuleKey(key), "应识别矛头模块 key: " + key);
        }
        String[] notSpearheads = {"single/head", "spearhead_alt", "xspearhead"};
        for (String key : notSpearheads) {
            helper.assertTrue(!TetraSpearProfile.isSpearheadModuleKey(key), "不应把该 key 当成矛头: " + key);
        }
        helper.assertTrue(!TetraSpearProfile.isSpearheadModuleKey(null), "null 不应识别为矛头");

        String[] longHandles = {"single/long_handle", "long_handle", "addon/tools/long_handle"};
        for (String key : longHandles) {
            helper.assertTrue(TetraSpearProfile.isLongHandleModuleKey(key), "应识别长手柄模块 key: " + key);
        }
        String[] notLongHandles = {"single/basic_handle", "long_handle_alt", "xlong_handle"};
        for (String key : notLongHandles) {
            helper.assertTrue(!TetraSpearProfile.isLongHandleModuleKey(key), "不应把该 key 当成手柄: " + key);
        }
        helper.assertTrue(!TetraSpearProfile.isLongHandleModuleKey(null), "null 不应识别为长手柄");
        helper.succeed();
    }

    private static void assertReach(GameTestHelper helper, double extraReach, double sourceBonus,
                                    double scale, double expected) {
        double actual = SpearCombat.weakenExtraReach(extraReach, sourceBonus, scale);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-6D,
                "额外距离削弱不符: extra=" + extraReach + ", bonus=" + sourceBonus
                        + ", scale=" + scale + ", expected=" + expected + ", actual=" + actual);
    }
}
