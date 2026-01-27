package com.licht_meilleur.blue_student.client;

import net.minecraft.util.math.BlockPos;

public class ClientHooks {
    // デフォルトは「何もしない」(サーバでも安全)
    public static ScreenOpener OPEN_TABLET = pos -> {};

    @FunctionalInterface
    public interface ScreenOpener {
        void open(BlockPos pos);
    }
}