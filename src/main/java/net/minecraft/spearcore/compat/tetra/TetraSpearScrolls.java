package net.minecraft.spearcore.compat.tetra;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 模块化长矛的「卷轴」定义。
 *
 * <p>Tetra 的卷轴不是独立的物品类型，而是 <b>{@code tetra:scroll_rolled} 这个方块物品 + 一段 NBT</b>：
 * {@code BlockEntityTag.data} 里放一个 ScrollData（{@code key}/{@code schematics}/{@code intricate}/
 * {@code material}/{@code ribbon}/{@code glyphs}），把卷轴方块放在工作台旁边时，
 * 工作台会从附近的 {@code ISchematicProviderBlock} 收集 {@code schematics} 列表塞进
 * {@code CraftingContext.unlocks}，配方里 {@code {"type": "tetra:locked", "key": ...}} 的 schematic
 * 才变成可制作。</p>
 *
 * <p>因此本模组新部件的配方都是 locked 的，必须先摆上对应卷轴。{@code intricate = true} 的卷轴
 * （Tetra 里叫「复杂原理图」）必须<b>正对工作台上方一格</b>才生效（{@code ScrollBlock#canUnlockSchematics}），
 * 普通卷轴摆在附近即可。</p>
 *
 * <p>这个类不依赖任何 {@code se.mickelus.tetra} 类，未装 Tetra 的 GameTest 服务端也能加载，
 * 测试据此交叉校验「卷轴声明的 schematic」与「数据文件里的 locked key」是否一致。</p>
 */
public final class TetraSpearScrolls {
    /** Tetra 的卷轴物品 id（方块物品，放置后是 rolled scroll 方块）。 */
    public static final ResourceLocation SCROLL_ITEM = new ResourceLocation("tetra", "scroll_rolled");

    /**
     * 一条卷轴定义，字段与 Tetra 的 ScrollData 对应。
     *
     * <p>不给 {@code details}：Tetra 的 {@code details} 是「右键打开长卷」的画面 id（GoetyRevelation 那类联动才用），
     * 原理图卷轴本来就不该有，缺失时右键就是正常的方块放置。</p>
     */
    public record Definition(String key, boolean intricate, int material, String ribbon,
                             int[] glyphs, String[] schematics) {
    }

    /** 槊头：重装长枪矛头，普通卷轴（附近即可）。 */
    public static final Definition SHUO_HEAD = new Definition(
            "spear/shuo_head", false, 2, "b58a4a",
            new int[]{2, 3, 5, 6}, new String[]{"tetra:spear/shuo_head"});

    /** 积竹木柲：复合层压矛杆，普通卷轴。 */
    public static final Definition COMPOSITE_SHAFT = new Definition(
            "spear/composite_shaft", false, 1, "8fa24a",
            new int[]{3, 4, 2, 7}, new String[]{"tetra:spear/composite_shaft_1"});

    /** 三尖两刃刀：二郎神制式，复杂原理图，必须摆在加工台正上方。 */
    public static final Definition THREE_POINT_BLADE = new Definition(
            "spear/three_point_blade", true, 0, "7a6fd0",
            new int[]{1, 7, 6, 8}, new String[]{"tetra:spear/three_point_blade"});

    public static final List<Definition> ALL = List.of(SHUO_HEAD, COMPOSITE_SHAFT, THREE_POINT_BLADE);

    private TetraSpearScrolls() {
    }

    /**
     * 按定义拼出一个卷轴物品栈；Tetra 未安装时返回 {@link ItemStack#EMPTY}（调用方负责守卫）。
     */
    public static ItemStack create(Definition definition) {
        Item item = BuiltInRegistries.ITEM.get(SCROLL_ITEM);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.setTag(buildTag(definition));
        return stack;
    }

    /** 拼 {@code BlockEntityTag.data[0]}，独立出来便于测试不需要注册表即可校验结构。 */
    public static CompoundTag buildTag(Definition definition) {
        CompoundTag scroll = new CompoundTag();
        scroll.putString("key", definition.key());
        ListTag schematics = new ListTag();
        for (String schematic : definition.schematics()) {
            schematics.add(StringTag.valueOf(schematic));
        }
        scroll.put("schematics", schematics);
        scroll.putBoolean("intricate", definition.intricate());
        scroll.putInt("material", definition.material());
        // Tetra 的 ScrollData#readRibbonFast 是按 16 进制字符串解析的，写成 int 会被读成 0
        scroll.putString("ribbon", definition.ribbon());
        ListTag glyphs = new ListTag();
        for (int glyph : definition.glyphs()) {
            glyphs.add(IntTag.valueOf(glyph));
        }
        scroll.put("glyphs", glyphs);

        ListTag data = new ListTag();
        data.add(scroll);
        CompoundTag blockEntity = new CompoundTag();
        blockEntity.put("data", data);
        CompoundTag root = new CompoundTag();
        root.put("BlockEntityTag", blockEntity);
        return root;
    }
}
