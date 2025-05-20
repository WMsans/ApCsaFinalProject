import World.Block;
import World.Terrain;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Renderer {

    private Shader shader;
    private Camera camera;

    // Define cube vertices (3 floats for position per vertex)
    private final float[] cubeVertices = {
            // Front face
            -0.5f, -0.5f,  0.5f, // Bottom-left
            0.5f, -0.5f,  0.5f, // Bottom-right
            0.5f,  0.5f,  0.5f, // Top-right
            -0.5f,  0.5f,  0.5f, // Top-left
            // Back face
            -0.5f, -0.5f, -0.5f,
            -0.5f,  0.5f, -0.5f,
            0.5f,  0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            // Top face
            -0.5f,  0.5f, -0.5f,
            -0.5f,  0.5f,  0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f,  0.5f, -0.5f,
            // Bottom face
            -0.5f, -0.5f, -0.5f,
            0.5f, -0.5f, -0.5f,
            0.5f, -0.5f,  0.5f,
            -0.5f, -0.5f,  0.5f,
            // Right face
            0.5f, -0.5f, -0.5f,
            0.5f,  0.5f, -0.5f,
            0.5f,  0.5f,  0.5f,
            0.5f, -0.5f,  0.5f,
            // Left face
            -0.5f, -0.5f, -0.5f,
            -0.5f, -0.5f,  0.5f,
            -0.5f,  0.5f,  0.5f,
            -0.5f,  0.5f, -0.5f,
    };

    // Define cube indices (2 triangles per face, 3 indices per triangle)
    private final int[] cubeIndices = {
            0, 1, 2,  0, 2, 3,   // Front face
            4, 5, 6,  4, 6, 7,   // Back face
            8, 9, 10, 8, 10, 11,  // Top face
            12, 13, 14, 12, 14, 15, // Bottom face
            16, 17, 18, 16, 18, 19, // Right face
            20, 21, 22, 20, 22, 23  // Left face
    };

    private int cubeVaoId; // Vertex Array Object ID
    private int cubeVboId; // Vertex Buffer Object ID (for vertex data)
    private int cubeEboId; // Element Buffer Object ID (for indices)

    public Renderer(Camera camera) {
        this.camera = camera;
        try {
            initShader();
            initCubeMesh();
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1); // Critical error
        }
    }

    private void initShader() throws Exception {
        shader = new Shader();
        // Ensure shader files are in src/main/resources/shaders/ directory
        // The path for loadResource should start with a "/" if it's at the root of resources,
        // or be relative like "/shaders/vertex.glsl"
        shader.createVertexShader(Shader.loadResource("/shaders/vertex.glsl"));
        shader.createFragmentShader(Shader.loadResource("/shaders/fragment.glsl"));
        shader.link();

        // Create uniforms that will be used in the shaders
        shader.createUniform("projectionMatrix");
        shader.createUniform("viewMatrix");
        shader.createUniform("modelMatrix");
        shader.createUniform("blockColor");
    }

    private void initCubeMesh() {
        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;
        try {
            // Create VAO
            cubeVaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(cubeVaoId);

            // Create VBO for vertex data
            cubeVboId = GL15.glGenBuffers();
            verticesBuffer = MemoryUtil.memAllocFloat(cubeVertices.length);
            verticesBuffer.put(cubeVertices).flip(); // Put data and flip for reading
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cubeVboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);
            // Define vertex attribute pointers (position is attribute 0)
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 0, 0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0); // Unbind VBO

            // Create EBO for indices
            cubeEboId = GL15.glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(cubeIndices.length);
            indicesBuffer.put(cubeIndices).flip();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, cubeEboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);
            // Note: Do NOT unbind EBO while VAO is still bound, VAO stores the EBO binding.

            GL30.glBindVertexArray(0); // Unbind VAO
            // EBO can be unbound after VAO is unbound, but it's not strictly necessary
            // if you always bind the VAO before drawing.
            // GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        } finally {
            if (verticesBuffer != null) {
                MemoryUtil.memFree(verticesBuffer);
            }
            if (indicesBuffer != null) {
                MemoryUtil.memFree(indicesBuffer);
            }
        }
    }

    public void renderTerrain(Terrain terrain) {
        shader.bind();
        // Set projection and view matrices once per frame (or if they change)
        shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        shader.setUniform("viewMatrix", camera.getViewMatrix());

        // Bind the VAO for the cube mesh
        GL30.glBindVertexArray(cubeVaoId);
        GL20.glEnableVertexAttribArray(0); // Enable vertex attribute 0 (position)

        // Render each block in the terrain
        for (Block block : terrain.getBlocks()) {
            // Calculate model matrix for this specific block (translation)
            Matrix4f modelMatrix = new Matrix4f().translate(block.getPosition());
            shader.setUniform("modelMatrix", modelMatrix);
            shader.setUniform("blockColor", block.getColor());

            // Draw the cube
            GL11.glDrawElements(GL11.GL_TRIANGLES, cubeIndices.length, GL11.GL_UNSIGNED_INT, 0);
        }

        // Unbind VAO and disable vertex attribute
        GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        // Delete OpenGL objects
        GL30.glDeleteVertexArrays(cubeVaoId);
        GL15.glDeleteBuffers(cubeVboId);
        GL15.glDeleteBuffers(cubeEboId);
    }
}
