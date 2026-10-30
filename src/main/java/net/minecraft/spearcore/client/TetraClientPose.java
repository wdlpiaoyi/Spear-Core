package net.minecraft.spearcore.client;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Tetra 3D 模型的姿态微调入口。
 *
 * <p>本模组的长矛是平面贴图，Tetra 的单头工具是 3D 模型：两者套同一套长矛姿势时，物品原点、朝向与
 * 视觉粗细都不一样。这里给 3D 模型叠一层额外的平移/旋转，默认值保持不偏移，方便在 {@code runClient}
 * 里对照调整——改常量即可，不必动 mixin。</p>
 */
public final class TetraClientPose {

    private TetraClientPose() {
    }

    /** 第一人称：在长矛基础手臂变换之后应用。 */
    public static void firstPerson(PoseStack poseStack) {
        // 调参入口：Tetra 3D 模型与贴图长矛的原点/朝向差异
    }

    /** 第三人称手持：在长矛基础变换之后应用。 */
    public static void thirdPerson(PoseStack poseStack) {
        // 调参入口：Tetra 3D 模型与贴图长矛的原点/朝向差异
    }
}
