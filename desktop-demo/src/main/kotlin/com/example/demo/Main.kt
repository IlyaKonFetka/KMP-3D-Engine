package com.example.demo

import com.example.demo.world.Aabb
import com.example.demo.world.WorldLoader
import com.example.engine3d.math.Vec3
import com.example.engine3d.renderer.jvm.OpenGLRenderer
import com.example.engine3d.scene.*
import org.lwjgl.glfw.Callbacks.glfwFreeCallbacks
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL11.GL_TRUE
import org.lwjgl.system.MemoryUtil.NULL
import kotlin.math.*

fun main() {
    check(glfwInit()) { "Failed to init GLFW" }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE,        GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)

    val cfg = loadRenderConfig("render.cfg")

    val window = glfwCreateWindow(cfg.windowW, cfg.windowH, "KMP 3D Engine", NULL, NULL)
    check(window != NULL) { "Failed to create GLFW window" }

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)

    val renderer = OpenGLRenderer()
    renderer.init()
    renderer.resize(cfg.windowW, cfg.windowH)
    renderer.setRenderResolution(cfg.renderW, cfg.renderH)
    renderer.scaleMode = cfg.scaleMode

    val pcfg = loadPlayerConfig("player.cfg")
    playerW = pcfg.playerWidth
    playerH = pcfg.playerHeight

    val (scene, colliders) = WorldLoader.load("world.world")

    // Пол как виртуальный AABB — обрабатывается той же swept-системой
    val floorCollider = Aabb(Vec3(-500f, -1f, -500f), Vec3(500f, 0f, 500f))
    val allColliders  = colliders + floorCollider

    // ── Персонаж: части из player_model.cfg ─────────────────────────────────
    val meshCache  = HashMap<String, Mesh>()
    val robotParts = loadModelConfig("player_model.cfg").map { def ->
        val mesh = meshCache.getOrPut(def.meshFile) { loadMeshResource("meshes/${def.meshFile}") }
        val transform = Transform()
        scene.addNode(SceneNode(
            mesh      = mesh,
            material  = Material(
                ambientColor  = def.ambient,
                diffuseColor  = def.diffuse,
                specularColor = def.specular,
                shininess     = def.shininess,
            ),
            transform = transform,
        ))
        Pair(transform, def)
    }

    var winWidth  = cfg.windowW
    var winHeight = cfg.windowH
    glfwSetFramebufferSizeCallback(window) { _, w, h ->
        winWidth  = w
        winHeight = h
        renderer.resize(w, h)
    }

    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED)

    var mouseDx    = 0.0
    var mouseDy    = 0.0
    var lastMouseX = 400.0
    var lastMouseY = 300.0
    var firstMouse = true

    glfwSetCursorPosCallback(window) { _, x, y ->
        if (firstMouse) { lastMouseX = x; lastMouseY = y; firstMouse = false }
        mouseDx    += x - lastMouseX
        mouseDy    += y - lastMouseY
        lastMouseX  = x
        lastMouseY  = y
    }

    val keys = mutableSetOf<Int>()
    glfwSetKeyCallback(window) { win, key, _, action, _ ->
        when (action) {
            GLFW_PRESS   -> keys += key
            GLFW_RELEASE -> keys -= key
        }
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS)
            glfwSetWindowShouldClose(win, true)
    }

    // Параметры управления (из player.cfg)
    val moveSpeed        = pcfg.moveSpeed
    val mouseSensitivity = pcfg.mouseSensitivity
    val jumpSpeed        = pcfg.jumpSpeed
    val gravity          = pcfg.gravity
    val camDist          = pcfg.camDist

    // Состояние игрока
    var playerPos  = Vec3(0f, 0f, 5f)
    var playerVelY = 0f
    var playerYaw  = 0f                 // радианы; 0 = смотрит на +Z
    var camPitch   = pcfg.camPitchInit  // вертикальный угол камеры
    var onGround   = false // обновляется swept-системой, используется в следующем кадре

    var lastTime   = glfwGetTime()
    var frameCount = 0
    var fpsTimer   = lastTime

    while (!glfwWindowShouldClose(window)) {
        val now   = glfwGetTime()
        val delta = (now - lastTime).toFloat()
        lastTime  = now

        // Поворот мышью
        playerYaw -= (mouseDx * mouseSensitivity).toFloat()
        camPitch   = (camPitch + (mouseDy * mouseSensitivity).toFloat())
            .coerceIn(0.05f, PI.toFloat() / 2f - 0.05f)
        mouseDx = 0.0
        mouseDy = 0.0

        // Направление взгляда игрока (XZ-плоскость)
        val forward = Vec3(sin(playerYaw), 0f, cos(playerYaw))
        val right   = Vec3.UP.cross(forward).normalized()

        // ── Ввод → направление движения (нормализуем, чтобы диагональ не давала x√2) ──

        var moveDir = Vec3.ZERO
        if (GLFW_KEY_W in keys) moveDir = moveDir + forward
        if (GLFW_KEY_S in keys) moveDir = moveDir - forward
        if (GLFW_KEY_A in keys) moveDir = moveDir + right
        if (GLFW_KEY_D in keys) moveDir = moveDir - right
        if (moveDir.lengthSquared() > 0.001f) moveDir = moveDir.normalized()

        // Прыжок (onGround из предыдущего кадра)
        if (GLFW_KEY_SPACE in keys && onGround) playerVelY = jumpSpeed

        // Гравитация
        playerVelY -= gravity * delta

        // Полное перемещение за кадр
        var displacement = Vec3(moveDir.x * moveSpeed * delta,
                                playerVelY * delta,
                                moveDir.z * moveSpeed * delta)

        // ── Swept AABB + скольжение (до 4 итераций) ─────────────────────────────
        // Стандартный алгоритм: находим t первого касания, двигаемся туда,
        // затем проецируем остаток скорости на плоскость нормали (скольжение).
        // Нормаль всегда с грани, к которой шли — никаких телепортаций.

        var pos = playerPos

        // ── Depenetration: выталкиваем из объектов до swept-цикла ─────────────
        // Защита от застревания: если игрок уже внутри объекта (fp-дрейф или
        // начальное положение), толкаем по оси минимального перекрытия.
        for (box in allColliders) {
            val (lPos, lMin, lMax) = obbLocal(pos, box)
            val pa = playerAabb(lPos)
            if (pa.min.x >= lMax.x || pa.max.x <= lMin.x) continue
            if (pa.min.y >= lMax.y || pa.max.y <= lMin.y) continue
            if (pa.min.z >= lMax.z || pa.max.z <= lMin.z) continue
            // Глубины перекрытия по каждой грани
            val ox0 = pa.max.x - lMin.x;  val ox1 = lMax.x - pa.min.x
            val oy0 = pa.max.y - lMin.y;  val oy1 = lMax.y - pa.min.y
            val oz0 = pa.max.z - lMin.z;  val oz1 = lMax.z - pa.min.z
            val min6 = minOf(ox0, ox1, oy0, oy1, oz0, oz1)
            val localOut = when (min6) {
                ox0 -> lPos.copy(x = lMin.x - playerW - PLAYER_SKIN)
                ox1 -> lPos.copy(x = lMax.x + playerW + PLAYER_SKIN)
                oy0 -> lPos.copy(y = lMin.y - playerH - PLAYER_SKIN).also { if (playerVelY > 0f) playerVelY = 0f }
                oy1 -> lPos.copy(y = lMax.y + PLAYER_SKIN).also { onGround = true; playerVelY = 0f.coerceAtMost(playerVelY) }
                oz0 -> lPos.copy(z = lMin.z - playerW - PLAYER_SKIN)
                else -> lPos.copy(z = lMax.z + playerW + PLAYER_SKIN)
            }
            pos = obbWorld(localOut, box)
        }

        // ── Swept OBB + скольжение (до 4 итераций) ───────────────────────────
        onGround = false

        run sweep@{
            for (iter in 0 until 4) {
                val dLen = displacement.length()
                if (dLen < 1e-5f) return@sweep

                var minT      = 1f
                var hitNormal = Vec3.ZERO

                for (box in allColliders) {
                    val hit = sweepCollider(pos, displacement, box) ?: continue
                    if (hit.first < minT) { minT = hit.first; hitNormal = hit.second }
                }

                val skinT = if (dLen > PLAYER_SKIN) PLAYER_SKIN / dLen else 0f
                pos = pos + displacement * (minT - skinT).coerceAtLeast(0f)

                if (minT >= 1f) return@sweep

                if (hitNormal.y >  0.7f) { onGround = true; playerVelY = 0f }
                if (hitNormal.y < -0.7f) { playerVelY = 0f }

                val remaining = displacement * (1f - minT)
                displacement = remaining - hitNormal * remaining.dot(hitNormal)
            }
        }

        playerPos = pos

        // ── Обновляем трансформы робота ───────────────────────────────────
        val cY = cos(playerYaw); val sY = sin(playerYaw)
        val rot = Vec3(0f, playerYaw, 0f)
        for ((transform, def) in robotParts) {
            transform.position = playerPos + Vec3(
                cY * def.localX + sY * def.localZ,
                def.localY,
                -sY * def.localX + cY * def.localZ,
            )
            transform.rotation = rot
            transform.scale    = Vec3(def.scaleX, def.scaleY, def.scaleZ)
        }

        // Камера орбитирует вокруг игрока (горизонталь = playerYaw, вертикаль = camPitch)
        val hDist = camDist * cos(camPitch)
        val vDist = camDist * sin(camPitch)
        val camTarget = playerPos + Vec3(0f, 1f, 0f)
        scene.camera.position = camTarget + Vec3(-sin(playerYaw) * hDist, vDist, -cos(playerYaw) * hDist)
        scene.camera.target   = camTarget

        renderer.render(scene, delta)
        glfwSwapBuffers(window)
        glfwPollEvents()

        frameCount++
        if (now - fpsTimer >= 1.0) {
            glfwSetWindowTitle(window,
                "KMP 3D Engine  |  FPS: $frameCount  |  " +
                "Render: ${cfg.renderW}×${cfg.renderH}  |  Window: ${winWidth}×${winHeight}")
            println("FPS: $frameCount")
            frameCount = 0
            fpsTimer   = now
        }
    }

    renderer.cleanup()
    glfwFreeCallbacks(window)
    glfwDestroyWindow(window)
    glfwTerminate()
}

