package net.minecraft.spearcore.event;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.init.SpearLikeSupport;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 调试命令：{@code /spearcore tetra}。
 *
 * <p>把手持模块化长矛的属性来源摊开（各槽模块/变体、属性贡献、ATTACK_DAMAGE / ATTACK_SPEED、
 * 改进列表），用于调数值时定位「攻速为什么是 0」这类问题。仅 op 2 可用，无副作用。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class SpearDebugCommand {

    private SpearDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spearcore")
                .then(Commands.literal("tetra")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> dump(context.getSource()))));
    }

    private static int dump(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("需要玩家执行"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || SpearLikeSupport.of(stack) == null) {
            source.sendFailure(Component.literal(
                    "请手持一把模块化长矛（Tetra 矛）：/spearcore tetra 会输出它的模块、属性与拟合数值，"
                            + "用来排查攻速/伤害/挥砍等问题。该命令只读，不会修改物品。"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "【Spear Core】模块化长矛诊断（把下面内容发给作者即可；只读，不会改物品）"), false);
        // 反射调用 TetraSpearDebug（它 import 了 Tetra 类）：这个 @EventBusSubscriber 类会在启动时被 FML
        // 主动加载，直接引用会让未装 Tetra 的 GameTest 服务端在类校验阶段崩掉。
        try {
            Class<?> debug = Class.forName("net.minecraft.spearcore.compat.tetra.TetraSpearDebug");
            Object result = debug.getMethod("describe", ItemStack.class).invoke(null, stack);
            if (result instanceof java.util.List<?> lines) {
                for (Object line : lines) {
                    source.sendSuccess(() -> Component.literal(String.valueOf(line)), false);
                }
            }
        } catch (Throwable throwable) {
            source.sendFailure(Component.literal("读取 Tetra 属性失败: " + throwable));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "提示：换不同矛头/材料再运行一次，可以对比「攻速为什么变慢」「挥砍是否识别」这类问题。"), false);
        return 1;
    }
}
