package net.minecraft.spearcore.resourcepack;

import com.mojang.logging.LogUtils;
import net.minecraft.spearcore.config.SpearConfig;
import org.slf4j.Logger;

/**
 * Spear Core 资源包兼容补丁。
 *
 * <p>本类只做两件事，都是纯客户端：</p>
 * <ol>
 *     <li>把 1.21.9+ 的资源包（{@code pack.mcmeta} 只有 {@code min_format}/{@code max_format}）
 *     补成 1.20.1 能读的 {@code pack_format: 15}，让它出现在资源包列表里；</li>
 *     <li>把包内 {@code assets/minecraft/textures/item/*_spear*.png} 映射成
 *     {@code assets/spearcore/textures/item/...}，于是一份按原版命名写的长矛贴图包也能覆盖
 *     Spear Core 的七把矛。</li>
 * </ol>
 *
 * <p>原来的独立模组 Spear-Core-Resourcepack-Compat 已并入本模组：不再单独注册 config，
 * 开关见 {@link SpearConfig#RESOURCEPACK_COMPAT}，读取统一走 {@link #enabled()}。</p>
 */
public final class ResourcepackCompat {

    /** 被兼容的命名空间：Spear Core 的物品与模型都在这里。 */
    public static final String SPEAR_CORE_NAMESPACE = "spearcore";

    public static final Logger LOGGER = LogUtils.getLogger();

    private ResourcepackCompat() {
    }

    /** 配置未加载时按默认值（启用）处理。 */
    public static boolean enabled() {
        try {
            return SpearConfig.RESOURCEPACK_COMPAT.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
