#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec3 BlockColorData;

uniform vec3 lightPos;
uniform vec3 lightColor;
uniform float gamma;
uniform vec3 viewPos;

void main() {
    // Ambient light
    float ambientStrength = gamma; // Use gamma as ambient strength
    vec3 ambient = ambientStrength * lightColor;

    // Diffuse lighting
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // For now, no specular lighting to keep it simpler
    // vec3 viewDir = normalize(viewPos - FragPos);
    // vec3 reflectDir = reflect(-lightDir, norm);
    // float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32); // 32 is shininess
    // vec3 specular = 0.5 * spec * lightColor; // Specular strength 0.5

    vec3 result = (ambient + diffuse) * BlockColorData;
    FragColor = vec4(result, 1.0);
}