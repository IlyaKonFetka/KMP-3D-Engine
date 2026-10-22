package com.example.demo.world

import com.example.engine3d.math.Vec3

/**
 * Bounding box для коллизий.
 * rotY — поворот вокруг Y (радианы); 0 = AABB, иначе OBB.
 * min/max хранятся в пространстве объекта до поворота (центр XZ = середина min/max).
 */
data class Aabb(val min: Vec3, val max: Vec3, val rotY: Float = 0f) {
    /** Только для axis-aligned (rotY == 0). */
    fun overlaps(other: Aabb) =
        min.x < other.max.x && max.x > other.min.x &&
        min.y < other.max.y && max.y > other.min.y &&
        min.z < other.max.z && max.z > other.min.z
}

data class WorldData(
    val skyColor:         Vec3,
    val floorColor:       Vec3,
    val floorSize:        Float,
    val ambientIntensity: Float,
    val objects:          List<ObjectPlacement>,
)

/** Одна запись из строки `object` в world-файле. */
data class ObjectPlacement(
    val meshFile:     String,
    val position:     Vec3,
    val rotationYDeg: Float,
    val scale:        Float,
    val color:        Vec3,
)