// ── Коллайдер игрока ─────────────────────────────────────────────────────────
// playerW / playerH — загружаются из player.cfg в main()

private var playerW = 0.3f             // полуширина (X и Z)
private var playerH = 1.8f             // полная высота
private const val PLAYER_SKIN = 0.005f // зазор от поверхности (стандарт game-engines)

private fun playerAabb(pos: Vec3) = Aabb(
    min = Vec3(pos.x - playerW, pos.y,           pos.z - playerW),
    max = Vec3(pos.x + playerW, pos.y + playerH, pos.z + playerW),
)

// ── OBB-трансформ ─────────────────────────────────────────────────────────────

/**
 * Возвращает (playerLocalPos, obbLocalMin, obbLocalMax):
 * координаты игрока и границы OBB в локальном пространстве коллайдера.
 * При rotY==0 — просто AABB без лишних вычислений.
 */
private fun obbLocal(worldPos: Vec3, box: Aabb): Triple<Vec3, Vec3, Vec3> {
    val cX = (box.min.x + box.max.x) * 0.5f
    val cZ = (box.min.z + box.max.z) * 0.5f
    val hs = (box.max.x - box.min.x) * 0.5f   // box — куб, X==Z

    val lMin = Vec3(-hs, box.min.y, -hs)
    val lMax = Vec3( hs, box.max.y,  hs)

    if (box.rotY == 0f) {
        val lPos = Vec3(worldPos.x - cX, worldPos.y, worldPos.z - cZ)
        return Triple(lPos, lMin, lMax)
    }
    val cosT = cos(box.rotY);  val sinT = sin(box.rotY)
    val rx = worldPos.x - cX;  val rz = worldPos.z - cZ
    val lPos = Vec3(cosT * rx - sinT * rz, worldPos.y, sinT * rx + cosT * rz)
    return Triple(lPos, lMin, lMax)
}

