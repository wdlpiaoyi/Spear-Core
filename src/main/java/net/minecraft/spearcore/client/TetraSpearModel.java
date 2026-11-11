package net.minecraft.spearcore.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
 * <p>第三方矛尖（RevelationFix 的 longinus / aeglos）不复制素材：那两条改进在原版剑上就是往剑刃
 * 贴图上再叠一张 16x16 的枪尖图标，这里在运行时按资源位置（{@code tetra:item/module/sword/blade/*}）
 * 从方块图集取贴图、自己生成叠加四边形（见 {@link TipOverlayGeometry}），外层包装交给
 * {@link TipOverlayModel}。装不装那个模组都不会影响本模组自己的贴图。</p>
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
    private static BakedModel model;
    private static BakedModel shuoModel;
    private static BakedModel threeModel;

    /**
     * RevelationFix 给剑刃叠加的枪尖图标。**只引用资源位置，不复制素材**：
     * 贴图由对方的 jar 提供（GoetyRevelation 的 jarjar），本模组在运行时从方块图集里取。
     */
    private static final ResourceLocation LONGINUS_TIP =
            new ResourceLocation("tetra", "item/module/sword/blade/longinus");
    private static final ResourceLocation AEGLOS_TIP =
            new ResourceLocation("tetra", "item/module/sword/blade/aeglos");

    private static final TipLayer LONGINUS = new TipLayer(LONGINUS_TIP);
    private static final TipLayer AEGLOS = new TipLayer(AEGLOS_TIP);

    private TetraSpearModel() {
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(LOCATION);
        event.register(LOCATION_SHUO);
        event.register(LOCATION_THREE);
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        model = flattenGui3d(event.getModels().get(LOCATION));
        shuoModel = flattenGui3d(event.getModels().get(LOCATION_SHUO));
        threeModel = flattenGui3d(event.getModels().get(LOCATION_THREE));
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

    /** 朗基努斯枪尖叠加层（手持 / 图标），没有装提供贴图的模组时是空列表。 */
    public static List<BakedQuad> getLonginusHeldTip() {
        return LONGINUS.held();
    }

    public static List<BakedQuad> getLonginusIconTip() {
        return LONGINUS.icon();
    }

    /** 艾格洛斯枪尖叠加层（8 帧动画由图集自己播，四边形 uv 不用动）。 */
    public static List<BakedQuad> getAeglosHeldTip() {
        return AEGLOS.held();
    }

    public static List<BakedQuad> getAeglosIconTip() {
        return AEGLOS.icon();
    }

    /**
     * 一张第三方枪尖贴图的叠加层。
     *
     * <p>只能在渲染时惰性生成：{@code ModelEvent.BakingCompleted} 跑在 {@code ModelManager#loadModels}
     * 里，而图集的 upload（{@code TextureAtlas#upload}）要等 {@code ModelManager#apply} 才在渲染线程执行，
     * 烘焙完成时从 {@code Minecraft#getTextureAtlas} 拿到的是上一轮（首次加载时是空）的 sprite。
     * 资源重载后 sprite 实例会换，所以用它当缓存 key：没换就不重建四边形。</p>
     */
    private static final class TipLayer {

        private final ResourceLocation texture;
        private TextureAtlasSprite sprite;
        private List<BakedQuad> held = List.of();
        private List<BakedQuad> icon = List.of();

        private TipLayer(ResourceLocation texture) {
            this.texture = texture;
        }

        private List<BakedQuad> held() {
            refresh();
            return held;
        }

        private List<BakedQuad> icon() {
            refresh();
            return icon;
        }

        private void refresh() {
            TextureAtlasSprite current = Minecraft.getInstance()
                    .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            if (current == sprite) {
                return;
            }
            sprite = current;
            if (current == null || !texture.equals(current.contents().name())) {
                // 没装提供这张贴图的模组（或贴图被资源包移除）：不挂叠加层。
                held = List.of();
                icon = List.of();
                return;
            }
            held = buildTipQuads(current, true);
            icon = buildTipQuads(current, false);
        }
    }

    /**
     * 按原 mod 的贴图位置现做一层枪尖四边形。
     *
     * <p>方块图集会用 {@code directory:item} 把**所有命名空间** {@code textures/item/**} 下的贴图都拼进去
     * （原版 {@code minecraft:atlases/blocks.json} 的定义），所以只要对方模组在，这两张贴图就已经在图集里，
     * 这里直接取。</p>
     */
    private static List<BakedQuad> buildTipQuads(TextureAtlasSprite sprite, boolean held) {
        float minX = held ? TipOverlayGeometry.HELD_MIN_X : TipOverlayGeometry.ICON_MIN_X;
        float maxX = held ? TipOverlayGeometry.HELD_MAX_X : TipOverlayGeometry.ICON_MAX_X;
        float minY = held ? TipOverlayGeometry.HELD_MIN_Y : TipOverlayGeometry.ICON_MIN_Y;
        float maxY = held ? TipOverlayGeometry.HELD_MAX_Y : TipOverlayGeometry.ICON_MAX_Y;
        return List.of(
                buildTipQuad(sprite, Direction.SOUTH, minX, maxX, minY, maxY, held),
                buildTipQuad(sprite, Direction.NORTH, minX, maxX, minY, maxY, held));
    }

    /**
     * 造一个面。顶点顺序和 uv 分配都照 {@code FaceBakery} 的做法（用 {@link FaceInfo} 取面内顶点顺序），
     * 这样绕序/法线不用自己推；z 比原版图层的 7.5/8.5 再让 0.0005，保证叠在原贴图上而不是打架。
     */
    private static BakedQuad buildTipQuad(TextureAtlasSprite sprite, Direction direction, float minX, float maxX,
                                          float minY, float maxY, boolean held) {
        boolean front = direction == Direction.SOUTH;
        float z = (front ? 8.5F : 7.5F) / 16.0F + (front ? 0.0005F : -0.0005F);
        float[] bounds = new float[6];
        bounds[FaceInfo.Constants.MIN_X] = minX;
        bounds[FaceInfo.Constants.MAX_X] = maxX;
        bounds[FaceInfo.Constants.MIN_Y] = minY;
        bounds[FaceInfo.Constants.MAX_Y] = maxY;
        bounds[FaceInfo.Constants.MIN_Z] = z;
        bounds[FaceInfo.Constants.MAX_Z] = z;

        float uCenter = (uAt(held, minX, minY, front) + uAt(held, maxX, maxY, front)) / 2.0F;
        float vCenter = (vAt(held, minX, minY, front) + vAt(held, maxX, maxY, front)) / 2.0F;
        float shrink = sprite.uvShrinkRatio();

        FaceInfo info = FaceInfo.fromFacing(direction);
        int[] vertices = new int[32];
        for (int i = 0; i < 4; i++) {
            FaceInfo.VertexInfo vertex = info.getVertexInfo(i);
            float x = bounds[vertex.xFace];
            float y = bounds[vertex.yFace];
            float vz = bounds[vertex.zFace];
            float u = Mth.lerp(shrink, uAt(held, x, y, front), uCenter);
            float v = Mth.lerp(shrink, vAt(held, x, y, front), vCenter);
            int offset = i * 8;
            vertices[offset] = Float.floatToRawIntBits(x);
            vertices[offset + 1] = Float.floatToRawIntBits(y);
            vertices[offset + 2] = Float.floatToRawIntBits(vz);
            vertices[offset + 3] = -1;
            vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(TipOverlayGeometry.texel(u)));
            vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(TipOverlayGeometry.texel(v)));
        }
        return new BakedQuad(vertices, -1, direction, sprite, true, false);
    }

    private static float uAt(boolean held, float x, float y, boolean front) {
        return held ? TipOverlayGeometry.heldU(y) : TipOverlayGeometry.iconU(x, front);
    }

    private static float vAt(boolean held, float x, float y, boolean front) {
        return held ? TipOverlayGeometry.heldV(x, front) : TipOverlayGeometry.iconV(y);
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
