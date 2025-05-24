package Graphics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ModelRenderer {

    private Shader entityShader;

    public ModelRenderer() {
    }

    public void init() throws Exception {
        entityShader = new Shader();
        entityShader.createVertexShader(Shader.loadResource("/shaders/entity_vertex.glsl"));
        entityShader.createFragmentShader(Shader.loadResource("/shaders/entity_fragment.glsl"));
        entityShader.link();

        entityShader.createUniform("projectionMatrix");
        entityShader.createUniform("viewMatrix");
        entityShader.createUniform("modelMatrix");
        // Add other uniforms if your entity_fragment.glsl uses them (e.g., lightColor, viewPos)
    }

    public void buildMesh(EntityModel model) {
        if (model == null || model.getVertices() == null || model.getIndices() == null) {
            System.err.println("ModelRenderer: Attempted to build mesh for null or incomplete model.");
            return;
        }
        if (model.getVaoId() != 0) { // Already built
            model.cleanup(); // Clean up old buffers if rebuilding
        }


        FloatBuffer verticesBuffer = null;
        IntBuffer indicesBuffer = null;

        try {
            verticesBuffer = MemoryUtil.memAllocFloat(model.getVertices().length);
            verticesBuffer.put(model.getVertices()).flip();

            indicesBuffer = MemoryUtil.memAllocInt(model.getIndices().length);
            indicesBuffer.put(model.getIndices()).flip();

            int vaoId = GL30.glGenVertexArrays();
            GL30.glBindVertexArray(vaoId);
            model.setVaoId(vaoId);

            int vboId = GL15.glGenBuffers();
            model.setVboId(vboId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verticesBuffer, GL15.GL_STATIC_DRAW);

            int eboId = GL15.glGenBuffers();
            model.setEboId(eboId);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, eboId);
            GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL15.GL_STATIC_DRAW);

            // Vertex attributes: position (vec3), color (vec3)
            // Stride is 6 floats (3 for pos, 3 for color)
            int stride = 6 * Float.BYTES;
            // Position attribute
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
            GL20.glEnableVertexAttribArray(0);
            // Color attribute
            GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
            GL20.glEnableVertexAttribArray(1);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL30.glBindVertexArray(0);

        } finally {
            if (verticesBuffer != null) MemoryUtil.memFree(verticesBuffer);
            if (indicesBuffer != null) MemoryUtil.memFree(indicesBuffer);
        }
    }

    public void render(EntityModel model, Matrix4f modelMatrix, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
        if (model == null || model.getVaoId() == 0) {
            return;
        }

        entityShader.bind();
        entityShader.setUniform("modelMatrix", modelMatrix);
        entityShader.setUniform("viewMatrix", viewMatrix);
        entityShader.setUniform("projectionMatrix", projectionMatrix);

        GL30.glBindVertexArray(model.getVaoId());
        GL11.glDrawElements(GL11.GL_TRIANGLES, model.getIndexCount(), GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);

        entityShader.unbind();
    }

    public void cleanup() {
        if (entityShader != null) {
            entityShader.cleanup();
        }
    }
}