package com.example.engine3d.renderer.android

/** OpenGL ES 2.0 Phong shaders (no layout qualifiers, precision required). */
internal object Shaders {

    val VERT = """
        attribute vec3 aPosition;
        attribute vec3 aNormal;

        uniform mat4 uModel;
        uniform mat4 uView;
        uniform mat4 uProjection;
        uniform mat3 uNormalMatrix;

        varying vec3 vFragPos;
        varying vec3 vNormal;

        void main() {
            vec4 worldPos = uModel * vec4(aPosition, 1.0);
            vFragPos    = worldPos.xyz;
            vNormal     = uNormalMatrix * aNormal;
            gl_Position = uProjection * uView * worldPos;
        }
    """.trimIndent()

    val FRAG = """
        precision mediump float;

        varying vec3 vFragPos;
        varying vec3 vNormal;

        uniform vec3  uViewPos;
        uniform vec3  uAmbient;
        uniform vec3  uDiffuse;
        uniform vec3  uSpecular;
        uniform float uShininess;

        uniform vec3  uLightDir;
        uniform vec3  uLightColor;

        void main() {
            vec3 norm    = normalize(vNormal);
            vec3 viewDir = normalize(uViewPos - vFragPos);

            vec3 lightDir = normalize(-uLightDir);
            float diff    = max(dot(norm, lightDir), 0.0);
            vec3 reflDir  = reflect(-lightDir, norm);
            float spec    = pow(max(dot(viewDir, reflDir), 0.0), uShininess);

            vec3 ambient  = uAmbient  * uLightColor;
            vec3 diffuse  = uDiffuse  * uLightColor * diff;
            vec3 specular = uSpecular * uLightColor * spec;

            gl_FragColor  = vec4(ambient + diffuse + specular, 1.0);
        }
    """.trimIndent()
}
