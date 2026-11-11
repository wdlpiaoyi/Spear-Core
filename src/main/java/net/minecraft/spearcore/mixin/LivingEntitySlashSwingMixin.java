package net.minecraft.spearcore.mixin;

import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 槊 / 三尖两刃刀的左键挥手速度 ×2。
 *
 * <p>这两种矛头走原版剑式挥砍，但长矛的模型/贴图本来就和原版物品不是同一套，原版那条
 * 「挥出快、收回低」的曲线在长矛上看起来像慢速原路返回。这里直接把攻击动画时长减半
 * （{@code attackAnim} 用 {@code swingTime / duration} 算，时长减半即动画 2 倍速），
 * 第一/第三人称都生效；普通矛头、Tetra 自带工具与其他武器不受影响。</p>
 *
 * <p>{@code require = 0}：方法名/结构随版本变动时静默退回原速，不让游戏崩。</p>
 */
@Mixin(LivingEntity.class)
public class LivingEntitySlashSwingMixin {

    @Redirect(method = "updateSwingTime",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getCurrentSwingDuration()I"),
            require = 0)
    private int spearcore$fasterSlashSwing(LivingEntity entity) {
        int duration = ((LivingEntitySwingAccess) entity).spearcore$swingDuration();
        // updateSwingTime 每 tick 都跑（没挥动时也会走），而 swingingArm 可以是 null；
        // 直接 getItemInHand(null) 会抛 Invalid hand null 把玩家 tick 崩掉（实机踩过）。
        if (entity instanceof Player player && player.swingingArm != null) {
            SpearLike spear = SpearLikeSupport.of(player.getItemInHand(player.swingingArm));
            if (spear != null && spear.slashing()) {
                return Math.max(2, duration / 2);
            }
        }
        return duration;
    }
}
