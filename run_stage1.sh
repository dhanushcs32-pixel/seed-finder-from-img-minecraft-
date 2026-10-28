#!/usr/bin/env bash
set -e

echo "================================================================================"
echo "  Executing Stage 1: Coordinate Triangulator (Java 17+ / Gradle)"
echo "================================================================================"

chmod +x gradlew
./gradlew :stage1-coord-triangulator:run --args="--config config/scene_constraints.json --input input/formation_data.json"

echo "[SUCCESS] Stage 1 completed successfully. Candidate coordinates saved to output/stage1_coordinates.json."
