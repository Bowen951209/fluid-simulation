#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;
layout (rgba32f, binding = 0) uniform image2D imgOutput;



void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    float r = fract(sin(dot(vec2(storePos), vec2(12.9898, 78.233))) * 43758.5453);
    vec4 data = vec4(r, r, r, 1.0);
    imageStore(imgOutput, storePos, data);
}