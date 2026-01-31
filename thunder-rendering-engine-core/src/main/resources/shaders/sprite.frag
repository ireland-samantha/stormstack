#version 330 core

in vec2 vTexCoord;
out vec4 FragColor;

uniform sampler2D sprite;

void main() {
    FragColor = texture(sprite, vTexCoord);
}
