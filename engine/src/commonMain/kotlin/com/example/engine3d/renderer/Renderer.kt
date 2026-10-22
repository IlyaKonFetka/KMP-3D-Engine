package com.example.engine3d.renderer

import com.example.engine3d.scene.Scene

interface Renderer {
    fun init()
    fun resize(width: Int, height: Int)
    fun render(scene: Scene, deltaTimeSeconds: Float)
    fun cleanup()
}
