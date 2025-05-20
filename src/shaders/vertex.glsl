#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aNormal; // Normal attribute

out vec3 FragPos;
out vec3 Normal;
out vec3 BlockColorData; // Pass block color if needed, or set in fragment shader

uniform mat4 modelMatrix;
uniform mat4 viewMatrix;
uniform mat4 projectionMatrix;
uniform vec3 blockColor; // Receive block color from Java

void main() {
    FragPos = vec3(modelMatrix * vec4(aPos, 1.0));
    Normal = mat3(transpose(inverse(modelMatrix))) * aNormal; // Transform normal to world space
    BlockColorData = blockColor;

    gl_Position = projectionMatrix * viewMatrix * vec4(FragPos, 1.0);
}