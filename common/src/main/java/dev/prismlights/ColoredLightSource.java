package dev.prismlights;

import net.minecraft.block.BlockState;

/** A world block whose vanilla emission also contributes to the client RGB field. */
public interface ColoredLightSource {
    int prismColor();
    default boolean prismActive(BlockState state) { return true; }
    /** Existing sources preserve their established RGB radius/power. */
    default int prismEmission(BlockState state) { return 15; }
}
