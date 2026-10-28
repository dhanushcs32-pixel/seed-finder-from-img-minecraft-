package com.seedcrack.stage2;

import java.util.Random;

/**
 * Exact implementation of Minecraft 1.16.3 ChunkRandom.
 */
public class ChunkRandom extends Random {

    public static final long MULTIPLIER = 0x5DEECE66DL;
    public static final long ADDEND = 0xBL;
    public static final long MASK48 = (1L << 48) - 1;

    public ChunkRandom() {
        super();
    }

    public ChunkRandom(long seed) {
        super(seed);
    }

    /**
     * Vanilla 1.16.3 ChunkRandom.setPopulationSeed
     */
    public long setPopulationSeed(long worldSeed, int blockX, int blockZ) {
        this.setSeed(worldSeed);
        long a = this.nextLong() | 1L;
        long b = this.nextLong() | 1L;
        long popSeed = (long)blockX * a + (long)blockZ * b ^ worldSeed;
        this.setSeed(popSeed);
        return popSeed;
    }

    /**
     * Vanilla 1.16.3 ChunkRandom.setDecoratorSeed
     * popSeed + index + (10000 * step)
     */
    public long setDecoratorSeed(long popSeed, int index, int step) {
        long decoratorSeed = popSeed + (long)index + (long)(10000 * step);
        this.setSeed(decoratorSeed);
        return decoratorSeed;
    }

    /**
     * Vanilla 1.16.3 Feature Seed (for ConfiguredFeatures)
     */
    public void setFeatureSeed(long popSeed, int index, int step) {
        setDecoratorSeed(popSeed, index, step);
    }
}
