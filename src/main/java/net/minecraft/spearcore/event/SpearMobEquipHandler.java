package net.minecraft.spearcore.event;

import net.minecraft.spearcore.SpearcoreMod;
import net.minecraft.spearcore.config.SpearConfig;
import net.minecraft.spearcore.entity.ai.goal.SpearUseGoal;
import net.minecraft.spearcore.init.SpearCoreItems;
import net.minecraft.spearcore.item.SpearItem;
import net.minecraft.spearcore.util.SpearRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * 让怪物出生时携带长矛。
 *
 * <p>名单与概率由 {@code behavior.mobSpearEquipTable} 决定：
 * JAVA = 原版名单（僵尸/尸壳/僵尸村民拿铁矛，猪灵/僵尸猪灵拿金矛，约 5%）；
 * MODDED = 本模组原本的概率表 + 随机材质。
 * 掉落率由 {@code behavior.mobSpearDropChance} 决定：JAVA 8.5%（抢夺每级 +1%），MODDED 固定 15%。</p>
 *
 * <p><b>为什么不在 {@code Mob#finalizeSpawn} 里直接发装备：</b>子类 override 通常先
 * {@code super.finalizeSpawn(...)}，再调 {@code populateDefaultEquipmentSlots(...)}。如果直接挂在
 * {@code Mob#finalizeSpawn} 的 TAIL，矛会被子类的弓/铁斧/金剑覆盖（僵尸猪灵、骷髅、卫道士等）。
 * 所以在 finalize 阶段只做判定并排队，等 {@link EntityJoinLevelEvent}（晚于子类 populate、早于首 tick）
 * 再真正装备；从磁盘加载的实体会没有排队记录，不会二次发矛。</p>
 */
@Mod.EventBusSubscriber(modid = SpearcoreMod.MODID)
public final class SpearMobEquipHandler {

    private record PendingEquip(ItemStack stack, float dropChance) {
    }

    /** 原版名单：材质固定，概率统一约 5%；首次 finalizeSpawn 时才初始化，避免类加载期碰 EntityType。 */
    private static volatile Map<EntityType<?>, Supplier<Item>> VANILLA_SPEARS;

    /** 原版携带概率（wiki 未给出具体数值，取社区常用的 5% 近似值）。 */
    private static final float VANILLA_CHANCE = 0.05F;

    private static Map<EntityType<?>, Float> CHANCES;
    private static Map<EntityType<?>, Supplier<ItemStack>> FIXED_SPEARS;

    /** finalizeSpawn 决定、EntityJoinLevelEvent 落地；只在服务端线程读写。 */
    private static final Map<Mob, PendingEquip> PENDING = new WeakHashMap<>();

    private static void ensureTables() {
        if (VANILLA_SPEARS != null) {
            return;
        }
        synchronized (SpearMobEquipHandler.class) {
            if (VANILLA_SPEARS != null) {
                return;
            }
            Map<EntityType<?>, Supplier<Item>> vanilla = new HashMap<>();
            vanilla.put(EntityType.ZOMBIE, () -> SpearCoreItems.IRON_SPEAR.get());
            vanilla.put(EntityType.HUSK, () -> SpearCoreItems.IRON_SPEAR.get());
            vanilla.put(EntityType.ZOMBIE_VILLAGER, () -> SpearCoreItems.IRON_SPEAR.get());
            vanilla.put(EntityType.PIGLIN, () -> SpearCoreItems.GOLDEN_SPEAR.get());
            vanilla.put(EntityType.ZOMBIFIED_PIGLIN, () -> SpearCoreItems.GOLDEN_SPEAR.get());

            Map<EntityType<?>, Float> chances = new HashMap<>();
            chances.put(EntityType.ZOMBIE, 0.08f);
            chances.put(EntityType.HUSK, 0.10f);
            chances.put(EntityType.DROWNED, 0.06f);
            chances.put(EntityType.ZOMBIE_VILLAGER, 0.08f);
            chances.put(EntityType.SKELETON, 0.05f);
            chances.put(EntityType.STRAY, 0.05f);
            chances.put(EntityType.WITHER_SKELETON, 0.12f);
            chances.put(EntityType.VINDICATOR, 0.15f);
            chances.put(EntityType.PILLAGER, 0.08f);

            Map<EntityType<?>, Supplier<ItemStack>> fixed = new HashMap<>();
            fixed.put(EntityType.PIGLIN_BRUTE, () -> new ItemStack(SpearCoreItems.GOLDEN_SPEAR.get()));
            fixed.put(EntityType.PIGLIN, () -> new ItemStack(SpearCoreItems.GOLDEN_SPEAR.get()));
            fixed.put(EntityType.ZOMBIFIED_PIGLIN, () -> new ItemStack(SpearCoreItems.GOLDEN_SPEAR.get()));

            VANILLA_SPEARS = vanilla;
            CHANCES = chances;
            FIXED_SPEARS = fixed;
        }
    }

    private SpearMobEquipHandler() {
    }

    /**
     * 由 {@code MobSpearMixin} 在 {@code Mob#finalizeSpawn} 的 TAIL 调用。
     *
     * <p>只做判定：如果装备会被子类覆盖，就排队到 {@link EntityJoinLevelEvent}；已经被加入世界的
     * 实体（例如转换/指令路径）则立即装备。</p>
     */
    public static void onFinalizeSpawn(Mob self) {
        if (self.level().isClientSide) return;
        if (!(self instanceof Monster monster)) return;
        if (monster.getMainHandItem().getItem() instanceof SpearItem) return;
        ensureTables();

        // 原版装备掉落率 8.5%，抢夺每级 +1%（原版 Mob.dropCustomDeathLoot 会自动加）；MODDED 固定 15%。
        float dropChance = SpearRules.usesVanillaDropChance() ? 0.085F : 0.15F;

        ItemStack spearStack;
        if (SpearRules.usesVanillaMobTable()) {
            Supplier<Item> vanilla = VANILLA_SPEARS.get(monster.getType());
            if (vanilla == null) return;
            // 原版长矛被关掉时怪物也不带，避免出现"拿得到但创造栏里没有"的矛。
            if (!SpearConfig.ENABLE_VANILLA_SPEARS.get()) return;
            if (monster.getRandom().nextFloat() >= VANILLA_CHANCE) return;
            spearStack = new ItemStack(vanilla.get());
        } else {
            List<Item> enabledSpears = getEnabledSpears();
            if (enabledSpears.isEmpty()) return;

            Float chance = CHANCES.get(monster.getType());
            if (chance == null) return;
            if (monster.getRandom().nextFloat() >= chance) return;

            Supplier<ItemStack> fixed = FIXED_SPEARS.get(monster.getType());
            spearStack = fixed != null
                    ? fixed.get()
                    : new ItemStack(enabledSpears.get(monster.getRandom().nextInt(enabledSpears.size())));
        }

        SpearMobEquipEvent equipEvent = new SpearMobEquipEvent(monster, spearStack, dropChance);
        MinecraftForge.EVENT_BUS.post(equipEvent);
        if (equipEvent.isCanceled() || equipEvent.getSpearStack().isEmpty()) return;

        if (monster.isAddedToWorld()) {
            equip(monster, equipEvent.getSpearStack(), dropChance);
        } else {
            PENDING.put(monster, new PendingEquip(equipEvent.getSpearStack(), dropChance));
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof Monster monster)) return;
        PendingEquip pending = PENDING.remove(monster);
        if (pending == null) return;
        if (monster.getMainHandItem().getItem() instanceof SpearItem) return;
        equip(monster, pending.stack(), pending.dropChance());
    }

    private static void equip(Monster monster, ItemStack spearStack, float dropChance) {
        monster.setItemSlot(EquipmentSlot.MAINHAND, spearStack);
        monster.setDropChance(EquipmentSlot.MAINHAND, dropChance);
        monster.goalSelector.addGoal(1, new SpearUseGoal<>(monster, 1.0, 1.0, 4.5f));
    }

    private static List<Item> getEnabledSpears() {
        List<Item> list = new ArrayList<>();
        if (!SpearConfig.ENABLE_VANILLA_SPEARS.get()) return list;
        list.add(SpearCoreItems.WOODEN_SPEAR.get());
        list.add(SpearCoreItems.STONE_SPEAR.get());
        list.add(SpearCoreItems.IRON_SPEAR.get());
        list.add(SpearCoreItems.GOLDEN_SPEAR.get());
        list.add(SpearCoreItems.DIAMOND_SPEAR.get());
        list.add(SpearCoreItems.NETHERITE_SPEAR.get());
        if (SpearConfig.ENABLE_COPPER_SPEAR.get()) list.add(SpearCoreItems.COPPER_SPEAR.get());
        return list;
    }

    /** GameTest 专用：绕过概率，直接排队一次装备，验证 EntityJoinLevelEvent 的落地路径。 */
    public static void queueForTest(Mob mob, ItemStack stack, float dropChance) {
        PENDING.put(mob, new PendingEquip(stack, dropChance));
    }
}
