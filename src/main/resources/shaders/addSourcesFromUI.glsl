#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D inputSampler;
layout (r32f, binding = 0) uniform image2D densityImage;
layout (rg32f, binding = 1) uniform image2D velocityImage;

void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    vec4 inputData = texelFetch(inputSampler, storePos, 0);

    imageStore(velocityImage, storePos, vec4(inputData.rg, 0.0, 0.0));
    imageStore(densityImage, storePos, vec4(inputData.b, 0.0, 0.0, 0.0));
}