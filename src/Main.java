import Graphics.Renderer;
import Graphics.Window;
import World.Entities.PlayerEntity;
import World.Entities.Entity; // Added
import World.Terrain.*;
import Input.*;
import Configuration.*;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.util.List; // Added

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main implements Runnable {

    private Window window;
    private Renderer renderer;
    private BaseTerrainGenerator terrain;
    private Input input;
    private PlayerEntity playerEntity;
    private Config config;

    private final String windowTitle = "LWJGL Minecraft Prototype";
    private final int initialWidth = 1280;
    private final int initialHeight = 720;
    private final TerrainType TERRAIN_GENERATOR_TYPE = TerrainType.CRYO_PEAK_WILDS;

    private enum TerrainType {
        NETHER,
        SIMPLE,
        CRYO_PEAK_WILDS,
        CRYO_PEAK_WILDS_WORLEY
    }

    private BaseTerrainGenerator GetTerrainGenerator() {
        switch (TERRAIN_GENERATOR_TYPE) {
            case NETHER: return new NetherTerrain(config);
            case SIMPLE: return new SimpleTerrain(config);
            case CRYO_PEAK_WILDS: return new CryoPeakWildsTerrain(config);
            case CRYO_PEAK_WILDS_WORLEY: return new CryoPeakWildsTerrainWorley(config);
            default: throw new IllegalStateException("Unknown terrain generator type selected.");
        }
    }

    public static void main(String[] args) {
        new Main().run();
    }

    @Override
    public void run() {
        try {
            init();
            loop();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private Vector3f findSafeSpawnLocation() {
        int renderDist = config.getRenderDistanceInChunks();
        int chunkSearchRadius = Math.max(1, renderDist / 2);
        Vector3f bestSpawnPoint = null;
        float highestGroundFound = -Float.MAX_VALUE;
        System.out.println("Finding safe spawn: Pre-generating initial chunks synchronously...");
        int maxChunkY = (int)Math.ceil( (256.0 / Chunk.CHUNK_SIZE_Y) /2.0) ;
        int minChunkY = -maxChunkY;
        for (int dx = -chunkSearchRadius; dx <= chunkSearchRadius; dx++) {
            for (int dz = -chunkSearchRadius; dz <= chunkSearchRadius; dz++) {
                for (int dy = maxChunkY + 2; dy >= minChunkY -2 ; dy--) {
                    terrain.getChunkSynchronous(new ChunkId(dx, dy, dz));
                }
            }
        }
        terrain.processCompletedChunks();
        System.out.println("Initial chunks for spawn search processed.");
        for (int x = -chunkSearchRadius * Chunk.CHUNK_SIZE_X; x < chunkSearchRadius * Chunk.CHUNK_SIZE_X; x++) {
            for (int z = -chunkSearchRadius * Chunk.CHUNK_SIZE_Z; z < chunkSearchRadius * Chunk.CHUNK_SIZE_Z; z++) {
                for (int y = Chunk.CHUNK_SIZE_Y * (maxChunkY +1) ; y >= Chunk.CHUNK_SIZE_Y * minChunkY; y--) {
                    Vector3f currentPoint = new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f);
                    if (terrain.isBlockAt(currentPoint)) {
                        boolean spaceClear = true;
                        for (int i = 1; i <= 2; i++) {
                            if (terrain.isBlockAt(new Vector3f(x + 0.5f, y + i + 0.5f, z + 0.5f))) {
                                spaceClear = false;
                                break;
                            }
                        }
                        if (spaceClear) {
                            if (y > highestGroundFound) {
                                highestGroundFound = y;
                                bestSpawnPoint = new Vector3f(x + 0.5f, y + 1.0f + 0.5f, z + 0.5f);
                            }
                        }
                        break;
                    }
                }
            }
        }
        if (bestSpawnPoint != null) {
            System.out.println("Found safe spawn at: " + bestSpawnPoint);
            return new Vector3f(bestSpawnPoint.x, bestSpawnPoint.y, bestSpawnPoint.z);
        } else {
            System.err.println("Could not find a safe spawn location. Defaulting to high up.");
            return new Vector3f(0, Chunk.CHUNK_SIZE_Y * (maxChunkY + 2) + 10.0f, 0);
        }
    }

    private void init() throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        config = new Config("Configuration/config.properties");
        window = new Window(windowTitle, initialWidth, initialHeight);
        window.create();
        input = new Input(window.getWindowHandle());

        glfwMakeContextCurrent(window.getWindowHandle());
        glfwSwapInterval(1);
        glfwShowWindow(window.getWindowHandle());
        GL.createCapabilities();

        glClearColor(0.1f, 0.1f, 0.15f, 0.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        terrain = GetTerrainGenerator();
        Vector3f playerStartPosition = findSafeSpawnLocation();
        playerEntity = new PlayerEntity(input, window, terrain, playerStartPosition, config);
        terrain.addEntity(playerEntity); // Add player to terrain's entity list

        // Initialize renderer
        renderer = new Renderer(playerEntity.getCamera(), config);

        // Initialize models for all entities
        for (Entity entity : terrain.getEntities()) {
            entity.initializeModel(renderer.getEntityRenderer());
        }
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();
        float deltaTime;

        while (!window.shouldClose()) {
            float currentTime = (float) glfwGetTime();
            deltaTime = currentTime - lastTime;
            lastTime = currentTime;
            if (deltaTime > 0.1f) deltaTime = 0.1f;
            if (deltaTime <= 0) deltaTime = 1.0f / 60.0f;

            input.pollEvents();
            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) glfwSetWindowShouldClose(window.getWindowHandle(), true);

            terrain.updateEntities(deltaTime, currentTime); // Hooks might be created here
            terrain.processCompletedChunks();
            ChunkId currentPlayerChunkId = Chunk.getChunkIdAtWorldPosition(playerEntity.getPosition());
            terrain.unloadDistantChunks(currentPlayerChunkId, config.getRenderDistanceInChunks(), playerEntity);

            List<Entity> currentEntities = terrain.getEntities(); // Get a stable list for this frame

            // Initialize models for any new entities or entities whose VAOs aren't built
            for (Entity entity : currentEntities) {
                if (!entity.isValid()) continue; // Skip invalid entities

                if (entity.getModel() == null) {
                    // Model data has not been created yet (e.g., new Hook)
                    entity.initializeModel(renderer.getEntityRenderer()); // This calls createModelData() then buildMesh()
                } else if (entity.getModel().getVaoId() == 0) {
                    // Model data exists, but GPU buffers (VAO) are not ready
                    // This implies createModelData() was called, but buildMesh() wasn't or failed
                    renderer.getEntityRenderer().buildMesh(entity.getModel());
                }
            }

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.renderTerrain(terrain, playerEntity.getPosition());
            renderer.renderEntities(currentEntities, playerEntity.getCamera(), playerEntity);
            window.swapBuffers();
            glfwPollEvents();
        }
    }

    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        // Clean up models for entities that are still in the list
        if (terrain != null) {
            for (Entity entity : terrain.getEntities()) {
                if (entity.getModel() != null) {
                    entity.getModel().cleanup();
                }
            }
            terrain.cleanup();
        }

        if (window != null && window.getWindowHandle() != NULL) {
            glfwFreeCallbacks(window.getWindowHandle());
            glfwDestroyWindow(window.getWindowHandle());
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }
}