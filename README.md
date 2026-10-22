# KMP-3D-Engine

3D-движок на Kotlin Multiplatform. Математика и граф сцены лежат в `commonMain`, рендереров два: OpenGL 3.3 через LWJGL для десктопа и OpenGL ES 2.0 через `GLSurfaceView` для Android.

В `desktop-demo` небольшая игра от третьего лица. Робот из нескольких мешей бегает по миру из `world.world`, прыгает и упирается в стены (swept AABB). Свет по Фонгу, кадр рендерится в уменьшенный буфер и растягивается на окно, разрешение задаётся в `render.cfg`.

Запуск: `gradle :desktop-demo:run`. Gradle wrapper в репозиторий пока не добавлен.
