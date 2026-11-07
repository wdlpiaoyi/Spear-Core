package net.minecraft.spearcore.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 物品颜色注册事件入口。
 *
 * <p>先用 {@code ModList} 守卫，确认装了 Tetra 才触碰 {@link TetraColorRegistration}，保证未安装
 * Tetra 时客户端不会加载任何 {@code se.mickelus.tetra.*} 类。</p>
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
}
