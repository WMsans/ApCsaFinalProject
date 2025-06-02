package Graphics;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

public class Skybox {

    private int vaoId;
    private int vboId;
    private Shader shader;

    // Simple cube vertices for the skybox (size 1, will be scaled by view/projection)
    private static final float[] SKYBOX_VERTICES = {
            // Positions
            -1.0f,  1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f,
            1.0f, -1.0f, -1.0f,
            1.0f, -1.0f, -1.0f,
            1.0f,  1.0f, -1.0f,
            -1.0f,  1.0f, -1.0f,

            -1.0f, -1.0f,  1.0f,
            -1.0f, -1.0f, -1.0f,
            -1.0f,  1.0f, -1.0f,
            -1.0f,  1.0f, -1.0f,
            -1.0f,  1.0f,  1.0f,
            -1.0f, -1.0f,  1.0f,

            1.0f, -1.0f, -1.0f,
            1.0f, -1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,
            1.0f,  1.0f, -1.0f,
            1.0f, -1.0f, -1.0f,

            -1.0f, -1.0f,  1.0f,
            -1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,
            1.0f, -1.0f,  1.0f,
            -1.0f, -1.0f,  1.0f,

            -1.0f,  1.0f, -1.0f,
            1.0f,  1.0f, -1.0f,
            1.0f,  1.0f,  1.0f,
            1.0f,  1.0f,  1.0f,
            -1.0f,  1.0f,  1.0f,
            -1.0f,  1.0f, -1.0f,

            -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f,  1.0f,
            1.0f, -1.0f, -1.0f, // Typo in original common skybox data, should be 1.0f, -1.0f, 1.0f
            1.0f, -1.0f, -1.0f, // Typo
            -1.0f, -1.0f,  1.0f, // Typo
            1.0f, -1.0f,  1.0f
    };
    // Corrected last face for a proper cube (bottom face):
    private static final float[] CORRECTED_SKYBOX_VERTICES = {
            // Positions
            -1.0f,  1.0f, -1.0f, -1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f, // Front face
            -1.0f, -1.0f,  1.0f, -1.0f, -1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f,  1.0f, -1.0f, -1.0f,  1.0f, // Left face
            1.0f, -1.0f, -1.0f,  1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f, -1.0f, // Right face
            -1.0f, -1.0f,  1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f, -1.0f,  1.0f, // Back face
            -1.0f,  1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f,  1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f, -1.0f, // Top face
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,  1.0f,  1.0f, -1.0f, -1.0f,  1.0f, -1.0f, -1.0f, -1.0f, -1.0f,  1.0f,  1.0f, -1.0f,  1.0f  // Bottom face
    };


    public Skybox() {
        // Shader will be initialized in Renderer or a dedicated init method
    }

    public void init() throws Exception {
        shader = new Shader();
        shader.createVertexShader(Shader.loadResource("/shaders/skybox_vertex.glsl"));
        shader.createFragmentShader(Shader.loadResource("/shaders/skybox_fragment.glsl"));
        shader.link();

        shader.createUniform("projection");
        shader.createUniform("view");
        // Optional: create uniforms for colors and starThreshold if you want to control them from Java
        shader.createUniform("colorDeepSpace");
        shader.createUniform("colorUpperAtmosphere");
        shader.createUniform("colorMiddleAtmosphere");
        shader.createUniform("colorHorizonGlow");
        shader.createUniform("starThreshold");
        shader.createUniform("starColor");


        FloatBuffer verticesBuffer = null;
        try {
            verticesBuffer = MemoryUtil.memAllocFloat(CORRECTED_SKYBOX_VERTICES.length);
            verticesBuffer.put(CORRECTED_SKYBOX_VERTICES).flip();

            vaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vaoId);

            vboId = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
            GL20.glEnableVertexAttribArray(0);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);
        } finally {
            if (verticesBuffer != null) {
                MemoryUtil.memFree(verticesBuffer);
            }
        }
    }

    public void render(Camera camera) {
        if (vaoId == 0 || shader == null) return;

        GL11.glDepthFunc(GL11.GL_LEQUAL); // Change depth function so Dunlop skybox passes when depth is 1.0
        // GL11.glDepthMask(GL11.GL_FALSE); // Alternative: Disable depth writing

        shader.bind();

        Matrix4f viewMatrix = new Matrix4f(camera.getViewMatrix());
        // Remove translation from the view matrix
        viewMatrix.m30(0); viewMatrix.m31(0); viewMatrix.m32(0);

        shader.setUniform("view", viewMatrix);
        shader.setUniform("projection", camera.getProjectionMatrix());

        // Set color uniforms (optional, if you made them uniforms in the shader)
        // shader.setUniform("colorDeepSpace", new Vector3f(0.05f, 0.00f, 0.15f));
        // shader.setUniform("colorUpperAtmosphere", new Vector3f(0.3f, 0.05f, 0.4f));
        // ... and so on for other colors and starThreshold

        GL30.glBindVertexArray(vaoId);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36); // 36 vertices for a cube (6 faces * 2 triangles * 3 vertices)
        GL30.glBindVertexArray(0);

        shader.unbind();

        // GL11.glDepthMask(GL11.GL_TRUE); // Re-enable depth writing if disabled
        GL11.glDepthFunc(GL11.GL_LESS); // Reset depth function to default
    }

    public void cleanup() {
        if (shader != null) {
            shader.cleanup();
        }
        if (vaoId != 0) {
            GL30.glDeleteVertexArrays(vaoId);
            vaoId = 0;
        }
        if (vboId != 0) {
            GL15.glDeleteBuffers(vboId);
            vboId = 0;
        }
    }
}