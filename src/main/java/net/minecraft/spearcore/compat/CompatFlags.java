package net.minecraft.spearcore.compat;

/**
 * 可选的第三方联动开关。
 *
 * <p><b>为什么单独一个类：</b>本类只依赖加载器自身的 API，不引用任何联动模组的类型，
 * 因此没装那个模组时也能安全加载。真正引用联动模组 API 的代码放在 {@code compat.*} 的
 * 独立类里，只有开关为 true 时才会被触达 —— JVM 的类解析是惰性的，这样能保证
 * "没装 Epic Fight 时 EpicFightCompat 永远不会被类加载器加载"，不会 NoClassDefFoundError。</p>
 */
public final class CompatFlags {

    private static boolean epicFight;

    private CompatFlags() {
    }

    public static void setEpicFight(boolean loaded) {
        epicFight = loaded;
    }

    /** Epic Fight 是否在场；在场时战斗结算完全交给 EF，我们只做武器注册。 */
    public static boolean epicFight() {
        return epicFight;
    }
}
