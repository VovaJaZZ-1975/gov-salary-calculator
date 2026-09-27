plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp") // Room annotation processing
}

// :data — Android-библиотека (Room требует android.database.sqlite, поэтому этот модуль,
// в отличие от :calculation, не может быть чистым kotlin("jvm")). Тем не менее он НЕ содержит
// ни Activity/Fragment/Compose, ни ViewModel — только Room/DataStore/Repository/domain-сервисы,
// как того требует architecture.md (data-слой) и мастер-промпт Фазы 3.
//
// НЕТ ни одной сетевой зависимости (Retrofit/OkHttp/Ktor и т.п.) — офлайн-режим (Фаза 3, п.11)
// обеспечен на уровне списка зависимостей, а не только логики.

android {
    namespace = "ru.govsalary.data"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // java.time.* без desugaring; при необходимости более низкого minSdk потребуется coreLibraryDesugaring
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":calculation"))

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
