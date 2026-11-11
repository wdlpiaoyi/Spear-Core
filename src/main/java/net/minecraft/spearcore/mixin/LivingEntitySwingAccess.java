package net.minecraft.spearcore.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 读 {@code LivingEntity#getCurrentSwingDuration()}（私有方法），用于给槊/刃加速挥手。 */
@Mixin(LivingEntity.class)
public interface LivingEntitySwingAccess {

    @Invoker("getCurrentSwingDuration")
    int spearcore$swingDuration();
}
