package net.minecraft.spearcore.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.spearcore.client.animation.SpearAnimations;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySpearClientMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void spearcore$handleSpearHitFeedback(byte eventId, CallbackInfo ci) {
        if (eventId == 2 && (Object) this == Minecraft.getInstance().player) {
            // 只让本地玩家的命中触发第一人称回弹；附近怪物持矛命中不应重置本地计时。
            SpearAnimations.triggerHitFeedback();
        }
    }
}
