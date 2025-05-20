import Input.Config;
import World.Block;
import World.Terrain;
import org.joml.Matrix4f;
import org.joml.Vector3f; // Added
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
    private Config config; // Added Config

    // Cube vertices (position + normal)
    private final float[] cubeVertices = {
            // Positions          // Normals
            // Front face
            -0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
            0.5f, -0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
            0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
            -0.5f,  0.5f,  0.5f,  0.0f,  0.0f,  1.0f,
            // Back face
            -0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
            -0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
            0.5f,  0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
            0.5f, -0.5f, -0.5f,  0.0f,  0.0f, -1.0f,
            // Top face
            -0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,
            -0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
            0.5f,  0.5f,  0.5f,  0.0f,  1.0f,  0.0f,
            0.5f,  0.5f, -0.5f,  0.0f,  1.0f,  0.0f,
            // Bottom face
            -0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
            0.5f, -0.5f, -0.5f,  0.0f, -1.0f,  0.0f,
            0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
            -0.5f, -0.5f,  0.5f,  0.0f, -1.0f,  0.0f,
            // Right face
            0.5f, -0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
            0.5f,  0.5f, -0.5f,  1.0f,  0.0f,  0.0f,
            0.5f,  0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
            0.5f, -0.5f,  0.5f,  1.0f,  0.0f,  0.0f,
            // Left face
            -0.5f, -0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
            -0.5f, -0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
            -0.5f,  0.5f,  0.5f, -1.0f,  0.0f,  0.0f,
            -0.5f,  0.5f, -0.5f, -1.0f,  0.0f,  0.0f,
    };

    private final int[] cubeIndices = {
            0, 1, 2,  0, 2, 3,   // Front face
            4, 5, 6,  4, 6, 7,   // Back face
            8, 9, 10, 8, 10, 11,  // Top face
            12, 13, 14, 12, 14, 15, // Bottom face
            16, 17, 18, 16, 18, 19, // Right face
            20, 21, 22, 20, 22, 23  // Left face
    };

    private int cubeVaoId;
    private int cubeVboId;
    private int cubeEboId;

    private Vector3f lightPosition; // Store light position
    private float gammaValue;       // Store gamma

    public Renderer(Camera camera, Config config) { // Added Config
        this.camera = camera;
        this.config = config; // Store config
        this.lightPosition = config.getLightPosition(); // Get from config
        this.gammaValue = config.getGamma();           // Get from config

        try {
            initShader();
            initCubeMesh();
        } catch (Exception e) {
            System.err.println("Error initializing renderer:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void initShader() throws Exception {
        shader = new Shader();
        shader.createVertexShader(Shader.loadResource("/shaders/vertex.glsl"));
        shader.createFragmentShader(Shader.loadResource("/shaders/fragment.glsl"));
        shader.link();

        shader.createUniform("projectionMatrix");
        shader.createUniform("viewMatrix");
        shader.createUniform("modelMatrix");
        shader.createUniform("blockColor");
        // New uniforms for lighting
        shader.createUniform("lightPos");
        shader.createUniform("lightColor");
        shader.createUniform("gamma");
    }

    private void initCubeMesh() {
        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;
        try {
            cubeVaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(cubeVaoId);

            cubeVboId = GL15.glGenBuffers();
            verticesBuffer = MemoryUtil.memAllocFloat(cubeVertices.length);
            verticesBuffer.put(cubeVertices).flip();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cubeVboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

            // Vertex attribute pointers
            // Position attribute (location 0)
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6 * Float.BYTES, 0); // Stride is 6 floats now
            GL20.glEnableVertexAttribArray(0);
            // Normal attribute (location 1)
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES); // Offset is 3 floats
            GL20.glEnableVertexAttribArray(1);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            cubeEboId = GL15.glGenBuffers();
            indicesBuffer = MemoryUtil.memAllocInt(cubeIndices.length);
            indicesBuffer.put(cubeIndices).flip();
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, cubeEboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

            GL30.glBindVertexArray(0);
        } finally {
            if (verticesBuffer != null) MemoryUtil.memFree(verticesBuffer);
            if (indicesBuffer != null) MemoryUtil.memFree(indicesBuffer);
        }
    }

    public void renderTerrain(Terrain terrain) {
        shader.bind();
        shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        shader.setUniform("viewMatrix", camera.getViewMatrix());

        // Set lighting uniforms (once per frame or if they change)
        shader.setUniform("lightPos", lightPosition);
        shader.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f)); // White light
        shader.setUniform("gamma", gammaValue);
        shader.setUniform("viewPos", camera.getPosition());


        GL30.glBindVertexArray(cubeVaoId);
        // GL20.glEnableVertexAttribArray(0); // Position (already enabled with VAO or in init)
        // GL20.glEnableVertexAttribArray(1); // Normal (already enabled with VAO or in init)

        for (Block block : terrain.getBlocks()) {
            Matrix4f modelMatrix = new Matrix4f().translate(block.getPosition());
            shader.setUniform("modelMatrix", modelMatrix);
            shader.setUniform("blockColor", block.getColor());

            GL11.glDrawElements(GL11.GL_TRIANGLES, cubeIndices.length, GL11.GL_UNSIGNED_INT, 0);
        }

        // GL20.glDisableVertexAttribArray(1); // No need to disable if VAO handles it
        // GL20.glDisableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        GL30.glDeleteVertexArrays(cubeVaoId);
        GL15.glDeleteBuffers(cubeVboId);
        GL15.glDeleteBuffers(cubeEboId);
    }
}