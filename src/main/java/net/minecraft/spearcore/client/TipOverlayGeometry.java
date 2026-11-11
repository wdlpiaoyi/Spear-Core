package net.minecraft.spearcore.client;

/**
 * RevelationFix 的 longinus / aeglos 枪尖叠加层的几何（纯函数 + 常量，可在 GameTest 里验证）。
 *
 * <p>叠加层不再手搓一张纸片：把原素材当成普通的 item 图层，交给 {@code ItemModelGenerator}
 * 生成「正面 + 背面 + 按透明度轮廓挤出 1 模型单位厚度」的完整一层（和本模组杆/矛头图层同一套厚度），
 * 再按这里的变换摆到矛头位置。这样枪尖有原版图层的厚度，侧面看也不会变成纸片。</p>
 *
 * <p>手持层：把生成的 16×16 图层绕**内容 bbox 中心旋转 90°**、缩到 0.5 后放到矛头区域
 * （内容 bbox 正好变成 6×6 模型单位，屏幕方向对齐实机确认过的 {@code comp_90}）；
 * 图标层不旋转、原样铺满 16×16。</p>
 */
public final class TipOverlayGeometry {

    /** 手持素材内容 bbox（模型空间）：贴图 16×16 里的 12×12 箭头，y 轴向上。 */
    public static final float HELD_SOURCE_MIN_X = 4.0F / 16.0F;
    public static final float HELD_SOURCE_MAX_X = 16.0F / 16.0F;
    public static final float HELD_SOURCE_MIN_Y = 1.0F - 12.0F / 16.0F;
    public static final float HELD_SOURCE_MAX_Y = 1.0F;
    /** 内容 bbox 中心（旋转轴），模型空间。 */
    public static final float HELD_SOURCE_CENTER_X = (HELD_SOURCE_MIN_X + HELD_SOURCE_MAX_X) / 2.0F;
    public static final float HELD_SOURCE_CENTER_Y = (HELD_SOURCE_MIN_Y + HELD_SOURCE_MAX_Y) / 2.0F;

    /** 矛头落点：32×32 手持贴图左上 (0,0)-(12,12) 像素 → 模型空间 6×6 单位。 */
    public static final float HELD_TARGET_MIN_X = 0.0F;
    public static final float HELD_TARGET_MAX_X = 6.0F / 16.0F;
    public static final float HELD_TARGET_MIN_Y = 10.0F / 16.0F;
    public static final float HELD_TARGET_MAX_Y = 1.0F;
    public static final float HELD_TARGET_CENTER_X = (HELD_TARGET_MIN_X + HELD_TARGET_MAX_X) / 2.0F;
    public static final float HELD_TARGET_CENTER_Y = (HELD_TARGET_MIN_Y + HELD_TARGET_MAX_Y) / 2.0F;

    /** 手持层缩放：12 像素内容 → 6 模型单位。 */
    public static final float HELD_SCALE = 0.5F;

    /** 前后让位：图层厚度 1 模型单位，正面再往前、背面再往后让一点，避免和原版图层打架。 */
    public static final float DEPTH_OFFSET = 0.0005F;

    /**
     * 挂上枪尖后要藏掉的原矛头图层。
     *
     * <p>本模组的矛模型是 {@code layer0 = tetra_spear_handle*}、{@code layer1 = tetra_spear_head*}，
     * 而 {@code ItemModelGenerator} 把图层序号直接当 tint index，所以「矛头」就是 tint 1。</p>
     */
    public static final int REPLACED_TINT_INDEX = 1;

    private TipOverlayGeometry() {
    }

    /** 这个 tint index 的四边形是否被枪尖替换（而不是叠在上面）。 */
    public static boolean replacesLayer(int tintIndex) {
        return tintIndex == REPLACED_TINT_INDEX;
    }

    /**
     * 把 0..1 的贴图比例换算成 {@code TextureAtlasSprite#getU/getV} 要的 0..16 texel 坐标。
     *
     * <p>坑：{@code TextureAtlasSprite#getU(double)} 内部会再除以 16，所以传 0..1 只会采样到
     * 贴图左上角不到 1/16 的一条边——枪尖内容在透明外边之外，整层叠加会「看不见」（实机踩过：
     * 物品栏和手上只剩一根棍子）。所有写给 sprite 的 uv 都必须先过这里。</p>
     */
    public static float texel(float normalized) {
        return normalized * 16.0F;
    }

    /**
     * 手持层 X：把生成的图层绕内容中心旋转到目标朝向（屏幕上是逆时针 90°，和实机确认过的
     * {@code comp_90} 一致），再缩放 {@link #HELD_SCALE} 并平移到矛头区域中心。
     */
    public static float heldX(float x, float y) {
        return HELD_TARGET_CENTER_X - (y - HELD_SOURCE_CENTER_Y) * HELD_SCALE;
    }

    /** 手持层 Y：与 {@link #heldX} 同一套旋转。 */
    public static float heldY(float x, float y) {
        return HELD_TARGET_CENTER_Y + (x - HELD_SOURCE_CENTER_X) * HELD_SCALE;
    }

    /** 前后错开：正面（z &gt; 0.5）再往前一点，背面再往后一点，保证叠在原图层外面。 */
    public static float depth(float z) {
        return z + (z > 0.5F ? DEPTH_OFFSET : -DEPTH_OFFSET);
    }
}
