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

public class Stage1CrossFilter {

    public record FilteredCandidate(
            int x0,
            int y0,
            int z0,
            Orientation orientation,
            String bedrockLoopOrder,
            String pass,
            boolean isStrict,
            int matchedBedrock,
            int totalBedrock,
            double matchPercentage,
            int tier,
            String tierDescription,
            int matchK,
            int matchDx,
            int matchDz,
            int diamondX,
            int diamondY,
            int diamondZ,
            int baseX,
            int baseY,
            int baseZ,
            int mineX,
            int mineY,
            int mineZ
    ) {}

    private static class BedrockChunkCache {
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        boolean[][][] bedrockData;

        public boolean isBedrock(int worldX, int y, int worldZ) {
            if (y < 0 || y > 4) return false;
            int chunkX = worldX >> 4;
            int chunkZ = worldZ >> 4;

            if (bedrockData == null || cachedChunkX != chunkX || cachedChunkZ != chunkZ) {
                cachedChunkX = chunkX;
                cachedChunkZ = chunkZ;
                bedrockData = MathHelper.generateBedrockChunk(chunkX, chunkZ, true); // true = X_INNER_Z_OUTER (BlockPos.iterate)
            }
            int localX = worldX & 15;
            int localZ = worldZ & 15;
            return bedrockData[localX][localZ][y];
        }
    }

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   Minecraft Java 1.16.3 Seed Cracker - Stage 1 Cross-Filter                    ");
        System.out.println("   Cross-Filtering Candidates with 18.29.12.png (Collinear & Branch Scan)       ");
        System.out.println("================================================================================");

        File candidatesFile = new File("output/stage1_coordinates.json");
        if (!candidatesFile.exists()) {
            System.err.println("[ERROR] Missing output/stage1_coordinates.json. Run Stage 1 first.");
            System.exit(1);
        }

        try {
            JsonObject root = new Gson().fromJson(new FileReader(candidatesFile), JsonObject.class);
            JsonArray arr = root.getAsJsonArray("candidates");

            System.out.printf("[INFO] Loaded %d candidates from output/stage1_coordinates.json%n", arr.size());

            // Step 1: Filter to keep ONLY candidates with true BlockPos.iterate order (X_INNER_Z_OUTER)
            List<JsonObject> trueLoopOrderCandidates = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String loopOrder = obj.get("bedrockLoopOrder").getAsString();
                if ("X_INNER_Z_OUTER".equalsIgnoreCase(loopOrder)) {
                    trueLoopOrderCandidates.add(obj);
                }
            }

            System.out.printf("[INFO] Filtered to %d candidates with true BlockPos.iterate bedrock order (X_INNER_Z_OUTER)%n",
                    trueLoopOrderCandidates.size());

            BedrockChunkCache bCache = new BedrockChunkCache();
            List<FilteredCandidate> tier1List = new ArrayList<>();
            List<FilteredCandidate> tier2List = new ArrayList<>();
            List<FilteredCandidate> tier3List = new ArrayList<>();

