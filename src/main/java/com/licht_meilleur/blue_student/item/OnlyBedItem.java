package com.licht_meilleur.blue_student.item;

import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.student.StudentId;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class OnlyBedItem extends Item {
    private final OnlyBedBlock block;
    private final StudentId student;

    public OnlyBedItem(OnlyBedBlock block, StudentId student, Settings settings) {
        super(settings);
        this.block = block;
        this.student = student;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();

        BlockPos footPos = ctx.getBlockPos().offset(ctx.getSide());
        Direction facing = ctx.getPlayer() != null ? ctx.getPlayer().getHorizontalFacing() : Direction.NORTH;
        BlockPos headPos = footPos.offset(facing);

        // 置けるか
        ItemPlacementContext footCtx = new ItemPlacementContext(ctx.getPlayer(), ctx.getHand(), ctx.getStack(),
                new net.minecraft.util.hit.BlockHitResult(ctx.getHitPos(), ctx.getSide(), footPos, false));
        ItemPlacementContext headCtx = new ItemPlacementContext(ctx.getPlayer(), ctx.getHand(), ctx.getStack(),
                new net.minecraft.util.hit.BlockHitResult(ctx.getHitPos(), ctx.getSide(), headPos, false));

        if (!world.getBlockState(footPos).canReplace(footCtx)) return ActionResult.FAIL;
        if (!world.getBlockState(headPos).canReplace(headCtx)) return ActionResult.FAIL;


        BlockState foot = block.getDefaultState()
                .with(OnlyBedBlock.FACING, facing)
                .with(OnlyBedBlock.PART, BedPart.FOOT)
                .with(OnlyBedBlock.STUDENT, student);

        BlockState head = foot.with(OnlyBedBlock.PART, BedPart.HEAD);

        if (!world.isClient) {
            world.setBlockState(footPos, foot, 3);
            world.setBlockState(headPos, head, 3);

            if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) {
                ctx.getStack().decrement(1);
            }
        }

        return ActionResult.SUCCESS;
    }
}