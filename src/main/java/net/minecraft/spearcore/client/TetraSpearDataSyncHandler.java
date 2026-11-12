package net.minecraft.spearcore.client;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Tetra 材料数据同步后的缓存刷新。
 *
 * <p>客户端资源重载已经会清材质色缓存，但 Tetra 在登录/数据同步时可能只通过网络下发材料数据，
 * 不一定触发资源重载；这里在配方同步和切换世界时再清一次。全部反射，未装 Tetra 不加载其类。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, value = Dist.CLIENT)
public final class TetraSpearDataSyncHandler {

    private TetraSpearDataSyncHandler() {
    }

    @SubscribeEvent
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        clearColors();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearColors();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        clearColors();
    }

    private static void clearColors() {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        try {
            Class.forName("net.minecraft.spearcore.compat.tetra.TetraSpearColors")
                    .getMethod("clearCache").invoke(null);
        } catch (Throwable ignored) {
            // 缓存清理失败只影响颜色刷新。
        }
    }
}
