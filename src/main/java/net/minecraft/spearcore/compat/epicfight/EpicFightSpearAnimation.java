package net.minecraft.spearcore.compat.epicfight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.event.SpearChargePhaseEvent;
import net.minecraft.spearcore.item.SpearItem;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * 实验性：让矛的蓄力/冲刺/戳出复用 Epic Fight 的长矛动作。
 *
 * <p>触发点是本模组自己的 {@link SpearChargePhaseEvent}（只在阶段切换时发一次），
 * 服务端调 {@code playAnimationSynchronized} 由 EF 自己同步给客户端。</p>
 *
 * <p><b>阶段 → 动作的映射是实验性的</b>（用户明说"先试试"），所以集中放在下面的常量里，
 * 调手感只需要改这里。EF 的矛动作全集：spear_dash（冲刺突刺）、spear_twohand_auto1/2（双手连击）、
 * spear_onehand_auto、spear_twohand_airslash、spear_mount_attack。
 * 举矛时间的语义来自本方阶段链：DELAY（举矛蓄势）→ DISMOUNT（冲刺起手）→ KNOCKBACK（冲刺中段）
 * → DAMAGE（戳出）。收矛在蓄力结束时触发。</p>
 */
public final class EpicFightSpearAnimation {

    /** 举矛蓄势（还没前冲）。 */
    private static final ResourceLocation HOLD = ResourceLocation.parse("epicfight:spear_twohand_auto2");
    /** 冲刺突刺。 */
    private static final ResourceLocation DASH = ResourceLocation.parse("epicfight:spear_dash");
    /** 戳出。 */
    private static final ResourceLocation THRUST = ResourceLocation.parse("epicfight:spear_twohand_auto1");
    /** 收矛（举矛冲刺结束）。 */
    private static final ResourceLocation RECOVER = ResourceLocation.parse("epicfight:spear_twohand_auto2");

    private EpicFightSpearAnimation() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EpicFightSpearAnimation::onPhaseChanged);
        // 举矛冲刺结束（松手 / 超出最大持续时间）→ 收矛
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Stop event) -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (!(event.getItem().getItem() instanceof SpearItem)) {
                return;
            }
            onChargeStopped(player);
        });
    }

    private static void onPhaseChanged(SpearChargePhaseEvent event) {
        if (!(event.getUser() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation anim = switch (event.getNewPhase()) {
            case DELAY -> HOLD;
            case DISMOUNT, KNOCKBACK -> DASH;
            case DAMAGE -> THRUST;
        };
        play(player, anim);
    }

    /** 举矛冲刺结束（松手/超时）后收矛。 */
    public static void onChargeStopped(ServerPlayer player) {
        play(player, RECOVER);
    }

    private static void play(ServerPlayer player, ResourceLocation id) {
        ServerPlayerPatch patch = EpicFightCapabilities.getServerPlayerPatch(player);
        if (patch == null) {
            return;
        }
        if (!(player.getMainHandItem().getItem() instanceof SpearItem)) {
            return;
        }
        try {
            AssetAccessor<? extends StaticAnimation> accessor =
                    AnimationManager.<StaticAnimation>byKey(id);
            if (AnimationManager.checkNull(accessor)) {
                return;
            }
            patch.playAnimationSynchronized(accessor, 0.0F);
            SpearcoreMod.LOGGER.debug("[spearcore] EF 动作切换 -> {}", id);
        } catch (Throwable t) {
            SpearcoreMod.LOGGER.warn("[spearcore] 播放 EF 动作 {} 失败：{}", id, t.toString());
        }
    }
}