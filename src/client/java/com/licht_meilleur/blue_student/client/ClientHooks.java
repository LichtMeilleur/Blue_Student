package com.licht_meilleur.blue_student.client;

import com.licht_meilleur.blue_student.client.screen.TabletScreen;
import net.minecraft.util.math.BlockPos;

public class ClientHooks {
    public static void openTabletScreen(BlockPos tabletPos) {
        TabletScreen.open(tabletPos);
    }
}