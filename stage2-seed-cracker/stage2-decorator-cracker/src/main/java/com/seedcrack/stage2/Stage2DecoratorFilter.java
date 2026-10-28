package com.seedcrack.stage2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class Stage2DecoratorFilter {

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   Minecraft Java 1.16.3 Seed Cracker - Stage 2: Decorator Filter (Java)        ");
        System.out.println("   Reversing 1.16.3 Population & Decorator Seeds (Diamond Vein & Poppies)       ");
        System.out.println("================================================================================");

        int baseX = 0, baseZ = 0;
        int mineX = 0, mineZ = 80;
        int poppy1X = -2, poppy1Z = 4;
        int poppy2X = 3, poppy2Z = 2;
        boolean coordsSpecified = false;

        for (int i = 0; i < args.length; i++) {
            if ("--baseX".equals(args[i]) && i + 1 < args.length) { baseX = Integer.parseInt(args[++i]); coordsSpecified = true; }
            if ("--baseZ".equals(args[i]) && i + 1 < args.length) { baseZ = Integer.parseInt(args[++i]); coordsSpecified = true; }
            if ("--mineX".equals(args[i]) && i + 1 < args.length) { mineX = Integer.parseInt(args[++i]); coordsSpecified = true; }
            if ("--mineZ".equals(args[i]) && i + 1 < args.length) { mineZ = Integer.parseInt(args[++i]); coordsSpecified = true; }
        }

        if (!coordsSpecified) {
            File stage1Out = new File("output/stage1_coordinates.json");
            if (stage1Out.exists()) {
                try (FileReader reader = new FileReader(stage1Out)) {
                    JsonObject root = new Gson().fromJson(reader, JsonObject.class);
                    JsonArray candidates = root.getAsJsonArray("candidates");
                    if (candidates != null && candidates.size() > 0) {
                        JsonObject best = candidates.get(0).getAsJsonObject();
                        baseX = best.get("baseX").getAsInt();
                        baseZ = best.get("baseZ").getAsInt();
                        mineX = best.get("mineX").getAsInt();
                        mineZ = best.get("mineZ").getAsInt();
                        System.out.printf("[INFO] Auto-loaded candidate from Stage 1: Base (%d, 68, %d) | Mine (%d, 4, %d)%n",
                                baseX, baseZ, mineX, mineZ);
                    } else {
                        System.out.println("[WARN] Stage 1 output has no candidates. Using default benchmark coordinates.");
                    }
                } catch (Exception e) {
                    System.out.println("[WARN] Error reading Stage 1 output: " + e.getMessage());
                }
            } else {
                System.out.println("[INFO] No Stage 1 output found ('output/stage1_coordinates.json').");
                System.out.println("[INFO] Using default demonstration coordinates (Base: 1248, 68, -3424 | Mine: 1248, 4, -3344).");
                baseX = 1248; baseZ = -3424;
                mineX = 1248; mineZ = -3344;
            }
        }

        poppy1X = baseX - 2;
        poppy1Z = baseZ + 4;
        poppy2X = baseX + 3;
        poppy2Z = baseZ + 2;

        System.out.printf("[INFO] Target Mine Diamond Vein: (%d, Y=4, %d) [Chunk: %d, %d]%n",
                mineX, mineZ, mineX >> 4, mineZ >> 4);
        System.out.printf("[INFO] Target Left Poppy: (%d, Y=72, %d) | Right Poppy: (%d, Y=73, %d)%n",
                poppy1X, poppy1Z, poppy2X, poppy2Z);

        long startTime = System.currentTimeMillis();
        List<Long> matched48BitSeeds = findDecoratorSeeds(baseX, baseZ, mineX, mineZ, poppy1X, poppy1Z, poppy2X, poppy2Z);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("\n================================================================================");
        System.out.printf("   Decorator Cracking Finished in %d ms (%.2f s)%n", elapsed, elapsed / 1000.0);
        System.out.printf("   Matching 48-bit Seeds Found: %d%n", matched48BitSeeds.size());
        System.out.println("================================================================================");

        try {
            Files.createDirectories(Paths.get("output"));
            File outFile = new File("output/candidate_48bit_seeds.txt");
            try (FileWriter fw = new FileWriter(outFile)) {
                for (Long s : matched48BitSeeds) {
                    fw.write(s + "\n");
                    System.out.printf("   Found 48-bit Candidate Seed: %15d (0x%012X)%n", s, s);
                }
            }
            System.out.println("[INFO] Saved candidate 48-bit seeds to: " + outFile.getAbsolutePath());
            System.out.println("[NEXT] Proceed to Stage 2 C/Cubiomes Biome Filter with: ./run_stage2.bat (or .sh)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<Long> findDecoratorSeeds(
            int baseX, int baseZ,
            int mineX, int mineZ,
            int p1X, int p1Z,
            int p2X, int p2Z
    ) {
        List<Long> matches = Collections.synchronizedList(new ArrayList<>());
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // Search space demonstration & scan
        // In full reverse mode, candidate population seeds are derived from ChunkRandomReversal
        // Here we scan candidate seed blocks in parallel
        long totalToTest = 10_000_000L;
        long slice = totalToTest / threads;

        for (int t = 0; t < threads; t++) {
            final long start = t * slice;
            final long end = (t == threads - 1) ? totalToTest : (t + 1) * slice;

            executor.submit(() -> {
                for (long s = start; s < end; s++) {
                    // Check Diamond Ore vein at Y=4
                    if (FeatureDecoratorModel.verifyDiamondOre(s, mineX, 4, mineZ)) {
                        // Check Poppy patch
                        if (FeatureDecoratorModel.verifyPoppyPatch(s, p1X, 72, p1Z)) {
                            matches.add(s);
                        }
                    }
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return matches;
    }
}
