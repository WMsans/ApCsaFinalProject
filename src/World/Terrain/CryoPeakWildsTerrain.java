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

    // Constants for new dramatic height calculation
    private static final float FLAT_LOW_LEVEL = 10.0f;
    private static final float PEAK_HIGH_LEVEL = 70.0f;
    private static final float DRAMATIC_THRESHOLD_LOW = 0f;
    private static final float DRAMATIC_THRESHOLD_HIGH = 0.3f;
    private static final float LOW_VARIATION_AMP = 5.0f;
    private static final float HIGH_VARIATION_AMP = 5.0f;


    private static final float PILLAR_THRESHOLD = 0.6f; // Noise value above which pillars generate
    private static final float PILLAR_MIN_HEIGHT = 15.0f;
    private static final float PILLAR_AMPLITUDE = 170.0f; // Max additional height for pillars

    // Define colors based on the image's aesthetic for the blocks themselves
    // These will be dark, allowing the shader grid to be the prominent color feature.
    private static final Vector3f COLOR_BASE_TERRAIN_DARK = new Vector3f(0.05f, 0.02f, 0.15f); // Very dark desaturated blue/purple
    private static final Vector3f COLOR_PILLAR_DARK = new Vector3f(0.1f, 0.05f, 0.2f);   // Slightly different dark purple for pillars


    // Constants for sine wave carving (can be kept or removed based on desired final look)
    private static final float SINE_WAVE_FREQUENCY_X_TO_Z = 25.0f;
    private static final float SINE_WAVE_AMPLITUDE_X_TO_Z = 6.0f;
    private static final float SINE_WAVE_FREQUENCY_Z_TO_X = 25.0f;
    private static final float SINE_WAVE_AMPLITUDE_Z_TO_X = 6.0f;
    private static final float SINE_WAVE_THICKNESS = 5.0f;
    private static final float WAVE_SEPARATION_DISTANCE = 50.0f;


    public CryoPeakWildsTerrain(Config config) {
        super(config);
        int worldSeed = config.hashCode();
        this.random = new Random(worldSeed);

        baseNoise = new FastNoiseLite(worldSeed);
        baseNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        baseNoise.SetFrequency(0.008f);
        baseNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        baseNoise.SetFractalOctaves(4);
        baseNoise.SetFractalLacunarity(2.0f);
        baseNoise.SetFractalGain(0.5f);

        pillarNoise = new FastNoiseLite(worldSeed + 1);
        pillarNoise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        pillarNoise.SetFrequency(0.025f);
        pillarNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        pillarNoise.SetFractalOctaves(3);
        pillarNoise.SetFractalLacunarity(2.2f);
        pillarNoise.SetFractalGain(0.6f);
    }

    // The old lerpColor and getInterpolatedColor methods are removed as we'll use simpler, darker base colors.
    // The vibrant grid will be shader-controlled.

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
                float currentBaseSurfaceY;

                if (baseHeightNoiseVal < DRAMATIC_THRESHOLD_LOW) {
                    float t = (baseHeightNoiseVal - (-1.0f)) / (DRAMATIC_THRESHOLD_LOW - (-1.0f));
                    currentBaseSurfaceY = FLAT_LOW_LEVEL + t * LOW_VARIATION_AMP;
                } else if (baseHeightNoiseVal <= DRAMATIC_THRESHOLD_HIGH) {
                    float t = (baseHeightNoiseVal - DRAMATIC_THRESHOLD_LOW) / (DRAMATIC_THRESHOLD_HIGH - DRAMATIC_THRESHOLD_LOW);
                    currentBaseSurfaceY = FLAT_LOW_LEVEL + t * (PEAK_HIGH_LEVEL - FLAT_LOW_LEVEL);
                } else {
                    float t = (baseHeightNoiseVal - DRAMATIC_THRESHOLD_HIGH) / (1.0f - DRAMATIC_THRESHOLD_HIGH);
                    currentBaseSurfaceY = PEAK_HIGH_LEVEL + t * HIGH_VARIATION_AMP;
                }

                float pillarPlacementNoiseVal = (pillarNoise.GetNoise(worldX, worldZ) + 1) / 2f; // 0 to 1
                float pillarHeightNoiseVal = (pillarNoise.GetNoise(worldX * 0.5f, worldZ * 0.5f) +1) / 2f; // 0 to 1

                boolean isPillarCandidateLocation = pillarPlacementNoiseVal > PILLAR_THRESHOLD;
                boolean carveOutPillarForThisColumn = false;

                if (isPillarCandidateLocation) {
                    double baseSineZ = Math.sin(worldX / SINE_WAVE_FREQUENCY_X_TO_Z) * SINE_WAVE_AMPLITUDE_X_TO_Z;
                    double diffZ = worldZ - baseSineZ;
                    double modDiffZ = diffZ % WAVE_SEPARATION_DISTANCE;
                    if (modDiffZ < 0) modDiffZ += WAVE_SEPARATION_DISTANCE;
                    if (modDiffZ < SINE_WAVE_THICKNESS || modDiffZ > (WAVE_SEPARATION_DISTANCE - SINE_WAVE_THICKNESS)) {
                        carveOutPillarForThisColumn = true;
                    }

                    if (!carveOutPillarForThisColumn) {
                        double baseSineX = Math.sin(worldZ / SINE_WAVE_FREQUENCY_Z_TO_X) * SINE_WAVE_AMPLITUDE_Z_TO_X;
                        double diffX = worldX - baseSineX;
                        double modDiffX = diffX % WAVE_SEPARATION_DISTANCE;
                        if (modDiffX < 0) modDiffX += WAVE_SEPARATION_DISTANCE;
                        if (modDiffX < SINE_WAVE_THICKNESS || modDiffX > (WAVE_SEPARATION_DISTANCE - SINE_WAVE_THICKNESS)) {
                            carveOutPillarForThisColumn = true;
                        }
                    }
                }

                for (int ly = 0; ly < Chunk.CHUNK_SIZE_Y; ly++) {
                    float worldY = worldChunkYBase + ly + 0.5f;
                    boolean placeBlock = false;
                    Vector3f blockColor = COLOR_BASE_TERRAIN_DARK; // Default to base terrain dark color

                    if (worldY < currentBaseSurfaceY) {
                        placeBlock = true;
                        // Block color remains COLOR_BASE_TERRAIN_DARK as per new style
                    }

                    if (isPillarCandidateLocation && !carveOutPillarForThisColumn) {
                        float pillarMaxHeight = PILLAR_MIN_HEIGHT + pillarHeightNoiseVal * PILLAR_AMPLITUDE;
                        if (worldY < pillarMaxHeight && worldY >= currentBaseSurfaceY - 2.0f) {
                            placeBlock = true;
                            blockColor = COLOR_PILLAR_DARK; // Pillars get their specific dark color
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