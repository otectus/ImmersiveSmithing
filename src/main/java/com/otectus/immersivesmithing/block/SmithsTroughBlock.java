package com.otectus.immersivesmithing.block;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.SigningService;
import net.minecraft.world.SimpleContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.SmithingQuality;
import com.otectus.immersivesmithing.material.RecyclingData;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import com.otectus.immersivesmithing.work.TroughActions;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.otectus.immersivesmithing.util.StationEffects;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Smith's Trough: water for quenching. A shaped piece is lowered in, held while the hiss subsides and lifted out; the
 * trough holds it in between ({@link TroughActions}).
 */
public class SmithsTroughBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Floor on its feet, staves and ends round an open basin (1.2 geometry; the straps are painted). */
    private static final StationShape SHAPE = new StationShape(Shapes.or(
            Block.box(1, 0, 3, 15, 3, 13),
            Block.box(1, 3, 3, 15, 9, 4), Block.box(1, 3, 12, 15, 9, 13),
            Block.box(1, 3, 4, 2, 9, 12), Block.box(14, 3, 4, 15, 9, 12)));

    public SmithsTroughBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.facing(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmithsTroughBlockEntity(pos, state);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SmithsTroughBlockEntity trough ? Math.round(trough.fillFraction() * 15) : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SmithsTroughBlockEntity trough) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        interact(sp, trough, held, hand);
        return InteractionResult.CONSUME;
    }

    private void interact(ServerPlayer player, SmithsTroughBlockEntity trough, ItemStack held, InteractionHand hand) {
        if (trough.occupied() && com.otectus.immersivesmithing.work.WorkLeases.heldByOther(player.level(), trough.getBlockPos(), player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        TroughActions.use(player, trough, hand, held);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.SMITHS_TROUGH.get(), SmithsTroughBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.SMITHS_TROUGH.get(), SmithsTroughBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof SmithsTroughBlockEntity trough) {
            com.otectus.immersivesmithing.work.WorkLeases.onStationGone(level, pos);
            trough.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
