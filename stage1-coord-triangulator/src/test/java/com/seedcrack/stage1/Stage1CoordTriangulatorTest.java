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
        boolean[][][] chunkA = MathHelper.generateBedrockChunk(10, -20, true);
        boolean[][][] chunkB = MathHelper.generateBedrockChunk(10, -20, false);

        // In 1.16.3 Overworld, Y=0 is always bedrock
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertTrue(chunkA[x][z][0], "Y=0 must always be bedrock");
                assertTrue(chunkB[x][z][0], "Y=0 must always be bedrock");
            }
        }
    }
}
