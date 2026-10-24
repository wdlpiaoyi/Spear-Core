package net.minecraft.spearcore.compat.epicfight.client;

import com.google.gson.JsonElement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.client.animation.SpearAnimations;
import net.minecraft.spearcore.init.SpearCoreItems;
import net.minecraft.spearcore.item.SpearItem;
import yesman.epicfight.api.client.event.EpicFightClientEventHooks;
import yesman.epicfight.api.client.event.types.registry.RegisterPatchedRenderersEvent;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.item.RenderItemBase;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Epic Fight 下的矛物品渲染：EF 会把玩家渲染器整个换成 PPlayerRenderer，
 * 原版的 ItemInHandLayer 根本不会执行，所以我们的举矛姿态在 EF 下会失效。
 *
 * <p>这里走 EF 自己的官方扩展点：继承 {@link RenderItemBase}，重写
 * {@link RenderItemBase#renderItemInHand} —— 它会直接给一个 PoseStack，
 * 所以我们可以原样复用 {@link SpearAnimations#thirdPersonUseItem} 那套姿态。</p>
 *
 * <p><b>只在客户端加载</b>：本类引用 EF 的 client 包，不能出现在服务端代码路径上。</p>
 */
public final class EpicFightSpearRenderer {

    private EpicFightSpearRenderer() {
    }

    public static void init() {
        EpicFightClientEventHooks.Registry.PATCHED_ITEM.registerEvent(event -> {
            register(event, SpearCoreItems.WOODEN_SPEAR.get());
            register(event, SpearCoreItems.STONE_SPEAR.get());
            register(event, SpearCoreItems.IRON_SPEAR.get());
            register(event, SpearCoreItems.GOLDEN_SPEAR.get());
            register(event, SpearCoreItems.DIAMOND_SPEAR.get());
            register(event, SpearCoreItems.NETHERITE_SPEAR.get());
            register(event, SpearCoreItems.COPPER_SPEAR.get());
        });
        SpearcoreMod.LOGGER.info("[spearcore] 已向 Epic Fight 注册矛的自定义物品渲染器");
    }

    private static void register(RegisterPatchedRenderersEvent.Item event, Item item) {
        event.addItemRenderer(BuiltInRegistries.ITEM.getKey(item), SpearRenderer::new);
    }

    /** 复用本模组第三人称举矛姿态的 EF 渲染器。 */
    private static final class SpearRenderer extends RenderItemBase {

        SpearRenderer(JsonElement json) {
            super(json);
        }

        @Override
        public void renderItemInHand(ItemStack stack, LivingEntityPatch<?> patch, InteractionHand hand,
                                     OpenMatrix4f[] poses, MultiBufferSource buffers, PoseStack poseStack,
                                     int light, float partialTick) {
            if (stack.getItem() instanceof SpearItem) {
                LivingEntity holder = patch.getOriginal();
                if (holder != null && holder.isUsingItem()) {
                    float timeHeld = stack.getUseDuration(holder) - holder.getUseItemRemainingTicks() + partialTick;
                    HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                            ? holder.getMainArm()
                            : holder.getMainArm().getOpposite();
                    SpearAnimations.thirdPersonUseItem(partialTick, poseStack, timeHeld, arm, stack, holder,
                            partialTick);
                }
            }
            super.renderItemInHand(stack, patch, hand, poses, buffers, poseStack, light, partialTick);
        }
    }
}