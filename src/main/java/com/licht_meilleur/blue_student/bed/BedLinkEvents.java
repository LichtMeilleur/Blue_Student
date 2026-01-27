package com.licht_meilleur.blue_student.bed;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.student.StudentId;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;

public class BedLinkEvents {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);

            // バニラベッド全色OK
            if (!(state.getBlock() instanceof BedBlock)) return ActionResult.PASS;

            StudentId linking = BedLinkManager.getLinking(player.getUuid());
            if (linking == null) return ActionResult.PASS;

            var facing = state.get(BedBlock.FACING);
            var part   = state.get(BedBlock.PART);

            // クリックがHEADでもFOOTでも、FOOT基準に揃える
            BlockPos footPos = (part == BedPart.FOOT) ? pos : pos.offset(facing.getOpposite());
            BlockPos headPos = footPos.offset(facing);

            // ★向きが逆ならここで反転（モデルの向きがバニラと逆なため）
            var bedFacing = facing.getOpposite();

            // ★既に同じ生徒の専用ベッドがあるなら消して1つにする
            BlockPos oldFoot = BedLinkManager.getBedPos(player.getUuid(), linking);
            if (oldFoot != null) {
                BlockState old = world.getBlockState(oldFoot);
                if (old.isOf(BlueStudentMod.ONLY_BED_BLOCK)) {
                    // 片側だけ消せば onStateReplaced が相方も消す
                    world.setBlockState(oldFoot, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }

            BlockState footState = BlueStudentMod.ONLY_BED_BLOCK.getDefaultState()
                    .with(OnlyBedBlock.FACING, bedFacing)
                    .with(OnlyBedBlock.PART, BedPart.FOOT)
                    .with(OnlyBedBlock.STUDENT, linking);

            BlockState headState = footState.with(OnlyBedBlock.PART, BedPart.HEAD);

            int flagsNoDrops = Block.NOTIFY_ALL | Block.SKIP_DROPS;

// ① 先に HEAD を消す（ベッドの相方処理が走っても落ちないように）
            world.setBlockState(headPos, Blocks.AIR.getDefaultState(), flagsNoDrops);
            world.setBlockState(footPos, Blocks.AIR.getDefaultState(), flagsNoDrops);

// ② 専用ベッドを設置
            world.setBlockState(footPos, footState, Block.NOTIFY_ALL);
            world.setBlockState(headPos, headState, Block.NOTIFY_ALL);

// 記録
            BedLinkManager.setBedPos(player.getUuid(), linking, footPos);
            BedLinkManager.clearLinking(player.getUuid());
            player.sendMessage(Text.literal("Linked bed -> " + linking.asString()), false);
            return ActionResult.SUCCESS;
        });
    }
}