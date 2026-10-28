#!/usr/bin/env bash
set -e

echo "================================================================================"
echo "  Executing Stage 2: Decorator & Biome Seed Cracker"
echo "================================================================================"

echo "[1/2] Running Java Decorator Cracker (Diamond Vein & Poppy Reversal)..."
chmod +x gradlew
./gradlew :stage2-seed-cracker:stage2-decorator-cracker:run

echo ""
echo "[2/2] Running C / Cubiomes Biome & Spawn Filter..."

BIOME_EXE="stage2-seed-cracker/stage2-biome-filter/biome_seed_finder"

if [ ! -f "$BIOME_EXE" ]; then
    if command -v gcc >/dev/null 2>&1; then
        echo "[INFO] Compiling biome_seed_finder with gcc..."
        make -C stage2-seed-cracker/stage2-biome-filter
    elif command -v clang >/dev/null 2>&1; then
        echo "[INFO] Compiling biome_seed_finder with clang..."
        make -C stage2-seed-cracker/stage2-biome-filter CC=clang
    else
        echo "[NOTICE] No C compiler found on PATH (gcc/clang)."
        echo "[NOTICE] Candidate 48-bit seeds have been written to output/candidate_48bit_seeds.txt."
        echo "[NOTICE] Build stage2-seed-cracker/stage2-biome-filter with CMake or Makefile when compiler is ready."
        exit 0
    fi
fi

if [ -f "$BIOME_EXE" ]; then
    "$BIOME_EXE" --seeds output/candidate_48bit_seeds.txt
fi
