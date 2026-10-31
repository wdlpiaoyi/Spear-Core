package net.minecraft.spearcore.mixin;

import net.minecraft.spearcore.client.SpearClientExtensions;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Item.class)
public class ItemInitializeClientMixin {

    // Tetra 的模块化工具不在这里注册：物品构造期 ModList 可能还没就绪，改由
    // compat/tetra/mixin/ItemRenderPropertiesTetraMixin 在渲染时惰性挂载（见该类注释）。
    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true, remap = false)
    public void onInitializeClient(Consumer<IClientItemExtensions> consumer, CallbackInfo ci) {
        Item self = (Item) (Object) this;
        if (self instanceof SpearItem) {
            consumer.accept(new SpearClientExtensions());
            ci.cancel();
        }
    }
}