            for (JsonObject obj : trueLoopOrderCandidates) {
                int x0 = obj.get("x0").getAsInt();
                int y0 = obj.get("y0").getAsInt();
                int z0 = obj.get("z0").getAsInt();
                Orientation orientation = Orientation.valueOf(obj.get("orientation").getAsString());
                String pass = obj.get("pass").getAsString();
                boolean isStrict = obj.get("isStrict").getAsBoolean();
                int matchedBedrock = obj.get("matchedBedrock").getAsInt();
                int totalBedrock = obj.get("totalBedrock").getAsInt();
                double matchPercentage = obj.get("matchPercentage").getAsDouble();

                int baseX = obj.has("baseX") ? obj.get("baseX").getAsInt() : x0;
                int baseY = obj.has("baseY") ? obj.get("baseY").getAsInt() : 68;
                int baseZ = obj.has("baseZ") ? obj.get("baseZ").getAsInt() : (orientation == Orientation.NORTH ? z0 + 80 : z0 - 80);

                // Tier 1 Scan: Collinear (dx = 0, k in [-60, +60])
                boolean foundTier1 = false;
                for (int k = -60; k <= 60; k++) {
                    int zSeg = orientation.tunnelWorldZ(z0, k);
                    if (evaluateSegment182912(bCache, x0, y0, zSeg, orientation)) {
                        int diamondX = x0;
                        int diamondZ = orientation.tunnelWorldZ(zSeg, 4); // Diamond ore vein at d'=4
                        tier1List.add(new FilteredCandidate(
                                x0, y0, z0, orientation, "X_INNER_Z_OUTER", pass, isStrict,
                                matchedBedrock, totalBedrock, matchPercentage,
                                1, "Tier 1: Exact Collinear Match (k=" + k + ")",
                                k, 0, k,
                                diamondX, y0, diamondZ,
                                baseX, baseY, baseZ,
                                x0, y0, z0
                        ));
                        foundTier1 = true;
                        break;
                    }
                }

                if (foundTier1) continue;

                // Tier 2 Scan: Nearby parallel/perpendicular branch within dx in [-16, +16], dz in [-60, +60]
                boolean foundTier2 = false;
                for (int dx = -16; dx <= 16; dx++) {
                    if (dx == 0) continue; // dx=0 already tested in Tier 1
                    for (int dz = -60; dz <= 60; dz++) {
                        int xSeg = orientation.tunnelWorldX(x0, dx);
                        int zSeg = orientation.tunnelWorldZ(z0, dz);
                        if (evaluateSegment182912(bCache, xSeg, y0, zSeg, orientation)) {
                            int diamondX = xSeg;
                            int diamondZ = orientation.tunnelWorldZ(zSeg, 4);
                            tier2List.add(new FilteredCandidate(
                                    x0, y0, z0, orientation, "X_INNER_Z_OUTER", pass, isStrict,
                                    matchedBedrock, totalBedrock, matchPercentage,
                                    2, "Tier 2: Nearby Branch Match (dx=" + dx + ", dz=" + dz + ")",
                                    dz, dx, dz,
                                    diamondX, y0, diamondZ,
                                    baseX, baseY, baseZ,
                                    x0, y0, z0
                            ));
                            foundTier2 = true;
                            break;
                        }
                    }
                    if (foundTier2) break;
                }

                if (foundTier2) continue;

                // Tier 3: All remaining strict Y0 = 4 candidates with true BlockPos.iterate order
                if (y0 == 4 && isStrict) {
                    int diamondX = x0;
                    int diamondZ = z0;
                    tier3List.add(new FilteredCandidate(
                            x0, y0, z0, orientation, "X_INNER_Z_OUTER", pass, isStrict,
                            matchedBedrock, totalBedrock, matchPercentage,
                            3, "Tier 3: Strict Y0=4 Candidate",
                            0, 0, 0,
                            diamondX, y0, diamondZ,
                            baseX, baseY, baseZ,
                            x0, y0, z0
                    ));
                }
            }

            List<FilteredCandidate> allFiltered = new ArrayList<>();
            tier2List.sort((a, b) -> Integer.compare(b.y0(), a.y0()));
            allFiltered.addAll(tier1List);
            allFiltered.addAll(tier2List);
            allFiltered.addAll(tier3List);

            System.out.println("================================================================================");
            System.out.printf("   Cross-Filter Complete! Total Filtered Candidates: %d%n", allFiltered.size());
            System.out.printf("   Tier 1 (Exact Collinear): %d | Tier 2 (Nearby Branch): %d | Tier 3 (Strict Y=4): %d%n",
                    tier1List.size(), tier2List.size(), tier3List.size());
            System.out.println("================================================================================");

            System.out.printf("%-18s | %-12s | %-6s | %-32s | %-18s%n",
                    "Tunnel (x0, y0, z0)", "Orientation", "Tier", "Match Details", "Diamond (X, Y, Z)");
            System.out.println("--------------------------------------------------------------------------------");
            for (FilteredCandidate c : allFiltered) {
                System.out.printf("(%6d, %2d, %6d) | %-12s | Tier %d | %-32s | (%6d, %2d, %6d)%n",
                        c.x0, c.y0, c.z0, c.orientation, c.tier, c.tierDescription,
                        c.diamondX, c.diamondY, c.diamondZ);
            }

