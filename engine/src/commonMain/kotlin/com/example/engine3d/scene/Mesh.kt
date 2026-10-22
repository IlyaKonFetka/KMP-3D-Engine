package com.example.engine3d.scene

/**
 * Mesh data stored in CPU memory.
 * [positions] — flat array of XYZ floats, 3 floats per vertex.
 * [normals]   — flat array of XYZ floats, 3 floats per vertex (same count as positions).
 * [indices]   — triangle indices (every 3 ints = one triangle).
 */
class Mesh(
    val positions: FloatArray,
    val normals:   FloatArray,
    val indices:   IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val indexCount:  Int get() = indices.size

    companion object {
        /**
         * Unit cube centered at origin.
         * 24 unique vertices (4 per face × 6 faces) for correct flat normals.
         * 36 indices (2 triangles × 3 indices × 6 faces).
         */
        fun cube(): Mesh {
            // positions: 4 vertices per face, 6 faces = 24 vertices × 3 floats
            val pos = floatArrayOf(
                // +Z front  (normal 0, 0, 1)
                -1f, -1f,  1f,   1f, -1f,  1f,   1f,  1f,  1f,  -1f,  1f,  1f,
                // -Z back   (normal 0, 0,-1)
                 1f, -1f, -1f,  -1f, -1f, -1f,  -1f,  1f, -1f,   1f,  1f, -1f,
                // +X right  (normal 1, 0, 0)
                 1f, -1f,  1f,   1f, -1f, -1f,   1f,  1f, -1f,   1f,  1f,  1f,
                // -X left   (normal-1, 0, 0)
                -1f, -1f, -1f,  -1f, -1f,  1f,  -1f,  1f,  1f,  -1f,  1f, -1f,
                // +Y top    (normal 0, 1, 0)
                -1f,  1f,  1f,   1f,  1f,  1f,   1f,  1f, -1f,  -1f,  1f, -1f,
                // -Y bottom (normal 0,-1, 0)
                -1f, -1f, -1f,   1f, -1f, -1f,   1f, -1f,  1f,  -1f, -1f,  1f,
            )

            val nor = floatArrayOf(
                // +Z
                0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 1f,
                // -Z
                0f, 0f,-1f,  0f, 0f,-1f,  0f, 0f,-1f,  0f, 0f,-1f,
                // +X
                1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 0f,
                // -X
               -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f, -1f, 0f, 0f,
                // +Y
                0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,  0f, 1f, 0f,
                // -Y
                0f,-1f, 0f,  0f,-1f, 0f,  0f,-1f, 0f,  0f,-1f, 0f,
            )

            // Each face: quad split into 2 triangles (CCW winding, front face = outside)
            // Face vertex offsets: 0,1,2 and 0,2,3
            val idx = IntArray(36)
            for (face in 0..5) {
                val base = face * 4
                val i    = face * 6
                idx[i + 0] = base; idx[i + 1] = base + 1; idx[i + 2] = base + 2
                idx[i + 3] = base; idx[i + 4] = base + 2; idx[i + 5] = base + 3
            }

            return Mesh(pos, nor, idx)
        }
    }
}