/** Обратное преобразование: локальная позиция → мировая (только XZ). */
private fun obbWorld(localPos: Vec3, box: Aabb): Vec3 {
    val cX = (box.min.x + box.max.x) * 0.5f
    val cZ = (box.min.z + box.max.z) * 0.5f
    if (box.rotY == 0f) return Vec3(localPos.x + cX, localPos.y, localPos.z + cZ)
    val cosT = cos(box.rotY);  val sinT = sin(box.rotY)
    return Vec3( cosT * localPos.x + sinT * localPos.z + cX,
                localPos.y,
                -sinT * localPos.x + cosT * localPos.z + cZ)
}

// ── Swept collision ────────────────────────────────────────────────────────────

/**
 * Swept OBB: трансформирует игрока в локальное пространство коллайдера,
 * выполняет AABB-sweep (slab method), трансформирует нормаль обратно.
 * Для axis-aligned (rotY==0) — прямой AABB-тест без матриц.
 */
private fun sweepCollider(worldPos: Vec3, vel: Vec3, box: Aabb): Pair<Float, Vec3>? {
    val (lPos, lMin, lMax) = obbLocal(worldPos, box)

    val lVel = if (box.rotY == 0f) vel else {
        val cosT = cos(box.rotY);  val sinT = sin(box.rotY)
        Vec3(cosT * vel.x - sinT * vel.z, vel.y, sinT * vel.x + cosT * vel.z)
    }

    val pa = playerAabb(lPos)
    val (t, lNormal) = sweepAabb(pa.min, pa.max, lVel, lMin, lMax) ?: return null

    // Нормаль обратно в мировое пространство
    val wNormal = if (box.rotY == 0f) lNormal else {
        val cosT = cos(box.rotY);  val sinT = sin(box.rotY)
        Vec3( cosT * lNormal.x + sinT * lNormal.z, lNormal.y, -sinT * lNormal.x + cosT * lNormal.z)
    }
    return Pair(t, wNormal)
}

