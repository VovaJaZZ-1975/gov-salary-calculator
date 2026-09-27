package ru.govsalary.app.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import ru.govsalary.app.domain.BackupUseCases
import ru.govsalary.app.domain.CalculatorUseCases
import ru.govsalary.app.domain.CsvImportUseCases
import ru.govsalary.app.domain.GoalsAndSavingsUseCases
import ru.govsalary.app.domain.HistoryUseCases
import ru.govsalary.app.domain.SettingsUseCases
import ru.govsalary.app.domain.WhatIfUseCases
import ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine
import ru.govsalary.data.domain.ForecastService
import ru.govsalary.data.domain.WhatIfService
import ru.govsalary.data.io.CsvHistoryImportService
import ru.govsalary.data.io.ImportExportService
import ru.govsalary.data.local.datastore.AppSettingsDataStore
import ru.govsalary.data.local.db.AppDatabase
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

/**
 * Простой ручной DI-контейнер (без Hilt/Koin — в офлайн-песочнице нельзя проверить совместимость
 * дополнительного annotation-processor-плагина, поэтому выбран самый простой надёжный вариант).
 * Единственное место в приложении, где создаются Room/DataStore/Repository — весь остальной код
 * (ViewModel) получает уже готовые UseCase (мастер-промпт Фазы 4, п.25-26).
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME,
    ).build()

    private val settingsStore = AppSettingsDataStore(context.applicationContext.settingsDataStore)

    private val salaryHistoryRepository = SalaryHistoryRepository(database.salaryHistoryDao())
    private val financialGoalRepository = FinancialGoalRepository(database.financialGoalDao())
    private val savingsPlanRepository = SavingsPlanRepository(database.savingsPlanDao(), database.savingsPeriodDao())

    private val calculationEngine = DefaultSalaryCalculationEngine()
    private val forecastService = ForecastService(salaryHistoryRepository, financialGoalRepository, savingsPlanRepository)
    private val whatIfService = WhatIfService()
    private val importExportService = ImportExportService(salaryHistoryRepository, financialGoalRepository, savingsPlanRepository)
    private val csvHistoryImportService = CsvHistoryImportService(salaryHistoryRepository, calculationEngine)

    val calculatorUseCases = CalculatorUseCases(calculationEngine, salaryHistoryRepository)
    val historyUseCases = HistoryUseCases(salaryHistoryRepository)
    val goalsAndSavingsUseCases = GoalsAndSavingsUseCases(financialGoalRepository, savingsPlanRepository, forecastService)
    val whatIfUseCases = WhatIfUseCases(whatIfService)
    val settingsUseCases = SettingsUseCases(settingsStore)
    val backupUseCases = BackupUseCases(importExportService, salaryHistoryRepository, financialGoalRepository, savingsPlanRepository, context.applicationContext)
    val csvImportUseCases = CsvImportUseCases(csvHistoryImportService, context.applicationContext)
}
