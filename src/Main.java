import Graphics.Renderer;
import Graphics.Window;
import World.Entities.PlayerEntity;
import World.Terrain.*;
import Input.*;
import Configuration.*;
import World.Chunk.Chunk;
import World.Chunk.ChunkId;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main implements Runnable {

    private Window window;
    private Renderer renderer;
    private BaseTerrainGenerator terrain; // Stays as BaseTerrainGenerator
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
            case NETHER:
                return new NetherTerrain(config);
            case SIMPLE:
                return new SimpleTerrain(config);
            case CRYO_PEAK_WILDS:
                return new CryoPeakWildsTerrain(config);
            case CRYO_PEAK_WILDS_WORLEY:
                return new CryoPeakWildsTerrainWorley(config);
            default:
                throw new IllegalStateException("Unknown terrain generator type selected.");
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
        int chunkSearchRadius = Math.max(1, renderDist / 2); // Search within a smaller radius for performance
        Vector3f bestSpawnPoint = null;
        float highestGroundFound = -Float.MAX_VALUE;

        System.out.println("Finding safe spawn: Pre-generating initial chunks synchronously...");
        int maxChunkY = (int)Math.ceil( (256.0 / Chunk.CHUNK_SIZE_Y) /2.0) ; // Example: if world max Y is 256
        int minChunkY = -maxChunkY;


        for (int dx = -chunkSearchRadius; dx <= chunkSearchRadius; dx++) {
            for (int dz = -chunkSearchRadius; dz <= chunkSearchRadius; dz++) {
                for (int dy = maxChunkY + 2; dy >= minChunkY -2 ; dy--) { // Iterate downwards through chunk Y IDs
                    terrain.getChunkSynchronous(new ChunkId(dx, dy, dz));
                }
            }
        }
        terrain.processCompletedChunks(); // Process any stragglers, though getChunkSynchronous should be blocking
        System.out.println("Initial chunks for spawn search processed.");


        // Search for a spawnable block column by column
        // World X and Z from -searchRadius*ChunkSize to +searchRadius*ChunkSize
        for (int x = -chunkSearchRadius * Chunk.CHUNK_SIZE_X; x < chunkSearchRadius * Chunk.CHUNK_SIZE_X; x++) {
            for (int z = -chunkSearchRadius * Chunk.CHUNK_SIZE_Z; z < chunkSearchRadius * Chunk.CHUNK_SIZE_Z; z++) {
                // Search from a reasonable max height downwards. Configurable or based on world gen.
                // Max height could be config.chunkSizeY * (maxChunkY + 1)
                for (int y = Chunk.CHUNK_SIZE_Y * (maxChunkY +1) ; y >= Chunk.CHUNK_SIZE_Y * minChunkY; y--) {
                    Vector3f currentPoint = new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f);
                    if (terrain.isBlockAt(currentPoint)) {
                        // Found a block, check if space above is clear for player
                        boolean spaceClear = true;
                        // Player height is approx 1.8f, check 2 blocks above + eye height buffer
                        for (int i = 1; i <= 2; i++) { // Check two blocks directly above
                            if (terrain.isBlockAt(new Vector3f(x + 0.5f, y + i + 0.5f, z + 0.5f))) {
                                spaceClear = false;
                                break;
                            }
                        }
                        if (spaceClear) {
                            // This is a potential spawn surface. Choose the highest one.
                            if (y > highestGroundFound) {
                                highestGroundFound = y;
                                // Player spawns on top of this block (y+1), centered.
                                bestSpawnPoint = new Vector3f(x + 0.5f, y + 1.0f + 0.5f, z + 0.5f);
                            }
                        }
                        break; // Found the highest solid block in this (x,z) column, move to next column
                    }
                }
            }
        }


        if (bestSpawnPoint != null) {
            System.out.println("Found safe spawn at: " + bestSpawnPoint);
            return new Vector3f(bestSpawnPoint.x, bestSpawnPoint.y, bestSpawnPoint.z);
        } else {
            System.err.println("Could not find a safe spawn location. Defaulting to high up.");
            return new Vector3f(0, Chunk.CHUNK_SIZE_Y * (maxChunkY + 2) + 10.0f, 0); // Default spawn if no safe spot found
        }
    }


    private void init() throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        config = new Config("Configuration/config.properties"); // Load config first

        window = new Window(windowTitle, initialWidth, initialHeight);
        window.create();
        input = new Input(window.getWindowHandle());

        glfwMakeContextCurrent(window.getWindowHandle());
        glfwSwapInterval(1); // Enable v-sync
        glfwShowWindow(window.getWindowHandle());
        GL.createCapabilities(); // IMPORTANT: Create OpenGL capabilities AFTER context is current

        glClearColor(0.1f, 0.1f, 0.15f, 0.0f); // Dark blueish-grey background
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        terrain = GetTerrainGenerator(); // Initialize terrain generator

        // Player needs to be initialized after terrain and config.
        Vector3f playerStartPosition = findSafeSpawnLocation(); // Find spawn *after* terrain is ready
        playerEntity = new PlayerEntity(input, window, terrain, playerStartPosition, config);
        terrain.addEntity(playerEntity); // Add player to terrain's entity list

        renderer = new Renderer(playerEntity.getCamera(), config); // Graphics.Renderer needs camera and config
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();
        float deltaTime;

        while (!window.shouldClose()) {
            float currentTime = (float) glfwGetTime();
            deltaTime = currentTime - lastTime;
            lastTime = currentTime;

            // Cap delta time to prevent unusually large jumps (e.g., after a breakpoint)
            if (deltaTime > 0.1f) deltaTime = 0.1f;
            // Ensure deltaTime is not zero or negative if time reverses or stalls
            if (deltaTime <= 0) deltaTime = 1.0f / 60.0f; // Default to 60 FPS if issues

            input.pollEvents(); // Process GLFW events and update input states

            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
                glfwSetWindowShouldClose(window.getWindowHandle(), true);
            }

            // Update all entities managed by the terrain system (including player)
            terrain.updateEntities(deltaTime, currentTime);

            // Process any chunks that finished generating in worker threads
            terrain.processCompletedChunks();

            // Unload distant chunks and their entities (excluding player)
            ChunkId currentPlayerChunkId = Chunk.getChunkIdAtWorldPosition(playerEntity.getPosition());
            terrain.unloadDistantChunks(currentPlayerChunkId, config.getRenderDistanceInChunks(), playerEntity);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // Clear screen
            renderer.renderTerrain(terrain, playerEntity.getPosition()); // Render terrain and entities via renderer
            window.swapBuffers(); // Display rendered frame
            glfwPollEvents(); // Check for window events (like close button)
        }
    }

    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        if (terrain != null) terrain.cleanup(); // This will also shutdown the ExecutorService and clear entities

        if (window != null && window.getWindowHandle() != NULL) {
            glfwFreeCallbacks(window.getWindowHandle()); // Release callbacks
            glfwDestroyWindow(window.getWindowHandle()); // Destroy window
        }
        glfwTerminate(); // Terminate GLFW
        GLFWErrorCallback callback = glfwSetErrorCallback(null); // Release error callback
        if (callback != null) callback.free();
    }
}