#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D samp;
layout (r32f, binding = 0) uniform image2D img;

uniform float deltaTime;

void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    vec4 value = texelFetch(samp, storePos, 0) * deltaTime + imageLoad(img, storePos);
    imageStore(img, storePos, value);
}