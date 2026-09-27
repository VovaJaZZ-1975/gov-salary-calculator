plugins {
    id("com.android.application")
    kotlin("android")
}

// :app — presentation-слой (мастер-промпт Фазы 4, п.25): Compose UI -> ViewModel -> UseCase ->
// Repository -> Calculation Engine / Room. Зависит от :data (которое уже зависит от :calculation),
// UI-код сам НИКОГДА не импортирует ru.govsalary.calculation.* или ru.govsalary.data.local.db.* —
// только ru.govsalary.data.repository.* / ru.govsalary.data.domain.* через собственный слой
// domain/usecase этого модуля (см. app/src/main/kotlin/ru/govsalary/app/domain/).

android {
    namespace = "ru.govsalary.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.govsalary.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
    }

    // Явно фиксируем версию Compose Compiler, совместимую с Kotlin 1.9.24 (см. корневой
    // build.gradle.kts). Без этой настройки Gradle подставляет устаревшее значение по
    // умолчанию (1.3.2, рассчитанное на Kotlin 1.7.20) и падает с ошибкой несовместимости —
    // именно так и произошло при первой реальной сборке в CI. Официальное соответствие версий:
    // Kotlin 1.9.24 -> Compose Compiler 1.5.14.
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false // явное решение: обфускация R8 оставлена на решение владельца релиза (нет keystore/подписи в этом окружении — см. docs/security-check.md)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":data"))
    implementation(project(":calculation")) // только для типов CalculationConfiguration/Premium/WorkCalendar в What-If UseCase, см. WhatIfUseCases.kt

    // Room и DataStore нужны здесь напрямую: AppContainer.kt (композиционный корень) сам строит
    // Room.databaseBuilder(...) и preferencesDataStore(...) — а :data объявляет эти зависимости
    // как implementation (не api), поэтому они не видны транзитивно модулю :app. Версии — те же,
    // что и в data/build.gradle.kts, чтобы не тянуть в classpath две разные версии одной библиотеки.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.window:window:1.3.0") // WindowSizeClass на планшетах/раскладном экране

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
