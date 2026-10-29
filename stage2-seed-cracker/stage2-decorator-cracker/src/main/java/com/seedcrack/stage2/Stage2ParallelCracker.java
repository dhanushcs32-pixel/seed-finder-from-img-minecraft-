package com.seedcrack.stage2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import kaptainwutax.biomeutils.source.OverworldBiomeSource;
import kaptainwutax.biomeutils.biome.Biome;
import kaptainwutax.biomeutils.biome.Biomes;
import kaptainwutax.mcutils.version.MCVersion;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Stage2ParallelCracker {

    public static boolean isBambooJungle(Biome b) {
        return b == Biomes.BAMBOO_JUNGLE || b == Biomes.BAMBOO_JUNGLE_HILLS;
    }

    public static boolean isOcean(Biome b) {
        return b == Biomes.OCEAN || b == Biomes.DEEP_OCEAN ||
               b == Biomes.WARM_OCEAN || b == Biomes.LUKEWARM_OCEAN ||
               b == Biomes.DEEP_LUKEWARM_OCEAN || b == Biomes.COLD_OCEAN ||
               b == Biomes.DEEP_COLD_OCEAN || b == Biomes.FROZEN_OCEAN ||
               b == Biomes.DEEP_FROZEN_OCEAN;
    }

    public static boolean isSpawnBiome(Biome b) {
        return b == Biomes.FOREST || b == Biomes.PLAINS ||
               b == Biomes.TAIGA || b == Biomes.TAIGA_HILLS ||
               b == Biomes.WOODED_HILLS;
    }

    public static int[] getSpawnPoint(OverworldBiomeSource obs, long worldSeed) {
        ChunkRandom rng = new ChunkRandom(worldSeed);
        int spawnX = 8, spawnZ = 8;
        int found = 0;

        for (int nz = -64; nz <= 64; nz += 4) {
            for (int nx = -64; nx <= 64; nx += 4) {
                Biome b = obs.getBiomeForNoiseGen(nx, 0, nz);
                if (isSpawnBiome(b)) {
                    if (found == 0 || rng.nextInt(found + 1) == 0) {
                        spawnX = nx * 4;
                        spawnZ = nz * 4;
                    }
                    found++;
                }
            }
        }
        return new int[]{spawnX, spawnZ};
    }

    public static boolean verifyAllConditions(
            OverworldBiomeSource obs,
            long worldSeed,
            int baseX, int baseZ,
            int diamondX, int diamondY, int diamondZ,
            int[] outSpawn
    ) {
        // 1. Base Biome Condition at (baseX, 68, baseZ)
        Biome baseBiome = obs.getBiome(baseX, 68, baseZ);
        if (!isBambooJungle(baseBiome)) {
            return false;
        }

        // 2. Western Ocean / Lagoon Condition: 16..64 blocks West (-X) must transition to beach and ocean
        boolean foundBeach = false;
        boolean foundOcean = false;
        for (int dx = 16; dx <= 64; dx += 4) {
            Biome b = obs.getBiome(baseX - dx, 62, baseZ);
            if (b == Biomes.BEACH) foundBeach = true;
            if (isOcean(b)) foundOcean = true;
        }
        if (!foundBeach || !foundOcean) {
            return false;
        }

        // 3. Small Island Spawn Condition
        int[] spawn = getSpawnPoint(obs, worldSeed);
        if (spawn[0] >= baseX) {
            return false;
        }

        // Island check: land area < 150 blocks, surrounded by ocean
        int landSamples = 0;
        boolean oceanPerimeter = true;
        for (int dz = -24; dz <= 24; dz += 8) {
            for (int dx = -24; dx <= 24; dx += 8) {
                Biome b = obs.getBiome(spawn[0] + dx, 64, spawn[1] + dz);
                boolean isLand = !isOcean(b) && b != Biomes.RIVER && b != Biomes.FROZEN_RIVER;
                if (isLand) {
                    landSamples++;
                }
                if (Math.abs(dx) >= 20 || Math.abs(dz) >= 20) {
                    if (isLand) oceanPerimeter = false;
                }
            }
        }

        if (landSamples > 9 || !oceanPerimeter) {
            return false;
        }

        // 4. Diamond Ore Vein Generation
        if (!FeatureDecoratorModel.verifyDiamondOre(worldSeed & 0xFFFFFFFFFFFFL, diamondX, diamondY, diamondZ)) {
            return false;
        }

        if (outSpawn != null && outSpawn.length >= 2) {
            outSpawn[0] = spawn[0];
            outSpawn[1] = spawn[1];
        }

        return true;
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   Minecraft Java 1.16.3 Seed Cracker - Stage 2: Biome & Decorator Cracker      ");
        System.out.println("   Accelerated Biome + Western Lagoon + Small Island Spawn + Diamond Vein       ");
        System.out.println("================================================================================");

        try {
            JsonObject root = new Gson().fromJson(new FileReader("output/stage1_filtered_coordinates.json"), JsonObject.class);
            JsonArray arr = root.getAsJsonArray("candidates");

            List<JsonObject> candidates = new ArrayList<>();
            for (JsonElement el : arr) candidates.add(el.getAsJsonObject());

            // Prioritize Tier 2 Y=4 candidates
            List<JsonObject> priorityCandidates = new ArrayList<>();
            for (JsonObject c : candidates) {
                if (c.get("y0").getAsInt() == 4) {
                    priorityCandidates.add(c);
                }
            }

            System.out.printf("[INFO] Testing %d prioritized Y=4 candidates...%n", priorityCandidates.size());

            int threads = Runtime.getRuntime().availableProcessors();
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            File recoveredFile = new File("output/recovered_world_seeds.txt");
            FileWriter writer = new FileWriter(recoveredFile, false);
            AtomicInteger matchCount = new AtomicInteger(0);

            // Coarse Biome Grid Search: scan 64-bit space in blocks
            long searchBlocks = 500_000L;
            long stepPerThread = searchBlocks / threads;

            System.out.printf("[INFO] Scanning %d world seeds across %d threads...%n", searchBlocks, threads);

            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final long startSeed = t * stepPerThread;
                final long endSeed = (t == threads - 1) ? searchBlocks : (t + 1) * stepPerThread;

                futures.add(executor.submit(() -> {
                    int[] sp = new int[2];
                    for (long s = startSeed; s < endSeed; s++) {
                        OverworldBiomeSource obs = null;

                        for (JsonObject c : priorityCandidates) {
                            int baseX = c.get("baseX").getAsInt();
                            int baseZ = c.get("baseZ").getAsInt();
                            int diamondX = c.get("diamondX").getAsInt();
                            int diamondY = c.get("diamondY").getAsInt();
                            int diamondZ = c.get("diamondZ").getAsInt();

                            if (obs == null) {
                                obs = new OverworldBiomeSource(MCVersion.v1_16_3, s);
                            }

                            if (verifyAllConditions(obs, s, baseX, baseZ, diamondX, diamondY, diamondZ, sp)) {
                                int id = matchCount.incrementAndGet();
                                synchronized (writer) {
                                    System.out.println("\n********************************************************************************");
                                    System.out.printf(">>> RECOVERED 64-BIT MINECRAFT WORLD SEED #%d! <<<%n", id);
                                    System.out.printf("    World Seed (Decimal) : %d%n", s);
                                    System.out.printf("    World Seed (Hex)     : 0x%016X%n", s);
                                    System.out.printf("    Lower 48-bit Seed    : %d (0x%012X)%n", s & 0xFFFFFFFFFFFFL, s & 0xFFFFFFFFFFFFL);
                                    System.out.printf("    World Spawn Point    : (%d, %d) [Ocean Island < 150 blocks]%n", sp[0], sp[1]);
                                    System.out.printf("    Cliffside Base       : (%d, Y=68, %d) [Bamboo Jungle + Western Beach/Ocean]%n", baseX, baseZ);
                                    System.out.printf("    Diamond Vein Mine    : (%d, Y=%d, %d)%n", diamondX, diamondY, diamondZ);
                                    System.out.println("********************************************************************************\n");

                                    try {
                                        writer.write(s + "\n");
                                        writer.flush();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                f.get();
            }

            executor.shutdown();
            writer.close();

            System.out.println("================================================================================");
            System.out.printf("   Stage 2 Complete. Total Recovered 64-bit Seeds: %d%n", matchCount.get());
            System.out.printf("   Seeds written to: %s%n", recoveredFile.getAbsolutePath());
            System.out.println("================================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

