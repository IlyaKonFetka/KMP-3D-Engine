package com.example.engine3d.scene

import com.example.engine3d.math.Mat4
import com.example.engine3d.math.Vec3
import kotlin.math.PI

class Camera(
    var position: Vec3  = Vec3(0f, 0f, 5f),
    var target:   Vec3  = Vec3.ZERO,
    var up:       Vec3  = Vec3.UP,
    var fovY:     Float = (60.0 * PI / 180.0).toFloat(),
    var near:     Float = 0.1f,
    var far:      Float = 1000f,
) {
    fun viewMatrix(): Mat4 = Mat4.lookAt(position, target, up)

    fun projectionMatrix(aspectRatio: Float): Mat4 =
        Mat4.perspective(fovY, aspectRatio, near, far)
}
