package com.example.engine3d.renderer.jvm

import com.example.engine3d.scene.Mesh
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

/**
 * GPU-side VAO/VBO/EBO for one [Mesh].
 * - location 0 → positions (vec3)
 * - location 1 → normals   (vec3)
 */
class MeshBuffer(mesh: Mesh) {

    private val vaoId:       Int
    private val vboPos:      Int
    private val vboNor:      Int
    private val eboId:       Int
    val indexCount: Int = mesh.indexCount

    init {
        vaoId  = glGenVertexArrays()
        vboPos = glGenBuffers()
        vboNor = glGenBuffers()
        eboId  = glGenBuffers()

        glBindVertexArray(vaoId)

        // Positions → attribute 0
        glBindBuffer(GL_ARRAY_BUFFER, vboPos)
        glBufferData(GL_ARRAY_BUFFER, mesh.positions, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0L)
        glEnableVertexAttribArray(0)

        // Normals → attribute 1
        glBindBuffer(GL_ARRAY_BUFFER, vboNor)
        glBufferData(GL_ARRAY_BUFFER, mesh.normals, GL_STATIC_DRAW)
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0L)
        glEnableVertexAttribArray(1)

        // Indices
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, eboId)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, mesh.indices, GL_STATIC_DRAW)

        glBindVertexArray(0)
    }

    fun bind()   = glBindVertexArray(vaoId)
    fun unbind() = glBindVertexArray(0)

    fun delete() {
        glDeleteBuffers(vboPos)
        glDeleteBuffers(vboNor)
        glDeleteBuffers(eboId)
        glDeleteVertexArrays(vaoId)
    }
}
