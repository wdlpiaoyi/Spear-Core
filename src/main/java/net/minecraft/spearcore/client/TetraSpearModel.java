package net.minecraft.spearcore.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 模块化长矛的贴图模型。
 *
 * <p>Tetra 自己的动态物品模型是「模块拼接」的短模型，看起来不像长矛；这里用本模组 assets 里的
 * {@code spearcore:tetra_spear}（复用贴图长矛的 4 张贴图：杆/矛头各一套物品栏与手持）替换它的渲染，
 * 杆/矛头分别走 tint 0/1，从而保留 Tetra 的按材料换色。</p>
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
