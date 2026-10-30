package net.minecraft.spearcore.mixin;

import net.minecraft.spearcore.client.SpearClientExtensions;
import net.minecraft.spearcore.compat.tetra.TetraClientCompat;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(Item.class)
public class ItemInitializeClientMixin {

    @Inject(method = "initializeClient", at = @At("HEAD"), cancellable = true, remap = false)
    public void onInitializeClient(Consumer<IClientItemExtensions> consumer, CallbackInfo ci) {
        Item self = (Item) (Object) this;
        if (self instanceof SpearItem) {
            consumer.accept(new SpearClientExtensions());
            ci.cancel();
            return;
        }
        // Tetra 的模块化工具也需要本模组的第一人称长矛姿势；未装 Tetra 时不碰 TetraClientCompat，
        // 避免在常量池里解析 Tetra 类型。
        if (ModList.get() != null && ModList.get().isLoaded("tetra") && TetraClientCompat.isModularItem(self)) {
            consumer.accept(new SpearClientExtensions());
            ci.cancel();
        }
    }
}
