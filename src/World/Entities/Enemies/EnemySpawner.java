package World.Entities.Enemies;

import World.Entities.*;
import World.Terrain.BaseTerrainGenerator;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import World.Entities.Enemies.ChromeSentinel; // Import specific enemies here or use a more generic way
import World.FastNoiseLite;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EnemySpawner {
    private static final int ENEMY_CAP = 20;
    private static final float SPAWN_CHECK_INTERVAL = 3.0f; // Seconds between spawn attempts
    private static final float SPAWN_ATTEMPT_RADIUS_MAX = 90.0f; // Max world units from player to attempt spawn
    private static final float SPAWN_ATTEMPT_RADIUS_MIN = 30.0f; // Min world units from player
    private static final float MIN_SPAWN_HEIGHT_ABOVE_TERRAIN = 20.0f;
    private static final float MAX_SPAWN_HEIGHT_ABOVE_TERRAIN = 60.0f;
    private static final int SPAWN_ATTEMPTS_PER_CYCLE = 3; // How many locations to check each interval

    private BaseTerrainGenerator worldTerrain;
    private PlayerEntity player;
    private List<EnemyFactory> enemyFactories;
    private Random random;
    private FastNoiseLite spawnPointNoise;
    private float spawnTimer;

    @FunctionalInterface
    public interface EnemyFactory {
        Enemy create(BaseTerrainGenerator terrain, Vector3f position);
    }

    public EnemySpawner(BaseTerrainGenerator worldTerrain, PlayerEntity player) {
        this.worldTerrain = worldTerrain;
        this.player = player;
        this.enemyFactories = new ArrayList<>();
        this.random = new Random();
        this.spawnTimer = SPAWN_CHECK_INTERVAL * random.nextFloat(); // Stagger initial spawn

        this.spawnPointNoise = new FastNoiseLite((int) System.currentTimeMillis());
        this.spawnPointNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        this.spawnPointNoise.SetFrequency(0.015f); // Adjust for desired spawn area density
        this.spawnPointNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        this.spawnPointNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.CellValue); // Values typically -1 to 1
        this.spawnPointNoise.SetCellularJitter(0.8f);

        // Register enemy types that can be spawned
        registerEnemyType(ChromeSentinel::new);
        // Example for future: registerEnemyType(SomeOtherEnemy::new);
    }

    public void registerEnemyType(EnemyFactory factory) {
        this.enemyFactories.add(factory);
    }

    public void update(float deltaTime) {
        spawnTimer -= deltaTime;
        if (spawnTimer <= 0) {
            spawnTimer = SPAWN_CHECK_INTERVAL;
            trySpawnEnemies();
        }
    }

    private int getCurrentEnemyCount() {
        int count = 0;
        for (Entity e : worldTerrain.getEntities()) {
            if (e instanceof Enemy && e.isValid()) {
                count++;
            }
        }
        return count;
    }

    private void trySpawnEnemies() {
        if (enemyFactories.isEmpty() || player == null || !player.isValid()) {
            return;
        }

        int currentEnemies = getCurrentEnemyCount();
        if (currentEnemies >= ENEMY_CAP) {
            return;
        }

        int enemiesToPotentiallySpawn = ENEMY_CAP - currentEnemies;

        for (int i = 0; i < Math.min(enemiesToPotentiallySpawn, SPAWN_ATTEMPTS_PER_CYCLE); i++) {
            if (getCurrentEnemyCount() >= ENEMY_CAP) break; // Re-check cap inside loop

            float angle = random.nextFloat() * 2.0f * (float) Math.PI;
            float radius = SPAWN_ATTEMPT_RADIUS_MIN + random.nextFloat() * (SPAWN_ATTEMPT_RADIUS_MAX - SPAWN_ATTEMPT_RADIUS_MIN);

            float spawnX = player.getPosition().x + (float) Math.cos(angle) * radius;
            float spawnZ = player.getPosition().z + (float) Math.sin(angle) * radius;

            float noiseVal = spawnPointNoise.GetNoise(spawnX, spawnZ);

            // Use a threshold for cellular noise; CellValue is -1 to 1.
            // A positive threshold means less frequent spawns but potentially more clustered if jitter is low.
            // A threshold around 0 or slightly negative might work for broader areas.
            if (noiseVal > -0.2f) { // Adjust this threshold based on testing with Cellular noise
                float terrainApproxY = getApproxTerrainHeight(spawnX, spawnZ);
                if (terrainApproxY == -Float.MAX_VALUE && player != null) { // Fallback if no terrain found
                    terrainApproxY = player.getPosition().y - 10; // Default to below player if no terrain found
                } else if (terrainApproxY == -Float.MAX_VALUE) {
                    terrainApproxY = 30; // Absolute fallback
                }


                float spawnY = terrainApproxY + MIN_SPAWN_HEIGHT_ABOVE_TERRAIN +
                        random.nextFloat() * (MAX_SPAWN_HEIGHT_ABOVE_TERRAIN - MIN_SPAWN_HEIGHT_ABOVE_TERRAIN);

                // Ensure spawnY is somewhat reasonable if terrain is very low or very high
                spawnY = Math.max(spawnY, Chunk.CHUNK_SIZE_Y); // At least one chunk height above absolute 0
                spawnY = Math.min(spawnY, Chunk.CHUNK_SIZE_Y * 10); // Cap at 10 chunks high (e.g. 160 for 16-size chunks)


                Vector3f spawnPosition = new Vector3f(spawnX, spawnY, spawnZ);

                // Ensure the 3D distance from player to spawn position is not less than the minimum radius.
                if (player != null && player.isValid() && spawnPosition.distance(player.getPosition()) < SPAWN_ATTEMPT_RADIUS_MIN) {
                    continue;
                }

                EnemyFactory factory = enemyFactories.get(random.nextInt(enemyFactories.size()));
                Enemy enemy = factory.create(this.worldTerrain, spawnPosition); // Pass terrain and position
                worldTerrain.addEntity(enemy);
                // System.out.println("Spawned " + enemy.getClass().getSimpleName() + " at " + spawnPosition + " (Noise: " + String.format("%.2f", noiseVal) + ", TerrainY: " + String.format("%.2f", terrainApproxY) + ")");
            }
        }
    }

    private float getApproxTerrainHeight(float worldX, float worldZ) {
        ChunkId baseChunkId = Chunk.getChunkIdAtWorldPosition(worldX, 0, worldZ);
        float highestYFound = -Float.MAX_VALUE;
        boolean blockFound = false;

        // Search a column of chunks around a typical surface level.
        // Assuming terrain surface is generally not extremely high or low for typical spawns.
        int centralChunkY = (player != null) ? Chunk.getChunkIdAtWorldPosition(player.getPosition()).y : 0;
        int searchDepthChunks = 4; // How many chunks up/down from player's Y to search for terrain surface

        for (int dy = searchDepthChunks; dy >= -searchDepthChunks; dy--) {
            ChunkId targetChunkId = new ChunkId(baseChunkId.x, centralChunkY + dy, baseChunkId.z);
            Chunk currentChunk = worldTerrain.getChunkSynchronous(targetChunkId); // Blocking call

            if (currentChunk != null) {
                // Iterate from top of this chunk downwards
                for (int ly = Chunk.CHUNK_SIZE_Y - 1; ly >= 0; ly--) {
                    float testY = currentChunk.getMinCorner().y + ly + 0.5f; // Center of block Y
                    // Check if block exists at (worldX, testY, worldZ)
                    // This requires checking the specific block list of currentChunk,
                    // matching worldX and worldZ to a local block coordinate.
                    int localXInChunk = (int)Math.floor(worldX - currentChunk.getMinCorner().x);
                    int localZInChunk = (int)Math.floor(worldZ - currentChunk.getMinCorner().z);

                    if (localXInChunk >= 0 && localXInChunk < Chunk.CHUNK_SIZE_X &&
                            localZInChunk >= 0 && localZInChunk < Chunk.CHUNK_SIZE_Z) {
                        // Now check for a block at this (localXInChunk, ly, localZInChunk)
                        // This is still not perfect, as isBlockAt operates on world coordinates
                        // A direct block check within chunk data would be better.
                        if (worldTerrain.isBlockAt(new Vector3f(worldX, testY, worldZ))) {
                            highestYFound = testY;
                            blockFound = true;
                            return highestYFound; // Found the highest solid block in this column scan
                        }
                    }
                }
            }
        }
        return blockFound ? highestYFound : -Float.MAX_VALUE; // Return highest found or indicator of no block
    }
}