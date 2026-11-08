package net.minecraft.spearcore.compat.tetra;

import org.jetbrains.annotations.Nullable;

/**
 * Tetra 模块门禁的「角色映射」：把第三方数据里写死的 {@code sword/*}、{@code single/*}、
 * {@code double/*} 模块要求，映射到本模组矛的对应槽位。
 *
 * <p>很多 Tetra 联动（例如 RevelationFix 的神灵金锭附魔）把 crafting effect 的 requirement 写死成
 * 「模块是某把剑的剑刃/剑柄」；本模组的矛槽位永远匹配不上。这里按**角色**比较槽位名与模块名，
 * 而不是去枚举某个 mod 的具体 key，因此不针对单个整合包：</p>
 * <ul>
 *   <li>head 类：{@code head}/{@code blade}/{@code point}/{@code spike}/{@code tip} → 矛的 {@code spear/head}</li>
 *   <li>handle 类：{@code handle}/{@code hilt}/{@code shaft}/{@code grip} → {@code spear/handle}</li>
 *   <li>binding 类：{@code binding}/{@code guard}/{@code wrap} → {@code spear/binding}</li>
 *   <li>butt 类：{@code butt}/{@code pommel}/{@code counterweight} → {@code spear/butt}</li>
 * </ul>
 *
 * <p>纯函数、不 import Tetra，便于 GameTest 直接校验。</p>
 */
public final class TetraModuleGateMapping {

    private TetraModuleGateMapping() {
    }

    /** 是否应把 {@code requiredModuleKey} 视为满足当前矛槽位。 */
    public static boolean matches(@Nullable String targetSlot, @Nullable String requiredModuleKey) {
        if (targetSlot == null || requiredModuleKey == null) {
            return false;
        }
        if (!requiredModuleKey.startsWith("sword/")
                && !requiredModuleKey.startsWith("single/")
                && !requiredModuleKey.startsWith("double/")) {
            return false;
        }
        String targetRole = roleOfSlot(targetSlot);
        String requiredRole = roleOfModuleKey(requiredModuleKey);
        return targetRole != null && targetRole.equals(requiredRole);
    }

    /**
     * crafting effect 的 {@code anySlot} 条件：只要模块角色对应到矛的任一槽位即可。
     *
     * <p>这类条件不携带槽位字符串，无法判断具体是刃/柄/护手/配重，只能按角色放行。</p>
     */
    public static boolean matchesAnySpearSlot(@Nullable String requiredModuleKey) {
        if (requiredModuleKey == null) {
            return false;
        }
        if (!requiredModuleKey.startsWith("sword/")
                && !requiredModuleKey.startsWith("single/")
                && !requiredModuleKey.startsWith("double/")) {
            return false;
        }
        return roleOfModuleKey(requiredModuleKey) != null;
    }

    @Nullable
    public static String roleOfSlot(String slot) {
        int index = slot.lastIndexOf('/');
        String suffix = index >= 0 ? slot.substring(index + 1) : slot;
        return switch (suffix) {
            case "head", "blade", "point", "spike", "tip" -> "head";
            case "handle", "hilt", "shaft", "grip" -> "handle";
            case "binding", "guard", "wrap" -> "binding";
            case "butt", "pommel", "counterweight" -> "butt";
            default -> null;
        };
    }

    @Nullable
    public static String roleOfModuleKey(String moduleKey) {
        int index = moduleKey.lastIndexOf('/');
        String name = index >= 0 ? moduleKey.substring(index + 1) : moduleKey;
        if (name.contains("blade") || name.contains("head") || name.contains("point")
                || name.contains("spike") || name.contains("tip")) {
            return "head";
        }
        if (name.contains("hilt") || name.contains("handle") || name.contains("shaft")
                || name.contains("grip")) {
            return "handle";
        }
        if (name.contains("binding") || name.contains("guard") || name.contains("wrap")) {
            return "binding";
        }
        if (name.contains("butt") || name.contains("pommel") || name.contains("counterweight")) {
            return "butt";
        }
        return null;
    }
}
