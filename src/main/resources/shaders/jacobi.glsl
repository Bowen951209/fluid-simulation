#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (REPLACE_ME, binding = 0) uniform image2D img;
layout (binding = 0) uniform sampler2D samp;

uniform float a;
uniform float b;

// A jacobi iteration program for pressure solver. 20 ~ 50 iterations required.
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);

    // Read the values of the current grid point and its four neighbors
    vec4 left   = imageLoad(img, storePos + ivec2(-1, 0));
    vec4 right  = imageLoad(img, storePos + ivec2(1, 0));
    vec4 down   = imageLoad(img, storePos + ivec2(0, -1));
    vec4 up     = imageLoad(img, storePos + ivec2(0, 1));
    vec4 sampValue = texelFetch(samp, storePos, 0);

    // Perform Jacobi iteration: update based on neighbor values and center value
    vec4 newValue = (sampValue + a * (left + right + up + down)) * b;

    imageStore(img, storePos, newValue);
}