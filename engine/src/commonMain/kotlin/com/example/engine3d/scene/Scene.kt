package com.example.engine3d.scene

import com.example.engine3d.math.Vec3

class Scene {
    val nodes:  MutableList<SceneNode> = mutableListOf()
    var camera: Camera                 = Camera()
    val lights: MutableList<Light>     = mutableListOf()
    var backgroundColor: Vec3          = Vec3(0.1f, 0.1f, 0.15f)

    fun addNode(node: SceneNode): SceneNode { nodes += node; return node }
    fun removeNode(node: SceneNode) { nodes -= node }
}
