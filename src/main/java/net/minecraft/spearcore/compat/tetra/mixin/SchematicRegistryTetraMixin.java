package net.minecraft.spearcore.compat.tetra.mixin;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.spearcore.compat.tetra.TetraFullerImportImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import se.mickelus.tetra.module.schematic.SchematicDefinition;

/**
 * Tetra 数据装载完成后再导入第三方 fuller 变体。
 *
 * <p>{@code setupSchematics} 返回时模块变体、改进和配方 outcome 都已展开；此时导入
 * 不会撞上「Tetra 的 DataStore 还没加载」的时序问题。之前只挂 {@code TagsUpdatedEvent}，
 * 导入在 DataStore 加载前跑，日志里能看到导入失败，玩家只看到「不洁之血没效果」。</p>
 */
@Mixin(targets = "se.mickelus.tetra.module.SchematicRegistry", remap = false)
public abstract class SchematicRegistryTetraMixin {

    @Inject(method = "setupSchematics", at = @At("RETURN"), remap = false)
    private void spearcore$importAfterTetraDataLoad(Map<ResourceLocation, SchematicDefinition> definitions,
                                                    CallbackInfo ci) {
        try {
            TetraFullerImportImpl.importAll();
        } catch (Throwable ignored) {
            // 导入自身会记录一次失败原因。
        }
    }
}
