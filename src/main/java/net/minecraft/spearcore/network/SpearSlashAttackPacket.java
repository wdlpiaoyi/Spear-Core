package net.minecraft.spearcore.network;

import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端左键挥砍 -&gt; 服务端。
 *
 * <p>为什么需要这个包：原版客户端只会在准星 {@code ENTITY} 时把攻击交给原版；矛的射程（约 4.5+）
 * 大于原版 3.0 的拾取距离，瞄实体时经常得到 MISS/方块结果。以前这种点击会发
 * {@link SpearStabAttackPacket} 走戳刺，导致槊头/三尖两刃刀也变成 jab。现在挥砍矛改发这个包，
 * 由服务端按矛的射程射线找最近目标，再走一次真正的 {@code Player#attack}（保留附魔/其它 mod 钩子）。</p>
 */
public class SpearSlashAttackPacket {

    public SpearSlashAttackPacket() {
    }

    public static void register() {
        SpearStabAttackPacket.CHANNEL.registerMessage(1, SpearSlashAttackPacket.class,
                SpearSlashAttackPacket::encode,
                SpearSlashAttackPacket::decode,
                SpearSlashAttackPacket::handle);
    }

    public static void encode(SpearSlashAttackPacket packet, FriendlyByteBuf buffer) {
        // 无载荷
    }

    public static SpearSlashAttackPacket decode(FriendlyByteBuf buffer) {
        return new SpearSlashAttackPacket();
    }

    public static void handle(SpearSlashAttackPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            if (!player.level().getChunkSource().hasChunk(
                    SectionPos.blockToSectionCoord(player.getX()),
                    SectionPos.blockToSectionCoord(player.getZ()))) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            SpearLike spear = SpearLikeSupport.of(stack);
            if (spear == null || !spear.slashing()) {
                return;
            }
            if (!SpearCombat.canPlayerPerformStab(player, stack, spear)) {
                return;
            }
            LivingEntity target = SpearCombat.findSlashTarget(player, spear);
            if (target == null) {
                // 挥空：客户端已经补了挥动和音效，这里不需要再结算。
                return;
            }
            // 不用 virtualAttack：那条路径会被 SpearAttackHandler 取消，只给其它 mod 看、不结算伤害。
            // 这里要的是「真正的原版攻击」：服务端 AttackEntityEvent -> slashing 分支加横扫，
            // 原版主目标伤害、附魔与其它 mod 钩子全部保留。
            player.attack(target);
            player.resetAttackStrengthTicker();
        });
        context.setPacketHandled(true);
    }
}
