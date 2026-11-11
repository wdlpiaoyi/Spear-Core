package net.minecraft.spearcore.event;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShieldItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 矛盾共持（{@code behavior.shieldCoWield}）的接盾 / 收盾。
 *
 * <p>两种模式：</p>
 * <ul>
 *   <li>{@code BEDROCK}：潜行即自动举盾（原版基岩版的盾牌机制，不需要右键）。松手没有意义，
 *       所以只由本类负责收盾；蹲下时如果正在蓄力，会先收矛再举盾（基岩版蹲下会被盾牌占用）。</li>
 *   <li>{@code MODDED}：举盾由右键触发（{@link SpearCombat#tryStartCharging} 把右键让给副手盾），
 *       这里只负责「起身收盾」——玩家站起来而没松右键时，原版不会自己收盾。</li>
 * </ul>
 *
 * <p>只在服务端执行：{@code LivingEntity#startUsingItem/stopUsingItem} 会写 using-item 的实体数据，
 * 由原版同步给客户端（{@code onSyncedDataUpdated} 会补上 useItem）；只在客户端调用不发包，
 * 两侧状态会不一致。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class SpearShieldStanceHandler {

    private SpearShieldStanceHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        boolean sneaking = player.isShiftKeyDown();
        boolean mainHandSpear = SpearLikeSupport.of(player.getMainHandItem()) != null;
        boolean offhandShield = player.getOffhandItem().getItem() instanceof ShieldItem;
        boolean usingOffhandShield = player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.OFF_HAND
                && player.getUseItem().getItem() instanceof ShieldItem;

        if (mainHandSpear && SpearCombat.autoRaisesOffhandShield(sneaking, offhandShield)) {
            // BEDROCK：潜行即举盾；若正在蓄力则先收矛，别和盾牌抢 using 状态。
            if (!usingOffhandShield) {
                if (player.isUsingItem()) {
                    if (player.getUsedItemHand() != InteractionHand.MAIN_HAND
                            || SpearLikeSupport.of(player.getUseItem()) == null) {
                        return; // 正在吃/用别的东西，不抢
                    }
                    player.stopUsingItem();
                }
                player.startUsingItem(InteractionHand.OFF_HAND);
            }
            return;
        }

        // BEDROCK / MODDED：站起来就收盾，手回到矛上。
        if (SpearCombat.shouldReleaseOffhandShield(sneaking, usingOffhandShield, mainHandSpear)) {
            player.stopUsingItem();
        }
    }
}
