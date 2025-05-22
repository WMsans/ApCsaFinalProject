package World.Terrain;

import Configuration.Config;
import World.Block;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import World.FastNoiseLite;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CryoPeakWildsTerrainWorley extends BaseTerrainGenerator {

    private final FastNoiseLite baseNoise;
    private final FastNoiseLite pillarPlacementNoise; // Cellular for placement
    private final FastNoiseLite pillarHeightNoise;    // Perlin for height variation

    private static final float BASE_TERRAIN_HEIGHT = 20.0f;
    private static final float BASE_TERRAIN_AMPLITUDE = 10.0f;

    // Pillar settings for Worley/Cellular noise
    private static final float PILLAR_PLACEMENT_THRESHOLD = 0.08f; // Distance to cell center (small values for pillars)
    // Range of Distance typically 0 to ~1.0 before normalization for default freq
    private static final float PILLAR_MIN_HEIGHT = 25.0f;
    private static final float PILLAR_AMPLITUDE = 180.0f; // Max additional height for pillars
    private static final float PILLAR_JITTER = 0.75f; // Jitter for Cellular noise

    // Define some colors for the terrain
    private static final Vector3f COLOR_SNOW = new Vector3f(0.95f, 0.95f, 0.98f);
    private static final Vector3f COLOR_ICE = new Vector3f(0.6f, 0.8f, 0.95f);
    private static final Vector3f COLOR_ROCK = new Vector3f(0.5f, 0.5f, 0.55f);


    public CryoPeakWildsTerrainWorley(Config config) {
        super(config);
        int worldSeed = config.hashCode(); // Or use a fixed seed

        baseNoise = new FastNoiseLite(worldSeed);
        baseNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        baseNoise.SetFrequency(0.008f);
        baseNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        baseNoise.SetFractalOctaves(4);
        baseNoise.SetFractalLacunarity(2.0f);
        baseNoise.SetFractalGain(0.5f);

        pillarPlacementNoise = new FastNoiseLite(worldSeed + 1);
        pillarPlacementNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        pillarPlacementNoise.SetFrequency(0.048f); // Adjust frequency for pillar density
        pillarPlacementNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq); // Using squared Euclidean
        pillarPlacementNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance); // Distance to closest point
        pillarPlacementNoise.SetCellularJitter(PILLAR_JITTER);

        pillarHeightNoise = new FastNoiseLite(worldSeed + 2); // Separate Perlin for height
        pillarHeightNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        pillarHeightNoise.SetFrequency(0.03f); // Different frequency for height variation
        pillarHeightNoise.SetFractalOctaves(3);
    }

    @Override
    protected Chunk generateChunkData(ChunkId chunkId) {
        Chunk newChunk = new Chunk(chunkId);
        float worldChunkXBase = (float) chunkId.x * Chunk.CHUNK_SIZE_X;
        float worldChunkYBase = (float) chunkId.y * Chunk.CHUNK_SIZE_Y;
        float worldChunkZBase = (float) chunkId.z * Chunk.CHUNK_SIZE_Z;

        List<Block> tempBlockList = new ArrayList<>();

        for (int lx = 0; lx < Chunk.CHUNK_SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.CHUNK_SIZE_Z; lz++) {
                float worldX = worldChunkXBase + lx + 0.5f;
                float worldZ = worldChunkZBase + lz + 0.5f;

                float baseHeightNoiseVal = baseNoise.GetNoise(worldX, worldZ); // -1 to 1
                float currentBaseSurfaceY = BASE_TERRAIN_HEIGHT + baseHeightNoiseVal * BASE_TERRAIN_AMPLITUDE;

                // Cellular noise for pillar placement. Output is distance, typically >= 0.
                // Smaller values mean closer to a cell center.
                float rawPillarPlacementVal = pillarPlacementNoise.GetNoise(worldX, worldZ);

                // Perlin noise for pillar height variation, normalized to 0-1
                float pillarHeightVariation = (pillarHeightNoise.GetNoise(worldX * 0.7f, worldZ * 0.7f) + 1) / 2.0f;

                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f;
                    boolean placeBlock = false;
                    Vector3f blockColor = COLOR_ROCK;

                    if (worldY < currentBaseSurfaceY) {
                        placeBlock = true;
                        if (worldY > currentBaseSurfaceY - 1.5f) {
                            blockColor = COLOR_SNOW;
                        } else if (worldY > currentBaseSurfaceY - 4.0f) {
                            blockColor = COLOR_ICE;
                        } else {
                            blockColor = COLOR_ROCK;
                        }
                    }

                    // Pillar generation: if current point is close to a Worley cell center
                    if (rawPillarPlacementVal < PILLAR_PLACEMENT_THRESHOLD) {
                        float pillarCoreHeight = PILLAR_MIN_HEIGHT + pillarHeightVariation * PILLAR_AMPLITUDE;
                        float actualPillarTopY = currentBaseSurfaceY + pillarCoreHeight; // Pillars rise from the base terrain

                        if (worldY < actualPillarTopY && worldY >= currentBaseSurfaceY - 1.0f) { // Pillars start from base surface
                            placeBlock = true;
                            // Color variation for pillars
                            float pillarRelativeHeight = (worldY - (currentBaseSurfaceY - 1.0f)) / pillarCoreHeight;
                            if (pillarRelativeHeight > 0.85f) {
                                blockColor = COLOR_SNOW;
                            } else if (pillarRelativeHeight > 0.4f) {
                                blockColor = COLOR_ICE;
                            } else {
                                blockColor = COLOR_ROCK;
                            }
                        }
                    }

                    if (placeBlock) {
                        tempBlockList.add(new Block(worldX, worldY, worldZ, blockColor));
                    }
                }
            }
        }

        for (Block b : tempBlockList) {
            newChunk.addBlock(b);
        }
        return newChunk;
    }
}