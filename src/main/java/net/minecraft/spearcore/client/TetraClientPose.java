package net.minecraft.spearcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * Tetra 3D 模型的姿态微调入口。
 *
 * <p>本模组的长矛是平面贴图，Tetra 的单头工具是 3D 模型：两者套同一套长矛姿势时，物品原点、朝向与
 * 视觉粗细都不一样。这里在长矛基础变换之后叠一层参数化平移/旋转/缩放，参数来自
 * {@link TetraPoseConfig}（游戏内按 F9 可实时调整，保存在 {@code config/spearcore-tetra-pose.json}）。</p>
 */
public final class TetraClientPose {

    private TetraClientPose() {
    }

    /** 第一人称：在长矛基础手臂变换之后应用。 */
    public static void firstPerson(PoseStack poseStack) {
        apply(poseStack, TetraPoseConfig.get().firstPerson);
    }

    /** 第三人称手持：在长矛基础变换之后应用。 */
    public static void thirdPerson(PoseStack poseStack) {
        apply(poseStack, TetraPoseConfig.get().thirdPerson);
    }

    private static void apply(PoseStack poseStack, TetraPoseConfig.Pose pose) {
        if (pose.translateX != 0.0D || pose.translateY != 0.0D || pose.translateZ != 0.0D) {
            poseStack.translate(pose.translateX, pose.translateY, pose.translateZ);
        }
        if (pose.rotateX != 0.0D) {
            poseStack.mulPose(Axis.XP.rotationDegrees((float) pose.rotateX));
        }
        if (pose.rotateY != 0.0D) {
            poseStack.mulPose(Axis.YP.rotationDegrees((float) pose.rotateY));
        }
        if (pose.rotateZ != 0.0D) {
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) pose.rotateZ));
        }
        if (pose.scale != 1.0D) {
            poseStack.scale((float) pose.scale, (float) pose.scale, (float) pose.scale);
        }
    }
}
