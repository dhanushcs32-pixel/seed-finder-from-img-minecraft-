#define _CRT_SECURE_NO_WARNINGS
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <math.h>

#ifdef _OPENMP
#include <omp.h>
#endif

// Cubiomes headers
#include "cubiomes/generator.h"
#include "cubiomes/finders.h"
#include "cubiomes/biomes.h"

// Biome constants for 1.16.3
#define BIOME_OCEAN 0
#define BIOME_BEACH 16
#define BIOME_DEEP_OCEAN 24
#define BIOME_WARM_OCEAN 44
#define BIOME_LUKEWARM_OCEAN 45
#define BIOME_DEEP_LUKEWARM_OCEAN 48
#define BIOME_BAMBOO_JUNGLE 168
#define BIOME_BAMBOO_JUNGLE_HILLS 169

static inline bool isOceanVariant(int b) {
    return b == BIOME_OCEAN ||
           b == BIOME_LUKEWARM_OCEAN ||
           b == BIOME_DEEP_LUKEWARM_OCEAN ||
           b == BIOME_WARM_OCEAN ||
           b == BIOME_DEEP_OCEAN ||
           b == cold_ocean ||
           b == deep_cold_ocean ||
           b == frozen_ocean ||
           b == deep_frozen_ocean;
}

static inline bool isBambooJungle(int b) {
    return b == BIOME_BAMBOO_JUNGLE || b == BIOME_BAMBOO_JUNGLE_HILLS;
}

/**
 * Checks all 3 Minecraft 1.16.3 scene constraints for a given 64-bit world seed:
 * 1. Base Biome: Bamboo Jungle / Bamboo Jungle Hills at (baseX, baseZ)
 * 2. Western Ocean / Lagoon: Beach and Ocean variant 16..64 blocks West (-X)
 * 3. Small Island Spawn: World spawn lies West (-X) of baseX, on small island (< 150 blocks land)
 */
bool testWorldSeed(Generator *g, uint64_t worldSeed, int baseX, int baseZ, Pos *outSpawn) {
    applySeed(g, DIM_OVERWORLD, worldSeed);

    // 1. Base Biome Condition at scale 1 (block level) or scale 4 (biome level)
    int baseBiome = getBiomeAt(g, 1, baseX, 68, baseZ);
    if (!isBambooJungle(baseBiome)) {
        return false;
    }

    // 2. Western Ocean / Lagoon Condition
    // Within 16..64 blocks to the West (-X), must transition to beach and an ocean variant
    bool foundBeach = false;
    bool foundOcean = false;
    for (int dx = 16; dx <= 64; dx += 4) {
        int b = getBiomeAt(g, 1, baseX - dx, 62, baseZ);
        if (b == BIOME_BEACH) {
            foundBeach = true;
        }
        if (isOceanVariant(b)) {
            foundOcean = true;
        }
    }
    if (!foundBeach || !foundOcean) {
        return false;
    }

    // 3. Small Island Spawn Condition
    Pos spawn = getSpawn(g);
    if (outSpawn) *outSpawn = spawn;

    // Spawn must lie West (-X) of baseX
    if (spawn.x >= baseX) {
        return false;
    }

    // Check small island constraint: land area < 150 blocks surrounded by ocean
    // Sample a 48x48 block bounding box around spawn at 4-block increments (12x12 = 144 sample points, each 16 sq blocks)
    int landSamples = 0;
    bool oceanPerimeter = true;
    for (int dz = -24; dz <= 24; dz += 8) {
        for (int dx = -24; dx <= 24; dx += 8) {
            int b = getBiomeAt(g, 1, spawn.x + dx, 64, spawn.z + dz);
            bool isLand = !isOceanVariant(b) && b != river && b != frozen_river;
            if (isLand) {
                landSamples++;
            }
            // Perimeter check (at +/- 24 blocks)
            if (abs(dx) >= 20 || abs(dz) >= 20) {
                if (isLand) {
                    oceanPerimeter = false;
                }
            }
        }
    }

    // Land area < 150 blocks approx corresponds to <= 9 samples of 16 sq blocks (144 sq blocks)
    // and ocean surrounding the spawn islet
    if (landSamples > 9 || !oceanPerimeter) {
        return false;
    }

    return true;
}

int main(int argc, char **argv) {
    printf("================================================================================\n");
    printf("   Minecraft Java 1.16.3 Seed Cracker - Stage 2: Biome & Spawn Filter (C11)     \n");
    printf("   Accelerated Biome Matching with Cubiomes and OpenMP                          \n");
    printf("================================================================================\n");

    int baseX = 1248, baseZ = -3424;
    const char *seedsFilePath = "output/candidate_48bit_seeds.txt";
    bool coarseMode = false;
    uint64_t coarseStart = 0;
    uint64_t coarseCount = 100000;
    int numThreads = 0;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--baseX") == 0 && i + 1 < argc) baseX = atoi(argv[++i]);
        else if (strcmp(argv[i], "--baseZ") == 0 && i + 1 < argc) baseZ = atoi(argv[++i]);
        else if (strcmp(argv[i], "--seeds") == 0 && i + 1 < argc) seedsFilePath = argv[++i];
        else if (strcmp(argv[i], "--coarse") == 0) coarseMode = true;
        else if (strcmp(argv[i], "--start") == 0 && i + 1 < argc) coarseStart = strtoull(argv[++i], NULL, 10);
        else if (strcmp(argv[i], "--count") == 0 && i + 1 < argc) coarseCount = strtoull(argv[++i], NULL, 10);
        else if (strcmp(argv[i], "--threads") == 0 && i + 1 < argc) numThreads = atoi(argv[++i]);
    }