/**
 * Slab-метод: AABB vs AABB swept test.
 * Возвращает (t, normal) первого касания, или null.
 */
private fun sweepAabb(
    pMin: Vec3, pMax: Vec3,
    vel:  Vec3,
    bMin: Vec3, bMax: Vec3,
): Pair<Float, Vec3>? {

    fun slabs(pLo: Float, pHi: Float, v: Float, bLo: Float, bHi: Float): Pair<Float, Float> =
        when {
            v > 0f                   -> Pair((bLo - pHi) / v, (bHi - pLo) / v)
            v < 0f                   -> Pair((bHi - pLo) / v, (bLo - pHi) / v)
            pHi <= bLo || pLo >= bHi -> Pair(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
            else                     -> Pair(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        }

    val (eX, xX) = slabs(pMin.x, pMax.x, vel.x, bMin.x, bMax.x)
    val (eY, xY) = slabs(pMin.y, pMax.y, vel.y, bMin.y, bMax.y)
    val (eZ, xZ) = slabs(pMin.z, pMax.z, vel.z, bMin.z, bMax.z)

    val entry = maxOf(eX, eY, eZ)
    val exit  = minOf(xX, xY, xZ)

    if (entry > exit || exit <= 0f || entry >= 1f || entry < 0f) return null

    val t = entry
    val normal = when {
        eX >= eY && eX >= eZ -> if (vel.x > 0f) Vec3(-1f,  0f,  0f) else Vec3(1f,  0f, 0f)
        eY >= eX && eY >= eZ -> if (vel.y > 0f) Vec3( 0f, -1f,  0f) else Vec3(0f,  1f, 0f)
        else                 -> if (vel.z > 0f) Vec3( 0f,  0f, -1f) else Vec3(0f,  0f, 1f)
    }
    return Pair(t, normal)
}

/**
 * Загружает .mesh файл из ресурсов и строит Mesh с flat-нормалями.
 * Формат: v x y z | f i1 i2 i3 (индексы с 0, CCW обход снаружи).
 */
private fun loadMeshResource(resource: String): Mesh {
    val text = object {}::class.java.classLoader
        ?.getResourceAsStream(resource)
        ?.bufferedReader()?.readText()
        ?: error("Resource not found: $resource")

    val verts = mutableListOf<FloatArray>()
    val faces = mutableListOf<IntArray>()
    for (line in text.lines()) {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) continue
        val p = t.split("\\s+".toRegex())
        when (p[0]) {
            "v" -> verts += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
            "f" -> faces += intArrayOf(p[1].toInt(), p[2].toInt(), p[3].toInt())
        }
    }

    val positions = FloatArray(faces.size * 9)
    val normals   = FloatArray(faces.size * 9)
    val indices   = IntArray(faces.size * 3)

    for ((fi, face) in faces.withIndex()) {
        val v0 = verts[face[0]]; val v1 = verts[face[1]]; val v2 = verts[face[2]]
        val ax = v1[0]-v0[0]; val ay = v1[1]-v0[1]; val az = v1[2]-v0[2]
        val bx = v2[0]-v0[0]; val by = v2[1]-v0[1]; val bz = v2[2]-v0[2]
        var nx = ay*bz-az*by; var ny = az*bx-ax*bz; var nz = ax*by-ay*bx
        val len = sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(1e-7f)
        nx /= len; ny /= len; nz /= len

        val b = fi * 9
        positions[b+0]=v0[0]; positions[b+1]=v0[1]; positions[b+2]=v0[2]
        positions[b+3]=v1[0]; positions[b+4]=v1[1]; positions[b+5]=v1[2]
        positions[b+6]=v2[0]; positions[b+7]=v2[1]; positions[b+8]=v2[2]
        normals[b+0]=nx; normals[b+1]=ny; normals[b+2]=nz
        normals[b+3]=nx; normals[b+4]=ny; normals[b+5]=nz
        normals[b+6]=nx; normals[b+7]=ny; normals[b+8]=nz

        val ib = fi * 3
        indices[ib+0]=ib; indices[ib+1]=ib+1; indices[ib+2]=ib+2
    }

    return Mesh(positions, normals, indices)
}

private data class RenderConfig(
    val windowW:   Int,
    val windowH:   Int,
    val renderW:   Int,
    val renderH:   Int,
    val scaleMode: String,
)

/** Парсит render.cfg из ресурсов. При отсутствии файла — дефолты. */
private fun loadRenderConfig(resource: String): RenderConfig {
    var winW  = 800;   var winH  = 600
    var renW  = 1280;  var renH  = 720
    var mode  = "letterbox"

    val stream = object {}::class.java.classLoader
        ?.getResourceAsStream(resource) ?: return RenderConfig(winW, winH, renW, renH, mode)

    stream.bufferedReader().forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val parts = line.split("\\s+".toRegex())
        when (parts[0]) {
            "window_width"  -> winW = parts[1].toInt()
            "window_height" -> winH = parts[1].toInt()
            "render_width"  -> renW = parts[1].toInt()
            "render_height" -> renH = parts[1].toInt()
            "scale_mode"    -> mode = parts[1]
        }
    }
    return RenderConfig(winW, winH, renW, renH, mode)
}

