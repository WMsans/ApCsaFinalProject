#version 330 core
layout (location = 0) in int aPosNormalPacked; // x:6, y:6, z:6, normalIdx:3 (total 21 bits)
layout (location = 1) in int aColorPacked;    // r:8, g:8, b:8 (total 24 bits)

out vec3 FragPos;
out vec3 Normal;
out vec3 v_color; // Unpacked block color

uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;

// Precomputed normals for 6 faces
const vec3 faceNormals[6] = vec3[](
vec3( 0.0,  0.0,  1.0), // Front
vec3( 0.0,  0.0, -1.0), // Back
vec3( 0.0,  1.0,  0.0), // Top
vec3( 0.0, -1.0,  0.0), // Bottom
vec3( 1.0,  0.0,  0.0), // Right
vec3(-1.0,  0.0,  0.0)  // Left
);

void main()
{
    // Unpack position (local to chunk)
    float x = float(aPosNormalPacked & 0x3F);       // 6 bits for X
    float y = float((aPosNormalPacked >> 6) & 0x3F);  // 6 bits for Y
    float z = float((aPosNormalPacked >> 12) & 0x3F); // 6 bits for Z
    vec3 localPos = vec3(x, y, z);

    // Unpack normal index
    int normalIndex = (aPosNormalPacked >> 18) & 0x07; // 3 bits for normal index
    Normal = faceNormals[normalIndex];

    // Unpack color
    float r = float((aColorPacked >> 16) & 0xFF) / 255.0;
    float g = float((aColorPacked >> 8) & 0xFF) / 255.0;
    float b = float(aColorPacked & 0xFF) / 255.0;
    v_color = vec3(r, g, b);

    // Calculate world position
    FragPos = vec3(modelMatrix * vec4(localPos + vec3(0.5), 1.0));

    gl_Position = projectionMatrix * viewMatrix * vec4(FragPos, 1.0);
}