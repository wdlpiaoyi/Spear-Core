package net.minecraft.spearcore.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.spearcore.compat.tetra.TetraChargeRamp;

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

    /** 第一人称：在长矛基础手臂变换之后应用（空闲姿态）。 */
    public static void firstPerson(PoseStack poseStack) {
        apply(poseStack, TetraPoseConfig.get().firstPerson);
    }

    /** 第三人称手持：在长矛基础变换之后应用（空闲姿态）。 */
    public static void thirdPerson(PoseStack poseStack) {
        apply(poseStack, TetraPoseConfig.get().thirdPerson);
    }

    /**
     * 蓄力（举矛）姿态：在空闲锚点与蓄力锚点之间按 {@link TetraChargeRamp} 平滑插值，满蓄力后
     * 叠一点正弦呼吸。3D 模型不再复用贴图长矛的分段曲线，避免在 dismount/knockback 边界跳变。
     *
     * @param usedTicks 已经举矛的 tick 数（含 partialTick）
     */
    public static void applyCharge(PoseStack poseStack, boolean firstPerson, float usedTicks) {
        TetraPoseConfig config = TetraPoseConfig.get();
        TetraPoseConfig.Pose idle = firstPerson ? config.firstPerson : config.thirdPerson;
        TetraPoseConfig.Pose charge = firstPerson ? config.chargeFirstPerson : config.chargeThirdPerson;

        float t = TetraChargeRamp.smoothProgress(usedTicks);
        double sway = firstPerson ? TetraChargeRamp.sway(usedTicks) : 0.0D;

        double translateX = MthLerp(idle.translateX, charge.translateX, t);
        double translateY = MthLerp(idle.translateY, charge.translateY, t) + sway;
        double translateZ = MthLerp(idle.translateZ, charge.translateZ, t);
        double rotateX = MthLerp(idle.rotateX, charge.rotateX, t);
        double rotateY = MthLerp(idle.rotateY, charge.rotateY, t);
        double rotateZ = MthLerp(idle.rotateZ, charge.rotateZ, t);
        double scale = MthLerp(idle.scale, charge.scale, t);

        if (translateX != 0.0D || translateY != 0.0D || translateZ != 0.0D) {
            poseStack.translate(translateX, translateY, translateZ);
        }
        if (rotateX != 0.0D) {
            poseStack.mulPose(Axis.XP.rotationDegrees((float) rotateX));
        }
        if (rotateY != 0.0D) {
            poseStack.mulPose(Axis.YP.rotationDegrees((float) rotateY));
        }
        if (rotateZ != 0.0D) {
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) rotateZ));
        }
        if (scale != 1.0D) {
            poseStack.scale((float) scale, (float) scale, (float) scale);
        }
    }

    private static double MthLerp(double from, double to, float t) {
        return from + (to - from) * t;
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
