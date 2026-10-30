package net.minecraft.spearcore.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tetra 3D 长矛的第一/第三人称姿态参数。
 *
 * <p>贴图长矛与 3D 模型的原点、朝向、粗细不同，套同一套长矛姿势必然有偏差。这里把差异参数化，
 * 由游戏内 GUI（默认 F9）实时调整并存到 {@code config/spearcore-tetra-pose.json}，
 * 不必为了改一个角度重新编译。</p>
 */
public final class TetraPoseConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("spearcore-tetra-pose.json");

    private static TetraPoseConfig instance;

    public Pose firstPerson = Pose.firstPersonDefault();
    public Pose thirdPerson = Pose.thirdPersonDefault();

    private TetraPoseConfig() {
    }

    /** 延迟加载；首次调用时读文件，之后返回同一个可变实例。 */
    public static synchronized TetraPoseConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static TetraPoseConfig load() {
        if (Files.isRegularFile(FILE)) {
            try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                TetraPoseConfig config = GSON.fromJson(reader, TetraPoseConfig.class);
                if (config != null) {
                    return config.sanitized();
                }
            } catch (Exception exception) {
                SpearcoreMod.LOGGER.warn("[spearcore] 读取 Tetra 姿态配置失败，使用默认值：{}", FILE, exception);
            }
        }
        return new TetraPoseConfig();
    }

    private TetraPoseConfig sanitized() {
        if (firstPerson == null) firstPerson = Pose.firstPersonDefault();
        if (thirdPerson == null) thirdPerson = Pose.thirdPersonDefault();
        firstPerson.sanitize();
        thirdPerson.sanitize();
        return this;
    }

    public void reset() {
        firstPerson = Pose.firstPersonDefault();
        thirdPerson = Pose.thirdPersonDefault();
    }

    public void save() {
        try {
            Path parent = FILE.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException exception) {
            SpearcoreMod.LOGGER.warn("[spearcore] 保存 Tetra 姿态配置失败：{}", FILE, exception);
        }
    }

    /** 当前参数的 JSON 文本，供 GUI 的「复制」按钮使用。 */
    public String toJson() {
        return GSON.toJson(this);
    }

    /** 一套姿态：先平移、再按 XYZ 旋转、最后等比缩放。 */
    public static final class Pose {
        public double translateX;
        public double translateY;
        public double translateZ;
        public double rotateX;
        public double rotateY;
        public double rotateZ;
        public double scale = 1.0D;

        public Pose() {
        }

        public Pose(double translateX, double translateY, double translateZ,
                    double rotateX, double rotateY, double rotateZ, double scale) {
            this.translateX = translateX;
            this.translateY = translateY;
            this.translateZ = translateZ;
            this.rotateX = rotateX;
            this.rotateY = rotateY;
            this.rotateZ = rotateZ;
            this.scale = scale;
        }

        static Pose firstPersonDefault() {
            // Tetra 3D 模型默认偏平，往下压一点、前倾 15° 更接近本模组长矛的持握角度。
            return new Pose(0.0D, 0.0D, 0.0D, -15.0D, 0.0D, 0.0D, 1.0D);
        }

        static Pose thirdPersonDefault() {
            return new Pose(0.0D, 0.0D, 0.0D, -10.0D, 0.0D, 0.0D, 1.0D);
        }

        void sanitize() {
            if (!Double.isFinite(translateX) || !Double.isFinite(translateY) || !Double.isFinite(translateZ)
                    || !Double.isFinite(rotateX) || !Double.isFinite(rotateY) || !Double.isFinite(rotateZ)
                    || !Double.isFinite(scale) || scale <= 0.0D) {
                Pose defaults = firstPersonDefault();
                translateX = defaults.translateX;
                translateY = defaults.translateY;
                translateZ = defaults.translateZ;
                rotateX = defaults.rotateX;
                rotateY = defaults.rotateY;
                rotateZ = defaults.rotateZ;
                scale = defaults.scale;
            }
        }
    }
}
