#version 330
#extension GL_ARB_separate_shader_objects : require

// Limbo status overlay. One shader, many looks: every post effect in assets/limbo/post_effect/ feeds it
// different numbers (see tools/gen_post_effects.py). Everything is deliberately gentle: the centre of the
// screen is never touched, only the rim is tinted/darkened, so you can always see what is in front of you.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform TintConfig {
    vec4 EdgeColor; // rgb = colour pulled in at the rim, a = how much of it at the very edge (0..1)
    vec4 Shape;     // x = radius that stays completely clear, y = radius where the rim is at full strength,
                    // z = desaturation of the whole screen, w = darkening at the rim
    vec4 Misc;      // x = whole-screen tint amount, y = colour fringing at the rim
};

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float aspect = OutSize.x / max(OutSize.y, 1.0);

    // 0 in the middle, 1 in the corners; round on any aspect ratio
    vec2 p = vec2(centered.x * aspect, centered.y);
    float d = length(p) / length(vec2(0.5 * aspect, 0.5));
    float rim = smoothstep(Shape.x, max(Shape.y, Shape.x + 0.01), d);

    vec2 fringe = centered * Misc.y * rim;
    vec3 col = vec3(
        texture(InSampler, texCoord + fringe).r,
        texture(InSampler, texCoord).g,
        texture(InSampler, texCoord - fringe).b
    );

    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), clamp(Shape.z, 0.0, 1.0));
    col = mix(col, EdgeColor.rgb * clamp(lum * 1.4 + 0.1, 0.0, 1.0), clamp(Misc.x, 0.0, 1.0));
    col = mix(col, EdgeColor.rgb, rim * clamp(EdgeColor.a, 0.0, 1.0));
    col *= 1.0 - rim * clamp(Shape.w, 0.0, 1.0);

    fragColor = vec4(col, 1.0);
}
