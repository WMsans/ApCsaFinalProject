#version 330 core

// Input: Two packed integers per vertex
layout (location = 0) in int packedPositionNormal;
layout (location = 1) in int packedColorData; // This is now (R_val << 16) | (G_val << 8) | B_val

// Uniforms
uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
uniform mat4 modelMatrix;

// Outputs to Fragment Shader
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
// These describe the structure of the integer *as it is read by the shader*
// COLOR_R_MASK_C is used for the lowest 8 bits, G_SHIFT_C for the next, B_SHIFT_C for the next.
const int BLUE_MASK_FROM_PACKED  = 0xFF;    // To extract B from 0xRRGGBB (lowest 8 bits)
const int GREEN_SHIFT_IN_PACKED = 8;
const int GREEN_MASK_FROM_PACKED = 0xFF;    // To extract G from 0xRRGGBB
const int RED_SHIFT_IN_PACKED   = 16;
const int RED_MASK_FROM_PACKED   = 0xFF;    // To extract R from 0xRRGGBB

void main() {
    // Unpack position and normal
    int localPosX   = (packedPositionNormal) & POS_X_MASK_PN;
    int localPosY   = (packedPositionNormal >> POS_Y_SHIFT_PN) & POS_Y_MASK_PN;
    int localPosZ   = (packedPositionNormal >> POS_Z_SHIFT_PN) & POS_Z_MASK_PN;
    int normalIndex = (packedPositionNormal >> NORMAL_SHIFT_PN) & NORMAL_MASK_PN;

    vec3 localPosition = vec3(localPosX, localPosY, localPosZ);

    vec4 worldPosHomogeneous = modelMatrix * vec4(localPosition, 1.0);
    FragPos_FS_world = worldPosHomogeneous.xyz / worldPosHomogeneous.w;

    Normal_FS_world = NORMALS[normalIndex];
    // For proper lighting with rotations/scaling in modelMatrix, use:
    // Normal_FS_world = normalize(mat3(transpose(inverse(modelMatrix))) * NORMALS[normalIndex]);

    gl_Position = projectionMatrix * viewMatrix * worldPosHomogeneous;

    // Unpack color: packedColorData is (R_val << 16) | (G_val << 8) | B_val
    // The constants COLOR_R_MASK_C etc. were named for the original packing.
    // We'll use new local variables for clarity or ensure correct interpretation.

    float b_component = float((packedColorData) & BLUE_MASK_FROM_PACKED) / 255.0;
    float g_component = float((packedColorData >> GREEN_SHIFT_IN_PACKED) & GREEN_MASK_FROM_PACKED) / 255.0;
    float r_component = float((packedColorData >> RED_SHIFT_IN_PACKED) & RED_MASK_FROM_PACKED) / 255.0;

    VertexColor_FS = vec3(r_component, g_component, b_component); // Assemble as R, G, B
}