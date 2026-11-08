package net.minecraft.spearcore.compat.tetra;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 把本模组的矛部件卷轴挂进 Tetra 的创造栏。
 *
 * <p>卷轴的正常获取是工具匠/武器匠交易与四张战利品表；创造栏保留一份作为验收入口，
 * 避免「测试时拿不到卷轴 → 工作台看不到配方」被误判成兼容坏掉。Tetra 没装时直接返回，
 * 不触碰任何 Tetra 类（{@code ModList} 是 Forge 的）。</p>
 *
 * <p>bus 必须写 MOD：{@code BuildCreativeModeTabContentsEvent} 是 {@code IModBusEvent}。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TetraSpearScrollsTab {
    private static final ResourceKey<CreativeModeTab> TETRA_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, new ResourceLocation("tetra", "default"));

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != TETRA_TAB) return;
        if (!ModList.get().isLoaded("tetra")) return;
        for (TetraSpearScrolls.Definition definition : TetraSpearScrolls.ALL) {
            ItemStack stack = TetraSpearScrolls.create(definition);
            if (!stack.isEmpty()) {
                event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
    }
}
