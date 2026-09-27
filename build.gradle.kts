// Корневой build.gradle.kts. Версии плагинов объявлены здесь один раз (apply false) —
// подмодули (:calculation, :data) применяют плагины без повторного указания версии,
// чтобы Gradle не сообщал о конфликте версий одного и того же плагина.
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("android") version "1.9.24" apply false
    kotlin("plugin.serialization") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
