plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

val lwjglNatives = when {
    System.getProperty("os.name").startsWith("Windows") -> "natives-windows"
    System.getProperty("os.name") == "Linux"            -> "natives-linux"
    else                                                -> "natives-macos"
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.lwjgl.core)
    implementation(libs.lwjgl.opengl)
    implementation(libs.lwjgl.glfw)

    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$lwjglNatives")
}

application {
    mainClass.set("com.example.demo.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED"
    )
}

// Resolve LWJGL version from version catalog for native classifiers
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.lwjgl") {
            useVersion(libs.versions.lwjgl.get())
        }
    }
}
