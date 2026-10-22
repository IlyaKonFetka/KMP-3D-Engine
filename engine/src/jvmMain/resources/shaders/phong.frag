#version 330 core

in vec3 vFragPos;
in vec3 vNormal;

// ---- Camera ----
uniform vec3 uViewPos;

// ---- Material ----
struct Material {
    vec3  ambient;
    vec3  diffuse;
    vec3  specular;
    float shininess;
};
uniform Material uMat;

// ---- Directional light ----
struct DirLight {
    vec3  direction;
    vec3  color;
    float intensity;
};
uniform DirLight uDirLight;

// ---- Point light ----
struct PointLight {
    vec3  position;
    vec3  color;
    float intensity;
    float constant;
    float linear;
    float quadratic;
};
uniform PointLight uPointLight;

out vec4 FragColor;

vec3 calcDirLight(vec3 norm, vec3 viewDir) {
    vec3 lightDir = normalize(-uDirLight.direction);
    float diff    = max(dot(norm, lightDir), 0.0);
    vec3 reflDir  = reflect(-lightDir, norm);
    float spec    = pow(max(dot(viewDir, reflDir), 0.0), uMat.shininess);

    vec3 ambient  = uMat.ambient  * uDirLight.color * uDirLight.intensity;
    vec3 diffuse  = uMat.diffuse  * uDirLight.color * uDirLight.intensity * diff;
    vec3 specular = uMat.specular * uDirLight.color * uDirLight.intensity * spec;
    return ambient + diffuse + specular;
}

vec3 calcPointLight(vec3 norm, vec3 viewDir) {
    vec3  lightDir    = normalize(uPointLight.position - vFragPos);
    float diff        = max(dot(norm, lightDir), 0.0);
    vec3  reflDir     = reflect(-lightDir, norm);
    float spec        = pow(max(dot(viewDir, reflDir), 0.0), uMat.shininess);

    float dist        = length(uPointLight.position - vFragPos);
    float attenuation = 1.0 / (uPointLight.constant
                              + uPointLight.linear    * dist
                              + uPointLight.quadratic * dist * dist);

    vec3 ambient  = uMat.ambient  * uPointLight.color * uPointLight.intensity * attenuation;
    vec3 diffuse  = uMat.diffuse  * uPointLight.color * uPointLight.intensity * diff * attenuation;
    vec3 specular = uMat.specular * uPointLight.color * uPointLight.intensity * spec * attenuation;
    return ambient + diffuse + specular;
}

void main() {
    vec3 norm    = normalize(vNormal);
    vec3 viewDir = normalize(uViewPos - vFragPos);

    vec3 result = vec3(0.0);
    if (uDirLight.intensity   > 0.0) result += calcDirLight(norm, viewDir);
    if (uPointLight.intensity > 0.0) result += calcPointLight(norm, viewDir);

    // Fallback: at least show ambient even if no lights
    if (uDirLight.intensity == 0.0 && uPointLight.intensity == 0.0)
        result = uMat.ambient;

    FragColor = vec4(result, 1.0);
}
