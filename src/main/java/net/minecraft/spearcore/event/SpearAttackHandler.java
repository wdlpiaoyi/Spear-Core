package net.minecraft.spearcore.event;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearCombat;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
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

        event.setCanceled(true);
        if (!SpearCombat.canPlayerPerformStab(player, stack, spear)) return;

        SpearCombat.performStabAttack(player, stack, EquipmentSlot.MAINHAND, true);
        player.resetAttackStrengthTicker();
    }
}
