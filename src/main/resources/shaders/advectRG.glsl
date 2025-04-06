#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D velocitySamp;
layout (binding = 1) uniform sampler2D sourceFieldSamp;
layout (rg32f, binding = 0) uniform image2D img;

uniform float deltaTime0;

void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);

    // Read velocity from velocity field (sample at center of cell)
    vec2 velocity = texelFetch(velocitySamp, storePos, 0).xy;

    // Backtrace position (in texel space)
    vec2 pos = vec2(storePos) - velocity * deltaTime0;

    // Normalize to [0,1] for texture sampling
    vec2 texSize = vec2(textureSize(sourceFieldSamp, 0));
    vec2 uv = pos / texSize;

    // Perform bilinear interpolation via texture()
    vec4 result = texture(sourceFieldSamp, uv);

    // Write result to output field
    imageStore(img, storePos, result);
}