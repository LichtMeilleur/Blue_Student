package com.licht_meilleur.blue_student.bed;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.state.StudentWorldState;
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
import net.minecraft.util.math.Direction;

public class BedLinkEvents {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);

            if (!(state.getBlock() instanceof BedBlock)) return ActionResult.PASS;

            StudentId linking = BedLinkManager.getLinking(player.getUuid());
            if (linking == null) return ActionResult.PASS;

            Direction vanillaFacing = state.get(BedBlock.FACING);
            BedPart part = state.get(BedBlock.PART);

            // バニラのFOOT基準に揃える
            BlockPos vanillaFootPos = (part == BedPart.FOOT) ? pos : pos.offset(vanillaFacing.getOpposite());
            BlockPos vanillaHeadPos = vanillaFootPos.offset(vanillaFacing);

            // OnlyBed の向き（モデル都合で反転したいなら opposite のままでOK）
            //Direction onlyFacing = vanillaFacing.getOpposite();
            Direction onlyFacing = vanillaFacing;              // ★opposite を外す

            // OnlyBed の HEAD は onlyFacing 基準
            BlockPos onlyHeadPos = vanillaFootPos.offset(onlyFacing);

            // 既存OnlyBed除去（ドロップなし）
            BlockPos oldFoot = StudentWorldState.get(((ServerWorld)world).getServer()).getBed(linking);
            if (oldFoot == null) {
                oldFoot = BedLinkManager.getBedPos(player.getUuid(), linking); // 互換フォールバック
            }

            if (oldFoot != null) {
                BlockState old = world.getBlockState(oldFoot);
                if (old.isOf(BlueStudentMod.ONLY_BED_BLOCK) && old.contains(OnlyBedBlock.FACING)) {
                    Direction f = old.get(OnlyBedBlock.FACING);
                    BlockPos oldHead = oldFoot.offset(f);
                    int killFlags = Block.NOTIFY_ALL | Block.SKIP_DROPS;
                    world.setBlockState(oldHead, Blocks.AIR.getDefaultState(), killFlags);
                    world.setBlockState(oldFoot, Blocks.AIR.getDefaultState(), killFlags);
                }
            }


            // ★バニラベッドを「両方」ドロップなしで削除（重要：HEAD→FOOT）
            int killFlags = Block.NOTIFY_ALL | Block.SKIP_DROPS;
            world.setBlockState(vanillaHeadPos, Blocks.AIR.getDefaultState(), killFlags);
            world.setBlockState(vanillaFootPos, Blocks.AIR.getDefaultState(), killFlags);

            // OnlyBed 設置
            BlockState footState = BlueStudentMod.ONLY_BED_BLOCK.getDefaultState()
                    .with(OnlyBedBlock.FACING, onlyFacing)
                    .with(OnlyBedBlock.PART, BedPart.FOOT)
                    .with(OnlyBedBlock.STUDENT, linking);

            BlockState headState = footState.with(OnlyBedBlock.PART, BedPart.HEAD);

            world.setBlockState(vanillaFootPos, footState, Block.NOTIFY_ALL);
            world.setBlockState(onlyHeadPos, headState, Block.NOTIFY_ALL);

            // ★永続化込みで保存
            BedLinkManager.setBedPosAndPersist((ServerWorld) world, player.getUuid(), linking, vanillaFootPos);

            BedLinkManager.clearLinking(player.getUuid());
            player.sendMessage(Text.literal("Linked bed -> " + linking.asString()), false);
            return ActionResult.SUCCESS;

        });
    }
}