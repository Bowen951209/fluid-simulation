#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D samp;
layout (rg32f, binding = 0) uniform image2D img;

void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    vec2 rgVal = texelFetch(samp, storePos, 0).xy;
    imageStore(img, storePos, vec4(rgVal, 0.0, 0.0));
}