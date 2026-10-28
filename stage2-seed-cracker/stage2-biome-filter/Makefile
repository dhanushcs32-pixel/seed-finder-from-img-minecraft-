CC ?= gcc
CFLAGS ?= -O3 -Wall -std=c11 -fopenmp -Icubiomes
LDFLAGS ?= -fopenmp -lm

CUBIOMES_SRC = cubiomes/biomenoise.c cubiomes/biomes.c cubiomes/finders.c \
               cubiomes/generator.c cubiomes/layers.c cubiomes/noise.c \
               cubiomes/quadbase.c cubiomes/util.c

all: biome_seed_finder

biome_seed_finder: biome_seed_finder.c $(CUBIOMES_SRC)
	$(CC) $(CFLAGS) biome_seed_finder.c $(CUBIOMES_SRC) $(LDFLAGS) -o biome_seed_finder

clean:
	rm -f biome_seed_finder biome_seed_finder.exe *.o
