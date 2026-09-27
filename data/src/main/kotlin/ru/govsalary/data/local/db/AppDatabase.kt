package ru.govsalary.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.govsalary.data.local.db.dao.FinancialGoalDao
import ru.govsalary.data.local.db.dao.SalaryHistoryDao
import ru.govsalary.data.local.db.dao.SavingsPeriodDao
import ru.govsalary.data.local.db.dao.SavingsPlanDao
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity

/**
 * Room-база. Полностью офлайн (мастер-промпт Фазы 3, п.11) — модуль :data не содержит
 * ни одной сетевой зависимости (нет Retrofit/OkHttp/облачных SDK в build.gradle.kts).
 */
@Database(
    entities = [
        SalaryHistoryEntity::class,
        FinancialGoalEntity::class,
        SavingsPlanEntity::class,
        SavingsPeriodEntity::class,
    ],
    version = 1,
    // exportSchema=false: без настройки room.schemaLocation в Gradle-плагине Room экспорт схемы
    // всё равно не работает, только шумит предупреждением в сборке (см. лог CI). Для единственной
    // версии схемы (version=1, миграций ещё нет) это не критично; если появятся миграции — стоит
    // включить room Gradle-плагин и room.schemaLocation, чтобы тестировать миграции по экспортам.
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun salaryHistoryDao(): SalaryHistoryDao
    abstract fun financialGoalDao(): FinancialGoalDao
    abstract fun savingsPlanDao(): SavingsPlanDao
    abstract fun savingsPeriodDao(): SavingsPeriodDao

    companion object {
        const val DATABASE_NAME = "gov_salary_calculator.db"
    }
}
