package net.minecraft.spearcore.client;

/**
 * RevelationFix 的 longinus / aeglos 枪尖叠加层的几何计算。
 *
 * <p>那两条改进在原版剑上只是「往剑刃贴图上再叠一张枪尖图标」，图标本身是 16x16 的贴图
 * （{@code tetra:item/module/sword/blade/longinus} / {@code aeglos}，aeglos 是 8 帧动画）。
 * 本模组**不复制**这份第三方素材，而是在运行时按资源位置取样、自己生成一层贴图四边形盖在本模组的
 * 长矛模型上；这里只放纯函数与常量，客户端代码负责把它们写成 {@code BakedQuad}，
 * GameTest 则可以在不加载客户端的情况下验证「旋转 + 落点」的映射没被改坏。</p>
 *
 * <p>坐标系：模型空间 0..1（原版物品模型固定 16 单位，模型 JSON 里的 0..16 会除以 16），
 * uv 也是 0..1（按 16x16 的贴图采样，正好等于原图第几格 / 16）。</p>
 *
 * <p>两种显示上下文要对齐两套不同的原画：</p>
 * <ul>
 *   <li>手持贴图是 32x32，矛头画在左上角、指向左上：枪尖图标要**逆时针转 90°**，并按 1:1 像素贴进
 *       左上 (0,0)-(12,12) 的像素区（换算到模型空间就是 6x6 单位，正好盖住 10x10 像素的矛头）；</li>
 *   <li>物品栏图标是 16x16，矛头画在右上、指向右上：枪尖图标**本来就指向右上**，原样整张盖上去即可
 *       （内容区 (4,0)-(16,12) 会落在图标矛头的同一位置）。</li>
 * </ul>
 */
public final class TipOverlayGeometry {

    /** 手持层落点：32x32 贴图左上 12x12 像素 → 模型空间 (0, 10/16)-(6/16, 1)。 */
    public static final float HELD_MIN_X = 0.0F;
    public static final float HELD_MAX_X = 6.0F / 16.0F;
    public static final float HELD_MIN_Y = 10.0F / 16.0F;
    public static final float HELD_MAX_Y = 1.0F;

    /** 图标层落点：整张 16x16 图标，原样覆盖。 */
    public static final float ICON_MIN_X = 0.0F;
    public static final float ICON_MAX_X = 1.0F;
    public static final float ICON_MIN_Y = 0.0F;
    public static final float ICON_MAX_Y = 1.0F;

    /**
     * 挂上枪尖后要藏掉的原矛头图层。
     *
     * <p>本模组的矛模型是 {@code layer0 = tetra_spear_handle*}、{@code layer1 = tetra_spear_head*}，
     * 而 {@code ItemModelGenerator} 把图层序号直接当 tint index，所以「矛头」就是 tint 1。
     * 枪尖图标是斜箭头、四角透明，不藏原矛头会露出灰边（实机反馈过）。</p>
     */
    public static final int REPLACED_TINT_INDEX = 1;

    private TipOverlayGeometry() {
    }

    /** 这个 tint index 的四边形是否被枪尖替换（而不是叠在上面）。 */
    public static boolean replacesLayer(int tintIndex) {
        return tintIndex == REPLACED_TINT_INDEX;
    }

    /**
     * 手持层 u：显示坐标 y 越大 → u 越大（原图右侧）。逆时针转 90° 后原图右上角（u=1,v=0）
     * 落在显示区左上角，所以矛尖（原图右上）指向左上，和本模组手持贴图的矛头方向一致。
     *
     * @param modelY 显示点模型空间的 y（0.625~1 之间）
     * @return 0..1 的 u（对应原图 16 格采样，0.25~1 恰好是图标内容区）
     */
    public static float heldU(float modelY) {
        return 2.0F * modelY - 1.0F;
    }

    /**
     * 手持层 v：显示坐标 x 越大 → v 越大（原图下方）。背面（北面）镜像，和原版双层物品模型一致。
     *
     * @param modelX 显示点模型空间的 x（0~0.375 之间）
     * @param front  true = 正面（南面），false = 背面（北面）
     */
    public static float heldV(float modelX, boolean front) {
        return front ? 2.0F * modelX : 0.75F - 2.0F * modelX;
    }

    /** 图标层 u：原样取样（正面），背面水平镜像。 */
    public static float iconU(float modelX, boolean front) {
        return front ? modelX : 1.0F - modelX;
    }

    /** 图标层 v：原样取样，y 向上对应 v 向上（v = 1 - y）。 */
    public static float iconV(float modelY) {
        return 1.0F - modelY;
    }
}
