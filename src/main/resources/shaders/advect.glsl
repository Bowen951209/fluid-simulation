#version 430 core

layout (local_size_x = 16, local_size_y = 16) in;

layout (binding = 0) uniform sampler2D velocitySampler; // For reading (built-in bilinear interpolation)
layout (rg32f, binding = 0) uniform image2D velocityImg; // For writing

uniform float deltaTime;
uniform vec2 resolution;

void main() {
    ivec2 storePos = ivec2(gl_GlobalInvocationID.xy);
    vec2 uv = (vec2(storePos) + 0.5) / resolution;

    // Read current velocity
    vec2 velocity = texture(velocitySampler, storePos).xy;

    // Calculate new position (backwards tracing)
    vec2 prevPos = uv - velocity * deltaTime;

    // Sample from old position (Advection)
    vec2 newVelocity = texture(velocitySampler, prevPos).xy;
//    vec2 newVelocity = vec2(fract(sin(dot(vec2(storePos), vec2(12.9898, 78.233))) * 43758.5453));


    imageStore(velocityImg, storePos, vec4(newVelocity, 0.0, 0.0));
}