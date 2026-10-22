package com.example.engine3d.scene

class SceneNode(
    val mesh:      Mesh,
    val transform: Transform = Transform(),
    val material:  Material  = Material(),
)
