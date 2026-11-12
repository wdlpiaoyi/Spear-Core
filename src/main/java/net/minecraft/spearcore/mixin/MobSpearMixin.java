package net.minecraft.spearcore.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.spearcore.event.SpearMobEquipHandler;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 怪物出生时的长矛判定。
 *
 * <p>只在这里做判定并排队，真正装备在 {@link SpearMobEquipHandler#onEntityJoin}；原因见该类注释：
 * 子类 {@code finalizeSpawn} 会在 {@code super} 之后覆盖主手装备。</p>
 */
@Mixin(Mob.class)
public abstract class MobSpearMixin {

    @Inject(method = "finalizeSpawn", at = @At("TAIL"))
    private void spearcore$queueSpear(ServerLevelAccessor level, DifficultyInstance difficulty,
                                      MobSpawnType spawnType, SpawnGroupData spawnData, CompoundTag tag,
                                      CallbackInfoReturnable<SpawnGroupData> cir) {
        SpearMobEquipHandler.onFinalizeSpawn((Mob) (Object) this);
    }
}
