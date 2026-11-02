package net.minecraft.spearcore.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * 内部用的「标准武器」探针，只用来判断模组附魔是否属于武器类附魔。
 *
 * <p>不要把钻石剑当代理：整合包可能删掉钻石剑。这个物品不在创造物品栏、没有配方、
 * 不进任何本模组 tag，只额外挂到 {@code #minecraft:swords}，让按标签判断武器的模组也认得它。</p>
 */
public class EnchantmentProbeItem extends SwordItem {

    public EnchantmentProbeItem() {
        super(Tiers.NETHERITE, 3, -2.4F, new Item.Properties());
    }
}
