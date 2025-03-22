#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D velocitySampler;
layout (rg32f, binding = 0) uniform image2D velocityImg;

uniform float a; // deltaTime * diffusionRate
uniform float b; // 1.0 / (4.0 + a)

// A jacobi iteration program. 20 ~ 50 iterations required.
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);

    // Read the values of the current grid point and its four neighbors
    vec2 left   = texelFetchOffset(velocitySampler, storePos, 0, ivec2(-1, 0)).xy;
    vec2 right  = texelFetchOffset(velocitySampler, storePos, 0, ivec2(1, 0)).xy;
    vec2 down   = texelFetchOffset(velocitySampler, storePos, 0, ivec2(0, -1)).xy;
    vec2 up     = texelFetchOffset(velocitySampler, storePos, 0, ivec2(0, 1)).xy;
    vec2 v      = imageLoad(velocityImg, storePos).xy;

    // Perform Jacobi iteration: update based on neighbor values and center value
    vec2 newValue = (left + right + up + down + a * v) * b;

    imageStore(velocityImg, storePos, vec4(newValue, 0.0, 0.0));
}