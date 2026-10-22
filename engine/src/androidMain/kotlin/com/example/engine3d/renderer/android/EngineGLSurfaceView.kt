package com.example.engine3d.renderer.android

import android.content.Context
import android.opengl.GLSurfaceView
import com.example.engine3d.renderer.Renderer
import com.example.engine3d.scene.Scene

/**
 * Drop-in GLSurfaceView that runs the 3D engine.
 *
 * Usage in Activity/Fragment:
 * ```kotlin
 * val engineView = EngineGLSurfaceView(context, scene)
 * setContentView(engineView)
 * ```
 */
class EngineGLSurfaceView(context: Context, scene: Scene) : GLSurfaceView(context) {

    private val glRenderer = AndroidGLRenderer(scene)

    /** Access the engine [Renderer] interface (e.g. to call [Renderer.cleanup]). */
    val renderer: Renderer get() = glRenderer

    init {
        setEGLContextClientVersion(2)
        setRenderer(glRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
