package ru.govsalary.app.domain

import android.content.Context
import ru.govsalary.data.io.CsvHistoryImportService
import ru.govsalary.data.io.CsvHistoryTemplate
import ru.govsalary.data.io.CsvImportPreview
import ru.govsalary.data.io.CsvImportResult
import ru.govsalary.data.io.ImportStrategy
import java.io.File

/**
 * Импорт ретроспективных данных (коэффициенты + премии по месяцам) из CSV-шаблона.
 * Следует тому же паттерну хранения файлов, что и BackupUseCases (Фаза 4, п.20): без Storage
 * Access Framework, файлы лежат в app-специфичной директории — не требуют runtime-разрешений
 * на Android 10+, полностью офлайн.
 *
 * "Дружелюбный" сценарий для пользователя:
 * 1. Нажать «Скачать шаблон CSV» — appendет sample-файл в папку приложения.
 * 2. Отредактировать его во внешнем приложении (файловый менеджер/Excel/Google Таблицы) и
 *    положить готовый файл в ту же папку (или использовать сам шаблон).
 * 3. В приложении выбрать файл из списка → предпросмотр (сколько строк, ошибки, конфликты
 *    с уже сохранёнными расчётами) → выбрать стратегию → подтвердить импорт.
 */
class CsvImportUseCases(
    private val csvHistoryImportService: CsvHistoryImportService,
    private val context: Context,
) {
    private fun importDir(): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }

    /** Список файлов *.csv, доступных для импорта (папка приложения — куда пользователь кладёт свой файл). */
    fun listCsvFiles(): List<File> = importDir().listFiles()?.filter { it.extension.equals("csv", ignoreCase = true) } ?: emptyList()

    /** Сохраняет готовый шаблон с примером (январь/февраль 2026 — реальные проверенные значения) рядом с остальными файлами. */
    fun writeSampleTemplate(fileName: String = "salary_history_template.csv"): File {
        val file = File(importDir(), fileName)
        file.writeText(CsvHistoryTemplate.sampleCsv())
        return file
    }

    suspend fun preview(file: File): CsvImportPreview = csvHistoryImportService.preview(file.readText())

    suspend fun apply(preview: CsvImportPreview, strategy: ImportStrategy): CsvImportResult =
        csvHistoryImportService.apply(preview, strategy)
}
