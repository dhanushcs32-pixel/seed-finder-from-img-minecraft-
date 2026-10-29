package com.seedcrack.stage1;

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

public class Stage1CoordTriangulator {

    public record CandidateMatch(
            int x0,
            int y0,
            int z0,
            Orientation orientation,
            String bedrockLoopOrder,
            boolean isStrict,
            int matchedBedrockCount,
            int totalBedrockCount,
            String passName,
            double matchPercentage,
            // Compatibility fields for Stage 2 decorator filter
            int baseX,
            int baseY,
            int baseZ,
            int mineX,
            int mineY,
            int mineZ
    ) {}

    private static class ThreadLocalBedrockCache {
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        boolean cachedXInner;
        boolean[][][] bedrockData;

        public boolean isBedrock(int worldX, int y, int worldZ, boolean xInnerZOuter) {
            if (y < 0 || y > 4) return false;

            int chunkX = worldX >> 4;
            int chunkZ = worldZ >> 4;

            if (bedrockData == null || cachedChunkX != chunkX || cachedChunkZ != chunkZ || cachedXInner != xInnerZOuter) {
                cachedChunkX = chunkX;
                cachedChunkZ = chunkZ;
                cachedXInner = xInnerZOuter;
                bedrockData = MathHelper.generateBedrockChunk(chunkX, chunkZ, xInnerZOuter);
            }

            int localX = worldX & 15;
            int localZ = worldZ & 15;
            return bedrockData[localX][localZ][y];
        }
    }

    private static final ThreadLocal<ThreadLocalBedrockCache> BEDROCK_CACHE =
            ThreadLocal.withInitial(ThreadLocalBedrockCache::new);

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   Minecraft Java 1.16.3 Seed Cracker - Stage 1: Coordinate Triangulator        ");
        System.out.println("   Exact 1.16.3 Stone/Bedrock Parity & Wall-Mirror Tunnel Solver (18.30.57.png) ");
        System.out.println("================================================================================");

