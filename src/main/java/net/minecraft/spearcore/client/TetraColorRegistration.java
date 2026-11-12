package net.minecraft.spearcore.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.compat.tetra.TetraSpearColors;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.spearcore.item.SpearLike;
import net.minecraft.spearcore.item.SpearStyle;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.registries.ForgeRegistries;
import se.mickelus.tetra.items.modular.IModularItem;

/**
 * 给 Tetra 模组物品注册自绘模型的物品颜色：tint 0 = 杆/箍（{@code sword/hilt} 材料），tint 1 = 矛头（{@code sword/blade} 材料）。
 *
 * <p>只在确认 Tetra 已加载后由 {@link TetraSpearColorsEvent} 调用，本类里的 Tetra 类型因此不会在
 * 未安装 Tetra 的客户端上被加载。</p>
 */
public final class TetraColorRegistration {

    private TetraColorRegistration() {
    }

    public static void register(RegisterColorHandlersEvent.Item event) {
        // 只给模块化长矛用的物品注册 tint；给所有 IModularItem 注册 -1 会抹掉 Tetra
        // 或其它联动模组自己注册的物品色（Forge 的物品色是 last-wins）。
        ResourceLocation dynamicHandheld = new ResourceLocation("tetra", "dynamic_handheld");
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (!(item instanceof IModularItem)) {
                continue;
            }
            if (!dynamicHandheld.equals(ForgeRegistries.ITEMS.getKey(item))) {
                continue;
            }
            event.register(TetraColorRegistration::resolveColor, item);
        }
    }

    private static int resolveColor(ItemStack stack, int tintIndex) {
        SpearLike spear = SpearLikeSupport.of(stack);
        if (spear == null || spear.style() != SpearStyle.MODULAR_3D) {
            return -1;
        }
        return switch (tintIndex) {
            case 0 -> TetraSpearColors.handleColor(stack);
            case 1 -> TetraSpearColors.headColor(stack);
            default -> -1;
        };
    }
}
