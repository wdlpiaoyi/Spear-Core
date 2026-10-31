package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.client.SpearClientExtensions;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.impl.ModularSingleHeadedItem;

/**
 * 给 Tetra 单头工具挂上本模组的第一人称长矛扩展。
 *
 * <p>为什么不在 {@code Item#initializeClient} 里注册：物品是在构造期调用 {@code initClient()} 的，
 * 那个时刻 {@code ModList} / Tetra 的状态并不保证已经可用；一旦判定失败，{@code SpearClientExtensions}
 * 就永远挂不上，表现为「模型换了、姿势和动画却没有」。{@code getRenderPropertiesInternal} 是渲染时
 * 才被 {@code IClientItemExtensions.of(stack)} 调用的，此时模组状态一定就绪。</p>
 *
 * <p>只对 Tetra 的单头工具返回扩展；具体某个栈是不是「长手柄 + 矛头」由
 * {@link SpearClientExtensions#applyForgeHandTransform} 内部的 {@code SpearLikeSupport.of(stack)} 二次判定，
 * 不是长矛的 Tetra 工具会返回 {@code false}，交还原版渲染。</p>
 *
 * <p>本 mixin 只在 {@code spearcore.tetra.mixins.json} 的 client 列表里，且该配置由
 * {@code TetraMixinPlugin} 保证在未安装 Tetra 时不会加载，因此可以直接引用 Tetra 类。</p>
 */
@Mixin(Item.class)
public abstract class ItemRenderPropertiesTetraMixin {

    private static final IClientItemExtensions TETRA_SPEAR_EXTENSIONS = new SpearClientExtensions();

    @Inject(method = "getRenderPropertiesInternal", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$tetraSpearExtensions(CallbackInfoReturnable<Object> cir) {
        if (!((Object) this instanceof ModularSingleHeadedItem)) {
            return;
        }
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("tetra")) {
            return;
        }
        cir.setReturnValue(TETRA_SPEAR_EXTENSIONS);
    }
}
