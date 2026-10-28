package com.seedcrack.stage1;

public class BlockObservation {

    public enum Type {
        SAND_TOP(4),
        GRASS_TOP(4),
        STONE_TOP(4),
        STONE_SIDE(4),
        BEDROCK_TOP(4),
        BEDROCK_PRESENT(2),
        BEDROCK_ABSENT(2),
        POPPY_OFFSET(16);

        public final int modulus;

        Type(int modulus) {
            this.modulus = modulus;
        }
    }

    private final int dx;
    private final int y;
    private final int dz;
    private final Type type;
    private final int observedVariant;
    private final int observedOffsetX;
    private final int observedOffsetZ;
    private final String comment;

    public BlockObservation(int dx, int y, int dz, Type type, int observedVariant, int observedOffsetX, int observedOffsetZ, String comment) {
        this.dx = dx;
        this.y = y;
        this.dz = dz;
        this.type = type;
        this.observedVariant = observedVariant;
        this.observedOffsetX = observedOffsetX;
        this.observedOffsetZ = observedOffsetZ;
        this.comment = comment == null ? "" : comment;
    }

    public int getDx() { return dx; }
    public int getY() { return y; }
    public int getDz() { return dz; }
    public Type getType() { return type; }
    public int getObservedVariant() { return observedVariant; }
    public int getObservedOffsetX() { return observedOffsetX; }
    public int getObservedOffsetZ() { return observedOffsetZ; }
    public String getComment() { return comment; }

    @Override
    public String toString() {
        return String.format("[%s at dx=%d, y=%d, dz=%d (var=%d)]", type, dx, y, dz, observedVariant);
    }
}
