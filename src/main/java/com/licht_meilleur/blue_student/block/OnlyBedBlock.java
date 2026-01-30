package com.licht_meilleur.blue_student.block;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class OnlyBedBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = Properties.BED_PART;

    public static final EnumProperty<StudentId> STUDENT =
            EnumProperty.of("student", StudentId.class);

    private static final VoxelShape BED_SHAPE =
            Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 9.0, 16.0); // 9/16 高さ（バニラ寄せ）

    public OnlyBedBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, BedPart.FOOT)
                .with(STUDENT, StudentId.SHIROKO)
        );
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, STUDENT);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(PART) == BedPart.FOOT ? new OnlyBedBlockEntity(pos, state) : null;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BedPart part = state.get(PART);
        Direction facing = state.get(FACING);

        if (part == BedPart.FOOT) {
            BlockPos headPos = pos.offset(facing);
            return world.getBlockState(headPos).isReplaceable();
        } else {
            BlockPos footPos = pos.offset(facing.getOpposite());
            BlockState foot = world.getBlockState(footPos);
            return foot.isOf(this) && foot.get(PART) == BedPart.FOOT;
        }
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction dir,
                                                BlockState neighborState, WorldAccess world,
                                                BlockPos pos, BlockPos neighborPos) {

        // ★相方が存在しないなら自分も消える（dir/neighborPosに依存しない）
        BlockPos other = findOtherHalfPos(world, pos, state);
        if (other == null) {
            return Blocks.AIR.getDefaultState();
        }

        return state; // superに行かずそのまま保持が安全
    }




    /**
     * ★相方座標を「両候補チェック」で確実に取得する
     * - 向き反転や置換経路で、FACINGと配置の対応がズレても拾える
     */
    private @Nullable BlockPos findOtherHalfPos(WorldAccess world, BlockPos pos, BlockState state) {
        if (!state.isOf(this)) return null;

        BedPart part = state.get(PART);
        Direction facing = state.get(FACING);

        // 通常候補
        BlockPos cand1 = (part == BedPart.FOOT) ? pos.offset(facing) : pos.offset(facing.getOpposite());
        BlockState s1 = world.getBlockState(cand1);
        if (s1.isOf(this) && s1.contains(PART) && s1.get(PART) != part) return cand1;

        // 逆候補（反転事故対策）
        BlockPos cand2 = (part == BedPart.FOOT) ? pos.offset(facing.getOpposite()) : pos.offset(facing);
        BlockState s2 = world.getBlockState(cand2);
        if (s2.isOf(this) && s2.contains(PART) && s2.get(PART) != part) return cand2;

        return null;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos otherPos = findOtherHalfPos(world, pos, state);
            if (otherPos != null) {
                world.breakBlock(otherPos, false); // dropなし
            }
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) {
            super.onStateReplaced(state, world, pos, newState, moved);
            return;
        }

        if (!world.isClient) {
            BlockPos otherPos = findOtherHalfPos(world, pos, state);
            if (otherPos != null) {
                world.breakBlock(otherPos, false); // dropなし
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state,
                           @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.afterBreak(world, player, pos, state, blockEntity, tool);

        // ★ドロップはFOOTだけ
        if (!world.isClient && state.get(PART) == BedPart.FOOT) {
            StudentId sid = state.get(STUDENT);
            dropStack(world, pos, new ItemStack(BlueStudentMod.getBedItemFor(sid)));
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return BED_SHAPE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return BED_SHAPE;
    }
}