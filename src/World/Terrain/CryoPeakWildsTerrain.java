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
    private static final float PEAK_HIGH_LEVEL = 40.0f;
    private static final float DRAMATIC_THRESHOLD_LOW = 0f;
    private static final float DRAMATIC_THRESHOLD_HIGH = 0.3f;
    private static final float LOW_VARIATION_AMP = 5.0f;
    private static final float HIGH_VARIATION_AMP = 5.0f;


    private static final float PILLAR_THRESHOLD = 0.6f; // Noise value above which pillars generate
    private static final float PILLAR_MIN_HEIGHT = 15.0f;
    private static final float PILLAR_AMPLITUDE = 150.0f; // Max additional height for pillars

    // Color strategy from NetherTerrain
    private static final Vector3f[] TERRAIN_COLORS = {
            new Vector3f(0.545f, 0.118f, 1.0f), // #8c1eff
            new Vector3f(1.0f, 0.565f, 0.122f), // #ff901f
            new Vector3f(0.1f, 0.1f, 0.85f)     // #2f1cd9
    };
    private static final int COLOR_TRANSITION_RANGE_Y = 10;


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

    private Vector3f lerpColor(Vector3f color1, Vector3f color2, float t) {
        t = Math.max(0, Math.min(1, t));
        float r = color1.x * (1 - t) + color2.x * t;
        float g = color1.y * (1 - t) + color2.y * t;
        float b = color1.z * (1 - t) + color2.z * t;
        return new Vector3f(r, g, b);
    }

    private Vector3f getInterpolatedColor(float worldY, Vector3f[] palette) {
        float absoluteWorldY = Math.abs(worldY);
        float yProgress = (absoluteWorldY / COLOR_TRANSITION_RANGE_Y);
        int colorIndex1 = (int) Math.floor(yProgress) % palette.length;
        int colorIndex2 = (colorIndex1 + 1) % palette.length;
        float t = yProgress - (float)Math.floor(yProgress);
        return lerpColor(palette[colorIndex1], palette[colorIndex2], t);
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
                float currentBaseSurfaceY;

                if (baseHeightNoiseVal < DRAMATIC_THRESHOLD_LOW) {
                    // Normalize noise in the range [-1, DRAMATIC_THRESHOLD_LOW) to [0, 1)
                    float t = (baseHeightNoiseVal - (-1.0f)) / (DRAMATIC_THRESHOLD_LOW - (-1.0f));
                    currentBaseSurfaceY = FLAT_LOW_LEVEL + t * LOW_VARIATION_AMP;
                } else if (baseHeightNoiseVal <= DRAMATIC_THRESHOLD_HIGH) {
                    // Normalize noise in the range [DRAMATIC_THRESHOLD_LOW, DRAMATIC_THRESHOLD_HIGH] to [0, 1]
                    float t = (baseHeightNoiseVal - DRAMATIC_THRESHOLD_LOW) / (DRAMATIC_THRESHOLD_HIGH - DRAMATIC_THRESHOLD_LOW);
                    currentBaseSurfaceY = FLAT_LOW_LEVEL + t * (PEAK_HIGH_LEVEL - FLAT_LOW_LEVEL);
                } else { // baseHeightNoiseVal > DRAMATIC_THRESHOLD_HIGH
                    // Normalize noise in the range (DRAMATIC_THRESHOLD_HIGH, 1] to (0, 1]
                    float t = (baseHeightNoiseVal - DRAMATIC_THRESHOLD_HIGH) / (1.0f - DRAMATIC_THRESHOLD_HIGH);
                    currentBaseSurfaceY = PEAK_HIGH_LEVEL + t * HIGH_VARIATION_AMP;
                }


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
                    Vector3f blockColor; // Will be set by new color logic

                    // Check for base terrain generation
                    if (worldY < currentBaseSurfaceY) {
                        placeBlock = true;
                    }

                    // Check for pillar generation (if a pillar candidate and not carved out)
                    if (isPillarCandidateLocation && !carveOutPillarForThisColumn) {
                        float pillarMaxHeight = PILLAR_MIN_HEIGHT + pillarHeightNoiseVal * PILLAR_AMPLITUDE;
                        // Check if current Y is within pillar height range and also above the base terrain
                        if (worldY < pillarMaxHeight && worldY >= currentBaseSurfaceY - 2.0f) { // Pillars can start from slightly below base surface
                            placeBlock = true; // This will ensure the block is placed, even if base terrain didn't dictate it
                        }
                    }


                    if (placeBlock) {
                        blockColor = getInterpolatedColor(worldY, TERRAIN_COLORS);
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