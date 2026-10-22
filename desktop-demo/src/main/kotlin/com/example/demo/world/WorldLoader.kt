package com.example.demo.world

import com.example.engine3d.math.Vec3
import com.example.engine3d.scene.*
import kotlin.math.PI
import kotlin.math.sqrt


object WorldLoader {

    fun load(worldResource: String): Pair<Scene, List<Aabb>> {
        val lines = readResource(worldResource).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        // --- значения по умолчанию ---
        var skyColor         = Vec3(0.53f, 0.81f, 0.98f)
        var floorColor       = Vec3(0.25f, 0.62f, 0.20f)
        var floorSize        = 200f
        var ambientIntensity = 0.35f
        var sunDir           = Vec3(-0.5f, -1f, -0.8f)
        var sunColor         = Vec3(1f, 0.95f, 0.88f)
        var sunIntensity     = 0.9f
        var fillPos          = Vec3(10f, 15f, 10f)
        var fillColor        = Vec3(0.5f, 0.7f, 1f)
        var fillIntensity    = 1.5f
        val placements       = mutableListOf<ObjectPlacement>()

        for (line in lines) {
            val p = line.split("\\s+".toRegex())
            when (p[0]) {
                "sky_color"         -> skyColor         = v3(p, 1)
                "floor_color"       -> floorColor       = v3(p, 1)
                "floor_size"        -> floorSize        = p[1].toFloat()
                "ambient_intensity" -> ambientIntensity = p[1].toFloat()
                "sun_direction"     -> sunDir           = v3(p, 1)
                "sun_color"         -> sunColor         = v3(p, 1)
                "sun_intensity"     -> sunIntensity     = p[1].toFloat()
                "fill_position"     -> fillPos          = v3(p, 1)
                "fill_color"        -> fillColor        = v3(p, 1)
                "fill_intensity"    -> fillIntensity    = p[1].toFloat()
                "object"            -> placements += ObjectPlacement(
                    meshFile     = p[1],
                    position     = v3(p, 2),
                    rotationYDeg = p[5].toFloat(),
                    scale        = p[6].toFloat(),
                    color        = v3(p, 7),
                )
            }
        }

        // --- собираем сцену ---
        val scene = Scene()
        scene.backgroundColor = skyColor

        scene.lights += Light.Directional(
            direction = sunDir,
            color     = sunColor,
            intensity = sunIntensity,
        )
        scene.lights += Light.Point(
            position  = fillPos,
            color     = fillColor,
            intensity = fillIntensity,
        )

        // пол
        scene.addNode(SceneNode(
            mesh     = buildFloorMesh(floorSize),
            material = Material(
                ambientColor  = floorColor * ambientIntensity,
                diffuseColor  = floorColor,
                specularColor = Vec3(0.05f, 0.1f, 0.05f),
                shininess     = 8f,
            ),
        ))

        // объекты
        val meshCache = HashMap<String, Mesh>()
        for (obj in placements) {
            val mesh = meshCache.getOrPut(obj.meshFile) { loadMesh("meshes/${obj.meshFile}") }
            val rotY = (obj.rotationYDeg * PI / 180.0).toFloat()
            scene.addNode(SceneNode(
                mesh     = mesh,
                material = Material(
                    ambientColor  = obj.color * ambientIntensity,
                    diffuseColor  = obj.color,
                    specularColor = Vec3(0.6f, 0.6f, 0.6f),
                    shininess     = 32f,
                ),
                transform = Transform().also {
                    it.position = obj.position
                    it.rotation = Vec3(0f, rotY, 0f)
                    it.scale    = Vec3(obj.scale, obj.scale, obj.scale)
                },
            ))
        }

        // OBB для каждого объекта: min/max по центрированному box, rotY из world-файла
        val colliders = placements.map { obj ->
            val hs   = obj.scale * 0.5f
            val rotY = (obj.rotationYDeg * PI / 180.0).toFloat()
            Aabb(
                min  = Vec3(obj.position.x - hs, obj.position.y,            obj.position.z - hs),
                max  = Vec3(obj.position.x + hs, obj.position.y + obj.scale, obj.position.z + hs),
                rotY = rotY,
            )
        }

        return Pair(scene, colliders)
    }

    // ---- внутренние утилиты ----

    private fun v3(p: List<String>, i: Int) =
        Vec3(p[i].toFloat(), p[i + 1].toFloat(), p[i + 2].toFloat())

    /** Большой плоский квад на Y = 0 с нормалью вверх. */
    private fun buildFloorMesh(size: Float): Mesh {
        val h = size / 2f
        val pos = floatArrayOf(
            -h, 0f,  h,
             h, 0f,  h,
             h, 0f, -h,
            -h, 0f, -h,
        )
        val nor = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
        )
        return Mesh(pos, nor, intArrayOf(0, 1, 2, 0, 2, 3))
    }

    /**
     * Загружает .mesh файл из ресурсов.
     *
     * Формат:
     *   v x y z       — вершина
     *   f i1 i2 i3    — треугольник (индексы с 0)
     *
     * Нормали вычисляются автоматически (flat shading, CCW обход снаружи).
     */
    private fun loadMesh(resource: String): Mesh {
        val lines = readResource(resource).lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        val verts = mutableListOf<FloatArray>()
        val faces = mutableListOf<IntArray>()

        for (line in lines) {
            val p = line.split("\\s+".toRegex())
            when (p[0]) {
                "v" -> verts += floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat())
                "f" -> faces += intArrayOf(p[1].toInt(), p[2].toInt(), p[3].toInt())
            }
        }

        // Дублируем вершины на каждый треугольник (flat normals)
        val positions = FloatArray(faces.size * 9)
        val normals   = FloatArray(faces.size * 9)
        val indices   = IntArray(faces.size * 3)

        for ((fi, face) in faces.withIndex()) {
            val v0 = verts[face[0]]
            val v1 = verts[face[1]]
            val v2 = verts[face[2]]

            // (v1-v0) × (v2-v0)
            val ax = v1[0]-v0[0]; val ay = v1[1]-v0[1]; val az = v1[2]-v0[2]
            val bx = v2[0]-v0[0]; val by = v2[1]-v0[1]; val bz = v2[2]-v0[2]
            var nx = ay*bz - az*by
            var ny = az*bx - ax*bz
            var nz = ax*by - ay*bx
            val len = sqrt(nx*nx + ny*ny + nz*nz).coerceAtLeast(1e-7f)
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

    private fun readResource(path: String): String =
        WorldLoader::class.java.classLoader
            ?.getResourceAsStream(path)
            ?.bufferedReader()
            ?.readText()
            ?: error("Resource not found: $path")
}
