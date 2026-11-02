package net.minecraft.spearcore.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class SpearDamageTypes {
    public static final ResourceKey<DamageType> SPEAR = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation(SpearcoreMod.MODID, "spear")
    );

    /** 死亡消息前缀；与数据包里的 message_id 保持一致。 */
    private static final String DEATH_PREFIX = "death.attack.spear";

    private SpearDamageTypes() {
    }

    /**
     * 玩家持矛时使用原版 {@code player_attack} 伤害类型。
     *
     * <p>很多模组（例如 Enigmatic Legacy 的 Slayer）只把 {@code player_attack} 当作
     * 玩家攻击处理，自定义伤害类型会让它们的伤害钩子整段跳过。这里改用原版类型，
     * 再用 {@link PlayerSpearDamageSource} 把死亡消息换回长矛的文案。</p>
     *
     * <p>怪物持矛时仍用 {@code spearcore:spear}，避免误触发只该给玩家的逻辑。</p>
     */
    public static DamageSource spear(Level level, Entity attacker) {
        if (attacker instanceof Player) {
            Holder<DamageType> playerAttack = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.PLAYER_ATTACK);
            return new PlayerSpearDamageSource(playerAttack, attacker);
        }
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SPEAR), attacker);
    }

    /**
     * 类型是 {@code minecraft:player_attack}（使模组按原版玩家攻击处理），
     * 死亡消息仍是本模组的「被矛刺穿」。
     */
    private static final class PlayerSpearDamageSource extends DamageSource {
        private PlayerSpearDamageSource(Holder<DamageType> type, Entity attacker) {
            super(type, attacker);
        }

        @Override
        public Component getLocalizedDeathMessage(LivingEntity victim) {
            Entity attacker = this.getEntity();
            if (attacker == null) {
                return Component.translatable(DEATH_PREFIX, victim.getDisplayName());
            }
            if (attacker instanceof LivingEntity living) {
                ItemStack weapon = living.getMainHandItem();
                if (!weapon.isEmpty()) {
                    return Component.translatable(DEATH_PREFIX + ".item", victim.getDisplayName(),
                            attacker.getDisplayName(), weapon.getDisplayName());
                }
            }
            return Component.translatable(DEATH_PREFIX + ".player", victim.getDisplayName(),
                    attacker.getDisplayName());
        }
    }
}
