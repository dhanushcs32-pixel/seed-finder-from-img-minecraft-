package com.seedcrack.stage1;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class Stage1CoordTriangulatorTest {

    @Test
    public void testTextureVariantDeterminism() {
        int x = 500, y = 68, z = -1200;
        int var1 = MathHelper.getTextureVariant(x, y, z, 4);
        int var2 = MathHelper.getTextureVariant(x, y, z, 4);
        assertEquals(var1, var2);
        assertTrue(var1 >= 0 && var1 < 4);
    }

    @Test
    public void testFlowerOffsetBounds() {
        for (int x = -50; x <= 50; x += 10) {
            for (int z = -50; z <= 50; z += 10) {
                int offX = MathHelper.getFlowerOffsetX(x, z);
                int offZ = MathHelper.getFlowerOffsetZ(x, z);
                int offY = MathHelper.getGrassOffsetY(x, z);
                assertTrue(offX >= 0 && offX <= 15, "offX out of bounds: " + offX);
                assertTrue(offZ >= 0 && offZ <= 15, "offZ out of bounds: " + offZ);
                assertTrue(offY >= 0 && offY <= 15, "offY out of bounds: " + offY);
            }
        }
    }

    @Test
    public void testBedrockPatternGeneration() {
        // Chunk (10, -20)
        int chunkX = 10;
        int chunkZ = -20;
        boolean[][][] chunkA = MathHelper.generateBedrockChunk(chunkX, chunkZ, true);
        boolean[][][] chunkB = MathHelper.generateBedrockChunk(chunkX, chunkZ, false);

        // In 1.16.3 Overworld, Y=0 is always bedrock
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertTrue(chunkA[x][z][0], "Y=0 must always be bedrock");
                assertTrue(chunkB[x][z][0], "Y=0 must always be bedrock");
            }
        }

        // Verify exact PRNG stepping (5 rand.nextInt(5) calls per (x, z) column)
        long chunkSeed = (long) chunkX * 341873128712L + (long) chunkZ * 132897987541L;
        java.util.Random rand = new java.util.Random(chunkSeed);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 4; y >= 0; --y) {
                    boolean expectedBedrock = (y <= rand.nextInt(5));
                    assertEquals(expectedBedrock, chunkA[x][z][y],
                            String.format("Mismatch at (%d, %d, %d) for Z-outer X-inner", x, y, z));
                }
            }
        }
    }

    @Test
    public void testTopParityAndWallMirrorEvaluators() {
        for (int x = -50; x <= 50; x += 5) {
            for (int z = -50; z <= 50; z += 5) {
                for (int y = 0; y <= 7; y++) {
                    int v = MathHelper.getTextureVariant(x, y, z, 4);
                    int topParity = MathHelper.getTopParity(x, y, z);
                    int wallMirror = MathHelper.getWallMirror(x, y, z);

                    if (v == 0 || v == 3) {
                        assertEquals(0, topParity, "Top parity for variant 0 or 3 must be 0");
                    } else {
                        assertEquals(1, topParity, "Top parity for variant 1 or 2 must be 1");
                    }

                    if (v == 0 || v == 2) {
                        assertEquals(0, wallMirror, "Wall mirror for variant 0 or 2 must be 0");
                    } else {
                        assertEquals(1, wallMirror, "Wall mirror for variant 1 or 3 must be 1");
                    }
                }
            }
        }
    }
}
