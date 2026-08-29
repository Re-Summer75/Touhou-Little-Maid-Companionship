package com.laixia.maidintelligence.gametest.pathing;

import com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh
        .Surface;
import com.laixia.maidintelligence.feature.behavior.tlm.pathing.mesh
        .SurfaceCarver;
import com.laixia.maidintelligence.platform.resource.ModResources;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * 雕刻层的自证：**可走区域是面，不是点**。
 *
 * <p>点锚年代"柱占了格心 → 整格不可走"，挤缝只能靠特例。这里直接问几
 * 何：一格地面立着栅栏柱，雕出来应当是柱两侧的边带，且够宽的那几条留
 * 得下人。这条钉子不测她走不走得过去，只测**图看不看得见那条缝**——走
 * 得过去是后面的事，看不见就一切免谈。
 */
@GameTestHolder(ModResources.MOD_ID)
@PrefixGameTestTemplate(false)
@SuppressWarnings("null")
public final class SurfaceCarverGameTests {
    private static final int DECK = 2;

    private SurfaceCarverGameTests() {
    }

    /** 空地一格：整块 1×1 都能走。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void bareGroundCarvesOneWholeTile(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, DECK, 2), Blocks.STONE);
        List<Surface> pads = SurfaceCarver.carve(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, DECK + 1, 2)),
                0.6D, 1.8D);
        helper.assertTrue(pads.size() == 1,
                "空地该雕出整整一块，却成了 " + pads.size() + " 块");
        helper.assertTrue(pads.get(0).breadth() > 0.99D,
                "空地那块该有一格宽，实得 "
                        + String.format("%.2f", pads.get(0).breadth()));
        helper.succeed();
    }

    /**
     * 格心立柱：柱只占中间 0.375–0.625，雕完该剩下四条边带；每条净宽
     * 0.375，减去半个身位（0.3）后还剩 0.075——**塞得下人**，这正是玩
     * 家说的"像玩家一样贴边绕过去"。
     */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void aPostLeavesLanesAlongsideIt(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, DECK, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, DECK + 1, 2), Blocks.OAK_FENCE);
        List<Surface> pads = SurfaceCarver.carve(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, DECK + 1, 2)),
                0.6D, 1.8D);
        helper.assertTrue(!pads.isEmpty(),
                "柱旁该留得下贴边的路，却一条都没雕出来");
        double widest = pads.stream().mapToDouble(Surface::breadth)
                .max().orElse(0.0D);
        helper.assertTrue(widest >= 0.07D,
                "最宽的一条边带只有 " + String.format("%.3f", widest)
                        + "，柱旁本该留出 0.075");
        helper.succeed();
    }

    /** 门板立在格心：一整格高的竖片，靠一侧，另一侧仍是路。 */
    @GameTest(templateNamespace = "minecraft", template = "empty",
            batch = "pathing")
    public static void anUprightLidLeavesTheFarSide(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, DECK, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, DECK + 1, 2),
                Blocks.OAK_TRAPDOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block
                                .TrapDoorBlock.OPEN, true)
                        .setValue(net.minecraft.world.level.block
                                .TrapDoorBlock.HALF,
                                net.minecraft.world.level.block.state
                                        .properties.Half.BOTTOM)
                        .setValue(net.minecraft.world.level.block
                                .TrapDoorBlock.FACING,
                                net.minecraft.core.Direction.EAST));
        List<Surface> pads = SurfaceCarver.carve(helper.getLevel(),
                helper.absolutePos(new BlockPos(2, DECK + 1, 2)),
                0.6D, 1.8D);
        double widest = pads.stream().mapToDouble(Surface::breadth)
                .max().orElse(0.0D);
        helper.assertTrue(widest >= 0.2D,
                "门板贴着一面立，另一面该剩下半格多的路，实得 "
                        + String.format("%.3f", widest));
        helper.succeed();
    }
}
