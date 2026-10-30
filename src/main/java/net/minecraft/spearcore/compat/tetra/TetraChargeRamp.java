package net.minecraft.spearcore.compat.tetra;

import net.minecraft.util.Mth;

/**
 * Tetra 3D 长矛的蓄力抬升曲线。
 *
 * <p>本模组贴图长矛的蓄力动画（{@code client.animation.SpearAnimations.firstPersonUse}）是按
 * 贴图模型的原点与朝向逐帧调出来的，分段边界（dismount / knockback / damage 时间点）上会有
 * 明显折角；直接套在 Tetra 的 3D 模块模型上就会出现「跳变 + 指向不对」。</p>
 *
 * <p>所以 3D 模型改用一条独立的、单调的抬升曲线：起手 1 tick 内不抬、之后 5 tick 平滑过渡到
 * 蓄力锚点姿态，满蓄力后只保留极小的正弦呼吸。这里只放纯函数，便于 GameTest 在不加载 Tetra
 * 的环境里验证。</p>
 */
public final class TetraChargeRamp {

    /** 起手后多少 tick 抬到蓄力姿态。 */
    public static final float RAISE_TICKS = 5.0F;
    /** 满蓄力后呼吸幅度（格），0 表示关闭。 */
    public static final float SWAY_AMPLITUDE = 0.015F;

    private TetraChargeRamp() {
    }

    /** 0..1 的原始进度；前 1 tick 保持 0，避免刚按下右键就瞬移。 */
    public static float progress(float usedTicks) {
        return Mth.clamp((usedTicks - 1.0F) / RAISE_TICKS, 0.0F, 1.0F);
    }

    /** 平滑后的进度：smoothstep，两端速度为 0，不会在起手/到位处产生折角。 */
    public static float smoothProgress(float usedTicks) {
        float t = progress(usedTicks);
        return t * t * (3.0F - 2.0F * t);
    }

    /** 满蓄力后叠加的轻微正弦呼吸（格）；未抬满时为 0。 */
    public static float sway(float usedTicks) {
        return Mth.sin(usedTicks * 0.30F) * SWAY_AMPLITUDE * smoothProgress(usedTicks);
    }
}