private data class ModelPartDef(
    val meshFile:  String,
    val localX:    Float, val localY:  Float, val localZ:  Float,
    val scaleX:    Float, val scaleY:  Float, val scaleZ:  Float,
    val ambient:   Vec3,
    val diffuse:   Vec3,
    val specular:  Vec3,
    val shininess: Float,
)

/** Парсит player_model.cfg: каждая строка "part ..." → одна часть персонажа. */
private fun loadModelConfig(resource: String): List<ModelPartDef> {
    val stream = object {}::class.java.classLoader
        ?.getResourceAsStream(resource) ?: return emptyList()
    val parts = mutableListOf<ModelPartDef>()
    stream.bufferedReader().forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val p = line.split("\\s+".toRegex())
        if (p[0] != "part" || p.size < 17) return@forEachLine
        parts += ModelPartDef(
            meshFile  = p[1],
            localX    = p[2].toFloat(),  localY = p[3].toFloat(),  localZ = p[4].toFloat(),
            scaleX    = p[5].toFloat(),  scaleY = p[6].toFloat(),  scaleZ = p[7].toFloat(),
            ambient   = Vec3(p[8].toFloat(),  p[9].toFloat(),  p[10].toFloat()),
            diffuse   = Vec3(p[11].toFloat(), p[12].toFloat(), p[13].toFloat()),
            specular  = Vec3(p[14].toFloat(), p[15].toFloat(), p[16].toFloat()),
            shininess = p[17].toFloat(),
        )
    }
    return parts
}

private data class PlayerConfig(
    val moveSpeed:        Float,
    val jumpSpeed:        Float,
    val gravity:          Float,
    val mouseSensitivity: Float,
    val camDist:          Float,
    val camPitchInit:     Float,
    val playerWidth:      Float,
    val playerHeight:     Float,
)

/** Парсит player.cfg из ресурсов. При отсутствии файла — дефолты. */
private fun loadPlayerConfig(resource: String): PlayerConfig {
    var moveSpeed        = 6f
    var jumpSpeed        = 8f
    var gravity          = 20f
    var mouseSensitivity = 0.002f
    var camDist          = 6f
    var camPitchInit     = 0.46f
    var playerWidth      = 0.3f
    var playerHeight     = 1.8f

    val stream = object {}::class.java.classLoader
        ?.getResourceAsStream(resource) ?: return PlayerConfig(
            moveSpeed, jumpSpeed, gravity, mouseSensitivity,
            camDist, camPitchInit, playerWidth, playerHeight)

    stream.bufferedReader().forEachLine { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachLine
        val parts = line.split("\\s+".toRegex())
        when (parts[0]) {
            "move_speed"        -> moveSpeed        = parts[1].toFloat()
            "jump_speed"        -> jumpSpeed        = parts[1].toFloat()
            "gravity"           -> gravity          = parts[1].toFloat()
            "mouse_sensitivity" -> mouseSensitivity = parts[1].toFloat()
            "cam_dist"          -> camDist          = parts[1].toFloat()
            "cam_pitch_init"    -> camPitchInit     = parts[1].toFloat()
            "player_width"      -> playerWidth      = parts[1].toFloat()
            "player_height"     -> playerHeight     = parts[1].toFloat()
        }
    }
    return PlayerConfig(moveSpeed, jumpSpeed, gravity, mouseSensitivity,
                        camDist, camPitchInit, playerWidth, playerHeight)
}
