package net.minecraft.spearcore.compat.tetra;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetNbtFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 三尖两刃刀卷轴的低概率战利品：下界要塞、堡垒宝藏、要塞图书馆、末地城宝藏。
 *
 * <p>卷轴不是独立物品，必须先压 NBT；这里复用原版 {@link SetNbtFunction} 写入
 * {@link TetraSpearScrolls#buildTag(TetraSpearScrolls.Definition)}。不要自定义
 * {@code LootItemFunction} 再冒充 {@code minecraft:set_nbt}——那样池子被序列化时 NBT 会丢。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class TetraSpearLoot {

    private static final float CHANCE = 0.04F;

    /** 命名池；LootTable#addPool 对同名池会抛异常，也能阻止重复触发事件时重复注入。 */
    private static final String POOL_NAME = "spearcore_three_point_scroll";

    private static final String[] TABLES = {
            "minecraft:chests/nether_bridge",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/end_city_treasure"
    };

    private static final CompoundTag THREE_POINT_TAG =
            TetraSpearScrolls.buildTag(TetraSpearScrolls.THREE_POINT_BLADE);

    private TetraSpearLoot() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        String name = event.getName().toString();
        if (!isTargetTable(name)) {
            return;
        }
        if (event.getTable().getPool(POOL_NAME) != null) {
            return;
        }
        ItemStack probe = TetraSpearScrolls.create(TetraSpearScrolls.THREE_POINT_BLADE);
        if (probe.isEmpty()) {
            return;
        }
        LootPool pool = LootPool.lootPool()
                .name(POOL_NAME)
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(probe.getItem())
                        .apply(SetNbtFunction.setTag(THREE_POINT_TAG)))
                .when(LootItemRandomChanceCondition.randomChance(CHANCE))
                .build();
        event.getTable().addPool(pool);
        SpearcoreMod.LOGGER.info("[spearcore] 三尖两刃刀卷轴已注入战利品表 {}", name);
    }

    private static boolean isTargetTable(String name) {
        for (String table : TABLES) {
            if (table.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
