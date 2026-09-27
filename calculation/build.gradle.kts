plugins {
    kotlin("jvm")
}

// Чистый Kotlin/JVM модуль, без com.android.library — намеренно, чтобы Calculation Engine
// физически не мог получить зависимость на Android SDK/Compose/Context (мастер-промпт Фазы 2,
// "ГЛАВНАЯ ЦЕЛЬ"). Подключается в app-модуль как обычная Kotlin-библиотека.

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
