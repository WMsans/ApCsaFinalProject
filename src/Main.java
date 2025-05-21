import World.Terrain;
import Input.*;
import Configuration.*;
import org.joml.Vector3f;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main implements Runnable {

    private Window window;
    private Renderer renderer;
    private Terrain terrain;
    private Input input;
    private PlayerEntity playerEntity;
    private Config config;

    private final String windowTitle = "LWJGL Minecraft Prototype - Chunk System";
    private final int initialWidth = 1280;
    private final int initialHeight = 720;

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

    private void init() throws Exception {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        config = new Config("Configuration/config.properties");

        window = new Window(windowTitle, initialWidth, initialHeight);
        window.create();
        input = new Input(window.getWindowHandle());

        glfwMakeContextCurrent(window.getWindowHandle());
        glfwSwapInterval(1); // Enable v-sync
        glfwShowWindow(window.getWindowHandle());
        GL.createCapabilities();

        glClearColor(0.1f, 0.1f, 0.15f, 0.0f); // Background color
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        terrain = new Terrain(config);

        Vector3f playerStartPosition = new Vector3f(0, config.getChunkSizeY() + 20.0f, 0);
        playerEntity = new PlayerEntity(input, window, terrain, playerStartPosition, config);

        renderer = new Renderer(playerEntity.getCamera(), config);
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();
        float deltaTime;

        while (!window.shouldClose()) {
            float currentTime = (float) glfwGetTime(); // Get current time for game logic
            deltaTime = currentTime - lastTime;
            lastTime = currentTime;

            // Cap delta time to prevent physics issues if game hangs
            if (deltaTime > 0.1f) deltaTime = 0.1f;
            if (deltaTime <= 0) deltaTime = 1.0f / 60.0f; // Avoid zero or negative delta

            input.pollEvents(); // Update input states (e.g., for isKeyPressed)

            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
                glfwSetWindowShouldClose(window.getWindowHandle(), true);
            }

            // Pass deltaTime and currentTime to player update
            playerEntity.update(deltaTime, currentTime);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.renderTerrain(terrain, playerEntity.getPosition());
            window.swapBuffers();
            glfwPollEvents(); // Process OS events
        }
    }

    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        if (terrain != null) terrain.cleanup();

        if (window != null && window.getWindowHandle() != NULL) {
            glfwFreeCallbacks(window.getWindowHandle());
            glfwDestroyWindow(window.getWindowHandle());
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }
}
