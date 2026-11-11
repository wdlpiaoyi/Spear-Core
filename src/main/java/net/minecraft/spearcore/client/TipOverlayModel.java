package net.minecraft.spearcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * 在模块化长矛的贴图模型上叠一层第三方枪尖（longinus / aeglos）。
 *
 * <p>叠加用的四边形由 {@link TetraSpearModel} 在烘焙完成时按原 mod 的贴图资源位置现做，
 * 本模组不复制素材。因为本模组的矛走 {@code forge:separate_transforms}，不同显示上下文拿到的是
 * **不同的 BakedModel**（{@code SeparateTransformsModel.Baked#applyTransform} 会按上下文换模型），
 * 所以包装层必须在 {@link #applyTransform} 里重新包一次，否则手臂里的矛会被换回未包装的模型、
 * 叠加层直接消失。同理 {@link #getRenderPasses} 必须返回包装后的自己，否则
 * {@code ItemRenderer} 会拿原始模型去渲染。</p>
 */
public final class TipOverlayModel extends BakedModelWrapper<BakedModel> {

    private final List<BakedQuad> heldQuads;
    private final List<BakedQuad> iconQuads;
    private final boolean icon;

    public TipOverlayModel(BakedModel base, List<BakedQuad> heldQuads, List<BakedQuad> iconQuads) {
        this(base, heldQuads, iconQuads, false);
    }

    private TipOverlayModel(BakedModel base, List<BakedQuad> heldQuads, List<BakedQuad> iconQuads,
                            boolean icon) {
        super(base);
        this.heldQuads = heldQuads;
        this.iconQuads = iconQuads;
        this.icon = icon;
    }

    /** 物品栏 / 展示框 / 掉落物用 16x16 图标层，其余（手持、蓄力）用 32x32 手持层。 */
    private static boolean isIconContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.GUI
                || context == ItemDisplayContext.FIXED
                || context == ItemDisplayContext.GROUND;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack,
                                     boolean applyLeftHandTransform) {
        BakedModel transformed = originalModel.applyTransform(context, poseStack, applyLeftHandTransform);
        boolean iconContext = isIconContext(context);
        if (transformed == originalModel && icon == iconContext) {
            return this;
        }
        return new TipOverlayModel(transformed, heldQuads, iconQuads, iconContext);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        List<BakedModel> passes = originalModel.getRenderPasses(itemStack, fabulous);
        if (passes.size() == 1 && passes.get(0) == originalModel) {
            return List.of(this);
        }
        List<BakedModel> wrapped = new ArrayList<>(passes.size());
        for (BakedModel pass : passes) {
            wrapped.add(pass == originalModel ? this : new TipOverlayModel(pass, heldQuads, iconQuads, icon));
        }
        return wrapped;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        List<BakedQuad> base = originalModel.getQuads(state, side, rand);
        return append(base, side);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> base = originalModel.getQuads(state, side, rand, data, renderType);
        return append(base, side);
    }

    /**
     * 只在 {@code side == null} 的那一趟追加：{@code ItemRenderer#renderModelLists} 会先按 6 个方向
     * 取一遍、最后再取一遍无方向的，叠加层跟最后一趟走就不会被重复画 7 次。
     */
    private List<BakedQuad> append(List<BakedQuad> base, @Nullable Direction side) {
        List<BakedQuad> overlay = icon ? iconQuads : heldQuads;
        if (side != null || overlay.isEmpty()) {
            return base;
        }
        List<BakedQuad> merged = new ArrayList<>(base.size() + overlay.size());
        merged.addAll(base);
        merged.addAll(overlay);
        return merged;
    }
}
