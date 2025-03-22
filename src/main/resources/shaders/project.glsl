#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D pressureSampler;
layout (rg32f, binding = 0) uniform image2D velocityImg;

uniform float gridScale; // 0.5 / dx

void main() {
    ivec2 texelPos = ivec2(gl_GlobalInvocationID.xy);

    float pL = texelFetchOffset(pressureSampler, texelPos, 0, ivec2(-1, 0)).x;
    float pR = texelFetchOffset(pressureSampler, texelPos, 0, ivec2(1, 0)).x;
    float pB = texelFetchOffset(pressureSampler, texelPos, 0, ivec2(0, -1)).x;
    float pT = texelFetchOffset(pressureSampler, texelPos, 0, ivec2(0, 1)).x;

    vec2 velocity = imageLoad(velocityImg, texelPos).xy;
    velocity -= gridScale * vec2(pR - pL, pT - pB);

    imageStore(velocityImg, texelPos, vec4(velocity, 0.0, 1.0));
}
