package com.licht_meilleur.blue_student.bed;

import com.licht_meilleur.blue_student.BlueStudentMod;
import com.licht_meilleur.blue_student.block.OnlyBedBlock;
import com.licht_meilleur.blue_student.student.StudentId;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

public class BedLinkEvents {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return ActionResult.PASS;
            if (!player.isSneaking()) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);

            // バニラベッド全色OK（BedBlockなら全部対象）
            if (!(state.getBlock() instanceof BedBlock)) return ActionResult.PASS;

            StudentId linking = BedLinkManager.getLinking(player.getUuid());
            if (linking == null) return ActionResult.PASS; // 紐づけモードじゃない

            // HEAD/FOOT と FACING を取得
            var facing = state.get(BedBlock.FACING);
            var part = state.get(BedBlock.PART);

            BlockPos footPos = (part == BedPart.FOOT) ? pos : pos.offset(facing.getOpposite());
            BlockPos headPos = footPos.offset(facing);

            // 置換：OnlyBed の FOOT/HEAD を同じ向きで置く
            BlockState footState = BlueStudentMod.ONLY_BED_BLOCK.getDefaultState()
                    .with(OnlyBedBlock.FACING, facing)
                    .with(OnlyBedBlock.PART, BedPart.FOOT)
                    .with(OnlyBedBlock.STUDENT, linking);

            BlockState headState = footState.with(OnlyBedBlock.PART, BedPart.HEAD);

            // バニラベッドを消して置換（ドロップ無し）
            world.breakBlock(footPos, false);
            world.breakBlock(headPos, false);

            world.setBlockState(footPos, footState, 3);
            world.setBlockState(headPos, headState, 3);

            // 紐づけモード解除（好みで。解除しないなら消してOK）
            BedLinkManager.clearLinking(player.getUuid());

            player.sendMessage(net.minecraft.text.Text.literal("Linked bed -> " + linking.asKey()), false);
            return ActionResult.SUCCESS;
        });
    }
}