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

public class CryoPeakWildsTerrain extends BaseTerrainGenerator {

    private final FastNoiseLite baseNoise;
    private final FastNoiseLite pillarNoise;
    private final Random random;

    private static final float BASE_TERRAIN_HEIGHT = 20.0f;
    private static final float BASE_TERRAIN_AMPLITUDE = 32.0f;
    private static final float PILLAR_THRESHOLD = 0.6f; // Noise value above which pillars generate
    private static final float PILLAR_MIN_HEIGHT = 15.0f;
    private static final float PILLAR_AMPLITUDE = 150.0f; // Max additional height for pillars
    // private static final int PILLAR_RADIUS = 3; // Radius of pillars in blocks (currently unused directly for shape)

    // Define some colors for the terrain
    private static final Vector3f COLOR_SNOW = new Vector3f(0.95f, 0.95f, 0.98f);
    private static final Vector3f COLOR_ICE = new Vector3f(0.6f, 0.8f, 0.95f);
    private static final Vector3f COLOR_ROCK = new Vector3f(0.5f, 0.5f, 0.55f);

    // Constants for sine wave carving
    private static final float SINE_WAVE_FREQUENCY_X_TO_Z = 25.0f; // Controls period of sin(x)
    private static final float SINE_WAVE_AMPLITUDE_X_TO_Z = 6.0f;  // Controls z-offset magnitude for sin(x)
    private static final float SINE_WAVE_FREQUENCY_Z_TO_X = 25.0f; // Controls period of sin(z)
    private static final float SINE_WAVE_AMPLITUDE_Z_TO_X = 6.0f;  // Controls x-offset magnitude for sin(z)
    private static final float SINE_WAVE_THICKNESS = 5.0f;      // How wide the carved path is
    private static final float WAVE_SEPARATION_DISTANCE = 50.0f; // Distance between parallel sine wave paths


    public CryoPeakWildsTerrain(Config config) {
        super(config);
        int worldSeed = config.hashCode(); // Or use a fixed seed if you prefer
        this.random = new Random(worldSeed);

        baseNoise = new FastNoiseLite(worldSeed);
        baseNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        baseNoise.SetFrequency(0.008f); // Lower frequency for broader base terrain
        baseNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        baseNoise.SetFractalOctaves(4);
        baseNoise.SetFractalLacunarity(2.0f);
        baseNoise.SetFractalGain(0.5f);

        pillarNoise = new FastNoiseLite(worldSeed + 1); // Different seed for pillar placement/height
        pillarNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        pillarNoise.SetFrequency(0.025f); // Higher frequency for more varied pillar placement
        pillarNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        pillarNoise.SetFractalOctaves(3);
        pillarNoise.SetFractalLacunarity(2.2f);
        pillarNoise.SetFractalGain(0.6f);
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

                // Generate base terrain height
                float baseHeightNoiseVal = baseNoise.GetNoise(worldX, worldZ); // -1 to 1
                float currentBaseSurfaceY = BASE_TERRAIN_HEIGHT + baseHeightNoiseVal * BASE_TERRAIN_AMPLITUDE;

                // Generate pillar noise
                float pillarPlacementNoiseVal = (pillarNoise.GetNoise(worldX, worldZ) + 1) / 2f; // 0 to 1
                float pillarHeightNoiseVal = (pillarNoise.GetNoise(worldX * 0.5f, worldZ * 0.5f) +1) / 2f; // 0 to 1, slightly different scale for height

                boolean isPillarCandidateLocation = pillarPlacementNoiseVal > PILLAR_THRESHOLD;
                boolean carveOutPillarForThisColumn = false;

                if (isPillarCandidateLocation) {
                    // Carving pattern 1: z = sin(x) style + repetitions
                    double baseSineZ = Math.sin(worldX / SINE_WAVE_FREQUENCY_X_TO_Z) * SINE_WAVE_AMPLITUDE_X_TO_Z;
                    double diffZ = worldZ - baseSineZ;
                    double modDiffZ = diffZ % WAVE_SEPARATION_DISTANCE;
                    if (modDiffZ < 0) {
                        modDiffZ += WAVE_SEPARATION_DISTANCE;
                    }
                    if (modDiffZ < SINE_WAVE_THICKNESS || modDiffZ > (WAVE_SEPARATION_DISTANCE - SINE_WAVE_THICKNESS)) {
                        carveOutPillarForThisColumn = true;
                    }

                    // Carving pattern 2: x = sin(z) style + repetitions
                    if (!carveOutPillarForThisColumn) {
                        double baseSineX = Math.sin(worldZ / SINE_WAVE_FREQUENCY_Z_TO_X) * SINE_WAVE_AMPLITUDE_Z_TO_X;
                        double diffX = worldX - baseSineX;
                        double modDiffX = diffX % WAVE_SEPARATION_DISTANCE;
                        if (modDiffX < 0) {
                            modDiffX += WAVE_SEPARATION_DISTANCE;
                        }
                        if (modDiffX < SINE_WAVE_THICKNESS || modDiffX > (WAVE_SEPARATION_DISTANCE - SINE_WAVE_THICKNESS)) {
                            carveOutPillarForThisColumn = true;
                        }
                    }
                }


                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f;
                    boolean placeBlock = false;
                    Vector3f blockColor = COLOR_ROCK; // Default to rock

                    // Check for base terrain generation
                    if (worldY < currentBaseSurfaceY) {
                        placeBlock = true;
                        if (worldY > currentBaseSurfaceY - 2.0f) { // Top layer of base
                            blockColor = COLOR_SNOW;
                        } else if (worldY > currentBaseSurfaceY - 5.0f) {
                            blockColor = COLOR_ICE;
                        } else {
                            blockColor = COLOR_ROCK;
                        }
                    }

                    // Check for pillar generation (if a pillar candidate and not carved out)
                    if (isPillarCandidateLocation && !carveOutPillarForThisColumn) {
                        float pillarMaxHeight = PILLAR_MIN_HEIGHT + pillarHeightNoiseVal * PILLAR_AMPLITUDE;
                        // Check if current Y is within pillar height range and also above the base terrain
                        if (worldY < pillarMaxHeight && worldY >= currentBaseSurfaceY - 2.0f) { // Pillars can start from slightly below base surface
                            placeBlock = true; // This will ensure the block is placed, even if base terrain didn't dictate it, or override base color
                            // Vary color with height for pillars
                            float pillarRelativeHeight = (worldY - (currentBaseSurfaceY - 2.0f)) / (pillarMaxHeight - (currentBaseSurfaceY - 2.0f));
                            if (pillarRelativeHeight > 0.8f) {
                                blockColor = COLOR_SNOW;
                            } else if (pillarRelativeHeight > 0.5f) {
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