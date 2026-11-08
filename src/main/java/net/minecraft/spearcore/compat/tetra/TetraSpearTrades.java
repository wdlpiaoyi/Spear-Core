package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 卷轴的村民交易：工具匠 master 卖积竹木柲卷轴，武器匠 master 卖槊头卷轴。
 *
 * <p>Tetra 的卷轴不是独立物品（{@code tetra:scroll_rolled} + NBT），所以每个 offer 都要现拼一份 NBT；
 * 未装 Tetra / 物品没注册时整段跳过。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class TetraSpearTrades {

    private static final int MASTER_LEVEL = 5;

    private TetraSpearTrades() {
    }

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        if (event.getType() == VillagerProfession.TOOLSMITH) {
            addScrollOffer(event, MASTER_LEVEL, TetraSpearScrolls.COMPOSITE_SHAFT, 16);
        } else if (event.getType() == VillagerProfession.WEAPONSMITH) {
            addScrollOffer(event, MASTER_LEVEL, TetraSpearScrolls.SHUO_HEAD, 24);
        }
    }

    private static void addScrollOffer(VillagerTradesEvent event, int level,
                                       TetraSpearScrolls.Definition definition, int emeralds) {
        if (TetraSpearScrolls.create(definition).isEmpty()) {
            return;
        }
        event.getTrades().computeIfAbsent(level, key -> new java.util.ArrayList<>())
                .add(new SpearScrollListing(definition, emeralds));
    }

    /** 固定报价的卷轴交易；每次 {@code getOffer} 现拼一份卷轴 NBT，避免共享同一个 ItemStack。 */
    private record SpearScrollListing(TetraSpearScrolls.Definition definition, int emeralds)
            implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ItemStack scroll = TetraSpearScrolls.create(definition);
            if (scroll.isEmpty()) {
                return null;
            }
            return new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), scroll, 1, 20, 0.05F);
        }
    }
}
