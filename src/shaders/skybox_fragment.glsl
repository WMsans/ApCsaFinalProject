#version 330 core
out vec4 FragColor;

in vec3 TexCoords;

// Color palette
uniform vec3 colorDeepSpace = vec3(0.05, 0.00, 0.15); // Darkest purple/indigo
uniform vec3 colorUpperAtmosphere = vec3(0.3, 0.05, 0.4); // Dark magenta
uniform vec3 colorMiddleAtmosphere = vec3(0.9, 0.1, 0.5);   // Bright vibrant pink/magenta
uniform vec3 colorHorizonGlow = vec3(1.0, 0.4, 0.25);  // Orange/Pink horizon

uniform float starThreshold = 0.995; // Adjust for star density
uniform vec3 starColor = vec3(0.9, 0.9, 1.0); // Slightly bluish white stars

// Simple pseudo-random generator
float random(vec3 st) {
    return fract(sin(dot(st.xyz, vec3(12.9898,78.233, 45.5432))) * 43758.5453123);
}

void main()
{
    vec3 dir = normalize(TexCoords);
    float t = dir.y; // Use normalized y-coordinate for gradient

    vec3 finalColor;

    // Gradient
    if (t > 0.6) { // Deep space to upper atmosphere
                   finalColor = mix(colorUpperAtmosphere, colorDeepSpace, smoothstep(0.6, 1.0, t));
    } else if (t > 0.15) { // Upper atmosphere to middle atmosphere
                           finalColor = mix(colorMiddleAtmosphere, colorUpperAtmosphere, smoothstep(0.15, 0.6, t));
    } else if (t > -0.1) { // Middle atmosphere to horizon glow
                           finalColor = mix(colorHorizonGlow, colorMiddleAtmosphere, smoothstep(-0.1, 0.15, t));
    } else { // Below horizon (can be a solid dark color or extend horizon)
             finalColor = colorHorizonGlow * (1.0 - smoothstep(-0.1, -0.3, t) * 0.5) ; // Fade slightly below horizon
    }

    // Stars
    float starNoise = random(dir * 200.0); // Scale dir for finer noise pattern
    if (starNoise > starThreshold) {
        float starBrightness = (starNoise - starThreshold) / (1.0 - starThreshold);
        starBrightness = pow(starBrightness, 3.0); // Make stars sharper
        finalColor = mix(finalColor, starColor, starBrightness * 0.8); // Blend stars
    }
    // Add a few brighter, sparser stars
    float bigStarNoise = random(dir * 50.0);
    if (bigStarNoise > 0.999) {
        float bigStarBrightness = (bigStarNoise - 0.999) / (1.0 - 0.999);
        finalColor = mix(finalColor, vec3(1.0), bigStarBrightness);
    }


    FragColor = vec4(finalColor, 1.0);
}