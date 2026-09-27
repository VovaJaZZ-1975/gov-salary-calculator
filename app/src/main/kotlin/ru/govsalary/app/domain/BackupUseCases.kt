package ru.govsalary.app.domain

import android.content.Context
import kotlinx.coroutines.flow.first
import ru.govsalary.data.io.ImportExportService
import ru.govsalary.data.io.ImportPreview
import ru.govsalary.data.io.ImportStrategy
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.io.File

/**
 * Экспорт/импорт/резервная копия (мастер-промпт Фазы 4, п.20, Фаза 3, п.8-9).
 *
 * Файлы пишутся в app-специфичную директорию (`context.getExternalFilesDir(null)`), которая
 * не требует ни одного runtime-разрешения на Android 10+ (scoped storage) и остаётся полностью
 * офлайн (Фаза 3, п.11). Выбор произвольного места через Storage Access Framework — заявленное
 * упрощение объёма этой фазы, а не забытая функциональность: замена места хранения на SAF-Uri
 * потребует изменить только этот файл, ImportExportService (Фаза 3) уже не завязан на путь.
 */
class BackupUseCases(
    private val importExportService: ImportExportService,
    private val salaryHistoryRepository: SalaryHistoryRepository,
    private val financialGoalRepository: FinancialGoalRepository,
    private val savingsPlanRepository: SavingsPlanRepository,
    private val context: Context,
) {
    private fun backupDir(): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }

    suspend fun exportJsonBackup(fileName: String = "backup.json"): File {
        val goals = financialGoalRepository.observeAll().first()
        val plans = savingsPlanRepository.observeAllPlans().first()
        val periods = plans.flatMap { savingsPlanRepository.getPeriods(it.id) }
        val json = importExportService.exportJson(goals = goals, plans = plans, periods = periods)
        val file = File(backupDir(), fileName)
        file.writeText(json)
        return file
    }

    suspend fun exportCsvHistory(fileName: String = "salary_history.csv"): File {
        val all = salaryHistoryRepository.getAll()
        val csv = importExportService.exportCsv(all)
        val file = File(backupDir(), fileName)
        file.writeText(csv)
        return file
    }

    suspend fun previewImport(file: File): ImportPreview = importExportService.previewImport(file.readText())

    suspend fun applyImport(preview: ImportPreview, strategy: ImportStrategy) =
        importExportService.applyImport(preview, strategy)

    fun listBackupFiles(): List<File> = backupDir().listFiles()?.filter { it.extension == "json" } ?: emptyList()
}