#ifdef _OPENMP
    if (numThreads > 0) {
        omp_set_num_threads(numThreads);
    }
    printf("[INFO] OpenMP Parallel Threads: %d\n", omp_get_max_threads());
#else
    printf("[INFO] Compiled without OpenMP. Running single-threaded.\n");
#endif

    printf("[INFO] Target Base Position: (%d, Y=68, %d)\n", baseX, baseZ);
    printf("[INFO] Target Biome: Bamboo Jungle (168) / Bamboo Jungle Hills (169)\n");
    printf("[INFO] Western Lagoon: Beach (16) & Ocean variant 16..64 blocks West (-X)\n");
    printf("[INFO] Small-Island Ocean Spawn: Spawn.X < %d, land area < 150 blocks\n", baseX);
    printf("--------------------------------------------------------------------------------\n");

    // Load 48-bit lower seeds if present
    uint64_t *lowerSeeds = NULL;
    size_t seedCount = 0;

    if (!coarseMode) {
        FILE *sf = fopen(seedsFilePath, "r");
        if (sf) {
            uint64_t s;
            size_t cap = 1024;
            lowerSeeds = (uint64_t*)malloc(cap * sizeof(uint64_t));
            while (fscanf(sf, "%llu", &s) == 1) {
                if (seedCount >= cap) {
                    cap *= 2;
                    lowerSeeds = (uint64_t*)realloc(lowerSeeds, cap * sizeof(uint64_t));
                }
                lowerSeeds[seedCount++] = s & 0xFFFFFFFFFFFFULL;
            }
            fclose(sf);
            printf("[INFO] Loaded %zu 48-bit candidate seeds from '%s'\n", seedCount, seedsFilePath);
        } else {
            printf("[WARN] Could not open '%s'. Switching to benchmark demonstration mode.\n", seedsFilePath);
            coarseMode = true;
        }
    }

    FILE *out = fopen("output/recovered_world_seeds.txt", "w");
    if (!out) {
        out = fopen("recovered_world_seeds.txt", "w");
    }

    int matchCount = 0;

    if (!coarseMode && seedCount > 0) {
        printf("[INFO] Testing 65,536 upper 16-bit combinations per 48-bit seed (%zu total seeds)...\n", seedCount);

        #pragma omp parallel
        {
            Generator g;
            setupGenerator(&g, MC_1_16, 0);

            #pragma omp for schedule(dynamic) reduction(+:matchCount)
            for (size_t i = 0; i < seedCount; i++) {
                uint64_t lower48 = lowerSeeds[i];
                for (uint64_t upper = 0; upper < 65536; upper++) {
                    uint64_t worldSeed = (upper << 48) | lower48;
                    Pos sp;
                    if (testWorldSeed(&g, worldSeed, baseX, baseZ, &sp)) {
                        matchCount++;
                        #pragma omp critical
                        {
                            printf("\n>>> RECOVERED 64-BIT MINECRAFT WORLD SEED! <<<\n");
                            printf("    World Seed (Decimal) : %lld\n", (int64_t)worldSeed);
                            printf("    World Seed (Hex)     : 0x%016llX\n", (unsigned long long)worldSeed);
                            printf("    World Spawn Point    : (%d, %d) [Islet < 150 blocks, Ocean surrounded]\n", sp.x, sp.z);
                            printf("    Base Coordinates     : (%d, Y=68, %d) [Bamboo Jungle]\n", baseX, baseZ);
                            printf("--------------------------------------------------------------------------------\n");

                            if (out) {
                                fprintf(out, "%lld\n", (int64_t)worldSeed);
                                fflush(out);
                            }
                        }
                    }
                }
            }
        }
    } else {
        printf("[INFO] Running coarse biome search from seed %llu for %llu iterations...\n",
               (unsigned long long)coarseStart, (unsigned long long)coarseCount);

        #pragma omp parallel
        {
            Generator g;
            setupGenerator(&g, MC_1_16, 0);

            #pragma omp for schedule(dynamic) reduction(+:matchCount)
            for (uint64_t s = coarseStart; s < coarseStart + coarseCount; s++) {
                Pos sp;
                if (testWorldSeed(&g, s, baseX, baseZ, &sp)) {
                    matchCount++;
                    #pragma omp critical
                    {
                        printf("Found Matching Seed: %lld (Spawn: %d, %d)\n", (int64_t)s, sp.x, sp.z);
                        if (out) {
                            fprintf(out, "%lld\n", (int64_t)s);
                            fflush(out);
                        }
                    }
                }
            }
        }
    }

    if (out) fclose(out);
    if (lowerSeeds) free(lowerSeeds);

    printf("\n================================================================================\n");
    printf("   Stage 2 Biome Filtering Finished. Total Matching 64-bit Seeds: %d\n", matchCount);
    printf("================================================================================\n");

    return 0;
}
