package net.minecraft.spearcore.compat.tetra;

/**
 * Tetra 模块变体 key 与材料 key 的匹配规则。
 *
 * <p>Tetra 的材质变体 key 形如 {@code spearhead/iron}：前半段是模块 key 前缀，后半段是材料 key。
 * 找颜色时要按 {@code /} 边界匹配，不能用 {@code endsWith(material)}，否则 {@code ironwood}
 * 会被误判成 {@code iron}。</p>
 *
 * <p>放在 common 包、不 import 任何 Tetra 类，便于 GameTest 直接验证。</p>
 */
public final class TetraMaterialKey {

    private TetraMaterialKey() {
    }

    /** variantKey 是否由 {@code materialKey} 作为最后一段构成。 */
    public static boolean matches(String variantKey, String materialKey) {
        if (variantKey == null || materialKey == null || materialKey.isEmpty()) {
            return false;
        }
        if (variantKey.equals(materialKey)) {
            return true;
        }
        return variantKey.endsWith("/" + materialKey);
    }
}
