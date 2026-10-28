@echo off
setlocal

echo ================================================================================
echo   Executing Stage 2: Decorator & Biome Seed Cracker
echo ================================================================================

echo [1/2] Running Java Decorator Cracker (Diamond Vein & Poppy Reversal)...
call gradlew.bat :stage2-seed-cracker:stage2-decorator-cracker:run

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java Decorator Filter failed with exit code %ERRORLEVEL%.
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Running C / Cubiomes Biome ^& Spawn Filter...

set BIOME_EXE=stage2-seed-cracker\stage2-biome-filter\biome_seed_finder.exe

if not exist "%BIOME_EXE%" (
    where gcc >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        echo [INFO] Compiling biome_seed_finder.c with GCC...
        pushd stage2-seed-cracker\stage2-biome-filter
        gcc -O3 -std=c11 -fopenmp biome_seed_finder.c cubiomes\biomenoise.c cubiomes\biomes.c cubiomes\finders.c cubiomes\generator.c cubiomes\layers.c cubiomes\noise.c cubiomes\quadbase.c cubiomes\util.c -lm -o biome_seed_finder.exe
        popd
    ) else (
        where cl >nul 2>nul
        if %ERRORLEVEL% EQU 0 (
            echo [INFO] Compiling biome_seed_finder.c with MSVC...
            pushd stage2-seed-cracker\stage2-biome-filter
            cl /O2 /openmp biome_seed_finder.c cubiomes\*.c /Fe:biome_seed_finder.exe
            popd
        ) else (
            echo [NOTICE] GCC or MSVC not found on PATH.
            echo [NOTICE] 48-bit seeds have been cracked and written to output/candidate_48bit_seeds.txt.
            echo [NOTICE] Once gcc or cmake is installed, build stage2-seed-cracker/stage2-biome-filter/
            echo [NOTICE] or compile using: gcc -O3 -std=c11 -fopenmp biome_seed_finder.c cubiomes/*.c -lm -o biome_seed_finder.exe
            exit /b 0
        )
    )
)

if exist "%BIOME_EXE%" (
    "%BIOME_EXE%" --seeds output\candidate_48bit_seeds.txt
)
