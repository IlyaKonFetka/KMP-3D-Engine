package com.example.engine3d.math

data class Vec4(val x: Float, val y: Float, val z: Float, val w: Float) {

    operator fun plus(other: Vec4)  = Vec4(x + other.x, y + other.y, z + other.z, w + other.w)
    operator fun minus(other: Vec4) = Vec4(x - other.x, y - other.y, z - other.z, w - other.w)
    operator fun times(s: Float)    = Vec4(x * s, y * s, z * s, w * s)
    operator fun div(s: Float)      = Vec4(x / s, y / s, z / s, w / s)

    /** Perspective divide: returns Vec3(x/w, y/w, z/w) if w != 0, else Vec3(x, y, z). */
    fun toVec3(): Vec3 = if (w != 0f && w != 1f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
}