            exportFilteredResults(allFiltered);

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean evaluateSegment182912(
            BedrockChunkCache bCache,
            int xSeg, int y0, int zSeg,
            Orientation orientation
    ) {
        // 1. Floor Bedrock Absence: All 6 floor blocks (d'=0..5) have isBedrock == false
        for (int d = 0; d <= 5; d++) {
            int wz = orientation.tunnelWorldZ(zSeg, d);
            if (bCache.isBedrock(xSeg, y0, wz)) {
                return false;
            }
        }

        // World Z coordinates for d'=0..3
        int wz0 = orientation.tunnelWorldZ(zSeg, 0);
        int wz1 = orientation.tunnelWorldZ(zSeg, 1);
        int wz2 = orientation.tunnelWorldZ(zSeg, 2);
        int wz3 = orientation.tunnelWorldZ(zSeg, 3);

        // 2. Floor Stone Parity (y = y0)
        int fp0 = MathHelper.getTopParity(xSeg, y0, wz0);
        int fp1 = MathHelper.getTopParity(xSeg, y0, wz1);
        if (fp0 != fp1) return false;

        int fp2 = MathHelper.getTopParity(xSeg, y0, wz2);
        if (fp1 != fp2) return false;

        int fp3 = MathHelper.getTopParity(xSeg, y0, wz3);
        if (fp2 == fp3) return false;

        // 3. Ceiling Stone Parity (y = y0 + 3)
        int yCeil = y0 + 3;
        int cp0 = MathHelper.getTopParity(xSeg, yCeil, wz0);
        int cp1 = MathHelper.getTopParity(xSeg, yCeil, wz1);
        if (cp0 == cp1) return false;

        int cp2 = MathHelper.getTopParity(xSeg, yCeil, wz2);
        if (cp1 != cp2) return false;

        int cp3 = MathHelper.getTopParity(xSeg, yCeil, wz3);
        if (cp2 == cp3) return false;

        // 4. Wall Mirror Relationships
        // Left Wall (dx_cam = -1)
        int wxLeft = orientation.tunnelWorldX(xSeg, -1);
        int lu1 = MathHelper.getWallMirror(wxLeft, y0 + 2, wz1);
        int lu2 = MathHelper.getWallMirror(wxLeft, y0 + 2, wz2);
        if (lu1 == lu2) return false;
        int lu3 = MathHelper.getWallMirror(wxLeft, y0 + 2, wz3);
        if (lu2 != lu3) return false; // M(1) != M(2) and M(2) == M(3)

        int ll1 = MathHelper.getWallMirror(wxLeft, y0 + 1, wz1);
        int ll2 = MathHelper.getWallMirror(wxLeft, y0 + 1, wz2);
        if (ll1 == ll2) return false;
        int ll3 = MathHelper.getWallMirror(wxLeft, y0 + 1, wz3);
        if (ll2 == ll3) return false; // M(1) != M(2) and M(2) != M(3)

        // Right Wall (dx_cam = +1)
        int wxRight = orientation.tunnelWorldX(xSeg, 1);
        int ru1 = MathHelper.getWallMirror(wxRight, y0 + 2, wz1);
        int ru2 = MathHelper.getWallMirror(wxRight, y0 + 2, wz2);
        if (ru1 != ru2) return false;
        int ru3 = MathHelper.getWallMirror(wxRight, y0 + 2, wz3);
        if (ru2 == ru3) return false; // M(1) == M(2) and M(2) != M(3)

        int rl1 = MathHelper.getWallMirror(wxRight, y0 + 1, wz1);
        int rl2 = MathHelper.getWallMirror(wxRight, y0 + 1, wz2);
        if (rl1 != rl2) return false;
        int rl3 = MathHelper.getWallMirror(wxRight, y0 + 1, wz3);
        if (rl2 != rl3) return false; // M(1) == M(2) and M(2) == M(3)

        return true;
    }

    private static void exportFilteredResults(List<FilteredCandidate> results) throws IOException {
        Files.createDirectories(Paths.get("output"));
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("filteredCount", results.size());

        JsonArray arr = new JsonArray();
        for (FilteredCandidate c : results) {
            JsonObject m = new JsonObject();
            m.addProperty("x0", c.x0);
            m.addProperty("y0", c.y0);
            m.addProperty("z0", c.z0);
            m.addProperty("orientation", c.orientation.name());
            m.addProperty("bedrockLoopOrder", c.bedrockLoopOrder);
            m.addProperty("pass", c.pass);
            m.addProperty("isStrict", c.isStrict);
            m.addProperty("tier", c.tier);
            m.addProperty("tierDescription", c.tierDescription);
            m.addProperty("diamondX", c.diamondX);
            m.addProperty("diamondY", c.diamondY);
            m.addProperty("diamondZ", c.diamondZ);
            m.addProperty("baseX", c.baseX);
            m.addProperty("baseY", c.baseY);
            m.addProperty("baseZ", c.baseZ);
            m.addProperty("mineX", c.mineX);
            m.addProperty("mineY", c.mineY);
            m.addProperty("mineZ", c.mineZ);
            arr.add(m);
        }
        root.add("candidates", arr);

        try (FileWriter writer = new FileWriter("output/stage1_filtered_coordinates.json")) {
            new Gson().newBuilder().setPrettyPrinting().create().toJson(root, writer);
        }
        System.out.println("[INFO] Filtered candidates saved to: output/stage1_filtered_coordinates.json");
    }
}

