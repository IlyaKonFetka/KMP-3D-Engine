package com.example.engine3d.renderer.android

import android.opengl.GLES20
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import com.example.engine3d.math.Vec3
import com.example.engine3d.renderer.Renderer
import com.example.engine3d.scene.Light
import com.example.engine3d.scene.Mesh
import com.example.engine3d.scene.Scene
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer.
 * Implements both the engine [Renderer] interface and [GLSurfaceView.Renderer].
 */
class AndroidGLRenderer(private val scene: Scene) : Renderer, GLSurfaceView.Renderer {

    private var programId = 0
    private var viewportW = 1
    private var viewportH = 1

    // Attribute locations
    private var aPosition = 0
    private var aNormal   = 0

    // Mesh GPU buffers: Map<Mesh, IntArray(vboPos, vboNor, ebo, indexCount)>
    private val meshBuffers = HashMap<Mesh, IntArray>()

    // --- GLSurfaceView.Renderer ---
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) = init()
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = resize(width, height)
    override fun onDrawFrame(gl: GL10?) = render(scene, 0f)

    // --- Renderer ---
    override fun init() {
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)

        programId = createProgram(Shaders.VERT, Shaders.FRAG)
        aPosition = glGetAttribLocation(programId, "aPosition")
        aNormal   = glGetAttribLocation(programId, "aNormal")
    }

    override fun resize(width: Int, height: Int) {
        viewportW = width.coerceAtLeast(1)
        viewportH = height.coerceAtLeast(1)
        glViewport(0, 0, viewportW, viewportH)
    }

    override fun render(scene: Scene, deltaTimeSeconds: Float) {
        glClearColor(0.1f, 0.1f, 0.15f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glUseProgram(programId)

        val camera = scene.camera
        val aspect = viewportW.toFloat() / viewportH.toFloat()
        setMat4("uView",       camera.viewMatrix().m)
        setMat4("uProjection", camera.projectionMatrix(aspect).m)
        setVec3("uViewPos",    camera.position)

        // Upload first directional light
        val dirLight = scene.lights.filterIsInstance<Light.Directional>().firstOrNull()
        if (dirLight != null) {
            setVec3("uLightDir",   dirLight.direction.normalized())
            setVec3("uLightColor", dirLight.color * dirLight.intensity)
        } else {
            setVec3("uLightDir",   Vec3(0f, -1f, 0f))
            setVec3("uLightColor", Vec3(1f,  1f,  1f))
        }

        for (node in scene.nodes) {
            val (vboPos, vboNor, ebo, indexCount) = getOrCreateBuffers(node.mesh)

            setMat4 ("uModel",     node.transform.toModelMatrix().m)
            // Simple normal matrix: mat3 of model (correct for uniform scale)
            val m = node.transform.toModelMatrix().m
            val nm = floatArrayOf(m[0],m[1],m[2], m[4],m[5],m[6], m[8],m[9],m[10])
            setMat3("uNormalMatrix", nm)
            setVec3 ("uAmbient",   node.material.ambientColor)
            setVec3 ("uDiffuse",   node.material.diffuseColor)
            setVec3 ("uSpecular",  node.material.specularColor)
            setFloat("uShininess", node.material.shininess)

            // Bind positions
            glBindBuffer(GL_ARRAY_BUFFER, vboPos)
            glVertexAttribPointer(aPosition, 3, GL_FLOAT, false, 0, 0)
            glEnableVertexAttribArray(aPosition)

            // Bind normals
            glBindBuffer(GL_ARRAY_BUFFER, vboNor)
            glVertexAttribPointer(aNormal, 3, GL_FLOAT, false, 0, 0)
            glEnableVertexAttribArray(aNormal)

            // Draw
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
            glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0)
        }
    }

    override fun cleanup() {
        meshBuffers.values.forEach { buf -> glDeleteBuffers(3, buf, 0) }
        meshBuffers.clear()
        glDeleteProgram(programId)
    }

    // --- Helpers ---

    private fun getOrCreateBuffers(mesh: Mesh): IntArray {
        return meshBuffers.getOrPut(mesh) {
            val ids = IntArray(3)
            glGenBuffers(3, ids, 0)
            val (vboPos, vboNor, ebo) = ids

            glBindBuffer(GL_ARRAY_BUFFER, vboPos)
            glBufferData(GL_ARRAY_BUFFER, mesh.positions.size * 4,
                java.nio.FloatBuffer.wrap(mesh.positions), GL_STATIC_DRAW)

            glBindBuffer(GL_ARRAY_BUFFER, vboNor)
            glBufferData(GL_ARRAY_BUFFER, mesh.normals.size * 4,
                java.nio.FloatBuffer.wrap(mesh.normals), GL_STATIC_DRAW)

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * 4,
                java.nio.IntBuffer.wrap(mesh.indices), GL_STATIC_DRAW)

            intArrayOf(vboPos, vboNor, ebo, mesh.indexCount)
        }
    }

    private operator fun IntArray.component4() = this[3]

    private fun setMat4(name: String, m: FloatArray) =
        glUniformMatrix4fv(glGetUniformLocation(programId, name), 1, false, m, 0)

    private fun setMat3(name: String, m: FloatArray) =
        glUniformMatrix3fv(glGetUniformLocation(programId, name), 1, false, m, 0)

    private fun setVec3(name: String, v: Vec3) =
        glUniform3f(glGetUniformLocation(programId, name), v.x, v.y, v.z)

    private fun setFloat(name: String, v: Float) =
        glUniform1f(glGetUniformLocation(programId, name), v)

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GL_VERTEX_SHADER,   vertSrc)
        val frag = compileShader(GL_FRAGMENT_SHADER, fragSrc)
        val prog = glCreateProgram()
        glAttachShader(prog, vert)
        glAttachShader(prog, frag)
        glLinkProgram(prog)
        glDeleteShader(vert)
        glDeleteShader(frag)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val id = glCreateShader(type)
        glShaderSource(id, src)
        glCompileShader(id)
        return id
    }
}
