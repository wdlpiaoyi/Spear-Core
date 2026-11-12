package net.minecraft.spearcore.compat.tetra.mixin;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.compat.tetra.TetraSpearProfile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem;
import se.mickelus.tetra.module.data.SynergyData;

/**
 * 让模块化长矛能吃到 {@code data/tetra/synergies/spear/} 的协同数据。
 *
 * <p>Tetra 的 synergies 是各物品类在 {@code commonInit} 里用 {@code SynergyStore.getOrdered("sword/")}
 * 之类主动拉取的；{@code DynamicModularItem} 没有这一步，{@code ModularItem.synergies} 只有空数组初值。
 * 这里对 {@code modular_spear} 栈补上 {@code spear/} 目录的协同。</p>
 */
@Mixin(targets = "se.mickelus.tetra.items.modular.ModularItem", remap = false)
public abstract class ModularItemSynergyMixin {

    @Shadow
    protected SynergyData[] synergies;

    @Inject(method = "getAllSynergyData", at = @At("HEAD"), cancellable = true, remap = false)
    private void spearcore$spearSynergies(ItemStack stack, CallbackInfoReturnable<SynergyData[]> cir) {
        try {
            if (stack == null || stack.isEmpty()
                    || !TetraSpearProfile.isModularSpearArchetype(DynamicModularItem.getArchetypeKey(stack))) {
                return;
            }
            if (synergies != null && synergies.length > 0) {
                return;
            }
            SynergyData[] spearSynergies = getOrderedSynergies();
            if (spearSynergies != null) {
                cir.setReturnValue(spearSynergies);
            }
        } catch (Throwable ignored) {
            // 数据未就绪时退回 Tetra 原行为（无协同）。
        }
    }

    /** 反射结果缓存；失败只 warn 一次，避免每次效果聚合都 Class.forName + getMethod。 */
    @Unique
    private static Class<?> dataManagerClass;
    @Unique
    private static java.lang.reflect.Field instanceField;
    @Unique
    private static java.lang.reflect.Field synergyDataField;
    @Unique
    private static java.lang.reflect.Method getOrderedMethod;
    @Unique
    private static boolean synergyReflectionMissing;
    @Unique
    private static boolean synergyReflectionWarning;

    private static SynergyData[] getOrderedSynergies() throws ReflectiveOperationException {
        if (synergyReflectionMissing) {
            return null;
        }
        try {
            if (dataManagerClass == null) {
                dataManagerClass = Class.forName("se.mickelus.tetra.data.DataManager");
                instanceField = dataManagerClass.getField("instance");
                synergyDataField = dataManagerClass.getField("synergyData");
            }
            Object dataManager = instanceField.get(null);
            if (dataManager == null) {
                return null;
            }
            Object store = synergyDataField.get(dataManager);
            if (store == null) {
                return null;
            }
            if (getOrderedMethod == null) {
                getOrderedMethod = store.getClass().getMethod("getOrdered", String.class);
            }
            return (SynergyData[]) getOrderedMethod.invoke(store, "spear/");
        } catch (ReflectiveOperationException exception) {
            synergyReflectionMissing = true;
            if (!synergyReflectionWarning) {
                synergyReflectionWarning = true;
                SpearcoreMod.LOGGER.warn("[spearcore] 读取 Tetra synergyData 失败，模块化长矛将没有协同", exception);
            }
            throw exception;
        }
    }
}
