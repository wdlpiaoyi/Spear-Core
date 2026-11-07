package net.minecraft.spearcore.mixin;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.spearcore.client.animation.SpearAnimations;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.util.SpearCooldownAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySpearMixin extends Entity implements SpearCooldownAccessor {

    @Unique
    @Nullable
    private Object2LongMap<Entity> RecentStabbedEntities;

    public LivingEntitySpearMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    /**
     * 懒初始化「最近戳过谁」的表。
     *
     * <p>以前只在 {@code startUsingItem}（起手蓄力）时建表，于是<b>第一次戳刺</b>时表还是 null，
     * 「记住这一下打到了谁」被静默丢弃：同一 tick 的重复命中判定、以及先戳后立刻蓄力时的
     * 去重都会失准。改成任何一次查询/记录都按需建表。</p>
     */
    @Unique
    private Object2LongMap<Entity> spearcore$recentStabbed() {
        if (this.RecentStabbedEntities == null) {
            this.RecentStabbedEntities = new Object2LongOpenHashMap<>();
        }
        return this.RecentStabbedEntities;
    }

    @Inject(method = "startUsingItem", at = @At("TAIL"))
    private void onStartUsingItem(InteractionHand hand, CallbackInfo ci) {
        if (!this.level().isClientSide) {
            LivingEntity self = (LivingEntity) (Object) this;
            ItemStack stack = self.getItemInHand(hand);
            // JAVA 的「每次蓄力对同一目标只结算一次」靠的就是每次起手清空这张表；
            // 表本身懒建，这里只清不建。
            if (SpearLikeSupport.of(stack) != null && this.RecentStabbedEntities != null) {
                this.RecentStabbedEntities.clear();
            }
        }
    }

    @Override
    @Unique
    public boolean WasRecentlyStabbed(Entity target, int cooldownTicks) {
        Object2LongMap<Entity> stabbed = this.RecentStabbedEntities;
        if (stabbed == null) return false;
        // 必须显式判存在：fastutil 的 getLong 对不存在的键返回 0，
        // 于是世界刚开的头 cooldownTicks 刻里，任何"从未被戳过"的目标都会被算成
        // "在 tick 0 被戳过"而被静默跳过（GameTest 在全新世界跑，正好抓到）。
        if (!stabbed.containsKey(target)) return false;
        return this.level().getGameTime() - stabbed.getLong(target) < (long) cooldownTicks;
    }

    @Override
    @Unique
    public void RememberStabbedEntity(Entity target) {
        Object2LongMap<Entity> stabbed = this.spearcore$recentStabbed();
        long now = this.level().getGameTime();
        stabbed.put(target, now);
        // 真正用得到的只是最近几秒（最长也就是 contactCooldownTicks 的量级）。
        // 表在内存里且强引用实体，长期存档里会一直攒着死掉的实体，所以过阈值时清旧的。
        if (stabbed.size() > 64) {
            long threshold = now - 200L;
            stabbed.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
        }
    }
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void onGetCurrentSwingDuration(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        ItemStack stack = self.getMainHandItem();
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear != null) {
            int n = (int) (spear.getSwingTimes() * 20.0F);
            if (net.minecraft.world.effect.MobEffectUtil.hasDigSpeed(self)) {
                cir.setReturnValue(n - (1 + net.minecraft.world.effect.MobEffectUtil.getDigSpeedAmplification(self)));
            } else if (self.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN)) {
                cir.setReturnValue(n + (1 + self.getEffect(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2);
            } else {
                cir.setReturnValue(n);
            }
            cir.cancel();
        }
    }
}