package net.minecraft.spearcore.item;

/**
 * 长矛的表现风格。
 *
 * <p>{@link #VANILLA_SPEAR}：本模组自带的贴图长矛，第一 / 第三人称姿态按 {@code SpearAnimations} 走。
 * {@link #MODULAR_3D}：Tetra 里的模块化长矛（archetype {@code modular_spear}），复用同一套动画，
 * 外观同样走本模组的贴图长矛模型（杆/矛头按 Tetra 材料 tint）。枚举名是历史名字，不代表 3D 模型。</p>
 */
public enum SpearStyle {
    VANILLA_SPEAR,
    MODULAR_3D
}
