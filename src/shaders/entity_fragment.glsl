#version 330 core
in vec3 vertexColor;
out vec4 FragColor;

// Optional: Add lighting uniforms later if needed
// uniform vec3 lightColor; // Example: vec3(1.0, 1.0, 1.0)
// uniform float ambientStrength; // Example: 0.1

void main() {
    // vec3 ambient = ambientStrength * lightColor;
    // vec3 result = ambient * vertexColor + vertexColor; // Simplified lighting
    // FragColor = vec4(result, 1.0);
    FragColor = vec4(vertexColor, 1.0); // Just output vertex color for now
}