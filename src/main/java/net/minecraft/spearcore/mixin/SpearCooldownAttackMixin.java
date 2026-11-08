package net.minecraft.spearcore.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.init.SpearSounds;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.spearcore.network.SpearStabAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class SpearCooldownAttackMixin {

    @Shadow
    public HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void spearcore$startSpearAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft self = (Minecraft)(Object)this;
        LocalPlayer player = self.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null) return;

        // 挥砍矛（槊 / 三尖两刃刀）：左键完全交给原版 startAttack（包括蓄力、挥动、冷却），
        // 不拦、不取消、不发自定义包；服务端 AttackEntityEvent 的 slashing 分支只补横扫。
        if (spear.slashing()) {
            return;
        }

        boolean ready = spear.usesJabCooldown()
                ? !player.getCooldowns().isOnCooldown(stack.getItem())
                : player.getAttackStrengthScale(0.0F) >= 0.95F;

        if (!ready) {
            // 原版 Java：攻击蓄力不满时点左键不出手。
            // 基岩版走物品使用冷却，交给服务端 canPlayerPerformStab 判定，这里不拦。
            if (!spear.usesJabCooldown()) {
                cir.setReturnValue(false);
                cir.cancel();
            }
            return;
        }

        // 普通矛头：准星锁到实体时由服务端 AttackEntityEvent 接手，这里只补「MISS / 命中方块」两种情况。
        if (this.hitResult == null || this.hitResult.getType() != HitResult.Type.ENTITY) {
            // 3D 模型（Tetra）在创造模式下 canAttackBlock 返回 false，原版 startAttack 直接空转，
            // 既没有挥动也没有音效；这里补一次客户端挥动与挥空音。
            if (spear.style() == SpearStyle.MODULAR_3D) {
                player.swing(InteractionHand.MAIN_HAND);
                player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                        SpearSounds.ITEM_SPEAR_ATTACK.get(), SoundSource.PLAYERS, 0.9F,
                        1.0F + player.getRandom().nextFloat() * 0.1F, false);
            }
            SpearStabAttackPacket.CHANNEL.sendToServer(new SpearStabAttackPacket());
            SpearItem.jerotesLungeForwardMaybe(player);
        }
    }
}
