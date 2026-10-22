package com.example.engine3d.renderer.jvm

import com.example.engine3d.renderer.Renderer
import com.example.engine3d.scene.Light
import com.example.engine3d.scene.Mesh
import com.example.engine3d.scene.Scene
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.GL_SHADING_LANGUAGE_VERSION
import org.lwjgl.opengl.GL30.*

class OpenGLRenderer : Renderer {

    private lateinit var shader: ShaderProgram
    private val meshCache = HashMap<Mesh, MeshBuffer>()

    // Размер окна (обновляется при resize)
    private var windowW = 800
    private var windowH = 600

    // Внутренний FBO фиксированного разрешения (null = рендер прямо в окно)
    private var renderTarget: RenderTarget? = null

    // stretch | letterbox
    var scaleMode: String = "letterbox"

    override fun init() {
        GL.createCapabilities()

        println("OpenGL: ${glGetString(GL_VERSION)}")
        println("GLSL:   ${glGetString(GL_SHADING_LANGUAGE_VERSION)}")
        println("GPU:    ${glGetString(GL_RENDERER)}")

        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)

        shader = ShaderProgram(
            loadResource("shaders/phong.vert"),
            loadResource("shaders/phong.frag"),
        )
    }

    /**
     * Устанавливает внутреннее разрешение рендеринга.
     * Сцена будет рендериться в этот FBO, а потом масштабироваться на окно.
     * Можно вызвать после [init].
     */
    fun setRenderResolution(width: Int, height: Int) {
        renderTarget?.delete()
        renderTarget = RenderTarget(width, height)
        println("Render resolution: ${width}×${height}")
    }

    /** Вызывается при изменении размера окна — только запоминаем новый размер. */
    override fun resize(width: Int, height: Int) {
        windowW = width
        windowH = height
    }

    override fun render(scene: Scene, deltaTimeSeconds: Float) {
        val target = renderTarget

        if (target != null) {
            // ── Pass 1: рендер сцены в FBO (фиксированное разрешение) ──────
            target.bindForRendering()
            renderScene(scene, target.width, target.height)

            // ── Pass 2: blit FBO → окно с letterbox/stretch ─────────────────
            glBindFramebuffer(GL_READ_FRAMEBUFFER, target.fboId)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)

            // Чёрный фон на всё окно (полосы letterbox)
            glViewport(0, 0, windowW, windowH)
            glClearColor(0f, 0f, 0f, 1f)
            glClear(GL_COLOR_BUFFER_BIT)

            val vp = destViewport(target.width, target.height)  // [x, y, w, h]

            glBlitFramebuffer(
                0, 0, target.width, target.height,            // src — весь FBO
                vp[0], vp[1], vp[0] + vp[2], vp[1] + vp[3], // dst — letterbox-область
                GL_COLOR_BUFFER_BIT,
                GL_LINEAR,
            )

            glBindFramebuffer(GL_FRAMEBUFFER, 0)
        } else {
            // Без FBO — рендер прямо в окно
            glViewport(0, 0, windowW, windowH)
            renderScene(scene, windowW, windowH)
        }
    }

    // ── Приватные методы ────────────────────────────────────────────────────

    private fun renderScene(scene: Scene, viewW: Int, viewH: Int) {
        val bg = scene.backgroundColor
        glClearColor(bg.x, bg.y, bg.z, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        shader.use()

        val camera = scene.camera
        val view   = camera.viewMatrix()
        val proj   = camera.projectionMatrix(viewW.toFloat() / viewH.toFloat())

        shader.setMat4("uView",       view)
        shader.setMat4("uProjection", proj)
        shader.setVec3("uViewPos",    camera.position)

        var hasDir   = false
        var hasPoint = false
        for (light in scene.lights) {
            when (light) {
                is Light.Directional -> if (!hasDir) {
                    shader.setVec3 ("uDirLight.direction", light.direction.normalized())
                    shader.setVec3 ("uDirLight.color",     light.color)
                    shader.setFloat("uDirLight.intensity", light.intensity)
                    hasDir = true
                }
                is Light.Point -> if (!hasPoint) {
                    shader.setVec3 ("uPointLight.position",  light.position)
                    shader.setVec3 ("uPointLight.color",     light.color)
                    shader.setFloat("uPointLight.intensity", light.intensity)
                    shader.setFloat("uPointLight.constant",  light.constant)
                    shader.setFloat("uPointLight.linear",    light.linear)
                    shader.setFloat("uPointLight.quadratic", light.quadratic)
                    hasPoint = true
                }
            }
        }
        if (!hasDir)   shader.setFloat("uDirLight.intensity",   0f)
        if (!hasPoint) shader.setFloat("uPointLight.intensity", 0f)

        for (node in scene.nodes) {
            val buffer = meshCache.getOrPut(node.mesh) { MeshBuffer(node.mesh) }
            shader.setMat4 ("uModel",         node.transform.toModelMatrix())
            shader.setVec3 ("uMat.ambient",   node.material.ambientColor)
            shader.setVec3 ("uMat.diffuse",   node.material.diffuseColor)
            shader.setVec3 ("uMat.specular",  node.material.specularColor)
            shader.setFloat("uMat.shininess", node.material.shininess)

            buffer.bind()
            glDrawElements(GL_TRIANGLES, buffer.indexCount, GL_UNSIGNED_INT, 0L)
            buffer.unbind()
        }
    }

    /**
     * Вычисляет целевой viewport в окне для отображения кадра renderW×renderH.
     * Возвращает [x, y, width, height].
     *
     *  letterbox: сохраняет AR, добавляет чёрные полосы
     *  stretch:   растягивает без сохранения AR
     */
    private fun destViewport(renderW: Int, renderH: Int): IntArray {
        if (scaleMode == "stretch") return intArrayOf(0, 0, windowW, windowH)

        // scale = наименьший из двух коэффициентов масштаба → вписываем кадр целиком
        val scale = minOf(windowW.toFloat() / renderW, windowH.toFloat() / renderH)
        val vpW   = (renderW * scale).toInt()
        val vpH   = (renderH * scale).toInt()
        val vpX   = (windowW - vpW) / 2   // центрируем по X
        val vpY   = (windowH - vpH) / 2   // центрируем по Y
        return intArrayOf(vpX, vpY, vpW, vpH)
    }

    override fun cleanup() {
        meshCache.values.forEach { it.delete() }
        meshCache.clear()
        shader.delete()
        renderTarget?.delete()
    }

    private fun loadResource(path: String): String =
        OpenGLRenderer::class.java.classLoader
            ?.getResourceAsStream(path)
            ?.bufferedReader()
            ?.readText()
            ?: error("Resource not found: $path")
}
