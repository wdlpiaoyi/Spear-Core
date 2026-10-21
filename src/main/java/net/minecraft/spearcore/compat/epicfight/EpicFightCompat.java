package net.minecraft.spearcore.compat.epicfight;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCoreItems;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.item.SpearItem;

/**
 * Epic Fight 联动：把本模组的矛注册成 EF 的长矛武器，完全复用 EF 的长矛体系
 * （连招、站姿、突刺动画、命中判定都由 EF 提供）。
 *
 * <p><b>类加载隔离：</b>本类直接引用 yesman.epicfight.* ，所以只能在 EF 已加载时被
 * {@link #init(IEventBus)} 触达；调用点由 ModList.get().isLoaded("epicfight") 守住。
 * 没装 EF 时本类不会被解析，不会 NoClassDefFoundError。</p>
 *
 * <p><b>能力来源：</b>不自己拼 WeaponCapability.Builder（要手抄动画访问器，还得跟版本变），</p>
 * <p>而是找到 EF 自己的长矛物品、复用它的能力实例（能力不含物品特有状态，可共享）。</p>
 */
public final class EpicFightCompat {

    private static final String EPIC_FIGHT_ID = "epicfight";

    private EpicFightCompat() {
    }

    /** 只在确认 Epic Fight 已加载后调用。 */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EpicFightCompat::registerCapabilities);
        SpearcoreMod.LOGGER.info("[spearcore] 检测到 Epic Fight：矛注册为 EF 长矛武器，战斗交由 EF 结算");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        CapabilityItem cap = findSpearCapability();
        if (cap == null) {
            SpearcoreMod.LOGGER.warn("[spearcore] 未找到 Epic Fight 的长矛能力，EF 下矛可能按空手处理");
            return;
        }
        event.registerItem(EpicFightCapabilities.CAPABILITY_ITEM, (stack, ctx) -> cap,
                SpearCoreItems.WOODEN_SPEAR.get(), SpearCoreItems.STONE_SPEAR.get(),
                SpearCoreItems.IRON_SPEAR.get(), SpearCoreItems.GOLDEN_SPEAR.get(),
                SpearCoreItems.DIAMOND_SPEAR.get(), SpearCoreItems.NETHERITE_SPEAR.get(),
                SpearCoreItems.COPPER_SPEAR.get());
    }

    /** 复用 EF 自己长矛物品的能力实例。 */
    private static CapabilityItem findSpearCapability() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof SpearItem)) {
                continue;
            }
            if (!EPIC_FIGHT_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                continue;
            }
            CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(new ItemStack(item));
            if (cap != null && cap != CapabilityItem.EMPTY) {
                return cap;
            }
        }
        return null;
    }
}
