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
            int baseX,
            int baseY,
            int baseZ,
            int mineX,
            int mineY,
            int mineZ,
            Orientation orientation,
            String bedrockLoopOrder,
            int totalBlocksChecked,
            int matchingBlocks,
            double matchPercentage
    ) {}

    private static class ThreadLocalBedrockCache {
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        boolean cachedXInner;
        boolean[][][] bedrockData;

        public boolean isBedrock(int worldX, int y, int worldZ, boolean xInnerZOuter) {
            if (y == 0) return true;
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
        System.out.println("   Multi-threaded Texture Rotation & Bedrock Reverse-Engineering Engine         ");
        System.out.println("================================================================================");

        String configPath = "config/scene_constraints.json";
        String formationPath = "input/formation_data.json";

        for (int i = 0; i < args.length; i++) {
            if ("--config".equals(args[i]) && i + 1 < args.length) configPath = args[++i];
            if ("--input".equals(args[i]) && i + 1 < args.length) formationPath = args[++i];
        }

        File configFile = resolveFile(configPath);
        File formationFile = resolveFile(formationPath);

        if (!configFile.exists() || !formationFile.exists()) {
            System.err.printf("[ERROR] Missing input files: config=%s (exists=%b), formation=%s (exists=%b)%n",
                    configPath, configFile.exists(), formationPath, formationFile.exists());
            System.exit(1);
        }

        try {
            JsonObject config = new Gson().fromJson(new FileReader(configFile), JsonObject.class);
            JsonObject formation = new Gson().fromJson(new FileReader(formationFile), JsonObject.class);

            List<BlockObservation> surfaceBlocks = new ArrayList<>();
            List<BlockObservation> bedrockBlocks = new ArrayList<>();
            int stripMineOffset = parseObservations(formation, surfaceBlocks, bedrockBlocks);

            JsonObject bounds = config.getAsJsonObject("searchBounds");
            int minX = bounds.get("minX").getAsInt();
            int maxX = bounds.get("maxX").getAsInt();
            int minZ = bounds.get("minZ").getAsInt();
            int maxZ = bounds.get("maxZ").getAsInt();

            int threadCount = config.has("threadCount") && config.get("threadCount").getAsInt() > 0
                    ? config.get("threadCount").getAsInt()
                    : Runtime.getRuntime().availableProcessors();

            List<Orientation> orientations = new ArrayList<>();
            JsonArray orientArr = config.getAsJsonArray("orientations");
            if (orientArr != null) {
                for (JsonElement e : orientArr) {
                    orientations.add(Orientation.valueOf(e.getAsString().toUpperCase()));
                }
            } else {
                orientations.add(Orientation.SOUTH);
                orientations.add(Orientation.NORTH);
            }

            List<Boolean> loopOrders = new ArrayList<>();
            String loopConfig = config.has("bedrockLoopOrder") ? config.get("bedrockLoopOrder").getAsString() : "BOTH";
            if ("BOTH".equalsIgnoreCase(loopConfig)) {
                loopOrders.add(true);  // X-inner, Z-outer
                loopOrders.add(false); // Z-inner, X-outer
            } else if ("X_INNER_Z_OUTER".equalsIgnoreCase(loopConfig)) {
                loopOrders.add(true);
            } else {
                loopOrders.add(false);
            }

            System.out.printf("[INFO] Search Grid: X [%d .. %d], Z [%d .. %d]%n", minX, maxX, minZ, maxZ);
            System.out.printf("[INFO] Surface Blocks Loaded: %d | Bedrock Blocks: %d%n", surfaceBlocks.size(), bedrockBlocks.size());
            System.out.printf("[INFO] Orientations to Test: %s | Bedrock Loop Orders: %s%n", orientations, loopConfig);
            System.out.printf("[INFO] Worker Threads Allocated: %d%n", threadCount);
            System.out.println("--------------------------------------------------------------------------------");

            long startTime = System.currentTimeMillis();
            List<CandidateMatch> results = executeSearch(
                    minX, maxX, minZ, maxZ,
                    orientations, loopOrders,
                    surfaceBlocks, bedrockBlocks,
                    stripMineOffset, threadCount
            );
            long elapsed = System.currentTimeMillis() - startTime;

            System.out.println("\n================================================================================");
            System.out.printf("   Triangulation Completed in %d ms (%.2f s)%n", elapsed, elapsed / 1000.0);
            System.out.printf("   Candidates Found: %d%n", results.size());
            System.out.println("================================================================================");

            if (results.isEmpty()) {
                System.out.println("[WARN] No candidate matched 100% of the observed block constraints.");
                System.out.println("[TIP] Verify observed texture variants in 'input/formation_data.json'.");
            } else {
                System.out.printf("%-16s | %-16s | %-12s | %-18s | %-10s%n",
                        "Base (X, Y, Z)", "Mine (X, Y, Z)", "Orientation", "Bedrock Loop", "Confidence");
                System.out.println("--------------------------------------------------------------------------------");
                for (CandidateMatch c : results) {
                    System.out.printf("(%6d, %2d, %6d) | (%6d, %2d, %6d) | %-12s | %-18s | %6.2f%%%n",
                            c.baseX, c.baseY, c.baseZ,
                            c.mineX, c.mineY, c.mineZ,
                            c.orientation, c.bedrockLoopOrder, c.matchPercentage);
                }
            }

            // Export to output/stage1_coordinates.json
            exportResults(results, formation, stripMineOffset);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int parseObservations(JsonObject formation, List<BlockObservation> surface, List<BlockObservation> bedrock) {
        if (formation.has("doorOrigin")) {
            JsonObject o = formation.getAsJsonObject("doorOrigin");
            surface.add(parseBlock(o));
        }

        if (formation.has("sandSlope")) {
            for (JsonElement el : formation.getAsJsonArray("sandSlope")) {
                surface.add(parseBlock(el.getAsJsonObject()));
            }
        }

        if (formation.has("houseSurroundings")) {
            for (JsonElement el : formation.getAsJsonArray("houseSurroundings")) {
                surface.add(parseBlock(el.getAsJsonObject()));
            }
        }

        if (formation.has("poppies")) {
            for (JsonElement el : formation.getAsJsonArray("poppies")) {
                JsonObject p = el.getAsJsonObject();
                surface.add(parseBlock(p));
                if (p.has("underlyingBlock")) {
                    surface.add(parseBlock(p.getAsJsonObject("underlyingBlock")));
                }
            }
        }

        int stripMineOffset = 80;
        if (formation.has("stripMine")) {
            JsonObject sm = formation.getAsJsonObject("stripMine");
            if (sm.has("relativeOffsetAlongAxis")) {
                stripMineOffset = sm.get("relativeOffsetAlongAxis").getAsInt();
            }
            if (sm.has("blocks")) {
                for (JsonElement el : sm.getAsJsonArray("blocks")) {
                    JsonObject b = el.getAsJsonObject();
                    BlockObservation bo = parseBlock(b);
                    if (bo.getType() == BlockObservation.Type.BEDROCK_PRESENT ||
                        bo.getType() == BlockObservation.Type.BEDROCK_ABSENT) {
                        bedrock.add(bo);
                    } else {
                        surface.add(bo);
                    }
                }
            }
        }
        return stripMineOffset;
    }

    private static BlockObservation parseBlock(JsonObject obj) {
        int dx = obj.get("dx").getAsInt();
        int y = obj.get("y").getAsInt();
        int dz = obj.get("dz").getAsInt();
        BlockObservation.Type type = BlockObservation.Type.valueOf(obj.get("type").getAsString().toUpperCase());
        int observedVariant = obj.has("observedVariant") ? obj.get("observedVariant").getAsInt() : 0;
        int offX = obj.has("observedOffsetX") ? obj.get("observedOffsetX").getAsInt() : 0;
        int offZ = obj.has("observedOffsetZ") ? obj.get("observedOffsetZ").getAsInt() : 0;
        String comment = obj.has("comment") ? obj.get("comment").getAsString() : "";
        return new BlockObservation(dx, y, dz, type, observedVariant, offX, offZ, comment);
    }

    private static List<CandidateMatch> executeSearch(
            int minX, int maxX, int minZ, int maxZ,
            List<Orientation> orientations,
            List<Boolean> loopOrders,
            List<BlockObservation> surfaceBlocks,
            List<BlockObservation> bedrockBlocks,
            int stripMineOffset,
            int threadCount
    ) throws InterruptedException {
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

                for (int baseX = chunkMinX; baseX <= chunkMaxX; baseX++) {
                    for (int baseZ = minZ; baseZ <= maxZ; baseZ++) {
                        positionsTested.incrementAndGet();

                        for (Orientation orientation : orientations) {
                            // Stage 1a: Fast surface texture prune
                            boolean surfaceMatch = true;
                            for (BlockObservation b : surfaceBlocks) {
                                int wx = orientation.toWorldX(baseX, b.getDx());
                                int wz = orientation.toWorldZ(baseZ, b.getDz());

                                if (b.getType() == BlockObservation.Type.POPPY_OFFSET) {
                                    int expectedOffX = orientation.adjustExpectedOffsetX(b.getObservedOffsetX());
                                    int expectedOffZ = orientation.adjustExpectedOffsetZ(b.getObservedOffsetZ());
                                    int actualOffX = MathHelper.getFlowerOffsetX(wx, wz);
                                    int actualOffZ = MathHelper.getFlowerOffsetZ(wx, wz);
                                    if (actualOffX != expectedOffX || actualOffZ != expectedOffZ) {
                                        surfaceMatch = false;
                                        break;
                                    }
                                } else {
                                    int expectedVar = orientation.adjustExpectedVariant(b.getObservedVariant(), b.getType().modulus);
                                    int actualVar = MathHelper.getTextureVariant(wx, b.getY(), wz, b.getType().modulus);
                                    if (actualVar != expectedVar) {
                                        surfaceMatch = false;
                                        break;
                                    }
                                }
                            }

                            if (!surfaceMatch) continue;

                            // Stage 1b: Bedrock pattern validation
                            for (boolean xInner : loopOrders) {
                                boolean bedrockMatch = true;
                                for (BlockObservation bb : bedrockBlocks) {
                                    int wx = orientation.toWorldX(baseX, bb.getDx());
                                    int wz = orientation.toWorldZ(baseZ, bb.getDz());
                                    boolean isBedrock = bCache.isBedrock(wx, bb.getY(), wz, xInner);

                                    if (bb.getType() == BlockObservation.Type.BEDROCK_PRESENT && !isBedrock) {
                                        bedrockMatch = false;
                                        break;
                                    }
                                    if (bb.getType() == BlockObservation.Type.BEDROCK_ABSENT && isBedrock) {
                                        bedrockMatch = false;
                                        break;
                                    }
                                }

                                if (bedrockMatch) {
                                    int mineX = orientation.toWorldX(baseX, 0);
                                    int mineZ = orientation.toWorldZ(baseZ, stripMineOffset);
                                    int totalBlocks = surfaceBlocks.size() + bedrockBlocks.size();
                                    matches.add(new CandidateMatch(
                                            baseX, 68, baseZ,
                                            mineX, 4, mineZ,
                                            orientation,
                                            xInner ? "X_INNER_Z_OUTER" : "Z_INNER_X_OUTER",
                                            totalBlocks, totalBlocks, 100.0
                                    ));
                                }
                            }
                        }
                    }
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        return matches;
    }

    private static void exportResults(List<CandidateMatch> results, JsonObject formation, int stripMineOffset) throws IOException {
        Files.createDirectories(Paths.get("output"));
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("candidateCount", results.size());

        JsonArray arr = new JsonArray();
        for (CandidateMatch c : results) {
            JsonObject m = new JsonObject();
            m.addProperty("baseX", c.baseX);
            m.addProperty("baseY", c.baseY);
            m.addProperty("baseZ", c.baseZ);
            m.addProperty("mineX", c.mineX);
            m.addProperty("mineY", c.mineY);
            m.addProperty("mineZ", c.mineZ);
            m.addProperty("orientation", c.orientation.name());
            m.addProperty("bedrockLoopOrder", c.bedrockLoopOrder);
            m.addProperty("matchPercentage", c.matchPercentage);
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
