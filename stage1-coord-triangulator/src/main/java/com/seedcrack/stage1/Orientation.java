package com.seedcrack.stage1;

/**
 * Camera orientation alignment when observing the cliffside house and strip mine.
 */
public enum Orientation {
    SOUTH(1, 1, 0),
    NORTH(-1, -1, 2);

    public final int xSign;
    public final int zSign;
    public final int rotationVariantOffset;

    Orientation(int xSign, int zSign, int rotationVariantOffset) {
        this.xSign = xSign;
        this.zSign = zSign;
        this.rotationVariantOffset = rotationVariantOffset;
    }

    /**
     * Converts relative (dx, dz) to world coordinates given base position.
     */
    public int toWorldX(int baseX, int dx) {
        return baseX + (xSign * dx);
    }

    public int toWorldZ(int baseZ, int dz) {
        return baseZ + (zSign * dz);
    }

    /**
     * Adjusts expected texture rotation variant for this orientation.
     */
    public int adjustExpectedVariant(int observedVariant, int mod) {
        return (observedVariant + rotationVariantOffset) % mod;
    }

    /**
     * Adjusts sub-block flower offset index when rotated 180 degrees.
     */
    public int adjustExpectedOffsetX(int observedOffsetX) {
        return this == NORTH ? (15 - observedOffsetX) : observedOffsetX;
    }

    public int adjustExpectedOffsetZ(int observedOffsetZ) {
        return this == NORTH ? (15 - observedOffsetZ) : observedOffsetZ;
    }
}
