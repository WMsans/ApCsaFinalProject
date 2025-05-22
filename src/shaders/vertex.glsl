#version 330 core

// Input: Two packed integers per vertex
layout (location = 0) in int packedPositionNormal;
layout (location = 1) in int packedColorData;

// Uniforms
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix; // Changed from vec3 worldPosition to mat4 modelMatrix
// uniform vec3 viewPos; // Graphics.Camera's world position (if needed, pass from Java)

// Outputs to Fragment Graphics.Shader
out vec3 FragPos_FS_world;
out vec3 Normal_FS_world;
out vec3 VertexColor_FS;

// Predefined normals
const vec3 NORMALS[6] = vec3[](
vec3(0.0,  0.0,  1.0), vec3(0.0,  0.0, -1.0),
vec3(0.0,  1.0,  0.0), vec3(0.0, -1.0,  0.0),
vec3(1.0,  0.0,  0.0), vec3(-1.0, 0.0,  0.0)
);

// Bit masks and shifts for packedPositionNormal
const int POS_X_MASK_PN   = 0x3F;    // 6 bits
const int POS_Y_SHIFT_PN  = 6;
const int POS_Y_MASK_PN   = 0x3F;
const int POS_Z_SHIFT_PN  = 12;
const int POS_Z_MASK_PN   = 0x3F;
const int NORMAL_SHIFT_PN = 18;
const int NORMAL_MASK_PN  = 0x07;    // 3 bits

// Bit masks and shifts for packedColorData
const int COLOR_R_MASK_C   = 0xFF;    // 8 bits
const int COLOR_G_SHIFT_C  = 8;
const int COLOR_G_MASK_C   = 0xFF;
const int COLOR_B_SHIFT_C  = 16;
const int COLOR_B_MASK_C   = 0xFF;

void main() {
    // Unpack position and normal
    int localPosX   = (packedPositionNormal) & POS_X_MASK_PN;
    int localPosY   = (packedPositionNormal >> POS_Y_SHIFT_PN) & POS_Y_MASK_PN;
    int localPosZ   = (packedPositionNormal >> POS_Z_SHIFT_PN) & POS_Z_MASK_PN;
    int normalIndex = (packedPositionNormal >> NORMAL_SHIFT_PN) & NORMAL_MASK_PN;

    vec3 localPosition = vec3(localPosX, localPosY, localPosZ);

    // Transform local position to world position using the modelMatrix
    vec4 worldPosHomogeneous = modelMatrix * vec4(localPosition, 1.0);
    FragPos_FS_world = worldPosHomogeneous.xyz / worldPosHomogeneous.w; // Perspective divide for world position

    // Transform normal to world space (typically (inverse(transpose(modelMatrix)) * vec4(normal, 0.0)).xyz)
    // For now, assuming modelMatrix only contains translation and uniform scaling,
    // we can transform the normal by the model matrix (ignoring translation part)
    // A more robust solution is needed if non-uniform scaling or complex transforms are in modelMatrix.
    // For simple translation (as is the case here), the local normal is the world normal.
    Normal_FS_world = NORMALS[normalIndex]; // If modelMatrix only translates, normal doesn't change direction in world space relative to model
    // If modelMatrix has rotation/scaling, this needs to be: mat3(transpose(inverse(modelMatrix))) * NORMALS[normalIndex];

    gl_Position = projectionMatrix * viewMatrix * worldPosHomogeneous;

    // Unpack color
    float r = float((packedColorData) & COLOR_R_MASK_C) / 255.0;
    float g = float((packedColorData >> COLOR_G_SHIFT_C) & COLOR_G_MASK_C) / 255.0;
    float b = float((packedColorData >> COLOR_B_SHIFT_C) & COLOR_B_MASK_C) / 255.0;
    VertexColor_FS = vec3(r, g, b);
}