package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 三尖两刃刀卷轴的低概率战利品：下界要塞、堡垒宝藏、要塞图书馆、末地城宝藏。
 *
 * <p>卷轴不是独立物品，必须先压 NBT；这里用运行时构造的 {@link LootItemFunction} 写入
 * {@link TetraSpearScrolls#buildTag(TetraSpearScrolls.Definition)}。函数类型借用原版
 * {@code minecraft:set_nbt}（只为序列化/校验占位，池子是在内存里直接构建的）。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class TetraSpearLoot {

    private static final float CHANCE = 0.04F;

    private static final String[] TABLES = {
            "minecraft:chests/nether_bridge",
            "minecraft:chests/bastion_treasure",
            "minecraft:chests/stronghold_library",
            "minecraft:chests/end_city_treasure"
    };

    private static final LootItemFunction.Builder STAMP_BUILDER = StampFunction::new;

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
        ItemStack probe = TetraSpearScrolls.create(TetraSpearScrolls.THREE_POINT_BLADE);
        if (probe.isEmpty()) {
            return;
        }
        LootPool pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(probe.getItem()).apply(STAMP_BUILDER))
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

    /** 给卷轴压上 {@code BlockEntityTag.data[0]}；不依赖任何 Tetra 类。 */
    private static final class StampFunction implements LootItemFunction {
        @Override
        public LootItemFunctionType getType() {
            return LootItemFunctions.SET_NBT;
        }

        @Override
        public void validate(ValidationContext context) {
        }

        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            stack.setTag(TetraSpearScrolls.buildTag(TetraSpearScrolls.THREE_POINT_BLADE));
            return stack;
        }
    }
}
