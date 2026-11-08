package net.minecraft.spearcore.compat.tetra;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 给 {@code tetra:dynamic_handheld} 注册一个工作台维修项。
 *
 * <p>Tetra 的各个静态物品类在 {@code commonInit} 里各自 {@code new RepairSchematic(item, key)}，
 * 而 {@code DynamicModularItem} 没有这一步，所以本模组的模块化矛在工作台里连 Repair 动作都没有。</p>
 *
 * <p><b>必须全程反射</b>：这个类是 {@code @Mod.EventBusSubscriber}，FML 在启动时会主动加载它；
 * 如果直接 import Tetra 类型，未装 Tetra 的 GameTest 服务端会在类校验阶段就
 * {@code NoClassDefFoundError} 崩掉（3.4.0 踩过）。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TetraSpearRepairSetup {

    private static boolean loggedFailure;

    private TetraSpearRepairSetup() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TetraSpearRepairSetup::register);
    }

    private static void register() {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tetra", "dynamic_handheld"));
            if (item == null) {
                return;
            }
            Class<?> modularItem = Class.forName("se.mickelus.tetra.items.modular.IModularItem");
            if (!modularItem.isInstance(item)) {
                return;
            }
            Class<?> schematicClass = Class.forName("se.mickelus.tetra.module.schematic.UpgradeSchematic");
            Class<?> repairClass = Class.forName("se.mickelus.tetra.module.schematic.RepairSchematic");
            Object repair = repairClass.getConstructor(modularItem, String.class).newInstance(item, "modular_spear");
            Class<?> registryClass = Class.forName("se.mickelus.tetra.module.SchematicRegistry");
            Object registry = registryClass.getField("instance").get(null);
            registryClass.getMethod("registerSchematic", schematicClass).invoke(registry, repair);
            SpearcoreMod.LOGGER.info("[spearcore] 已为模块化长矛注册工作台维修项");
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 注册模块化长矛维修项失败，工作台将没有维修", throwable);
            }
        }
    }
}
