package net.minecraft.spearcore.compat.tetra;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Tetra 数据重载后清维修定义缓存。
 *
 * <p>维修定义缓存只保存成功结果；Tetra 数据包热重载换掉材料物品后，旧定义会继续指向旧材料。
 * 这里挂一个空 reload listener，在数据重载完成时清缓存。反射访问 compat 类，未装 Tetra 也安全。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class TetraSpearReloadHandler {

    private TetraSpearReloadHandler() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void value, ResourceManager resourceManager, ProfilerFiller profiler) {
                try {
                    Class.forName("net.minecraft.spearcore.compat.tetra.TetraSpearRepairDefinitions")
                            .getMethod("clearCache").invoke(null);
                } catch (Throwable ignored) {
                    // 缓存清理失败只影响热重载后的维修项。
                }
            }

            @Override
            public String getName() {
                return "spearcore_tetra_repair_cache";
            }
        });
    }
}
