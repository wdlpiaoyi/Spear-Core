package net.minecraft.spearcore.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.event.SpearAttackHandler;
import net.minecraft.spearcore.init.SpearSounds;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.util.SpearCondition;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * 第三方兼容缝测试。
 *
 * <p>AvaritiaSpear 1.4.0 直接按描述符 mixin 本模组的成员（remap=false），一旦名字 / 参数类型变了，
 * 对方启动就会 FATAL。这里用反射把已经承诺的旧签名钉死，防止后续重构再次误删。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CompatibilityGameTests {

    private static final String TEMPLATE = "empty5x5x5";

    private CompatibilityGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(CompatibilityGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void legacyThirdPartySeamsStillExist(GameTestHelper helper) {
        // AvaritiaSpear 的 SpearCollisionInfinityMixin 按这个描述符注入；不能改、不能删。
        Method legacyHitScan = getMethod(helper, SpearItem.class, "getHitEntitiesAlong",
                LivingEntity.class, SpearItem.class, float.class, Predicate.class);
        helper.assertTrue(List.class.isAssignableFrom(legacyHitScan.getReturnType()),
                "旧签名 getHitEntitiesAlong 必须返回 List<EntityHitResult>");
        Type genericReturn = legacyHitScan.getGenericReturnType();
        helper.assertTrue(genericReturn instanceof ParameterizedType parameterized
                        && parameterized.getActualTypeArguments().length == 1
                        && parameterized.getActualTypeArguments()[0] == EntityHitResult.class,
                "旧签名返回值应为 List<EntityHitResult>: " + genericReturn.getTypeName());

        // Tetra 路径使用的新签名也必须同时保留。
        getMethod(helper, SpearItem.class, "getHitEntitiesAlong",
                LivingEntity.class, SpearLike.class, float.class, Predicate.class);

        // AvaritiaSpear 的 SpearItemLungeMixin / SpearAttackHandlerInfinityMixin 的注入点。
        getMethod(helper, SpearItem.class, "jerotesLungeForwardMaybe", LivingEntity.class);
        getMethod(helper, SpearItem.class, "rushAttack", LivingEntity.class, float.class);
        getMethod(helper, SpearAttackHandler.class, "onPlayerAttack", AttackEntityEvent.class);

        // AvaritiaSpear 的 item 类 / mixin 直接引用的静态成员。
        for (String field : new String[]{
                "ITEM_SPEAR_ATTACK", "ITEM_SPEAR_HIT", "ITEM_SPEAR_USE",
                "ITEM_SPEAR_LUNGE_1", "ITEM_SPEAR_LUNGE_2", "ITEM_SPEAR_LUNGE_3"}) {
            try {
                SpearSounds.class.getField(field);
            } catch (NoSuchFieldException exception) {
                helper.fail("SpearSounds." + field + " 被删了，第三方兼容会断");
            }
        }
        try {
            SpearCondition.class.getDeclaredConstructor(int.class, float.class, float.class);
        } catch (NoSuchMethodException exception) {
            helper.fail("SpearCondition(int,float,float) 构造器被改了，第三方兼容会断");
        }

        helper.succeed();
    }

    private static Method getMethod(GameTestHelper helper, Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            helper.fail("兼容缝丢失: " + owner.getSimpleName() + "." + name
                    + Arrays.toString(parameterTypes));
            throw new AssertionError(exception);
        }
    }
}
