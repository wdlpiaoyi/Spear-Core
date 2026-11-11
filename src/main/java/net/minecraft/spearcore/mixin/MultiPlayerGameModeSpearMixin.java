package net.minecraft.spearcore.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BEDROCK 档「潜行自动举盾」的客户端配合。
 *
 * <p>自动举盾是服务端直接 {@code startUsingItem} 的，客户端并没有按住右键；
 * 原版 {@code Minecraft#handleKeybinds} 一看到 {@code isUsingItem() && !keyUse.isDown()}
 * 就会调用本方法把盾放下，于是服务端每 tick 又举起来——盾牌就举/放闪烁。
 * 这里在自动举盾状态下不把 release 发出去（BEDROCK 的盾本来就由潜行维持，不靠右键松手收）。</p>
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeSpearMixin {

    @Inject(method = "releaseUsingItem", at = @At("HEAD"), cancellable = true)
    private void spearcore$keepAutoRaisedShield(Player player, CallbackInfo ci) {
        if (SpearCombat.shouldKeepAutoRaisedShield(player)) {
            ci.cancel();
        }
    }
}
