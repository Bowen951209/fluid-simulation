#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (rg32f, binding = 0) uniform image2D img;
void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    imageStore(img, storePos, vec4(0.0));
}