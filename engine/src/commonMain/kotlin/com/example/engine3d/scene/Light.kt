package com.example.engine3d.scene

import com.example.engine3d.math.Vec3

sealed class Light {

    data class Directional(
        val direction: Vec3  = Vec3(0f, -1f, 0f),
        val color:     Vec3  = Vec3(1f, 1f, 1f),
        val intensity: Float = 1f,
    ) : Light()

    data class Point(
        val position:  Vec3  = Vec3(0f, 5f, 0f),
        val color:     Vec3  = Vec3(1f, 1f, 1f),
        val intensity: Float = 1f,
        val constant:  Float = 1f,
        val linear:    Float = 0.09f,
        val quadratic: Float = 0.032f,
    ) : Light()
}
