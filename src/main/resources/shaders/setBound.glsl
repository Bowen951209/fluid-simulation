#version 430 core

layout (local_size_x = 16) in;

layout (rg32f, binding = 0) uniform image2D img;

void main() {
    uint id = gl_GlobalInvocationID.x;
    ivec2 imgSize = imageSize(img);

    
    ivec2 storePos;
    vec4 storeValue;

    // Corners
    if(id == 0) {
        // Top-left corner
        storePos = ivec2(0, 0);
        storeValue = 0.5 * (imageLoad(img, ivec2(1, 0)) + imageLoad(img , ivec2(0, 1)));
    } else if (id == imgSize.x - 1) {
        // Top-right corner
        storePos = ivec2(imgSize.x - 1, 0);
        storeValue = 0.5 * (imageLoad(img, ivec2(imgSize.x - 2, 0)) + imageLoad(img , ivec2(imgSize.x - 1, 1)));
    } else if (id == imgSize.x * (imgSize.y - 1)) {
        // Bottom-left corner
        storePos = ivec2(0, imgSize.y - 1);
        storeValue = 0.5 * (imageLoad(img, ivec2(1, imgSize.y - 1)) + imageLoad(img , ivec2(0, imgSize.y - 2)));
    } else if (id == imgSize.x * imgSize.y - 1) {
        // Bottom-right corner
        storePos = ivec2(imgSize.x - 1, imgSize.y - 1);
        storeValue = 0.5 * (imageLoad(img, ivec2(imgSize.x - 2, imgSize.y - 1)) + imageLoad(img , ivec2(imgSize.x - 1, imgSize.y - 2)));
    }

    // Edges
    else if (id < imgSize.x) {
        // Top edge (left to right)
        storePos = ivec2(id, 0);
        storeValue = imageLoad(img, storePos + ivec2(0, 1));
        storeValue.y *= -1.0;
    } else if (id < 2 * imgSize.x) {
        // Bottom edge (left to right)
        storePos = ivec2(id - imgSize.x, imgSize.y - 1);
        storeValue = imageLoad(img, storePos + ivec2(0, -1));
        storeValue.y *= -1.0;
    } else if (id < 2 * imgSize.x + imgSize.y - 2) {
        // Left edge (top to bottom, excluding corners)
        storePos = ivec2(0, id - 2 * imgSize.x + 1);
        storeValue = imageLoad(img, storePos + ivec2(1, 0));
        storeValue.x *= -1.0;
    } else {
        // Right edge (top to bottom, excluding corners)
        storePos = ivec2(imgSize.x - 1, id - (2 * imgSize.x + imgSize.y - 2) + 1);
        storeValue = imageLoad(img, storePos + ivec2(-1, 0));
        storeValue.x *= -1.0;
    }

    imageStore(img, storePos, storeValue);
}