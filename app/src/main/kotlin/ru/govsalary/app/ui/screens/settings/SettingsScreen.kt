package ru.govsalary.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.data.io.CsvImportPreview
import ru.govsalary.data.io.ImportStrategy
import ru.govsalary.data.local.datastore.AppSettings
import ru.govsalary.data.local.datastore.AppTheme
import java.io.File

/**
 * Настройки (мастер-промпт Фазы 4, п.20) + импорт ретроспективных данных из CSV (коэффициенты
 * и премии по месяцам — см. data/.../io/CsvHistoryImportService.kt). Только DataStore +
 * BackupUseCases/CsvImportUseCases (Фаза 3, п.10) — UI не содержит бизнес-логики: парсинг,
 * расчёт и сохранение целиком в UseCase-слое.
 */
@Composable
fun SettingsRoute() {
    val container = rememberAppContainer()
    val settings by container.settingsUseCases.observe().collectAsStateWithLifecycle(
        initialValue = AppSettings(selectedYear = java.time.LocalDate.now().year, selectedMonth = java.time.LocalDate.now().monthValue),
    )
    val scope = rememberCoroutineScope()
    var lastBackupMessage by remember { mutableStateOf<String?>(null) }

    var csvFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedCsvFile by remember { mutableStateOf<File?>(null) }
    var csvPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var csvStrategy by remember { mutableStateOf(ImportStrategy.KEEP_EXISTING) }
    var csvStatusMessage by remember { mutableStateOf<String?>(null) }
    var csvBusy by remember { mutableStateOf(false) }

    fun refreshCsvFiles() {
        csvFiles = container.csvImportUseCases.listCsvFiles()
    }

    LaunchedEffect(Unit) { refreshCsvFiles() }

    SettingsScreen(
        settings = settings,
        onThemeChange = { theme -> scope.launch { container.settingsUseCases.setTheme(theme) } },
        onExportJson = {
            scope.launch {
                val file = container.backupUseCases.exportJsonBackup()
                lastBackupMessage = "Сохранено: ${file.absolutePath}"
            }
        },
        onExportCsv = {
            scope.launch {
                val file = container.backupUseCases.exportCsvHistory()
                lastBackupMessage = "Сохранено: ${file.absolutePath}"
            }
        },
        lastMessage = lastBackupMessage,
        csvFiles = csvFiles,
        selectedCsvFile = selectedCsvFile,
        csvPreview = csvPreview,
        csvStrategy = csvStrategy,
        csvStatusMessage = csvStatusMessage,
        csvBusy = csvBusy,
        onDownloadCsvTemplate = {
            val file = container.csvImportUseCases.writeSampleTemplate()
            refreshCsvFiles()
            csvStatusMessage = "Шаблон сохранён: ${file.absolutePath}. Отредактируйте его (файловый менеджер/Excel/Таблицы) и вернитесь сюда."
        },
        onRefreshCsvFiles = { refreshCsvFiles() },
        onSelectCsvFile = { file ->
            selectedCsvFile = file
            csvPreview = null
            csvStatusMessage = null
            csvBusy = true
            scope.launch {
                val preview = container.csvImportUseCases.preview(file)
                csvPreview = preview
                csvBusy = false
                csvStatusMessage = when {
                    preview.errors.isNotEmpty() -> "Найдено ошибок в файле: ${preview.errors.size}. Строк для импорта: ${preview.rows.size}."
                    preview.isEmpty -> "В файле не найдено ни одной строки с данными."
                    else -> "Готово к импорту: ${preview.rows.size} строк(и). Конфликтов с уже сохранёнными расчётами: ${preview.conflictingPeriods.size}."
                }
            }
        },
        onStrategyChange = { csvStrategy = it },
        onApplyCsvImport = {
            val preview = csvPreview ?: return@SettingsScreen
            csvBusy = true
            scope.launch {
                val result = container.csvImportUseCases.apply(preview, csvStrategy)
                csvBusy = false
                csvPreview = null
                selectedCsvFile = null
                csvStatusMessage = "Импортировано: ${result.importedCount}. Пропущено (уже было сохранено): ${result.skippedCount}."
            }
        },
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeChange: (AppTheme) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    lastMessage: String?,
    csvFiles: List<File> = emptyList(),
    selectedCsvFile: File? = null,
    csvPreview: CsvImportPreview? = null,
    csvStrategy: ImportStrategy = ImportStrategy.KEEP_EXISTING,
    csvStatusMessage: String? = null,
    csvBusy: Boolean = false,
    onDownloadCsvTemplate: () -> Unit = {},
    onRefreshCsvFiles: () -> Unit = {},
    onSelectCsvFile: (File) -> Unit = {},
    onStrategyChange: (ImportStrategy) -> Unit = {},
    onApplyCsvImport: () -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionCard(title = "Тема оформления") {
                ThemeOption("Системная", settings.theme == AppTheme.SYSTEM) { onThemeChange(AppTheme.SYSTEM) }
                ThemeOption("Светлая", settings.theme == AppTheme.LIGHT) { onThemeChange(AppTheme.LIGHT) }
                ThemeOption("Тёмная", settings.theme == AppTheme.DARK) { onThemeChange(AppTheme.DARK) }
            }
        }
        item {
            SectionCard(title = "Выбранный период") {
                Text("Год: ${settings.selectedYear}, месяц: ${settings.selectedMonth}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard(title = "Резервное копирование") {
                Text(
                    "Полный бэкап (JSON) и история зарплаты (CSV) сохраняются локально в папку приложения — офлайн, без сети.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportJson) { Text("Экспорт JSON") }
                    Button(onClick = onExportCsv) { Text("Экспорт CSV") }
                }
                lastMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            CsvImportSection(
                csvFiles = csvFiles,
                selectedCsvFile = selectedCsvFile,
                csvPreview = csvPreview,
                csvStrategy = csvStrategy,
                csvStatusMessage = csvStatusMessage,
                csvBusy = csvBusy,
                onDownloadCsvTemplate = onDownloadCsvTemplate,
                onRefreshCsvFiles = onRefreshCsvFiles,
                onSelectCsvFile = onSelectCsvFile,
                onStrategyChange = onStrategyChange,
                onApplyCsvImport = onApplyCsvImport,
            )
        }
        item {
            SectionCard(title = "О приложении") {
                Text("Версия приложения: 1.0.0", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Версия расчётной модели: ${CalculationConfiguration.excel2026Baseline().configVersionId}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * Импорт ретроспективных данных (оклад, коэффициенты надбавок, премия, рабочие дни по месяцам)
 * из CSV-шаблона. Шаги для пользователя пронумерованы прямо в интерфейсе — это и есть
 * "дружелюбный интерфейс" для не самого тривиального сценария (шаблон → редактирование во
 * внешнем приложении → выбор файла → предпросмотр с ошибками/конфликтами → подтверждение).
 */
@Composable
private fun CsvImportSection(
    csvFiles: List<File>,
    selectedCsvFile: File?,
    csvPreview: CsvImportPreview?,
    csvStrategy: ImportStrategy,
    csvStatusMessage: String?,
    csvBusy: Boolean,
    onDownloadCsvTemplate: () -> Unit,
    onRefreshCsvFiles: () -> Unit,
    onSelectCsvFile: (File) -> Unit,
    onStrategyChange: (ImportStrategy) -> Unit,
    onApplyCsvImport: () -> Unit,
) {
    SectionCard(title = "Импорт ретроспективных данных (CSV)") {
        Text(
            "Загрузите историю зарплаты за прошлые периоды по шаблону: оклад, коэффициенты " +
                "надбавок (ЕДП, гостайна, особые условия, выслуга), премия и рабочие дни за месяц. " +
                "Расчёт для каждой строки выполняется тем же Calculation Engine, что и на экране «Расчёт».",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text("Шаг 1. Скачайте шаблон и заполните его", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onDownloadCsvTemplate) { Text("Скачать шаблон CSV") }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        Text("Шаг 2. Выберите готовый файл", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        if (csvFiles.isEmpty()) {
            Text(
                "Файлы *.csv не найдены в папке приложения. Скачайте шаблон или положите свой файл рядом с ним.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            csvFiles.forEach { file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCsvFile(file) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = file == selectedCsvFile, onClick = { onSelectCsvFile(file) })
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onRefreshCsvFiles) { Text("Обновить список файлов") }

        if (csvBusy) {
            Spacer(Modifier.height(8.dp))
            Text("Обработка…", style = MaterialTheme.typography.bodyMedium)
        }

        csvPreview?.let { preview ->
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Text("Шаг 3. Проверьте предпросмотр", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text("Строк для импорта: ${preview.rows.size}", style = MaterialTheme.typography.bodyMedium)

            if (preview.errors.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ошибки в файле (эти строки пропущены):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                preview.errors.take(10).forEach { error ->
                    Text(
                        "• строка ${error.lineNumber}: ${error.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (preview.errors.size > 10) {
                    Text("… и ещё ${preview.errors.size - 10}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (preview.conflictingPeriods.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Уже есть сохранённый расчёт за: ${preview.conflictingPeriods.joinToString(", ") { it.toString() }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text("Шаг 4. Что делать с конфликтующими периодами?", style = MaterialTheme.typography.labelLarge)
                StrategyOption(
                    "Не перезаписывать существующие",
                    csvStrategy == ImportStrategy.KEEP_EXISTING,
                ) { onStrategyChange(ImportStrategy.KEEP_EXISTING) }
                StrategyOption(
                    "Перезаписать значениями из файла",
                    csvStrategy == ImportStrategy.OVERWRITE,
                ) { onStrategyChange(ImportStrategy.OVERWRITE) }
            }

            if (!preview.isEmpty) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onApplyCsvImport, enabled = !csvBusy) { Text("Импортировать") }
            }
        }

        csvStatusMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StrategyOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Settings - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun SettingsPreview() {
    GovSalaryTheme {
        SettingsScreen(
            settings = AppSettings(theme = AppTheme.SYSTEM, selectedYear = 2026, selectedMonth = 9),
            onThemeChange = {}, onExportJson = {}, onExportCsv = {}, lastMessage = null,
        )
    }
}

@Preview(name = "Settings - CSV preview state", widthDp = 360, heightDp = 900, showBackground = true)
@Composable
private fun SettingsCsvPreviewPreview() {
    GovSalaryTheme {
        SettingsScreen(
            settings = AppSettings(theme = AppTheme.SYSTEM, selectedYear = 2026, selectedMonth = 9),
            onThemeChange = {}, onExportJson = {}, onExportCsv = {}, lastMessage = null,
            csvFiles = listOf(File("salary_history_template.csv")),
            selectedCsvFile = File("salary_history_template.csv"),
            csvPreview = CsvImportPreview(
                rows = emptyList(),
                errors = listOf(
                    ru.govsalary.data.io.CsvRowError(3, "raw", "строка 3: не удалось разобрать число в столбце «оклад»: «abc»"),
                ),
                conflictingPeriods = listOf(ru.govsalary.calculation.model.CalculationPeriod(2026, 1)),
            ),
            csvStrategy = ImportStrategy.KEEP_EXISTING,
            csvStatusMessage = "Найдено ошибок в файле: 1. Строк для импорта: 1.",
        )
    }
}
