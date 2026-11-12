package net.minecraft.spearcore.event;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public class SpearAttackHandler {

    /** 挥砍主目标：等原版攻击真正造成伤害后再补横扫，避免攻击被别的 mod 取消时仍然横扫。 */
    private static final Map<Player, Entity> PENDING_SLASH = new WeakHashMap<>();
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Player player = event.getEntity();
        // 「虚拟原版攻击」：蓄力命中后补的一次 Player#attack，只给其它 mod 看，不重复结算本模组伤害。
        // 必须先于主手判定：副手蓄力时主手可能不是矛，提前 return 会让这次虚拟攻击完整结算一遍。
        if (SpearCombat.inVirtualAttack()) {
            event.setCanceled(true);
            return;
        }
        ItemStack stack = player.getMainHandItem();
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null) return;

        // 槊头 / 三尖两刃刀：左键就是普通剑的左键，完全不碰原版攻击。
        // 不加自定义横扫——横扫若按攻击力全额结算，会绕开原版的蓄力/冷却缩放，
        // 0 蓄力也能打出很高伤害（3.4.0 实机踩过）。
        if (spear.slashing()) {
            // 只有槊头要求左键满蓄力；三尖两刃刀不限制，保持高上限。
            if (spear.requiresFullCharge() && player.getAttackStrengthScale(0.5F) < 0.99F) {
                event.setCanceled(true);
                return;
            }
            // 槊/刃也保留和普通矛一样的近距限制：贴脸时不触发原版攻击。
            if (player.distanceToSqr(event.getTarget()) < Math.pow(spear.effectiveMinRange(player), 2.0D)) {
                event.setCanceled(true);
                return;
            }
            // 满蓄力时补一次原版剑式横扫，剑气以命中实体为中心；等 LivingHurtEvent 确认主目标真的受伤，
            // 这样别的 mod 取消原版攻击时不会留下一次凭空横扫。
            PENDING_SLASH.put(player, event.getTarget());
            return;
        }

        event.setCanceled(true);
        if (!SpearCombat.canPlayerPerformStab(player, stack, spear)) return;

        SpearCombat.performStabAttack(player, stack, EquipmentSlot.MAINHAND, true);
        player.resetAttackStrengthTicker();
    }

    /**
     * 挥砍的横扫延后到主目标真正受伤之后。
     *
     * <p>LOWEST 优先级可以观察到其它 mod 在更高优先级上的取消；如果攻击事件/受伤事件被取消，
     * 这里不会执行，避免“攻击被取消但剑气已经扫出去”。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        Entity pending = PENDING_SLASH.remove(player);
        if (pending == null) return;
        Entity hurtRoot = SpearCombat.rootTarget(event.getEntity());
        if (SpearCombat.rootTarget(pending) != hurtRoot) return;
        SpearLike spear = SpearLikeSupport.of(player.getMainHandItem());
        if (spear == null || !spear.slashing()) return;
        SpearCombat.vanillaSlashSweep(player, event.getEntity(), spear);
    }
}
