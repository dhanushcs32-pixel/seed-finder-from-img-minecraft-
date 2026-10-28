cmake_minimum_required(VERSION 3.16)
project(stage2_biome_filter C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

find_package(OpenMP)

file(GLOB CUBIOMES_SOURCES
    "cubiomes/*.c"
)
list(FILTER CUBIOMES_SOURCES EXCLUDE REGEX ".*(tests|main)\\.c$")

add_library(cubiomes STATIC ${CUBIOMES_SOURCES})
target_include_directories(cubiomes PUBLIC cubiomes)

add_executable(biome_seed_finder biome_seed_finder.c)
target_link_libraries(biome_seed_finder PRIVATE cubiomes)

if(OpenMP_C_FOUND)
    target_link_libraries(biome_seed_finder PRIVATE OpenMP::OpenMP_C)
endif()

if(UNIX)
    target_link_libraries(biome_seed_finder PRIVATE m)
endif()
