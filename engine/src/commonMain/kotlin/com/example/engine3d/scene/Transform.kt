package com.example.engine3d.scene

import com.example.engine3d.math.Mat4
import com.example.engine3d.math.Vec3

class Transform {
    var position: Vec3 = Vec3.ZERO
    /** Euler angles in radians: (pitch=X, yaw=Y, roll=Z). */
    var rotation: Vec3 = Vec3.ZERO
    var scale:    Vec3 = Vec3.ONE

    /** Model matrix = T * Ry * Rx * Rz * S */
    fun toModelMatrix(): Mat4 {
        val T  = Mat4.translation(position.x, position.y, position.z)
        val Rx = Mat4.rotationX(rotation.x)
        val Ry = Mat4.rotationY(rotation.y)
        val Rz = Mat4.rotationZ(rotation.z)
        val S  = Mat4.scale(scale.x, scale.y, scale.z)
        return T * Ry * Rx * Rz * S
    }
}
