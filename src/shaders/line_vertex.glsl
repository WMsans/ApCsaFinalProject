#version 330 core
layout (location = 0) in vec3 aPos;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;
// No modelMatrix needed if we pass world-space coordinates directly

void main()
{
    gl_Position = projectionMatrix * viewMatrix * vec4(aPos, 1.0);
}