package net.minecraft.spearcore.event;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;

/**
 * 长矛命中目标后触发。
 *
 * <p>{@link #getDamageDealt()} 是这次命中<b>实际结算</b>的伤害：已经包含附魔伤害修正
 * （锋利/亡灵杀手等），也包含 {@link SpearDamageEvent} 的倍率与属性倍率；
 * 但仍在护甲/吸收减免<b>之前</b>，所以它可能与目标掉的血量略有出入。</p>
 *
 * <p>其他模组可用于：吸血、点燃、粒子、计数、成就等。</p>
 */
public class SpearHitEvent extends Event {
    private final LivingEntity attacker;
    private final Entity target;
    private final float damageDealt;
    private final SpearDamageEvent.AttackType attackType;

    public SpearHitEvent(LivingEntity attacker, Entity target, float damageDealt,
                         SpearDamageEvent.AttackType attackType) {
        this.attacker = attacker;
        this.target = target;
        this.damageDealt = damageDealt;
        this.attackType = attackType;
    }

    public LivingEntity getAttacker() { return attacker; }
    public Entity getTarget() { return target; }
    public float getDamageDealt() { return damageDealt; }
    public SpearDamageEvent.AttackType getAttackType() { return attackType; }
}
