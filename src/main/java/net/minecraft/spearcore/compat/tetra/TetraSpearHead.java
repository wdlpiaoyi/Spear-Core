package net.minecraft.spearcore.compat.tetra;

import org.jetbrains.annotations.Nullable;

/**
 * 模块化长矛的矛头形态。
 *
 * <p>优先按矛头槽的模块 key 区分（更可靠），读不到再退回变体 key 前缀；本枚举本身不 import Tetra 类型。</p>
 * <ul>
 *   <li>{@link #PLAIN}：普通矛头（{@code spear_head/}），左键戳刺、右键蓄力；</li>
 *   <li>{@link #SHUO}：槊头（{@code spear_shuo_head/}），左键变成挥砍 + 自带横扫，攻速更慢、伤害更高；</li>
 *   <li>{@link #THREE}：三尖两刃刀（{@code spear_three_point/}），左键挥砍 + 横扫，全面强于普通矛头。</li>
 * </ul>
 *
 * <p>渲染选模型、物品名、行为分支都走这里，避免三处各写一遍前缀判断。纯数据，不 import Tetra 类型，
 * 可以在 GameTest 里直接测。</p>
 */
public enum TetraSpearHead {
    PLAIN("spear_head/", "item.spearcore.modular_spear.named", false),
    SHUO("spear_shuo_head/", "item.spearcore.modular_spear.named_shuo", true),
    THREE("spear_three_point/", "item.spearcore.modular_spear.named_blade", true);

    private final String variantPrefix;
    private final String nameKey;
    private final boolean slashing;

    TetraSpearHead(String variantPrefix, String nameKey, boolean slashing) {
        this.variantPrefix = variantPrefix;
        this.nameKey = nameKey;
        this.slashing = slashing;
    }

    /** 变体 key 前缀，例如 {@code spear_head/}。 */
    public String variantPrefix() {
        return variantPrefix;
    }

    /** 物品名 key（值形如 {@code %s矛} / {@code %s槊} / {@code %s刃}）。 */
    public String nameKey() {
        return nameKey;
    }

    /** 左键是否是挥砍（而不是戳刺）。 */
    public boolean slashing() {
        return slashing;
    }

    /** 按变体 key 判定形态；未知/为空按普通矛头处理。 */
    public static TetraSpearHead of(@Nullable String variantKey) {
        return ofVariantAndModule(variantKey, null);
    }

    /**
     * 先按模块 key、再按变体 key 判定形态。
     *
     * <p>只靠变体前缀在第三方改写变体 key 时会失效；模块 key 是槽位里真正安装的模块
     * （{@code spear/shuo_head} / {@code spear/three_point_blade}），更可靠。</p>
     */
    public static TetraSpearHead ofVariantAndModule(@Nullable String variantKey, @Nullable String moduleKey) {
        String modulePath = stripNamespace(moduleKey);
        if (modulePath != null) {
            if (modulePath.endsWith("spear/shuo_head")) {
                return SHUO;
            }
            if (modulePath.endsWith("spear/three_point_blade")) {
                return THREE;
            }
        }
        return ofVariant(variantKey);
    }

    @Nullable
    private static String stripNamespace(@Nullable String key) {
        if (key == null) {
            return null;
        }
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    private static TetraSpearHead ofVariant(@Nullable String variantKey) {
        String key = stripNamespace(variantKey);
        if (key != null) {
            for (TetraSpearHead head : values()) {
                if (head != PLAIN && (key.startsWith(head.variantPrefix)
                        || key.startsWith(head.variantPrefix.replace("spear_", "")))) {
                    return head;
                }
            }
        }
        return PLAIN;
    }
}
