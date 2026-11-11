package net.minecraft.spearcore.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 模块化长矛的贴图模型。
 *
 * <p>Tetra 自己的动态物品模型是「模块拼接」的短模型，看起来不像长矛；这里用本模组 assets 里的
 * {@code spearcore:tetra_spear}（复用贴图长矛的贴图：杆/矛头各一套物品栏与手持）替换它的渲染，
 * 杆/矛头分别走 tint 0/1，从而保留 Tetra 的按材料换色。</p>
 *
 * <p>矛头有三种外观：默认矛头（{@code tetra_spear}）、槊头（{@code tetra_spear_shuo}）、
 * 三尖两刃刀（{@code tetra_spear_three}）。三者共用同一张杆贴图，只是头部那一层不同——
 * 头部贴图本来就该盖住杆（槊头/三尖两刃刀比普通矛头长），所以**再加新矛头时只需要复制
 * 一份 {@code tetra_spear_head*} 改画，不需要为每种矛头单独画杆**。杆部件（积竹木柲）不提供
 * 自己的外观，只影响 tint。</p>
 *
 * <p>模型只在客户端烘焙，替换发生在 {@code ItemRendererTetraModelMixin}（按矛头槽的变体 key 选模型）。</p>
 *
 * <p>烘焙结果外面包一层 {@link BakedModelWrapper}，把 {@code isGui3d()} 固定成 {@code false}：
 * {@code forge:separate_transforms} 的几何上下文默认 {@code gui3d = true}（Forge 的
 * {@code BlockGeometryBakingContext} 里字段初值就是 {@code true}，JSON 里也没有能改它的键），而 Tetra 的
 * {@code WorkbenchTESR} 正是用 {@code BakedModel#isGui3d()} 决定工作台上怎么摆物品：</p>
 *
 * <ul>
 *   <li>{@code true}：只平移 {@code (0.5, 1.125, 0.5)} 再缩放 0.5，不旋转 —— 平面贴图会竖直地「立」在工作台上；</li>
 *   <li>{@code false}：平移 {@code (0.5, 1.0125, 0.5)} 后绕 X 轴转 90° 再缩放 0.5 —— 平面贴图正好平躺。</li>
 * </ul>
 *
 * <p>我们的模型整套都是平面 {@code item/generated} 贴图，本来就该报 {@code isGui3d = false}
 * （贴图长矛的 {@code item/generated} 也是这个值），所以这里不是特例 hack，而是把烘焙器给的错误默认值纠正掉。</p>
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TetraSpearModel {

    public static final ModelResourceLocation LOCATION =
            new ModelResourceLocation(new ResourceLocation("spearcore", "tetra_spear"), "inventory");
    public static final ModelResourceLocation LOCATION_SHUO =
            new ModelResourceLocation(new ResourceLocation("spearcore", "tetra_spear_shuo"), "inventory");
    public static final ModelResourceLocation LOCATION_THREE =
            new ModelResourceLocation(new ResourceLocation("spearcore", "tetra_spear_three"), "inventory");
    public static final ModelResourceLocation LOCATION_LONGINUS =
            new ModelResourceLocation(new ResourceLocation("spearcore", "tetra_spear_longinus"), "inventory");
    public static final ModelResourceLocation LOCATION_AEGLOS =
            new ModelResourceLocation(new ResourceLocation("spearcore", "tetra_spear_aeglos"), "inventory");

    private static BakedModel model;
    private static BakedModel shuoModel;
    private static BakedModel threeModel;
    private static BakedModel longinusModel;
    private static BakedModel aeglosModel;

    private TetraSpearModel() {
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(LOCATION);
        event.register(LOCATION_SHUO);
        event.register(LOCATION_THREE);
        event.register(LOCATION_LONGINUS);
        event.register(LOCATION_AEGLOS);
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        model = flattenGui3d(event.getModels().get(LOCATION));
        shuoModel = flattenGui3d(event.getModels().get(LOCATION_SHUO));
        threeModel = flattenGui3d(event.getModels().get(LOCATION_THREE));
        longinusModel = flattenGui3d(event.getModels().get(LOCATION_LONGINUS));
        aeglosModel = flattenGui3d(event.getModels().get(LOCATION_AEGLOS));
    }

    /** 默认矛头（以及烘焙完成前的回退，调用方需处理 null）。 */
    public static BakedModel getDefault() {
        return model;
    }

    /** 槊头外观。 */
    public static BakedModel getShuo() {
        return shuoModel;
    }

    /** 三尖两刃刀外观。 */
    public static BakedModel getThree() {
        return threeModel;
    }

    /** RevelationFix 朗基努斯之枪的矛尖外观。 */
    public static BakedModel getLonginus() {
        return longinusModel;
    }

    /** RevelationFix 艾格洛斯之枪的矛尖外观。 */
    public static BakedModel getAeglos() {
        return aeglosModel;
    }

    /** 平面贴图模型必须报 {@code isGui3d = false}，否则 Tetra 工作台会把矛竖起来放。 */
    private static BakedModel flattenGui3d(BakedModel baked) {
        if (baked == null) {
            return null;
        }
        return new BakedModelWrapper<BakedModel>(baked) {
            @Override
            public boolean isGui3d() {
                return false;
            }
        };
    }
}
