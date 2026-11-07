package net.minecraft.spearcore.item;

/**
 * 长矛的表现风格。
 *
 * <p>{@link #VANILLA_SPEAR}：本模组自带的贴图长矛，第一 / 第三人称姿态按 {@code SpearAnimations} 走。
 * {@link #MODULAR_3D}：本模组在 Tetra 里注册的模块化长矛（archetype {@code modular_spear}），复用同一套
 * 动画，但需要在客户端额外叠加一点位移 / 旋转，让 3D 模型看起来也像"端着一根矛"。</p>
 */
public enum SpearStyle {
    VANILLA_SPEAR,
    MODULAR_3D
}
