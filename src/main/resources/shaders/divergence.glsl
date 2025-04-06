#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D velocitySampler;
layout (r32f, binding = 0) uniform image2D divergenceImg;

uniform float h; // 1.0 / N

void main() {
    ivec2 pos = ivec2(gl_GlobalInvocationID.xy);

    float vL = texelFetchOffset(velocitySampler, pos, 0, ivec2(-1,  0)).x;
    float vR = texelFetchOffset(velocitySampler, pos, 0, ivec2( 1,  0)).x;
    float vB = texelFetchOffset(velocitySampler, pos, 0, ivec2( 0, -1)).y;
    float vT = texelFetchOffset(velocitySampler, pos, 0, ivec2( 0,  1)).y;

    float divergence = -0.5 * h * (vR - vL + vT - vB);
    imageStore(divergenceImg, pos, vec4(divergence, 0.0, 0.0, 0.0));
}