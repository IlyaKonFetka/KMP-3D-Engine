package com.example.engine3d.renderer.jvm

import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*

/**
 * Offscreen framebuffer (FBO) фиксированного разрешения.
 * Используется чтобы рендерить сцену в независимом разрешении,
 * а потом растянуть/letterbox на окно любого размера.
 */
class RenderTarget(val width: Int, val height: Int) {

    val fboId:        Int
    val colorTexture: Int
    private val depthRbo: Int

    init {
        fboId = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)

        // Цветовая текстура — финальный кадр сцены
        colorTexture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, colorTexture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE,
            null as java.nio.ByteBuffer?)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0)

        // Depth + stencil renderbuffer
        depthRbo = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, depthRbo)
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthRbo)

        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) {
            "FBO не полный (status ${glCheckFramebufferStatus(GL_FRAMEBUFFER)})"
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun bindForRendering() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId)
        glViewport(0, 0, width, height)
    }

    fun delete() {
        glDeleteRenderbuffers(depthRbo)
        glDeleteTextures(colorTexture)
        glDeleteFramebuffers(fboId)
    }
}
