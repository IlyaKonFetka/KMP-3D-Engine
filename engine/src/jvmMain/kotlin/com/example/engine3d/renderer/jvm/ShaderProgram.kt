package com.example.engine3d.renderer.jvm

import com.example.engine3d.math.Mat4
import com.example.engine3d.math.Vec3
import org.lwjgl.opengl.GL20.*
import org.lwjgl.system.MemoryStack

class ShaderProgram(vertSrc: String, fragSrc: String) {

    val programId: Int
    private val uniformCache = HashMap<String, Int>()

    init {
        val vert = compileShader(GL_VERTEX_SHADER,   vertSrc)
        val frag = compileShader(GL_FRAGMENT_SHADER, fragSrc)

        programId = glCreateProgram()
        glAttachShader(programId, vert)
        glAttachShader(programId, frag)
        glLinkProgram(programId)

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            error("Shader link error:\n${glGetProgramInfoLog(programId)}")
        }

        glDeleteShader(vert)
        glDeleteShader(frag)
    }

    fun use() = glUseProgram(programId)

    fun setFloat(name: String, v: Float)  = glUniform1f(loc(name), v)
    fun setInt(name: String, v: Int)      = glUniform1i(loc(name), v)

    fun setVec3(name: String, v: Vec3)    = glUniform3f(loc(name), v.x, v.y, v.z)

    fun setMat4(name: String, mat: Mat4) {
        MemoryStack.stackPush().use { stack ->
            val buf = stack.mallocFloat(16)
            buf.put(mat.m).flip()
            glUniformMatrix4fv(loc(name), false, buf)
        }
    }

    fun delete() = glDeleteProgram(programId)

    private fun loc(name: String): Int =
        uniformCache.getOrPut(name) { glGetUniformLocation(programId, name) }

    private fun compileShader(type: Int, src: String): Int {
        val id = glCreateShader(type)
        glShaderSource(id, src)
        glCompileShader(id)
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            val typeName = if (type == GL_VERTEX_SHADER) "vertex" else "fragment"
            error("$typeName shader compile error:\n${glGetShaderInfoLog(id)}")
        }
        return id
    }
}
