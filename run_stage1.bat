@echo off
setlocal

echo ================================================================================
echo   Executing Stage 1: Coordinate Triangulator (Java 17+ / Gradle)
echo ================================================================================

call gradlew.bat :stage1-coord-triangulator:run --args="--config config/scene_constraints.json --input input/formation_data.json"

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Stage 1 failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Stage 1 completed successfully. Candidate coordinates saved to output/stage1_coordinates.json.
