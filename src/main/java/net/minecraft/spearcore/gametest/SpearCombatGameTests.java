package net.minecraft.spearcore.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 多部件实体（末影龙等）的射线命中辅助函数测试。
 *
 * <p>这里只构造龙对象拿它的部件，不把龙 spawn 进世界，避免依赖末地 / 战斗管理器；
 * 真正的射线整合由用户进游戏验收。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpearCombatGameTests {

    private static final String TEMPLATE = "empty5x5x5";

    private SpearCombatGameTests() {
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        event.register(SpearCombatGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void multipartTargetsResolveToLivingParent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnderDragon dragon = new EnderDragon(EntityType.ENDER_DRAGON, level);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);

        helper.assertTrue(SpearCombat.resolveLivingTarget(dragon) == dragon,
                "末影龙本体应解析为自身");
        helper.assertTrue(SpearCombat.resolveLivingTarget(dragon.head) == dragon,
                "龙头部件应解析回母龙");
        helper.assertTrue(SpearCombat.resolveLivingTarget(dragon.getSubEntities()[2]) == dragon,
                "龙身部件应解析回母龙");
        helper.assertTrue(SpearCombat.resolveLivingTarget(zombie) == zombie,
                "普通生物应解析为自身");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = SpearcoreMod.MODID, template = TEMPLATE, batch = "spearcore")
    public static void multipartHitsCollapseToNearestPart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EnderDragon dragon = new EnderDragon(EntityType.ENDER_DRAGON, level);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);

        EntityHitResult headHit = new EntityHitResult(dragon.head, new Vec3(1.0D, 0.0D, 0.0D));
        EntityHitResult bodyHit = new EntityHitResult(dragon.getSubEntities()[2], new Vec3(2.0D, 0.0D, 0.0D));
        EntityHitResult zombieHit = new EntityHitResult(zombie, new Vec3(3.0D, 0.0D, 0.0D));

        List<EntityHitResult> sameDragon = SpearCombat.dedupeByRoot(List.of(headHit, bodyHit));
        helper.assertTrue(sameDragon.size() == 1, "同一只龙的多个部件应只保留一个，实际: " + sameDragon.size());
        helper.assertTrue(sameDragon.get(0) == headHit, "应保留最近的龙头部件");

        List<EntityHitResult> mixed = SpearCombat.dedupeByRoot(List.of(headHit, bodyHit, zombieHit));
        helper.assertTrue(mixed.size() == 2, "龙与僵尸应各保留一个，实际: " + mixed.size());
        helper.assertTrue(mixed.get(0) == headHit && mixed.get(1) == zombieHit,
                "去重后应保持原有按距离顺序");

        EnderDragonPart head = dragon.head;
        helper.assertTrue(SpearCombat.rootTarget(head) == dragon, "rootTarget 应返回母龙");
        helper.assertTrue(SpearCombat.rootTarget(zombie) == zombie, "rootTarget 对普通生物应返回自身");
        Entity unrelated = new net.minecraft.world.entity.item.ItemEntity(level, 0, 0, 0,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        helper.assertTrue(SpearCombat.rootTarget(unrelated) == unrelated, "rootTarget 对非生物应返回自身本身");
        helper.succeed();
    }
}
