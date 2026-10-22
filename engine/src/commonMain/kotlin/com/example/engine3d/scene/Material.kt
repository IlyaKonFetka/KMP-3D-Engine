package com.example.engine3d.scene

import com.example.engine3d.math.Vec3

data class Material(
    val ambientColor:  Vec3  = Vec3(0.1f, 0.1f, 0.1f),
    val diffuseColor:  Vec3  = Vec3(0.7f, 0.3f, 0.2f),
    val specularColor: Vec3  = Vec3(1f, 1f, 1f),
    val shininess:     Float = 32f,
)
