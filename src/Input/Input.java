package Input;

import static org.lwjgl.glfw.GLFW.*;

public class Input {
    // Array to store the state of all keyboard keys
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];
    // Array to track if a key was just pressed (for single press events)
    private final boolean[] keysJustPressed = new boolean[GLFW_KEY_LAST + 1];
    // Array to track previous state for just pressed logic
    private final boolean[] keysLastState = new boolean[GLFW_KEY_LAST + 1];

    // Mouse button states
    private final boolean[] mouseButtons = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseButtonsJustPressed = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];
    private final boolean[] mouseButtonsLastState = new boolean[GLFW_MOUSE_BUTTON_LAST + 1];

    private double mouseX, mouseY;

    public Input(long windowHandle) {
        // Setup key callback
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            if (key >= 0 && key <= GLFW_KEY_LAST) {
                if (action == GLFW_PRESS) {
                    keys[key] = true;
                } else if (action == GLFW_RELEASE) {
                    keys[key] = false;
                }
            }
        });

        // Setup mouse cursor position callback
        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
        });

        // Setup mouse button callback
        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (button >= 0 && button <= GLFW_MOUSE_BUTTON_LAST) {
                if (action == GLFW_PRESS) {
                    mouseButtons[button] = true;
                } else if (action == GLFW_RELEASE) {
                    mouseButtons[button] = false;
                }
            }
        });

        // Initialize last states
        for(int i=0; i <= GLFW_KEY_LAST; i++) {
            keysLastState[i] = false;
            keysJustPressed[i] = false;
        }
        for(int i=0; i <= GLFW_MOUSE_BUTTON_LAST; i++) {
            mouseButtonsLastState[i] = false;
            mouseButtonsJustPressed[i] = false;
        }
    }

    /**
     * Checks if a key is currently held down.
     * @param keyCode The GLFW key code.
     * @return True if the key is down, false otherwise.
     */
    public boolean isKeyDown(int keyCode) {
        if (keyCode < 0 || keyCode > GLFW_KEY_LAST) return false;
        return keys[keyCode];
    }

    /**
     * Checks if a key was just pressed in this frame.
     * Call pollEvents() once per frame before using this.
     * @param keyCode The GLFW key code.
     * @return True if the key was just pressed, false otherwise.
     */
    public boolean isKeyPressed(int keyCode) {
        if (keyCode < 0 || keyCode > GLFW_KEY_LAST) return false;
        return keysJustPressed[keyCode];
    }

    /**
     * Checks if a mouse button is currently held down.
     * @param buttonCode The GLFW mouse button code (e.g., GLFW_MOUSE_BUTTON_LEFT).
     * @return True if the button is down, false otherwise.
     */
    public boolean isMouseButtonDown(int buttonCode) {
        if (buttonCode < 0 || buttonCode > GLFW_MOUSE_BUTTON_LAST) return false;
        return mouseButtons[buttonCode];
    }

    /**
     * Checks if a mouse button was just pressed in this frame.
     * Call pollEvents() once per frame before using this.
     * @param buttonCode The GLFW mouse button code.
     * @return True if the button was just pressed, false otherwise.
     */
    public boolean isMouseButtonPressed(int buttonCode) {
        if (buttonCode < 0 || buttonCode > GLFW_MOUSE_BUTTON_LAST) return false;
        return mouseButtonsJustPressed[buttonCode];
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    /**
     * This method should be called once per game loop, typically at the beginning or
     * after glfwPollEvents(), to update the "just pressed" state of keys and mouse buttons.
     */
    public void pollEvents() {
        // Update key just pressed states
        for (int i = 0; i <= GLFW_KEY_LAST; i++) {
            boolean currentState = keys[i];
            keysJustPressed[i] = currentState && !keysLastState[i];
            keysLastState[i] = currentState;
        }
        // Update mouse button just pressed states
        for (int i = 0; i <= GLFW_MOUSE_BUTTON_LAST; i++) {
            boolean currentState = mouseButtons[i];
            mouseButtonsJustPressed[i] = currentState && !mouseButtonsLastState[i];
            mouseButtonsLastState[i] = currentState;
        }
        // Note: glfwPollEvents() in the main loop is what actually invokes the GLFW callbacks
        // that update the `keys`/`mouseButtons` arrays and `mouseX`/`mouseY`.
    }
}
