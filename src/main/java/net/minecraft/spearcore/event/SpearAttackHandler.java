package net.minecraft.spearcore.event;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public class SpearAttackHandler {
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null) return;

        // 「虚拟原版攻击」：蓄力命中后补的一次 Player#attack，只给其它 mod 看，不重复结算本模组伤害。
        if (SpearCombat.inVirtualAttack()) {
            event.setCanceled(true);
            return;
        }

        // 槊头 / 三尖两刃刀：左键就是普通剑的左键，完全不碰原版攻击。
        // 不加自定义横扫——横扫若按攻击力全额结算，会绕开原版的蓄力/冷却缩放，
        // 0 蓄力也能打出很高伤害（3.4.0 实机踩过）。
        if (spear.slashing()) {
            return;
        }

        event.setCanceled(true);
        if (!SpearCombat.canPlayerPerformStab(player, stack, spear)) return;

        SpearCombat.performStabAttack(player, stack, EquipmentSlot.MAINHAND, true);
        player.resetAttackStrengthTicker();
    }
}
