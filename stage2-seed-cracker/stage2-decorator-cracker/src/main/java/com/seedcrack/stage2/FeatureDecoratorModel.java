package com.seedcrack.stage2;

/**
 * Models Minecraft 1.16.3 decorator features:
 * - Diamond Ore Vein (GenerationStep.Feature.UNDERGROUND_ORES, step = 6)
 * - Poppy Flower Patch (VEGETAL_DECORATION, step = 8)
 * - Bamboo & Jungle Tree decorators
 */
public class FeatureDecoratorModel {

    // 1.16.3 GenerationStep ordinals
    public static final int STEP_UNDERGROUND_ORES = 6;
    public static final int STEP_VEGETAL_DECORATION = 8;

    // Diamond Ore Vein feature index within step 6 (typically index 7 or 8 in overworld biomes)
    public static final int DIAMOND_FEATURE_INDEX = 7;

    // Flower feature index within step 8
    public static final int FLOWER_FEATURE_INDEX = 12;

    public record DiamondVeinTarget(int blockX, int blockY, int blockZ, int size) {}
    public record PoppyTarget(int blockX, int blockY, int blockZ) {}

    /**
     * Checks if a 48-bit world seed produces a diamond ore vein at (mineX, 4, mineZ).
     */
    public static boolean verifyDiamondOre(long seed48, int mineX, int mineY, int mineZ) {
        int chunkX = mineX >> 4;
        int chunkZ = mineZ >> 4;
        int targetLocalX = mineX & 15;
        int targetLocalZ = mineZ & 15;

        ChunkRandom cr = new ChunkRandom();
        long popSeed = cr.setPopulationSeed(seed48, chunkX << 4, chunkZ << 4);

        // In 1.16.3, diamond ore is placed in step 6 (UNDERGROUND_ORES)
        for (int featureIdx = 0; featureIdx <= 15; featureIdx++) {
            cr.setDecoratorSeed(popSeed, featureIdx, STEP_UNDERGROUND_ORES);
            int oreX = cr.nextInt(16);
            int oreZ = cr.nextInt(16);
            int oreY = cr.nextInt(16); // 0..15 for diamond ore

            // Check if vein origin is within reach of target mine block (vein size 8 reaches ~2 blocks)
            if (oreY >= mineY - 1 && oreY <= mineY + 2) {
                if (Math.abs(oreX - targetLocalX) <= 2 && Math.abs(oreZ - targetLocalZ) <= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a 48-bit seed generates flower patches near the observed poppies.
     */
    public static boolean verifyPoppyPatch(long seed48, int poppyX, int poppyY, int poppyZ) {
        int chunkX = poppyX >> 4;
        int chunkZ = poppyZ >> 4;
        int targetLocalX = poppyX & 15;
        int targetLocalZ = poppyZ & 15;

        ChunkRandom cr = new ChunkRandom();
        long popSeed = cr.setPopulationSeed(seed48, chunkX << 4, chunkZ << 4);

        for (int featureIdx = 0; featureIdx <= 20; featureIdx++) {
            cr.setDecoratorSeed(popSeed, featureIdx, STEP_VEGETAL_DECORATION);
            int patchX = cr.nextInt(16);
            int patchZ = cr.nextInt(16);
            if (Math.abs(patchX - targetLocalX) <= 4 && Math.abs(patchZ - targetLocalZ) <= 4) {
                return true;
            }
        }
        return false;
    }
}
