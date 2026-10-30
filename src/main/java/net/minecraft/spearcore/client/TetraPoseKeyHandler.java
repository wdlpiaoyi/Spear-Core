package net.minecraft.spearcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.client.gui.TetraPoseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端 tick：按 F9 打开 Tetra 长矛姿态编辑器。
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, value = Dist.CLIENT)
public final class TetraPoseKeyHandler {

    private TetraPoseKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        while (TetraPoseKeyMapping.consumeOpenClick()) {
            minecraft.setScreen(new TetraPoseScreen());
        }
    }
}
