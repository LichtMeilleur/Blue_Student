package com.licht_meilleur.blue_student.block;

import com.licht_meilleur.blue_student.block.entity.OnlyBedBlockEntity;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class OnlyBedBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final EnumProperty<BedPart> PART = Properties.BED_PART;

    // StudentId を blockstate に持たせる（テクスチャ差し替え用）
    public static final EnumProperty<StudentId> STUDENT =
            EnumProperty.of("student", StudentId.class);

    public OnlyBedBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState()
                .with(FACING, net.minecraft.util.math.Direction.NORTH)
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
        // GeckoLibで描画する前提なら INVISIBLE 推奨（バニラモデルを出さない）
        return BlockRenderType.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        // どっち側にも置いとくと楽（レンダラ/テクスチャ参照が簡単）
        return new OnlyBedBlockEntity(pos, state);
    }

    // --- 設置（アイテムから置けるようにもしておく）
    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        var facing = ctx.getHorizontalPlayerFacing().getOpposite();
        BlockPos pos = ctx.getBlockPos();
        World world = ctx.getWorld();

        BlockPos otherPos = pos.offset(facing);
        if (!world.getBlockState(otherPos).canReplace(ctx)) return null;

        // ここでは STUDENT はデフォルト（必要ならアイテムNBTで変えられる）
        return this.getDefaultState().with(FACING, facing).with(PART, BedPart.FOOT);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer,
                         net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (world.isClient) return;

        // HEAD 側も置く
        BlockPos headPos = pos.offset(state.get(FACING));
        BlockState headState = state.with(PART, BedPart.HEAD);
        world.setBlockState(headPos, headState, Block.NOTIFY_ALL);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        // HEAD/FOOTの整合チェック
        var part = state.get(PART);
        var facing = state.get(FACING);

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
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // 片方壊したらもう片方も壊す
        BedPart part = state.get(PART);
        BlockPos otherPos = (part == BedPart.FOOT) ? pos.offset(state.get(FACING)) : pos.offset(state.get(FACING).getOpposite());
        BlockState other = world.getBlockState(otherPos);

        if (other.isOf(this) && other.get(PART) != part) {
            world.breakBlock(otherPos, false, player); // false: もう片方はドロップさせない
        }

        super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, net.minecraft.util.math.Direction dir,
                                                BlockState neighborState, WorldAccess world,
                                                BlockPos pos, BlockPos neighborPos) {

        var part = state.get(PART);
        var facing = state.get(FACING);

        // ベッドと同様に、相方が無いなら自壊
        if (part == BedPart.FOOT && dir == facing) {
            if (!neighborState.isOf(this) || neighborState.get(PART) != BedPart.HEAD) {
                return Blocks.AIR.getDefaultState();
            }
        }
        if (part == BedPart.HEAD && dir == facing.getOpposite()) {
            if (!neighborState.isOf(this) || neighborState.get(PART) != BedPart.FOOT) {
                return Blocks.AIR.getDefaultState();
            }
        }

        return super.getStateForNeighborUpdate(state, dir, neighborState, world, pos, neighborPos);
    }
}