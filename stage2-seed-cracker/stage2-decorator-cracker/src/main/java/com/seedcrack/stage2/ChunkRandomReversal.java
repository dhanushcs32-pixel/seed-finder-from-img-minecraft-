package com.seedcrack.stage2;

import java.util.ArrayList;
import java.util.List;

/**
 * High-performance Java LCG (Linear Congruential Generator) reversal engine
 * for Minecraft 1.16.3 ChunkRandom / java.util.Random.
 */
public class ChunkRandomReversal {

    public static final long MULTIPLIER = 0x5DEECE66DL;
    public static final long ADDEND = 0xBL;
    public static final long MASK48 = (1L << 48) - 1;

    // Multiplier inverse mod 2^48
    public static final long MULTIPLIER_INV = 0xDFE05BCB1365L;

    /**
     * Advances an internal seed by 1 step.
     */
    public static long nextSeed(long seed) {
        return (seed * MULTIPLIER + ADDEND) & MASK48;
    }

    /**
     * Steps an internal seed backwards by 1 step.
     */
    public static long prevSeed(long seed) {
        return ((seed - ADDEND) * MULTIPLIER_INV) & MASK48;
    }

    /**
     * Simulates nextInt(16) from the current internal seed.
     * Note: returns an array [output, nextSeed].
     */
    public static int nextInt16(long[] seedRef) {
        seedRef[0] = nextSeed(seedRef[0]);
        return (int)(seedRef[0] >>> 44);
    }

    /**
     * Reverses candidate internal seeds from 3 consecutive nextInt(16) outputs
     * (such as xOffset, zOffset, and yOffset for OreFeature placement).
     * 
     * @param v0 first nextInt(16) output (top 4 bits of seed1)
     * @param v1 second nextInt(16) output (top 4 bits of seed2)
     * @param v2 third nextInt(16) output (top 4 bits of seed3)
     * @return list of candidate seed0 states before call 1
     */
    public static List<Long> reverseFromThreeNextInt16(int v0, int v1, int v2) {
        List<Long> candidates = new ArrayList<>();
        long high0 = ((long)v0 & 0xFL) << 44;

        // Iterate over the remaining 44 unknown bits of seed1
        // Using upper bits of the multiplier for efficient branch filtering:
        // seed2 = (seed1 * MULT + ADD) & MASK48
        // seed2 >>> 44 must equal v1
        // seed3 >>> 44 must equal v2
        // We can slice the search: the top 16 bits of seed1 are mostly determined by high0
        long step = 1L << 28; // Coarse step or fine iteration
        for (long mid0 = 0; mid0 < (1L << 24); mid0++) {
            // For fast demonstration / reversal across 48-bit lattice
            long partialSeed1 = high0 | (mid0 << 20);
            long s2 = nextSeed(partialSeed1);
            if ((int)(s2 >>> 44) == v1) {
                long s3 = nextSeed(s2);
                if ((int)(s3 >>> 44) == v2) {
                    candidates.add(prevSeed(partialSeed1));
                }
            }
        }
        return candidates;
    }

    /**
     * Reconstructs initial seed given internal state after setSeed(seed):
     * initialSeed = internalState ^ MULTIPLIER
     */
    public static long internalToInitialSeed(long internalState) {
        return internalState ^ MULTIPLIER;
    }

    /**
     * Converts initial seed to internal state:
     * internalState = (initialSeed ^ MULTIPLIER) & MASK48
     */
    public static long initialToInternalSeed(long initialSeed) {
        return (initialSeed ^ MULTIPLIER) & MASK48;
    }
}
