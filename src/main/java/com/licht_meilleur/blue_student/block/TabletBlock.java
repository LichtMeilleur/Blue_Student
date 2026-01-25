package com.licht_meilleur.blue_student.block;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.ShirokoEntity;
import com.licht_meilleur.blue_student.block.entity.TabletBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

public class TabletBlock extends BlockWithEntity {
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;

    // ざっくり “薄い板 + スタンド” みたいな当たり判定（好きに調整OK）
    // 下段は床から 0~1 の範囲
    private static final VoxelShape LOWER_SHAPE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);
    // 上段は上のブロック1個ぶん（0~1の範囲で表現）
    private static final VoxelShape UPPER_SHAPE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);

    public TabletBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        // BlockEntityは下段にだけ置く（上段はBEなし）
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new TabletBlockEntity(pos, state) : null;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        // GeckoLibで描画するのでバニラモデルは消す（モデル/ブロックステートは登録のため最低限だけ用意）
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // 当たり判定も同じでOK（すり抜けさせたいなら OUTLINEだけにしてCOLLISIONは空にしてもいい）
        return state.get(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = world.getBlockState(pos.down());
            return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
        }
        return super.canPlaceAt(state, world, pos);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        World world = ctx.getWorld();
        // 上に1ブロック空きが必要
        if (pos.getY() >= world.getTopY() - 1) return null;
        if (!world.getBlockState(pos.up()).canReplace(ctx)) return null;

        return this.getDefaultState().with(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // 片方壊したら両方消す
        DoubleBlockHalf half = state.get(HALF);
        BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.up() : pos.down();
        BlockState other = world.getBlockState(otherPos);
        if (other.isOf(this) && other.get(HALF) != half) {
            world.breakBlock(otherPos, false, player);
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, net.minecraft.util.math.Direction dir, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.get(HALF);

        // 上下関係が崩れたら自壊
        if (dir == net.minecraft.util.math.Direction.UP && half == DoubleBlockHalf.LOWER) {
            if (!neighborState.isOf(this) || neighborState.get(HALF) != DoubleBlockHalf.UPPER) {
                return Blocks.AIR.getDefaultState();
            }
        }
        if (dir == net.minecraft.util.math.Direction.DOWN && half == DoubleBlockHalf.UPPER) {
            if (!neighborState.isOf(this) || neighborState.get(HALF) != DoubleBlockHalf.LOWER) {
                return Blocks.AIR.getDefaultState();
            }
        }
        return super.getStateForNeighborUpdate(state, dir, neighborState, world, pos, neighborPos);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {

        if (world.isClient) return ActionResult.SUCCESS;

        // 上段クリックでも下段を基準に動作させる
        BlockPos basePos = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;

        // 右クリックでシロコ召喚（ひとまず単純）
        if (world instanceof ServerWorld sw) {
            ShirokoEntity e = BlueStudentMod.SHIROKO.create(sw);
            if (e != null) {
                double x = basePos.getX() + 0.5;
                double y = basePos.getY() + 1.0;
                double z = basePos.getZ() + 0.5;
                e.refreshPositionAndAngles(x, y, z, player.getYaw(), 0f);
                sw.spawnEntity(e);
            }
        }

        return ActionResult.CONSUME;
    }
}