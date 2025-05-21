#version 330 core
layout (location = 0) in vec3 aPos;        // Vertex Position
layout (location = 1) in vec3 aNormal;     // Vertex Normal
layout (location = 2) in vec3 aColor;      // Vertex Color (New)

out vec3 FragPos_world; // Fragment position in world space
out vec3 Normal_world;  // Normal in world space
out vec3 VertexColor_FS;  // Color to pass to fragment shader

uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

void main() {
    FragPos_world = vec3(modelMatrix * vec4(aPos, 1.0));
    // Calculate normal in world space. Use transpose(inverse(modelMatrix)) for non-uniform scaling.
    // For uniform scaling, mat3(modelMatrix) * aNormal is often sufficient and cheaper.
    // Assuming uniform scaling or no scaling for normals of blocks within a chunk relative to chunk transform.
    Normal_world = mat3(transpose(inverse(modelMatrix))) * aNormal;
    VertexColor_FS = aColor;

    gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(aPos, 1.0);
}