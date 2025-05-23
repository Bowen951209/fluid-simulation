#version 430 core
in vec2 texCoord;

out vec4 fragColor;

layout(binding = 0) uniform sampler2D texSampler;

void main() {
    fragColor = texture(texSampler, texCoord) * 10.0;
}