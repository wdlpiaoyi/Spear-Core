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
 * <p>卷轴暂时没有获取方式（用户明确说先不要），所以只放在创造栏里便于验收；等以后接了战利品/配方，
 * 这里可以整段删掉。Tetra 没装时直接返回，不触碰任何 Tetra 类（{@code ModList} 是 Forge 的）。</p>
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
