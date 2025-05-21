import Configuration.Config;
import World.Block;
import World.Terrain;
import World.Chunk.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List; // For list of blocks/chunks

public class Renderer {

    private Shader shader;
    private Camera camera;
    private Config config; // For render distance and other settings

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

    private Vector3f lightPosition;
    private float gammaValue;

    public Renderer(Camera camera, Config config) {
        this.camera = camera;
        this.config = config;
        this.lightPosition = config.getLightPosition();
        this.gammaValue = config.getGamma();

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
        shader.createUniform("lightPos");
        shader.createUniform("lightColor");
        shader.createUniform("gamma");
        shader.createUniform("viewPos"); // Make sure this is created
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

            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 6 * Float.BYTES, 0);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES);
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

    public void renderTerrain(Terrain terrain, Vector3f playerPosition) {
        shader.bind();
        shader.setUniform("projectionMatrix", camera.getProjectionMatrix());
        shader.setUniform("viewMatrix", camera.getViewMatrix());
        shader.setUniform("lightPos", lightPosition);
        shader.setUniform("lightColor", new Vector3f(1.0f, 1.0f, 1.0f));
        shader.setUniform("gamma", gammaValue);
        shader.setUniform("viewPos", camera.getPosition());

        GL30.glBindVertexArray(cubeVaoId);

        ChunkId playerChunkId = Chunk.getChunkIdAtWorldPosition(playerPosition);
        int renderDist = config.getRenderDistanceInChunks();

        // Iterate through chunks in render distance
        for (int dx = -renderDist; dx <= renderDist; dx++) {
            for (int dy = -renderDist; dy <= renderDist; dy++) { // Iterate Y chunks as well
                for (int dz = -renderDist; dz <= renderDist; dz++) {
                    ChunkId currentChunkId = new ChunkId(playerChunkId.x + dx, playerChunkId.y + dy, playerChunkId.z + dz);
                    Chunk chunkToRender = terrain.getChunk(currentChunkId);

                    if (chunkToRender != null) {
                        // Optional: Frustum culling for the entire chunk AABB could go here
                        // if (!camera.isAABBInFrustum(chunkToRender.getMinCorner(), chunkToRender.getMaxCorner())) {
                        //    continue;
                        // }

                        for (Block block : chunkToRender.getBlocks()) {
                            // Optional: Frustum culling per block (more expensive)
                            // if (!camera.isPointInFrustum(block.getPosition())) continue; // Simple point culling
                            // Or AABB culling for block:
                            // CustomAABB blockAABB = CustomAABB.forBlock(block.getPosition());
                            // if (!camera.isAABBInFrustum(blockAABB.min, blockAABB.max)) continue;


                            Matrix4f modelMatrix = new Matrix4f().translate(block.getPosition());
                            shader.setUniform("modelMatrix", modelMatrix);
                            shader.setUniform("blockColor", block.getColor());
                            GL11.glDrawElements(GL11.GL_TRIANGLES, cubeIndices.length, GL11.GL_UNSIGNED_INT, 0);
                        }
                    }
                }
            }
        }
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
