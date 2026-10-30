package net.minecraft.spearcore.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.spearcore.client.TetraPoseConfig;

/**
 * Tetra 3D 长矛姿态编辑器（默认 F9 打开）。
 *
 * <p>左右两个视角二选一编辑：平移、XYZ 旋转、缩放都是滑块，改动即时生效（关屏不丢），
 * 「保存并关闭」写入 {@code config/spearcore-tetra-pose.json}。</p>
 */
public class TetraPoseScreen extends Screen {

    private static final int SLIDER_WIDTH = 240;
    private static final int SLIDER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 24;

    private final TetraPoseConfig config = TetraPoseConfig.get();
    private boolean editingThirdPerson;

    public TetraPoseScreen() {
        super(Component.translatable("screen.spearcore.tetra_pose"));
    }

    @Override
    protected void init() {
        rebuildPoseWidgets();
    }

    private TetraPoseConfig.Pose pose() {
        return editingThirdPerson ? config.thirdPerson : config.firstPerson;
    }

    private void rebuildPoseWidgets() {
        clearWidgets();

        int x = (width - SLIDER_WIDTH) / 2;
        int y = 56;
        TetraPoseConfig.Pose pose = pose();
        for (Field field : Field.values()) {
            addRenderableWidget(new PoseSlider(x, y, SLIDER_WIDTH, SLIDER_HEIGHT, field, pose));
            y += ROW_HEIGHT;
        }

        int buttonWidth = 110;
        int buttonsY = height - 36;
        addRenderableWidget(Button.builder(
                        Component.literal(editingThirdPerson ? "视角：第三人称" : "视角：第一人称"),
                        button -> {
                            editingThirdPerson = !editingThirdPerson;
                            rebuildPoseWidgets();
                        })
                .bounds((width - buttonWidth * 3 - 10) / 2, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("重置全部"), button -> {
            config.reset();
            rebuildPoseWidgets();
        }).bounds((width - buttonWidth * 3 - 10) / 2 + buttonWidth + 5, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("保存并关闭"), button -> {
            config.save();
            onClose();
        }).bounds((width - buttonWidth * 3 - 10) / 2 + (buttonWidth + 5) * 2, buttonsY, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFF);
        guiGraphics.drawCenteredString(font,
                Component.literal("改动即时生效；保存后写入 config/spearcore-tetra-pose.json"),
                width / 2, 30, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        config.save();
        super.onClose();
    }

    /** 姿态字段：滑块范围与读写。 */
    private enum Field {
        TRANSLATE_X("X 平移", -0.5D, 0.5D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.translateX;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.translateX = value;
            }
        },
        TRANSLATE_Y("Y 平移", -0.5D, 0.5D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.translateY;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.translateY = value;
            }
        },
        TRANSLATE_Z("Z 平移", -0.5D, 0.5D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.translateZ;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.translateZ = value;
            }
        },
        ROTATE_X("X 旋转（度）", -180.0D, 180.0D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.rotateX;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.rotateX = value;
            }
        },
        ROTATE_Y("Y 旋转（度）", -180.0D, 180.0D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.rotateY;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.rotateY = value;
            }
        },
        ROTATE_Z("Z 旋转（度）", -180.0D, 180.0D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.rotateZ;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.rotateZ = value;
            }
        },
        SCALE("缩放", 0.5D, 1.5D) {
            @Override
            double get(TetraPoseConfig.Pose pose) {
                return pose.scale;
            }

            @Override
            void set(TetraPoseConfig.Pose pose, double value) {
                pose.scale = value;
            }
        };

        private final String label;
        private final double min;
        private final double max;

        Field(String label, double min, double max) {
            this.label = label;
            this.min = min;
            this.max = max;
        }

        abstract double get(TetraPoseConfig.Pose pose);

        abstract void set(TetraPoseConfig.Pose pose, double value);

        double normalize(double value) {
            return (value - min) / (max - min);
        }

        double denormalize(double value) {
            return min + value * (max - min);
        }

        String format(double value) {
            return String.format("%s: %.3f", label, value);
        }
    }

    private static final class PoseSlider extends AbstractSliderButton {

        private final Field field;
        private final TetraPoseConfig.Pose pose;

        PoseSlider(int x, int y, int width, int height, Field field, TetraPoseConfig.Pose pose) {
            super(x, y, width, height, Component.empty(), field.normalize(field.get(pose)));
            this.field = field;
            this.pose = pose;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(field.format(field.denormalize(value))));
        }

        @Override
        protected void applyValue() {
            field.set(pose, field.denormalize(value));
        }
    }
}
