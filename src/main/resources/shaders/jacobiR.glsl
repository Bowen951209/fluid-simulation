#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (r32f, binding = 0) uniform image2D img;
layout (binding = 0) uniform sampler2D samp;

uniform float a;
uniform float b;

// A jacobi iteration program for pressure solver. 20 ~ 50 iterations required.
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(img);

    // Read the values of the current grid point and its four neighbors
    float left   = imageLoad(img, storePos + ivec2(-1, 0)).r;
    float right  = imageLoad(img, storePos + ivec2(1, 0)).r;
    float down   = imageLoad(img, storePos + ivec2(0, -1)).r;
    float up     = imageLoad(img, storePos + ivec2(0, 1)).r;
    float sampValue = texelFetch(samp, storePos, 0).r;

    // Perform Jacobi iteration: update based on neighbor values and center value
    float newValue = (sampValue + a * (left + right + up + down)) * b;

    imageStore(img, storePos, vec4(newValue, 0.0, 0.0, 0.0));
}