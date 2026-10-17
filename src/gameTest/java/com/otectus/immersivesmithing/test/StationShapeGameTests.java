package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The stations' cached shapes after the 1.2 geometry pass: few boxes per facing once Minecraft decomposes them, the same
 * shape object on every call, and the surfaces the stations are used by (the forge's basin and mouth, the anvil's
 * striking face, the trough's basin) where the models draw them.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationShapeGameTests {
    /** Decomposed boxes per station and facing: the budget for the outline and collision of one station. */
    private static final int MAX_BOXES = 12;

    private static List<Block> stations() {
        return List.of(ModBlocks.SMITHS_FORGE.get(), ModBlocks.SMITHS_ANVIL.get(), ModBlocks.SMITHS_TROUGH.get(), ModBlocks.SMITHS_GRINDSTONE.get());
    }

    private static VoxelShape shape(GameTestHelper h, Block block, Direction facing) {
        BlockState state = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
        return state.getShape(h.getLevel(), BlockPos.ZERO, CollisionContext.empty());
    }

    @GameTest(template = "empty")
    public static void shapesAreSmallAndCached(GameTestHelper h) {
        StringBuilder counts = new StringBuilder();
        for (Block block : stations()) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                VoxelShape first = shape(h, block, facing);
                int boxes = first.toAabbs().size();
                counts.append(block.getDescriptionId()).append(' ').append(facing).append('=').append(boxes).append(' ');
                h.assertTrue(boxes <= MAX_BOXES, block.getDescriptionId() + " " + facing + " decomposes into " + boxes + " boxes");
                h.assertTrue(shape(h, block, facing) == first, block.getDescriptionId() + " builds no shape per call");
                h.assertTrue(first.bounds().minX >= 0 && first.bounds().maxX <= 1 && first.bounds().maxY <= 1, "inside its block");
            }
            // A quarter turn keeps the volume: the cached rotations are faithful.
            double north = volume(shape(h, block, Direction.NORTH));
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                h.assertTrue(Math.abs(volume(shape(h, block, facing)) - north) < 1.0E-6, block.getDescriptionId() + " " + facing + " keeps its volume");
            }
        }
        ImmersiveSmithing.LOGGER.info("Station shape boxes: {}", counts.toString().trim());
        h.succeed();
    }

    private static double volume(VoxelShape shape) {
        return shape.toAabbs().stream().mapToDouble(b -> b.getXsize() * b.getYsize() * b.getZsize()).sum();
    }

    private static boolean solidAt(VoxelShape shape, double x, double y, double z) {
        for (AABB box : shape.toAabbs()) {
            if (box.contains(x / 16, y / 16, z / 16)) return true;
        }
        return false;
    }

    @GameTest(template = "empty")
    public static void shapesKeepTheWorkingSurfaces(GameTestHelper h) {
        VoxelShape forge = shape(h, ModBlocks.SMITHS_FORGE.get(), Direction.NORTH);
        h.assertTrue(!solidAt(forge, 8, 12, 8), "The forge's basin is open above the hearth");
        h.assertTrue(solidAt(forge, 8, 9.5, 8) && solidAt(forge, 1.5, 12, 8), "The hearth and the rim stones are solid");
        h.assertTrue(!solidAt(forge, 8, 4, 2), "The firebox mouth is open at the front");
        VoxelShape anvil = shape(h, ModBlocks.SMITHS_ANVIL.get(), Direction.NORTH);
        h.assertTrue(Math.abs(anvil.max(Direction.Axis.Y) - 1.0) < 1.0E-6 && solidAt(anvil, 8, 15.5, 9),
                "The anvil's striking face is at the top of the block, where the renderer lays the workpiece");
        h.assertTrue(solidAt(anvil, 8, 14.5, 1), "The horn is part of the outline");
        VoxelShape trough = shape(h, ModBlocks.SMITHS_TROUGH.get(), Direction.NORTH);
        h.assertTrue(!solidAt(trough, 8, 6, 8) && solidAt(trough, 8, 2, 8), "The trough is a basin on a floor");
        VoxelShape grindstone = shape(h, ModBlocks.SMITHS_GRINDSTONE.get(), Direction.NORTH);
        h.assertTrue(solidAt(grindstone, 8, 10, 8) && solidAt(grindstone, 0.5, 10, 8), "The wheel and its axle are part of the outline");
        h.succeed();
    }

    private StationShapeGameTests() {}
}
