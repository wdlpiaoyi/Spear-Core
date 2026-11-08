package net.minecraft.spearcore.compat.tetra;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 数据加载完成后触发一次「第三方 fuller 变体导入」。
 *
 * <p>这个类会被 FML 主动加载，所以它本身不能 import 任何 Tetra 类；
 * 真正实现放在 {@link TetraFullerImportImpl}，只有确认 Tetra 已加载后才反射调用。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class TetraFullerImportEvent {

    private TetraFullerImportEvent() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        importFullers();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // TagsUpdatedEvent 有时早于 Tetra 的 SchematicRegistry 初始化；世界启动后再补一次（幂等）。
        importFullers();
    }

    private static void importFullers() {
        if (!ModList.get().isLoaded("tetra")) {
            return;
        }
        try {
            Class<?> impl = Class.forName("net.minecraft.spearcore.compat.tetra.TetraFullerImportImpl");
            impl.getMethod("importAll").invoke(null);
        } catch (Throwable ignored) {
            // 数据未就绪/无第三方 fuller 时静默跳过，不影响基础附层。
        }
    }
}
