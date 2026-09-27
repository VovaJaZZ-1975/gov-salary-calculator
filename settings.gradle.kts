rootProject.name = "gov-salary-calculator"

include(":calculation")
include(":data")
include(":app")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// Фаза 2: модуль :calculation (Calculation Engine).
// Фаза 3: модуль :data (Room/DataStore/Repository/domain-сервисы), зависит от :calculation.
// Фаза 4: модуль :app (Compose UI/ViewModel/UseCase), зависит от :data и :calculation.
// Проект считается завершённым в рамках текущего задания после Фазы 4 (мастер-промпт Фазы 4, финал).
