import World.Terrain;
import Input.*;
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
    private PlayerEntity playerEntity; // Changed from Player to PlayerEntity

    private final String windowTitle = "LWJGL Minecraft Prototype - Entity System";
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

        window = new Window(windowTitle, initialWidth, initialHeight);
        window.create();
        input = new Input(window.getWindowHandle());

        glfwMakeContextCurrent(window.getWindowHandle());
        glfwSwapInterval(1);
        glfwShowWindow(window.getWindowHandle());
        GL.createCapabilities();

        glClearColor(0.5f, 0.7f, 1.0f, 0.0f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        terrain = new Terrain(20, 3, 20); // Slightly larger terrain

        // Create the player entity
        Vector3f playerStartPosition = new Vector3f(0, 5.0f, 0); // Start a bit higher to see gravity
        playerEntity = new PlayerEntity(input, window, terrain, playerStartPosition);

        renderer = new Renderer(playerEntity.getCamera()); // Renderer gets camera from PlayerEntity
    }

    private void loop() {
        float lastTime = (float) glfwGetTime();
        float deltaTime;

        while (!window.shouldClose()) {
            float currentTime = (float) glfwGetTime();
            deltaTime = currentTime - lastTime;
            if (deltaTime > 0.1f) deltaTime = 0.1f; // Clamp delta time to avoid large jumps
            lastTime = currentTime;

            input.pollEvents(); // Update input states

            if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
                glfwSetWindowShouldClose(window.getWindowHandle(), true);
            }

            // Update player entity (handles its own logic, movement, camera, interactions)
            playerEntity.update(deltaTime);

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            renderer.renderTerrain(terrain);
            // Future: renderer.renderEntities(world.getEntities());
            window.swapBuffers();
            glfwPollEvents();
        }
    }

    private void cleanup() {
        if (renderer != null) renderer.cleanup();
        if (terrain != null) terrain.cleanup();
        // PlayerEntity cleanup (if it held GL resources, it would need a cleanup method)

        if (window != null && window.getWindowHandle() != NULL) {
            glfwFreeCallbacks(window.getWindowHandle());
            glfwDestroyWindow(window.getWindowHandle());
        }
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) callback.free();
    }
}
