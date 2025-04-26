#version 430 core

layout (local_size_x = 16) in;

layout (rg32f, binding = 0) uniform image2D img;


void main() {
    uint id = gl_GlobalInvocationID.x;
    ivec2 imgSize = imageSize(img);

    
    ivec2 storePos;
    vec4 storeValue;
    if (id < imgSize.x) {
        // Top edge (left to right)
        storePos = ivec2(id, 0);
        storeValue = imageLoad(img, storePos + ivec2(0, 1));
        storeValue.y *= -1.0;
        imageStore(img, storePos, storeValue);
    } else if (id < 2 * imgSize.x) {
        // Bottom edge (left to right)
        storePos = ivec2(id - imgSize.x, imgSize.y - 1);
        storeValue = imageLoad(img, storePos + ivec2(0, -1));
        storeValue.y *= -1.0;
        imageStore(img, storePos, storeValue);
    } else if (id < 2 * imgSize.x + imgSize.y - 2) {
        // Left edge (top to bottom, excluding corners)
        storePos = ivec2(0, id - 2 * imgSize.x + 1);
        storeValue = imageLoad(img, storePos + ivec2(1, 0));
        storeValue.x *= -1.0;
        imageStore(img, storePos, storeValue);
    } else {
        // Right edge (top to bottom, excluding corners)
        storePos = ivec2(imgSize.x - 1, id - (2 * imgSize.x + imgSize.y - 2) + 1);
        storeValue = imageLoad(img, storePos + ivec2(-1, 0));
        storeValue.x *= -1.0;
        imageStore(img, storePos, storeValue);
    }
}