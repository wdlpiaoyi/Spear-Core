package net.minecraft.spearcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.client.animation.SpearAnimations;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, value = Dist.CLIENT)
public class SpearChargeHandler {

    /** 上一次 tick 的玩家实例；换维度/重生时 LocalPlayer 会被替换，需要清命中回弹。 */
    private static LocalPlayer lastPlayer;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            // 断开世界时 player 会变 null；不归零会让命中回弹计时冻结，进新世界后突然触发。
            lastPlayer = null;
            SpearAnimations.spearHitTicks = 0;
            return;
        }
        if (player != lastPlayer) {
            lastPlayer = player;
            SpearAnimations.spearHitTicks = 0;
        }

        // 命中反馈动画倒计时
        if (SpearAnimations.spearHitTicks > 0) {
            SpearAnimations.spearHitTicks--;
        }
    }
}
