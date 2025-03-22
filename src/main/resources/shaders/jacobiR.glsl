#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (r32f, binding = 0) uniform image2D pressureImg;
layout (binding = 0) uniform sampler2D divergenceTex;

uniform float a; // deltaTime * diffusionRate
uniform float b; // 1.0 / (4.0 + a)

// A jacobi iteration program for pressure solver. 20 ~ 50 iterations required.
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);

    // Read the values of the current grid point and its four neighbors
    float left   = texelFetchOffset(divergenceTex, storePos, 0, ivec2(-1, 0)).r;
    float right  = texelFetchOffset(divergenceTex, storePos, 0, ivec2(1, 0)).r;
    float down   = texelFetchOffset(divergenceTex, storePos, 0, ivec2(0, -1)).r;
    float up     = texelFetchOffset(divergenceTex, storePos, 0, ivec2(0, 1)).r;
    float p      = imageLoad(pressureImg, storePos).r;

    // Perform Jacobi iteration: update based on neighbor values and center value
    float newValue = (left + right + up + down + a * p) * b;

    imageStore(pressureImg, storePos, vec4(newValue, 0.0, 0.0, 0.0));
}