package com.seedcrack.stage1;

import java.util.Random;

/**
 * High-performance Minecraft Java Edition 1.16.3 world-generation reverse-engineering math helper.
 */
public final class MathHelper {

    private MathHelper() {}

    /**
     * Vanilla 1.13-1.21.1 coordinate random hash (MathHelper.getCoordinateRandom).
     */
    public static long getCoordinateRandom(int x, int y, int z) {
        long i = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
        i = i * i * 42317861L + i * 11L;
        return i >> 16;
    }

    /**
     * Returns the texture rotation / model variant index for a block at (x, y, z).
     * Modulo is typically 4 for Sand, Grass, Dirt, Stone, and Bedrock top faces.
     * Prevents the Integer.MIN_VALUE negative modulo trap.
     */
    public static int getTextureVariant(int x, int y, int z, int mod) {
        int r = (int) getCoordinateRandom(x, y, z);
        return (r == Integer.MIN_VALUE ? 0 : Math.abs(r)) % mod;
    }

    /**
     * For mod = 4 (Stone & Bedrock):
     * On up and down faces (floor and ceiling), Variants 0 and 3 look identical (topParity = 0),
     * and Variants 1 and 2 look identical (topParity = 1).
     * Invariant under 180-deg camera rotation (North vs South).
     */
    public static int getTopParity(int x, int y, int z) {
        int v = getTextureVariant(x, y, z, 4);
        return (v == 1 || v == 2) ? 1 : 0;
    }

    /**
     * On vertical side walls (north, south, east, west), cube_mirrored_all.json flips U horizontally ([16, 0, 0, 16]),
     * whereas y: 180 rotates the block without flipping U.
     * Thus on side walls, Variants 0 and 2 are unmirrored (wallMirror = 0),
     * and Variants 1 and 3 are horizontally mirrored (wallMirror = 1).
     */
    public static int getWallMirror(int x, int y, int z) {
        int v = getTextureVariant(x, y, z, 4);
        return (v & 1); // 0 for variants {0, 2}, 1 for variants {1, 3}
    }

    /**
     * Poppy & Flower sub-block X offset index (0..15).
     * AbstractBlock.getOffset calls MathHelper.getCoordinateRandom(x, 0, z).
     */
    public static int getFlowerOffsetX(int x, int z) {
        return (int)(getCoordinateRandom(x, 0, z) & 15L);
    }

    /**
     * Poppy & Flower sub-block Z offset index (0..15).
     */
    public static int getFlowerOffsetZ(int x, int z) {
        return (int)((getCoordinateRandom(x, 0, z) >> 8) & 15L);
    }

    /**
     * Grass sub-block Y offset index (0..15).
     */
    public static int getGrassOffsetY(int x, int z) {
        return (int)((getCoordinateRandom(x, 0, z) >> 4) & 15L);
    }

    /**
     * 1.16.3 Pre-1.18 Overworld Bedrock Chunk Generator (NoiseChunkGenerator.buildBedrock).
     * Generates a 16x16x5 boolean array representing bedrock presence for Y=0..4.
     * 
     * @param chunkX chunk X coordinate (worldX >> 4)
     * @param chunkZ chunk Z coordinate (worldZ >> 4)
     * @param xInnerZOuter true if inner loop is X and outer loop is Z; false if inner is Z and outer is X.
     * @return boolean[16][16][5] indexed as [localX][localZ][y]
     */
    public static boolean[][][] generateBedrockChunk(int chunkX, int chunkZ, boolean xInnerZOuter) {
        boolean[][][] chunk = new boolean[16][16][5];
        long chunkSeed = (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L;
        Random rand = new Random(chunkSeed);

        if (xInnerZOuter) {
            // Z outer, X inner
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    for (int y = 4; y >= 0; --y) {
                        if (y <= rand.nextInt(5)) {
                            chunk[x][z][y] = true;
                        }
                    }
                }
            }
        } else {
            // X outer, Z inner (standard BlockPos.iterate order in 1.16.3)
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 4; y >= 0; --y) {
                        if (y <= rand.nextInt(5)) {
                            chunk[x][z][y] = true;
                        }
                    }
                }
            }
        }
        return chunk;
    }
}
