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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Stage2SeedRecoveryEngine {

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

    public static boolean testWorldSeed(
            OverworldBiomeSource obs,
            long worldSeed,
            int baseX, int baseZ,
            int[] outSpawn
    ) {
        // 1. Base Biome Condition at (baseX, 68, baseZ)
        Biome baseBiome = obs.getBiome(baseX, 68, baseZ);
        if (!isBambooJungle(baseBiome)) {
            return false;
        }

        // 2. Western Ocean / Lagoon Condition
        // Within 16..64 blocks to the West (-X), must transition to beach and an ocean variant
        boolean foundBeach = false;
        boolean foundOcean = false;
        for (int dx = 16; dx <= 64; dx += 4) {
            Biome b = obs.getBiome(baseX - dx, 62, baseZ);
            if (b == Biomes.BEACH) {
                foundBeach = true;
            }
            if (isOcean(b)) {
                foundOcean = true;
            }
        }
        if (!foundBeach || !foundOcean) {
            return false;
        }

        // 3. Small Island Spawn Condition
        int[] spawn = getSpawnPoint(obs, worldSeed);
        if (outSpawn != null && outSpawn.length >= 2) {
            outSpawn[0] = spawn[0];
            outSpawn[1] = spawn[1];
        }

        // Spawn must lie West (-X) of baseX
        if (spawn[0] >= baseX) {
            return false;
        }

        // Check small island constraint: land area < 150 blocks surrounded by ocean
        // Sample a 48x48 block bounding box around spawn at 8-block increments
        int landSamples = 0;
        boolean oceanPerimeter = true;
        for (int dz = -24; dz <= 24; dz += 8) {
            for (int dx = -24; dx <= 24; dx += 8) {
                Biome b = obs.getBiome(spawn[0] + dx, 64, spawn[1] + dz);
                boolean isLand = !isOcean(b) && b != Biomes.RIVER && b != Biomes.FROZEN_RIVER;
                if (isLand) {
                    landSamples++;
                }
                // Perimeter check (at +/- 24 blocks)
                if (Math.abs(dx) >= 20 || Math.abs(dz) >= 20) {
                    if (isLand) {
                        oceanPerimeter = false;
                    }
                }
            }
        }

        if (landSamples > 9 || !oceanPerimeter) {
            return false;
        }

        return true;
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   Minecraft Java 1.16.3 Seed Cracker - Stage 2: Decorator & Biome Recovery     ");
        System.out.println("   Full 64-bit World Seed Cracker (Diamond Vein + Bamboo Jungle + Island Spawn) ");
        System.out.println("================================================================================");

        File filteredFile = new File("output/stage1_filtered_coordinates.json");
        if (!filteredFile.exists()) {
            System.err.println("[ERROR] Missing output/stage1_filtered_coordinates.json");
            System.exit(1);
        }

        try {
            JsonObject root = new Gson().fromJson(new FileReader(filteredFile), JsonObject.class);
            JsonArray arr = root.getAsJsonArray("candidates");

            System.out.printf("[INFO] Loaded %d filtered candidates from Stage 1.%n", arr.size());

            // Rank candidates: Tier 2 first (especially Y0=4), then Tier 3
            List<JsonObject> candidates = new ArrayList<>();
            for (JsonElement el : arr) candidates.add(el.getAsJsonObject());
            candidates.sort((a, b) -> {
                int tierA = a.get("tier").getAsInt();
                int tierB = b.get("tier").getAsInt();
                if (tierA != tierB) return Integer.compare(tierA, tierB);
                int yA = a.get("y0").getAsInt();
                int yB = b.get("y0").getAsInt();
                return Integer.compare(yB, yA); // y=4 before y=3
            });

            // Load candidate 48-bit seeds if file exists
            File candidateSeedsFile = new File("output/candidate_48bit_seeds.txt");
            List<Long> lower48Seeds = new ArrayList<>();
            if (candidateSeedsFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(candidateSeedsFile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            lower48Seeds.add(Long.parseLong(line) & 0xFFFFFFFFFFFFL);
                        }
                    }
                }
                System.out.printf("[INFO] Loaded %d candidate 48-bit seeds from '%s'%n", lower48Seeds.size(), candidateSeedsFile.getPath());
            }

            int threads = Runtime.getRuntime().availableProcessors();
            System.out.printf("[INFO] Worker Threads Allocated: %d%n", threads);
            System.out.println("--------------------------------------------------------------------------------");

            Files.createDirectories(Paths.get("output"));
            File recoveredFile = new File("output/recovered_world_seeds.txt");
            FileWriter recoveredWriter = new FileWriter(recoveredFile, false);

            List<Long> recoveredSeeds = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger totalMatches = new AtomicInteger(0);

            // For each top candidate:
            int testedCandidates = 0;
            for (JsonObject c : candidates) {
                testedCandidates++;
                int x0 = c.get("x0").getAsInt();
                int y0 = c.get("y0").getAsInt();
                int z0 = c.get("z0").getAsInt();
                int tier = c.get("tier").getAsInt();
                int diamondX = c.get("diamondX").getAsInt();
                int diamondY = c.get("diamondY").getAsInt();
                int diamondZ = c.get("diamondZ").getAsInt();
                int baseX = c.get("baseX").getAsInt();
                int baseZ = c.get("baseZ").getAsInt();
                String orientation = c.get("orientation").getAsString();

                System.out.printf("[CANDIDATE %d/%d] Tier %d | Tunnel (%d, %d, %d) %s | Diamond (%d, %d, %d) | Base (%d, %d)%n",
                        testedCandidates, candidates.size(), tier, x0, y0, z0, orientation,
                        diamondX, diamondY, diamondZ, baseX, baseZ);

                // Filter 48-bit seeds that verify diamond ore at (diamondX, diamondY, diamondZ)
                List<Long> matching48 = new ArrayList<>();
                for (Long s48 : lower48Seeds) {
                    if (FeatureDecoratorModel.verifyDiamondOre(s48, diamondX, diamondY, diamondZ)) {
                        matching48.add(s48);
                    }
                }

                System.out.printf("   [DIAMOND FILTER] %d / %d 48-bit seeds produce Diamond Ore at (%d, %d, %d)%n",
                        matching48.size(), lower48Seeds.size(), diamondX, diamondY, diamondZ);

                if (matching48.isEmpty()) {
                    continue;
                }

                // Check possible base offsets behind the tunnel: 70..110 blocks
                int dzSign = orientation.equalsIgnoreCase("NORTH") ? 1 : -1;
                int[] testOffsets = new int[]{80, 70, 90, 100, 110, 75, 85, 95, 105};

                // Test upper 16-bit space for these matching 48-bit seeds
                ExecutorService executor = Executors.newFixedThreadPool(threads);
                int chunkSize = Math.max(1, matching48.size() / threads);

                for (int t = 0; t < matching48.size(); t += chunkSize) {
                    final int startIdx = t;
                    final int endIdx = Math.min(matching48.size(), t + chunkSize);

                    executor.submit(() -> {
                        int[] spawnOut = new int[2];
                        for (int i = startIdx; i < endIdx; i++) {
                            long lower48 = matching48.get(i);

                            for (long upper = 0; upper < 65536; upper++) {
                                long worldSeed = (upper << 48) | lower48;

                                OverworldBiomeSource obs = null;

                                for (int offset : testOffsets) {
                                    int bZ = z0 + (offset * dzSign);

                                    if (obs == null) {
                                        obs = new OverworldBiomeSource(MCVersion.v1_16_3, worldSeed);
                                    }

                                    if (testWorldSeed(obs, worldSeed, baseX, bZ, spawnOut)) {
                                        int count = totalMatches.incrementAndGet();
                                        recoveredSeeds.add(worldSeed);

                                        synchronized (recoveredWriter) {
                                            System.out.println("\n********************************************************************************");
                                            System.out.printf(">>> RECOVERED 64-BIT MINECRAFT WORLD SEED #%d! <<<%n", count);
                                            System.out.printf("    World Seed (Decimal) : %d%n", worldSeed);
                                            System.out.printf("    World Seed (Hex)     : 0x%016X%n", worldSeed);
                                            System.out.printf("    Lower 48-bit Seed    : %d (0x%012X)%n", lower48, lower48);
                                            System.out.printf("    Upper 16-bit Seed    : %d (0x%04X)%n", upper, upper);
                                            System.out.printf("    World Spawn Point    : (%d, %d) [Ocean-Surrounded Island < 150 blocks]%n", spawnOut[0], spawnOut[1]);
                                            System.out.printf("    Cliffside Base       : (%d, Y=68, %d) [Bamboo Jungle + Western Beach/Ocean]%n", baseX, bZ);
                                            System.out.printf("    Strip Mine Diamond   : (%d, Y=%d, %d)%n", diamondX, diamondY, diamondZ);
                                            System.out.println("********************************************************************************\n");

                                            try {
                                                recoveredWriter.write(worldSeed + "\n");
                                                recoveredWriter.flush();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                        break; // Found matching seed for this worldSeed
                                    }
                                }
                            }
                        }
                    });
                }

                executor.shutdown();
                executor.awaitTermination(30, TimeUnit.MINUTES);

                if (!recoveredSeeds.isEmpty()) {
                    System.out.println("[SUCCESS] Recovered seeds found! Halting candidate search.");
                    break;
                }
            }

            recoveredWriter.close();

            System.out.println("================================================================================");
            System.out.printf("   Stage 2 Complete! Recovered 64-bit Seeds: %d%n", recoveredSeeds.size());
            System.out.printf("   Seeds written to: %s%n", recoveredFile.getAbsolutePath());
            System.out.println("================================================================================");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}