        String configPath = "config/scene_constraints.json";
        String formationPath = "input/formation_data.json";

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) configPath = args[++i];
            if ("--input".equals(args[i]) && i + 1 < args.length) formationPath = args[++i];
        }

        File configFile = resolveFile(configPath);
        File formationFile = resolveFile(formationPath);

        int minX = -10000, maxX = 10000, minZ = -10000, maxZ = 10000;
        int threadCount = Runtime.getRuntime().availableProcessors();
        List<Orientation> orientations = Arrays.asList(Orientation.NORTH, Orientation.SOUTH);
        List<Boolean> loopOrders = Arrays.asList(true, false); // true = X_INNER_Z_OUTER, false = Z_INNER_X_OUTER
        int[] yLevels = new int[]{4, 3};
        int stripMineOffset = 80;

        try {
            if (formationFile.exists()) {
                JsonObject formation = new Gson().fromJson(new FileReader(formationFile), JsonObject.class);
                if (formation.has("searchBounds")) {
                    JsonObject bounds = formation.getAsJsonObject("searchBounds");
                    if (bounds.has("minX")) minX = bounds.get("minX").getAsInt();
                    if (bounds.has("maxX")) maxX = bounds.get("maxX").getAsInt();
                    if (bounds.has("minZ")) minZ = bounds.get("minZ").getAsInt();
                    if (bounds.has("maxZ")) maxZ = bounds.get("maxZ").getAsInt();
                }
                if (formation.has("yLevels")) {
                    JsonArray ya = formation.getAsJsonArray("yLevels");
                    yLevels = new int[ya.size()];
                    for (int i = 0; i < ya.size(); i++) yLevels[i] = ya.get(i).getAsInt();
                }
            } else if (configFile.exists()) {
                JsonObject config = new Gson().fromJson(new FileReader(configFile), JsonObject.class);
                if (config.has("searchBounds")) {
                    JsonObject bounds = config.getAsJsonObject("searchBounds");
                    minX = bounds.get("minX").getAsInt();
                    maxX = bounds.get("maxX").getAsInt();
                    minZ = bounds.get("minZ").getAsInt();
                    maxZ = bounds.get("maxZ").getAsInt();
                }
            }
        } catch (Exception e) {
            System.out.println("[WARN] Could not parse configuration file, using defaults: " + e.getMessage());
        }

        System.out.printf("[INFO] Search Grid: X [%d .. %d], Z [%d .. %d]%n", minX, maxX, minZ, maxZ);
        System.out.printf("[INFO] Y Levels Tested: %s%n", Arrays.toString(yLevels));
        System.out.printf("[INFO] Orientations to Test: %s | Bedrock Loop Orders: [X_INNER_Z_OUTER, Z_INNER_X_OUTER]%n", orientations);
        System.out.printf("[INFO] Worker Threads Allocated: %d%n", threadCount);
        System.out.println("--------------------------------------------------------------------------------");

        long startTime = System.currentTimeMillis();
        List<CandidateMatch> results = executeSearch(
                minX, maxX, minZ, maxZ,
                yLevels,
                orientations, loopOrders,
                stripMineOffset, threadCount
        );
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.println("\n================================================================================");
        System.out.printf("   Triangulation Completed in %d ms (%.2f s)%n", elapsed, elapsed / 1000.0);
        System.out.printf("   Candidates Found: %d%n", results.size());
        System.out.println("================================================================================");

        if (results.isEmpty()) {
            System.out.println("[WARN] No candidate matched the observed block constraints in the search area.");
        } else {
            System.out.printf("%-18s | %-12s | %-18s | %-20s | %-10s%n",
                    "Tunnel (x0, y0, z0)", "Orientation", "Bedrock Loop", "Pass", "Confidence");
            System.out.println("--------------------------------------------------------------------------------");
            for (CandidateMatch c : results) {
                System.out.printf("(%6d, %2d, %6d) | %-12s | %-18s | %-20s | %6.2f%%%n",
                        c.x0, c.y0, c.z0,
                        c.orientation, c.bedrockLoopOrder, c.passName, c.matchPercentage);
            }
        }

        try {
            exportResults(results);
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to export candidates: " + e.getMessage());
        }
    }

    public static List<CandidateMatch> executeSearch(
            int minX, int maxX, int minZ, int maxZ,
            int[] yLevels,
            List<Orientation> orientations,
            List<Boolean> loopOrders,
            int stripMineOffset,
            int threadCount
    ) {
        List<CandidateMatch> matches = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong positionsTested = new AtomicLong(0);

        int chunkSize = Math.max(1, (maxX - minX + 1) / (threadCount * 8));
        List<Future<?>> futures = new ArrayList<>();

        for (int xStart = minX; xStart <= maxX; xStart += chunkSize) {
            final int chunkMinX = xStart;
            final int chunkMaxX = Math.min(maxX, xStart + chunkSize - 1);

            futures.add(executor.submit(() -> {
                ThreadLocalBedrockCache bCache = BEDROCK_CACHE.get();

                for (int x0 = chunkMinX; x0 <= chunkMaxX; x0++) {
                    for (int z0 = minZ; z0 <= maxZ; z0++) {
                        positionsTested.incrementAndGet();

                        for (int y0 : yLevels) {
                            for (Orientation orientation : orientations) {
                                // 1. Constraint Set 2: Floor Parity (dx=0, y=y0)
                                int wz0 = orientation.tunnelWorldZ(z0, 0);
                                int wz1 = orientation.tunnelWorldZ(z0, 1);
                                if (MathHelper.getTopParity(x0, y0, wz0) == MathHelper.getTopParity(x0, y0, wz1)) {
                                    continue;
                                }

                                int wz2 = orientation.tunnelWorldZ(z0, 2);
                                int wz3 = orientation.tunnelWorldZ(z0, 3);
                                if (MathHelper.getTopParity(x0, y0, wz2) == MathHelper.getTopParity(x0, y0, wz3)) {
                                    continue;
                                }

                                int wz4 = orientation.tunnelWorldZ(z0, 4);
                                int wz5 = orientation.tunnelWorldZ(z0, 5);
                                if (MathHelper.getTopParity(x0, y0, wz4) != MathHelper.getTopParity(x0, y0, wz5)) {
                                    continue;
                                }

                                // 2. Constraint Set 2: Ceiling Parity (dx=0, y=y0+3)
                                int yCeil = y0 + 3;
                                int cp0 = MathHelper.getTopParity(x0, yCeil, wz0);
                                int cp1 = MathHelper.getTopParity(x0, yCeil, wz1);
                                if (cp0 != cp1) {
                                    continue;
                                }

                                int cp2 = MathHelper.getTopParity(x0, yCeil, wz2);
                                if (cp1 == cp2) {
                                    continue;
                                }

                                int cp3 = MathHelper.getTopParity(x0, yCeil, wz3);
                                if (cp2 != cp3) {
                                    continue;
                                }

                                // 3. Constraint Set 3: Left Wall (dx = -1 relative to camera)
                                int wxLeft = orientation.tunnelWorldX(x0, -1);
                                // Upper wall (y = y0 + 2): getWallMirror(d=1) != getWallMirror(d=2)
                                int lu1 = MathHelper.getWallMirror(wxLeft, y0 + 2, wz1);
                                int lu2 = MathHelper.getWallMirror(wxLeft, y0 + 2, wz2);
                                if (lu1 == lu2) {
                                    continue;
                                }

                                // Lower wall (y = y0 + 1): getWallMirror(d=1) == getWallMirror(d=2) AND getWallMirror(d=2) != getWallMirror(d=3)
                                int ll1 = MathHelper.getWallMirror(wxLeft, y0 + 1, wz1);
                                int ll2 = MathHelper.getWallMirror(wxLeft, y0 + 1, wz2);
                                if (ll1 != ll2) {
                                    continue;
                                }
                                int ll3 = MathHelper.getWallMirror(wxLeft, y0 + 1, wz3);
                                if (ll2 == ll3) {
                                    continue;
                                }

                                // 4. Constraint Set 3: Right Wall (dx = +1 relative to camera)
                                int wxRight = orientation.tunnelWorldX(x0, 1);
                                // Upper wall (y = y0 + 2): getWallMirror(d=1) != getWallMirror(d=2) AND getWallMirror(d=2) == getWallMirror(d=3)
                                int ru1 = MathHelper.getWallMirror(wxRight, y0 + 2, wz1);
                                int ru2 = MathHelper.getWallMirror(wxRight, y0 + 2, wz2);
                                if (ru1 == ru2) {
                                    continue;
                                }
                                int ru3 = MathHelper.getWallMirror(wxRight, y0 + 2, wz3);
                                if (ru2 != ru3) {
                                    continue;
                                }

                                // Lower wall (y = y0 + 1): getWallMirror(d=1) == getWallMirror(d=2) AND getWallMirror(d=2) == getWallMirror(d=3)
                                int rl1 = MathHelper.getWallMirror(wxRight, y0 + 1, wz1);
                                int rl2 = MathHelper.getWallMirror(wxRight, y0 + 1, wz2);
                                if (rl1 != rl2) {
                                    continue;
                                }
                                int rl3 = MathHelper.getWallMirror(wxRight, y0 + 1, wz3);
                                if (rl2 != rl3) {
                                    continue;
                                }

                                // Passed all parity and wall mirror constraints! Now test Bedrock:
                                for (boolean xInner : loopOrders) {
                                    // Check fixed bedrock positions:
                                    // d=0: true
                                    if (!bCache.isBedrock(x0, y0, wz0, xInner)) continue;
                                    // d=1: true
                                    if (!bCache.isBedrock(x0, y0, wz1, xInner)) continue;
                                    // d=2: false
                                    if (bCache.isBedrock(x0, y0, wz2, xInner)) continue;
                                    // d=3: false
                                    if (bCache.isBedrock(x0, y0, wz3, xInner)) continue;

                                    // d=5: true
                                    if (!bCache.isBedrock(x0, y0, wz5, xInner)) continue;
                                    // d=6: true
                                    int wz6 = orientation.tunnelWorldZ(z0, 6);
                                    if (!bCache.isBedrock(x0, y0, wz6, xInner)) continue;

                                    // Check d=4:
                                    boolean b4 = bCache.isBedrock(x0, y0, wz4, xInner);
                                    int matchedBedrock = b4 ? 7 : 6;
                                    double matchPct = (matchedBedrock / 7.0) * 100.0;
                                    String passName = b4 ? "STRICT" : "RELAXED_D4_WILDCARD";

                                    int baseX = x0;
                                    int baseZ = orientation == Orientation.NORTH ? (z0 + stripMineOffset) : (z0 - stripMineOffset);

                                    matches.add(new CandidateMatch(
                                            x0, y0, z0,
                                            orientation,
                                            xInner ? "X_INNER_Z_OUTER" : "Z_INNER_X_OUTER",
                                            b4,
                                            matchedBedrock,
                                            7,
                                            passName,
                                            matchPct,
                                            baseX, 68, baseZ,
                                            x0, y0, z0
                                    ));
                                }
                            }
                        }
                    }
                }
            }));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Sort candidates: strict first, then by match percentage descending
        matches.sort((a, b) -> {
            if (a.isStrict != b.isStrict) return a.isStrict ? -1 : 1;
            return Double.compare(b.matchPercentage, a.matchPercentage);
        });

        return matches;
    }

    private static void exportResults(List<CandidateMatch> results) throws IOException {
        Files.createDirectories(Paths.get("output"));
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("candidateCount", results.size());

        JsonArray arr = new JsonArray();
        for (CandidateMatch c : results) {
            JsonObject m = new JsonObject();
            m.addProperty("x0", c.x0);
            m.addProperty("y0", c.y0);
            m.addProperty("z0", c.z0);
            m.addProperty("orientation", c.orientation.name());
            m.addProperty("bedrockLoopOrder", c.bedrockLoopOrder);
            m.addProperty("pass", c.passName);
            m.addProperty("isStrict", c.isStrict);
            m.addProperty("matchedBedrock", c.matchedBedrockCount);
            m.addProperty("totalBedrock", c.totalBedrockCount);
            m.addProperty("matchPercentage", c.matchPercentage);

            // Compatibility with stage 2 decorator filter
            m.addProperty("baseX", c.baseX);
            m.addProperty("baseY", c.baseY);
            m.addProperty("baseZ", c.baseZ);
            m.addProperty("mineX", c.mineX);
            m.addProperty("mineY", c.mineY);
            m.addProperty("mineZ", c.mineZ);
            arr.add(m);
        }
        root.add("candidates", arr);

        try (FileWriter writer = new FileWriter("output/stage1_coordinates.json")) {
            new Gson().newBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        System.out.println("[INFO] Candidates exported to: output/stage1_coordinates.json");
    }

    public static File resolveFile(String path) {
        File f = new File(path);
        if (f.exists()) return f;
        f = new File("..", path);
        if (f.exists()) return f;
        f = new File("../..", path);
        if (f.exists()) return f;
        return new File(path);
    }
}
