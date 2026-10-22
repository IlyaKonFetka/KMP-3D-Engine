package com.example.engine3d.math

import kotlin.math.sqrt

data class Vec3(val x: Float, val y: Float, val z: Float) {

    operator fun plus(other: Vec3)  = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(s: Float)    = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float)      = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus()       = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSquared(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-7f) this / len else Vec3(0f, 0f, 0f)
    }

    fun lerp(to: Vec3, t: Float) = Vec3(
        x + (to.x - x) * t,
        y + (to.y - y) * t,
        z + (to.z - z) * t
    )

    fun toVec4(w: Float = 1f) = Vec4(x, y, z, w)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE  = Vec3(1f, 1f, 1f)
        val UP   = Vec3(0f, 1f, 0f)
    }
}

operator fun Float.times(v: Vec3) = v * this
