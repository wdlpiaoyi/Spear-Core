package net.minecraft.spearcore.compat.tetra;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.world.item.ItemStack;
import se.mickelus.tetra.items.modular.IModularItem;
import se.mickelus.tetra.module.ItemModule;
import se.mickelus.tetra.module.data.VariantData;

/**
 * 模块化长矛的显示名：按 Tetra 自己的命名规则拼「材料前缀 + 物品名」。
 *
 * <p>Tetra 的模块化物品名来自 {@code IModularItem#getItemName}，它把模块的
 * {@code tetra.module.<模块>.item_name}（剑刃模块是「剑」）与材料前缀拼在一起，于是本模组的矛会显示成
 * 「铁剑」——因为四个槽位借用的是 Tetra 剑的模块。所以本类只借它的材料前缀：
 * {@code tetra.material.<材料>.prefix}（铁 / 下界合金 / 木 …），再拼上本模组的「矛」，
 * 得到「铁矛」「下界合金矛」，与贴图长矛的 {@code item.spearcore.iron_spear} 叫法一致。</p>
 *
 * <p>材料取矛头槽位（{@code sword/blade}）：Tetra 给剑命名时同样只看 major 槽位（剑刃），
 * 手柄/护手/配重不参与物品名。铁砧改过名的栈由调用方判定，不进这里。</p>
 *
 * <p>为什么不用 {@code IModularItem#getItemName} 直接拿名字：它在 {@code ItemModule#getItemName} 里走
 * 客户端专用的 {@code I18n}，而 {@code ItemStack#getHoverName} 在专用服务器上也会被调用
 * （死亡消息、命令反馈），服务端没有 {@code I18n} 会直接抛错。这里只产出
 * {@link Component}，翻译留到客户端解析。</p>
 *
 * <p>与颜色模块一样，所有 Tetra 查询都包在 {@code try/catch} 里：材料数据被数据包改坏时回退成
 * 不带材料的名，而不是让 tooltip 崩掉。</p>
 */
public final class TetraSpearNaming {

    /** 普通矛头的带材料前缀名字；槊头/三尖两刃刀用 {@link TetraSpearHead#nameKey()}。 */
    public static final String NAMED_KEY = "item.spearcore.modular_spear.named";

    private static boolean loggedFailure;

    private TetraSpearNaming() {
    }

    /**
     * 矛头材料对应的完整名字；材料读不到时返回 {@code null}，调用方回退到不带材料的
     * {@code item.spearcore.modular_spear}。
     */
    public static Component hoverName(ItemStack stack) {
        String materialKey = materialKey(stack);
        if (materialKey == null) {
            return null;
        }
        // 材料名优先用 Tetra 的「前缀」写法（铁 / 木 / 下界合金），缺翻译时退回材料全名（铁锭 / 木板）。
        Component material = Component.translatableWithFallback(
                "tetra.material." + materialKey + ".prefix",
                "tetra.material." + materialKey);
        TetraSpearHead head = TetraSpearHead.of(TetraSpearColors.headVariantKey(stack));
        Component name = Component.translatable(head.nameKey(), material);
        // 通用兼容第三方的「会改物品名」的改进（例如 RevelationFix 的 longinus / aeglos）：
        // Tetra 的改进把显示前缀写在 tetra.improvement.<key>.prefix，这里不认具体 modid，
        // 有翻译就拼到名字前面，没有就回退空串。
        try {
            IModularItem modular = (IModularItem) stack.getItem();
            for (var improvement : modular.getImprovements(stack)) {
                if (improvement == null || improvement.key == null) {
                    continue;
                }
                Component prefix = Component.translatableWithFallback(
                        "tetra.improvement." + improvement.key + ".prefix", "");
                MutableComponent combined = Component.empty().append(prefix);
                combined.append(" ");
                combined.append(name);
                name = combined;
            }
        } catch (Throwable ignored) {
            // 读不到改进时保留原名字。
        }
        return name;
    }

    /** 矛头槽位当前部件的材料 key：{@code basic_blade/netherite} → {@code netherite}。 */
    public static String materialKey(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IModularItem modular)) {
                return null;
            }
            ItemModule module = modular.getModuleFromSlot(stack, TetraSpearColors.HEAD_SLOT);
            if (module == null) {
                return null;
            }
            VariantData variant = module.getVariantData(stack);
            return variant == null ? null : TetraMaterialKey.materialOf(variant.key);
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 读取 Tetra 矛头材料失败，模块化长矛将使用不带材料的名", throwable);
            }
            return null;
        }
    }
}
