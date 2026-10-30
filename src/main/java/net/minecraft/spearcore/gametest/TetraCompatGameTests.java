package net.minecraft.spearcore.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraSpearStats;
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
}
