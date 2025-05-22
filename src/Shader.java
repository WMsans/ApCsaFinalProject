import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Shader {

    private final int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private final Map<String, Integer> uniforms;

    public Shader() throws Exception {
        programId = GL20.glCreateProgram();
        if (programId == 0) {
            throw new Exception("Could not create Shader program");
        }
        uniforms = new HashMap<>();
    }

    public void createVertexShader(String shaderCode) throws Exception {
        vertexShaderId = createShader(shaderCode, GL20.GL_VERTEX_SHADER);
    }

    public void createFragmentShader(String shaderCode) throws Exception {
        fragmentShaderId = createShader(shaderCode, GL20.GL_FRAGMENT_SHADER);
    }

    private int createShader(String shaderCode, int shaderType) throws Exception {
        int shaderId = GL20.glCreateShader(shaderType);
        if (shaderId == 0) {
            throw new Exception("Error creating shader. Type: " + shaderType);
        }

        GL20.glShaderSource(shaderId, shaderCode);
        GL20.glCompileShader(shaderId);

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new Exception("Error compiling Shader code (Type: " + shaderType + "): "
                    + GL20.glGetShaderInfoLog(shaderId, 1024));
        }

        GL20.glAttachShader(programId, shaderId);
        return shaderId;
    }

    public void link() throws Exception {
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new Exception("Error linking Shader code: "
                    + GL20.glGetProgramInfoLog(programId, 1024));
        }

        if (vertexShaderId != 0) {
            GL20.glDetachShader(programId, vertexShaderId);
        }
        if (fragmentShaderId != 0) {
            GL20.glDetachShader(programId, fragmentShaderId);
        }

        GL20.glValidateProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == 0) {
            System.err.println("Warning validating Shader code: "
                    + GL20.glGetProgramInfoLog(programId, 1024));
        }
    }

    public void createUniform(String uniformName) throws Exception {
        int uniformLocation = GL20.glGetUniformLocation(programId, uniformName);
        if (uniformLocation < 0) {
            System.err.println("Could not find uniform (it might be unused/optimized out): " + uniformName);
        }
        uniforms.put(uniformName, uniformLocation);
    }

    public void setUniform(String uniformName, Matrix4f value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer fb = stack.mallocFloat(16);
                value.get(fb);
                GL20.glUniformMatrix4fv(location, false, fb);
            }
        }
    }

    public void setUniform(String uniformName, Vector3f value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            GL20.glUniform3f(location, value.x, value.y, value.z);
        }
    }

    public void setUniform(String uniformName, float value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            GL20.glUniform1f(location, value);
        }
    }

    public void setUniform(String uniformName, int value) {
        Integer location = uniforms.get(uniformName);
        if (location != null && location >= 0) {
            GL20.glUniform1i(location, value);
        }
    }


    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public void cleanup() {
        unbind();
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
    }

    public static String loadResource(String fileName) throws Exception {
        String result;
        if (!fileName.startsWith("/")) {
            fileName = "/" + fileName;
        }
        try (InputStream in = Shader.class.getResourceAsStream(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            if (in == null) {
                throw new Exception("Cannot find resource: " + fileName +
                        ". Make sure it's in the classpath (e.g., under a 'resources' folder like 'resources/shaders" + fileName + "') " +
                        "and the resources folder is marked as such in your IDE.");
            }
            result = reader.lines().collect(Collectors.joining("\n"));
        }
        return result;
    }
}