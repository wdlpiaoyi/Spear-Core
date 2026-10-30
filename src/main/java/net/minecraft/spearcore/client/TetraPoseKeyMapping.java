package net.minecraft.spearcore.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Tetra 长矛姿态编辑器的按键（默认 F9）。
 *
 * <p>只在装了 Tetra 时注册，避免没装 Tetra 的玩家在按键设置里看到一个永远用不上的条目。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TetraPoseKeyMapping {

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.spearcore.tetra_pose_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.spearcore");

    private TetraPoseKeyMapping() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        if (ModList.get() != null && ModList.get().isLoaded("tetra")) {
            event.register(OPEN_EDITOR);
        }
    }

    public static boolean consumeOpenClick() {
        return OPEN_EDITOR.consumeClick();
    }
}
