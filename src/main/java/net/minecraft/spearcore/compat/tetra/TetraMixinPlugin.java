package net.minecraft.spearcore.compat.tetra;

import net.minecraftforge.fml.ModList;
import net.minecraft.spearcore.SpearcoreMod;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 让 spearcore.tetra.mixins.json 只针对"装了 Tetra"的环境生效。
 *
 * <p>和 Punchy 插件同样的坑：Mixin 在配置准备阶段就会调用 shouldApplyMixin，此时
 * ModList 可能还没初始化（ModList.get() 返回 null），所以优先用 FML 早期可用的
 * LoadingModList（反射，避免编译期依赖 FML 内部模块）。
 *
 * <p>Tetra 兼容的 mixin 目标全是原版 {@code ItemStack}，但它们要引用 Tetra 模块 API，
 * 所以在没装 Tetra 时整份配置都不应用；Tetra 版本不匹配时由运行期检测兜底
 * （检测失败只停用兼容，不影响游戏）。
 */
public final class TetraMixinPlugin implements IMixinConfigPlugin {

    private static Boolean tetraPresent;

    private static boolean spearcore$loggedActive;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        boolean present = tetraPresent();
        if (present && !spearcore$loggedActive) {
            spearcore$loggedActive = true;
            SpearcoreMod.LOGGER.info("[spearcore] Tetra 兼容已启用：长手柄 + 矛头的单头工具按本模组长矛工作");
        }
        return present;
    }

    private static boolean tetraPresent() {
        if (tetraPresent != null) return tetraPresent;

        Boolean known = null;

        // 1) 启动早期：LoadingModList 已经填充，而 ModList 还是 null
        try {
            Class<?> loadingModListClass = Class.forName("net.minecraftforge.fml.loading.LoadingModList");
            Object loadingModList = loadingModListClass.getMethod("get").invoke(null);
            if (loadingModList != null) {
                Object modFile = loadingModListClass
                        .getMethod("getModFileById", String.class)
                        .invoke(loadingModList, "tetra");
                known = modFile != null;
            }
        } catch (Throwable ignored) {
            // API 不匹配/未就绪：留给下一步
        }

        // 2) 常规阶段：ModList
        if (known == null) {
            try {
                ModList modList = ModList.get();
                if (modList != null) {
                    known = modList.isLoaded("tetra");
                }
            } catch (Throwable ignored) {
            }
        }

        if (known != null) {
            tetraPresent = known;
            return known;
        }
        // 3) 判定不了：放行（required=false 兜底），不缓存以便之后重新判定
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
