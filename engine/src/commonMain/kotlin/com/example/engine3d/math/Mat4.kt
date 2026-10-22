package com.example.engine3d.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 4x4 matrix, column-major storage (matching OpenGL convention).
 * Element at row r, column c is stored at m[c * 4 + r].
 *
 * Columns:
 *   m[ 0.. 3] = column 0 (X basis)
 *   m[ 4.. 7] = column 1 (Y basis)
 *   m[ 8..11] = column 2 (Z basis)
 *   m[12..15] = column 3 (translation)
 */
class Mat4(val m: FloatArray = FloatArray(16)) {

    // Access element at row r, column c
    private fun get(r: Int, c: Int): Float = m[c * 4 + r]
    private fun set(r: Int, c: Int, v: Float) { m[c * 4 + r] = v }

    operator fun times(other: Mat4): Mat4 {
        val result = Mat4()
        for (c in 0..3) {
            for (r in 0..3) {
                var sum = 0f
                for (k in 0..3) sum += get(r, k) * other.get(k, c)
                result.set(r, c, sum)
            }
        }
        return result
    }

    operator fun times(v: Vec4): Vec4 {
        return Vec4(
            get(0,0)*v.x + get(0,1)*v.y + get(0,2)*v.z + get(0,3)*v.w,
            get(1,0)*v.x + get(1,1)*v.y + get(1,2)*v.z + get(1,3)*v.w,
            get(2,0)*v.x + get(2,1)*v.y + get(2,2)*v.z + get(2,3)*v.w,
            get(3,0)*v.x + get(3,1)*v.y + get(3,2)*v.z + get(3,3)*v.w,
        )
    }

    operator fun times(v: Vec3): Vec3 = (this * v.toVec4(1f)).toVec3()

    fun transposed(): Mat4 {
        val r = Mat4()
        for (c in 0..3) for (row in 0..3) r.set(c, row, get(row, c))
        return r
    }

    /** Returns a copy of the internal float array (safe for GL upload). */
    fun toFloatArray(): FloatArray = m.copyOf()

    companion object {

        fun identity(): Mat4 = Mat4(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        ))

        fun translation(tx: Float, ty: Float, tz: Float): Mat4 = Mat4(floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, tz, 1f
        ))

        fun scale(sx: Float, sy: Float, sz: Float): Mat4 = Mat4(floatArrayOf(
            sx, 0f, 0f, 0f,
            0f, sy, 0f, 0f,
            0f, 0f, sz, 0f,
            0f, 0f, 0f, 1f
        ))

        fun rotationX(angleRad: Float): Mat4 {
            val c = cos(angleRad); val s = sin(angleRad)
            return Mat4(floatArrayOf(
                1f,  0f, 0f, 0f,
                0f,  c,  s,  0f,
                0f, -s,  c,  0f,
                0f,  0f, 0f, 1f
            ))
        }

        fun rotationY(angleRad: Float): Mat4 {
            val c = cos(angleRad); val s = sin(angleRad)
            return Mat4(floatArrayOf(
                c,  0f, -s, 0f,
                0f, 1f,  0f, 0f,
                s,  0f,  c,  0f,
                0f, 0f,  0f, 1f
            ))
        }

        fun rotationZ(angleRad: Float): Mat4 {
            val c = cos(angleRad); val s = sin(angleRad)
            return Mat4(floatArrayOf(
                c,  s,  0f, 0f,
               -s,  c,  0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f
            ))
        }

        /**
         * View matrix — right-handed coordinate system.
         * eye: camera position, center: look-at target, up: world up vector.
         */
        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val f = (center - eye).normalized()          // forward
            val r = f.cross(up).normalized()             // right
            val u = r.cross(f)                           // corrected up

            // Rotation part is transposed (inverse of orthonormal rotation)
            // Translation = -dot(basis, eye)
            return Mat4(floatArrayOf(
                 r.x,  u.x, -f.x, 0f,
                 r.y,  u.y, -f.y, 0f,
                 r.z,  u.z, -f.z, 0f,
                -r.dot(eye), -u.dot(eye), f.dot(eye), 1f
            ))
        }

        /**
         * Perspective projection matrix (right-handed, depth range [-1, 1] — OpenGL convention).
         * fovYRad: vertical field of view in radians.
         * aspect: width / height.
         */
        fun perspective(fovYRad: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan(fovYRad * 0.5f)
            val rangeInv = 1f / (near - far)
            return Mat4(floatArrayOf(
                f / aspect, 0f,  0f,                        0f,
                0f,         f,   0f,                        0f,
                0f,         0f,  (far + near) * rangeInv,  -1f,
                0f,         0f,  2f * far * near * rangeInv, 0f
            ))
        }
    }
}
