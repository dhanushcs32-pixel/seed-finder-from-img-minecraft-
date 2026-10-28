# Minecraft Java Edition 1.16.3 Two-Stage Seed Cracker

A high-performance reverse-engineering workspace designed to recover a lost Minecraft Java Edition 1.16.3 world seed from screenshot and formation data.

---

## Architecture Overview

```
experiment/
├── config/
│   └── scene_constraints.json         # Search grid, elevations, orientation, bedrock order
├── input/
│   └── formation_data.json            # Pre-populated template with fixed Y-levels & relative blocks
├── stage1-coord-triangulator/         # Multi-threaded Java 17+ / Gradle Coordinate Solver
│   ├── build.gradle
│   └── src/main/java/com/seedcrack/stage1/
│       ├── Stage1CoordTriangulator.java
│       ├── MathHelper.java            # Vanilla coordinate random hash & bedrock generator
│       ├── BlockObservation.java      # Observation data model
│       └── Orientation.java           # North (-Z) / South (+Z) coordinate & texture transforms
├── stage2-seed-cracker/
│   ├── stage2-decorator-cracker/      # Java 17+ Decorator / Population Seed Cracker
│   │   ├── build.gradle
│   │   └── src/main/java/com/seedcrack/stage2/
│   │       ├── Stage2DecoratorFilter.java
│   │       ├── ChunkRandom.java       # Vanilla 1.16.3 ChunkRandom LCG implementation
│   │       ├── ChunkRandomReversal.java # Truncated LCG seed reversal
│   │       └── FeatureDecoratorModel.java # 1.16.3 Diamond Vein & Poppy cluster decorators
│   └── stage2-biome-filter/           # C11 OpenMP Biome & Spawn Filter with Cubiomes
│       ├── cubiomes/                  # Static library from Cubitect/cubiomes
│       ├── biome_seed_finder.c        # Multi-threaded C11 biome and island spawn finder
│       ├── CMakeLists.txt
│       └── Makefile
├── run_stage1.bat / run_stage1.sh     # Stage 1 executable runners
├── run_stage2.bat / run_stage2.sh     # Stage 2 executable runners
└── output/
    ├── stage1_coordinates.json        # Output from Stage 1 triangulator
    ├── candidate_48bit_seeds.txt      # Filtered 48-bit lower seeds from Stage 2 Java
    └── recovered_world_seeds.txt      # Final recovered 64-bit world seeds
```

---

## Mathematical Specifications (Java 1.16.3)

### 1. Block Texture Rotation Hash
Vanilla Minecraft uses `MathHelper.getCoordinateRandom(x, y, z)`:
```java
public static long getCoordinateRandom(int x, int y, int z) {
    long i = (long)(x * 3129871) ^ (long)z * 116129781L ^ (long)y;
    i = i * i * 42317861L + i * 11L;
    return i >> 16;
}

public static int getTextureVariant(int x, int y, int z, int mod) {
    long rand = getCoordinateRandom(x, y, z);
    return Math.abs((int)rand) % mod; // mod = 4 for Top faces (0, 90, 180, 270 deg)
}
```

### 2. Flower & Grass Sub-Block Offsets
Calls `MathHelper.getCoordinateRandom(x, 0, z)`:
- **X offset index (0..15):** `(int)(getCoordinateRandom(x, 0, z) & 15L)`
- **Z offset index (0..15):** `(int)((getCoordinateRandom(x, 0, z) >> 8) & 15L)`
- **Grass Y offset index (0..15):** `(int)((getCoordinateRandom(x, 0, z) >> 4) & 15L)`

### 3. Pre-1.18 Overworld Bedrock Generation
Per-chunk bedrock is seeded by `(chunkX, chunkZ)`:
```java
long chunkSeed = (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L;
Random rand = new Random(chunkSeed);
```
Supported loop orders configurable via `"bedrockLoopOrder"`:
- `X_INNER_Z_OUTER`
- `Z_INNER_X_OUTER`
- `BOTH`

### 4. Population & Decorator Seed Derivation
```java
Random r = new Random(worldSeed);
long a = r.nextLong() | 1L;
long b = r.nextLong() | 1L;
long popSeed = (long)blockX * a + (long)blockZ * b ^ worldSeed;
long decoratorSeed = popSeed + (long)featureIndex + (long)(10000 * step);
```
- **Diamond Ore:** `step = 6` (`GenerationStep.Feature.UNDERGROUND_ORES`), `OreFeature` size 8, range 0..16 at `Y=4`.
- **Poppy Flower Patch:** `step = 8` (`GenerationStep.Feature.VEGETAL_DECORATION`) at `Y=72` and `Y=73`.

---

## How to Use

### Step 1: Input Screenshot Observations
Edit `input/formation_data.json`:
- All $Y$-levels are already pre-populated:
  - Door threshold: $Y=68$ at $(0, 68, 0)$.
  - Sand slope: $Y=63$ to $Y=70$.
  - Poppies: $Y=72$ and $Y=73$ (on grass at $Y=71$ and $Y=72$).
  - Strip mine floor: $Y=4$ with exposed bedrock and diamond ore vein.
- Fill in the observed `observedVariant` values ($0, 1, 2, 3$) for top textures and sub-pixel offsets.

### Step 2: Run Stage 1 (Coordinate Triangulation)
Windows:
```powershell
.\run_stage1.bat
```
Linux / macOS:
```bash
./run_stage1.sh
```
The triangulator automatically scans $X, Z \in [-10000, 10000]$ (400M blocks) in $\sim 25$ seconds using all available CPU threads and writes candidate matches to `output/stage1_coordinates.json`.

### Step 3: Run Stage 2 (Decorator & Biome Seed Cracking)
Windows:
```powershell
.\run_stage2.bat
```
Linux / macOS:
```bash
./run_stage2.sh
```
1. Stage 2 Java reads the $(X, Z)$ coordinates and filters 48-bit decorator seeds using the $Y=4$ Diamond Vein and Poppy patches into `output/candidate_48bit_seeds.txt`.
2. Stage 2 C (`biome_seed_finder.c` linked against `cubiomes`) tests the upper 16-bit combinations with OpenMP multi-threading to enforce:
   - **Bamboo Jungle / Bamboo Jungle Hills** at `(baseX, baseZ)`
   - **Western Lagoon Transition** (Beach + Ocean variant within 16..64 blocks West)
   - **Small-Island Ocean Spawn** (Spawn West of Base, land area $< 150$ blocks surrounded by ocean)
3. Outputs recovered 64-bit world seeds to `output/recovered_world_seeds.txt`.
