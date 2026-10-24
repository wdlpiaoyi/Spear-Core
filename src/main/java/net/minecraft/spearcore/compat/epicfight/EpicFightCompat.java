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
 * Epic Fight 联动：把本模组的矛注册成 EF 的长矛武器，复用 EF 的长矛体系。
 *
 * <p><b>类加载隔离：</b>本类直接引用 yesman.epicfight.*，只在 EF 已加载时由
 * SpearcoreMod 构造器通过 ModList 判定后触达；没装 EF 时本类不会被解析。</p>
 *
 * <p><b>为什么要懒加载能力实例：</b>注册发生在 RegisterCapabilitiesEvent，此时 EF 自己的物品
 * 能力可能还没注册完 —— 当场去取 EF 长矛物品的 CapabilityItem 会拿到 EMPTY，结果就是把矛当
 * 空手交给 EF（打不出伤害）。所以 provider 里再取，NeoForge 是真正被查询时才调 provider 的。</p>
 */
public final class EpicFightCompat {

    private static final String EPIC_FIGHT_ID = "epicfight";

    /** 缓存 EF 的长矛能力实例（懒加载一次）。 */
    private static CapabilityItem spearCapability;
    private static boolean capabilityResolved;

    private EpicFightCompat() {
    }

    /** 只在确认 Epic Fight 已加载后调用。 */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(EpicFightCompat::registerCapabilities);
        // 实验性：蓄力/冲刺/戳出时播放 EF 的长矛动作
        EpicFightSpearAnimation.init();
        // 启动收尾时主动解析一次：所有注册都已完成，结果会落到日志里，
        // 出问题时一眼能看出是"没找到 EF 长矛能力"还是别的。
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) ->
                resolveSpearCapability());
        SpearcoreMod.LOGGER.info("[spearcore] 检测到 Epic Fight：矛注册为 EF 长矛武器，战斗交由 EF 结算");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(EpicFightCapabilities.CAPABILITY_ITEM,
                (stack, context) -> resolveSpearCapability(),
                SpearCoreItems.WOODEN_SPEAR.get(), SpearCoreItems.STONE_SPEAR.get(),
                SpearCoreItems.IRON_SPEAR.get(), SpearCoreItems.GOLDEN_SPEAR.get(),
                SpearCoreItems.DIAMOND_SPEAR.get(), SpearCoreItems.NETHERITE_SPEAR.get(),
                SpearCoreItems.COPPER_SPEAR.get());
    }

    /** 懒加载：真正被查询时才去拿 EF 的长矛能力，此时 EF 的注册一定已经完成。 */
    private static CapabilityItem resolveSpearCapability() {
        if (!capabilityResolved) {
            capabilityResolved = true;
            spearCapability = findSpearCapability();
            if (spearCapability == null) {
                SpearcoreMod.LOGGER.warn("[spearcore] 未找到 Epic Fight 的长矛能力，EF 下矛将按空手处理");
            } else {
                SpearcoreMod.LOGGER.info("[spearcore] 已取得 Epic Fight 长矛能力：{}",
                        spearCapability.getWeaponCategory());
            }
        }
        return spearCapability;
    }

    /** 复用 EF 自己长矛物品的能力实例（它不含物品特有状态，可以共享）。 */
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