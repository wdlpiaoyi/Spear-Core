package net.minecraft.spearcore.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 物品颜色与材质色缓存的事件入口。
 *
 * <p>先用 {@code ModList} 守卫，确认装了 Tetra 才触碰 {@link TetraColorRegistration} / 清缓存，
 * 保证未安装 Tetra 时客户端不会加载任何 {@code se.mickelus.tetra.*} 类。</p>
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TetraSpearColorsEvent {

    private TetraSpearColorsEvent() {
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        TetraColorRegistration.register(event);
    }

    /**
     * 资源重载（包括 KubeJS / Tetra 的数据包热重载）时清掉材质色缓存。
     *
     * <p>不清的话，改过材料 tint 的整合包要重启游戏才生效；普通玩家看不到，但作者调试时会以为改坏了。</p>
     */
    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
                net.minecraft.spearcore.compat.tetra.TetraSpearColors.clearCache();
            }
        });
    }
}
