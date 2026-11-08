package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端在进入世界后再尝试一次 fuller 变体导入。
 *
 * <p>{@code TagsUpdatedEvent} 有时会在 Tetra 的 SchematicRegistry 建好之前触发；
 * 这里只作为补触发，导入本身是幂等的。类本身只 import Forge，Tetra 类由反射加载。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class TetraFullerImportClientEvent {

    private TetraFullerImportClientEvent() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        try {
            Class<?> impl = Class.forName("net.minecraft.spearcore.compat.tetra.TetraFullerImportImpl");
            impl.getMethod("importAll").invoke(null);
        } catch (Throwable ignored) {
            // 没有 Tetra 数据时静默跳过。
        }
    }
}
