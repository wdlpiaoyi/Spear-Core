package net.minecraft.spearcore.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tetra 长矛的自绘模型。
 *
 * <p>原版 Tetra 单头工具是「模块拼接」的短模型，第三人称看起来过短；这里用本模组自己的
 * {@code spearcore:tetra_spear} 模型替换 spear-like 栈的渲染，杆/矛头分别走 tint 0/1，从而保留
 * Tetra 的按材料换色。</p>
 *
 * <p>模型只在客户端烘焙，替换发生在 {@code ItemRendererTetraModelMixin}。</p>
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TetraSpearModel {

    public static final ModelResourceLocation LOCATION =
            new ModelResourceLocation(new ResourceLocation("spearcore", "tetra_spear"), "inventory");

    private static BakedModel model;

    private TetraSpearModel() {
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(LOCATION);
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        model = event.getModels().get(LOCATION);
    }

    /** 烘焙完成前为 null，调用方需回退到原模型。 */
    public static BakedModel get() {
        return model;
    }
}
