package com.seedcrack.stage1;

/**
 * Camera orientation alignment when observing the cliffside house and strip mine.
 */
public enum Orientation {
    SOUTH(1, 1, 0, -1, 1),
    NORTH(-1, -1, 2, 1, -1);

    public final int xSign;
    public final int zSign;
    public final int rotationVariantOffset;
    public final int dxRight;
    public final int dzSign;

    Orientation(int xSign, int zSign, int rotationVariantOffset, int dxRight, int dzSign) {
        this.xSign = xSign;
        this.zSign = zSign;
        this.rotationVariantOffset = rotationVariantOffset;
        this.dxRight = dxRight;
        this.dzSign = dzSign;
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
     * Computes tunnel world X given tunnel origin x0 and camera-relative dx (-1=left, 0=center, +1=right).
     */
    public int tunnelWorldX(int x0, int dxCam) {
        return x0 + (dxCam * dxRight);
    }

    /**
     * Computes tunnel world Z given tunnel origin z0 and depth d.
     */
    public int tunnelWorldZ(int z0, int d) {
        return z0 + (d * dzSign);
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
