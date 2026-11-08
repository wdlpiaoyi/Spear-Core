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
 * <p>这个类是客户端 {@code @Mod.EventBusSubscriber}，FML 会主动加载；所以这里**只用反射**接触
 * {@code TetraColorRegistration} / {@code TetraSpearColors}——它们 import 了 Tetra 类，
 * 未装 Tetra 的客户端如果被直接引用，会在类校验阶段 {@code NoClassDefFoundError}（3.4.0 踩过）。</p>
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
        try {
            Class<?> registration = Class.forName(
                    "net.minecraft.spearcore.client.TetraColorRegistration");
            registration.getMethod("register", RegisterColorHandlersEvent.Item.class)
                    .invoke(null, event);
        } catch (Throwable ignored) {
            // 兼容层失败只是没有按材料着色，不影响游戏。
        }
    }

    /**
     * 资源重载（包括 KubeJS / Tetra 的数据包热重载）时清掉材质色缓存。
     *
     * <p>不清的话，改过材料 tint 的整合包要重启游戏才生效。</p>
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
            protected void apply(Void value, ResourceManager resourceManager, ProfilerFiller profiler) {
                try {
                    Class<?> colors = Class.forName(
                            "net.minecraft.spearcore.compat.tetra.TetraSpearColors");
                    colors.getMethod("clearCache").invoke(null);
                } catch (Throwable throwable) {
                    // 缓存清理失败只影响热重载后的颜色。
                }
            }
        });
    }
}
