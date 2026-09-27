package ru.govsalary.data.io

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.config.RoundingRules
import ru.govsalary.calculation.config.TaxParameters
import ru.govsalary.calculation.engine.SalaryCalculationEngine
import ru.govsalary.calculation.model.Allowance
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.SalaryParameters
import ru.govsalary.calculation.model.WorkCalendar
import ru.govsalary.data.local.db.AllowancesJson
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.SalaryHistoryRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Импорт ретроспективных данных по шаблону CSV: коэффициенты (оклад, классный чин, ЕДП, гостайна,
 * особые условия, выслуга), премия и рабочий календарь за произвольный набор месяцев/лет —
 * то, что в исходном Excel хранилось только как неструктурированные примечания к ячейкам за
 * 2021-2025 гг. (см. docs/excel-analysis.md, раздел 9; docs/open-questions.md, п.9).
 *
 * Каждая строка прогоняется через СУЩЕСТВУЮЩИЙ Calculation Engine (:calculation, не изменялся) —
 * это не альтернативный/упрощённый расчёт, а тот же W21/AAn24/AAn29/AAn30, что и в Фазе 2, просто
 * с конфигурацией, взятой из строки CSV вместо `excel2026Baseline()`. Результат сохраняется как
 * `ValueSource.CALCULATED` (это то, что расчётная модель ДАЛА БЫ для введённых исторических
 * коэффициентов) — если у пользователя есть отдельно фактически полученные суммы за тот же
 * период, они вводятся отдельно как `ValueSource.ACTUAL` и не конфликтуют (см. `PeriodComparison`
 * в SalaryHistoryRepository.kt).
 *
 * Округление между соседними периодами: если два соседних месяца в CSV имеют РАЗНЫЕ коэффициенты
 * (что как раз ожидаемо для ретроспективных данных — оклад со временем меняется), выплата "за
 * вторую половину предыдущего месяца" считается по СОБСТВЕННЫМ коэффициентам предыдущего месяца,
 * а не текущего — в отличие от `DefaultSalaryCalculationEngine.calculateMonthlyResult`, который
 * (корректно для Фазы 2, где конфигурация одна на весь год) использует единую конфигурацию для
 * обеих частей выплаты. Здесь это собрано вручную из публичных методов `SalaryCalculationEngine`
 * (calculateNetMonthlyBase/calculateDailyRate) — сам движок не менялся ни на строку.
 */
object CsvHistoryTemplate {

    /** Порядок и названия столбцов. Разделитель ';' — совместимость с Excel в ru-RU локали (см. exportCsv). */
    val COLUMNS = listOf(
        "год", "месяц", "оклад", "классный_чин",
        "коэф_едп", "коэф_гостайна", "коэф_особые_условия", "коэф_выслуга",
        "премия", "рабочих_дней", "дней_до_15",
        "отработано_до_15", "отработано_после_15", "коэф_на_руки",
    )

    const val HEADER = "год;месяц;оклад;классный_чин;коэф_едп;коэф_гостайна;коэф_особые_условия;коэф_выслуга;премия;рабочих_дней;дней_до_15;отработано_до_15;отработано_после_15;коэф_на_руки"

    /**
     * Готовый шаблон с пояснением и примером строки (значения января/февраля 2026 из
     * docs/test-cases.md, Test Case 001-006 — реальные проверенные числа, не выдуманные).
     * Пустые ячейки `отработано_до_15`/`отработано_после_15`/`коэф_на_руки` — необязательные,
     * по умолчанию берутся плановые рабочие дни и множитель 0.87 соответственно.
     */
    fun sampleCsv(): String = buildString {
        appendLine(HEADER)
        appendLine("2026;1;24296;13853;0.9;0.1;1.2;0.1;0;15;4;;;")
        appendLine("2026;2;24296;13853;0.9;0.1;1.2;0.1;23997;19;10;;;")
    }
}

data class CsvHistoryRow(
    val lineNumber: Int,
    val period: CalculationPeriod,
    val baseSalary: BigDecimal,
    val rankSalary: BigDecimal,
    val edpRate: BigDecimal,
    val stateSecretRate: BigDecimal,
    val specialConditionsRate: BigDecimal,
    val seniorityRate: BigDecimal,
    val premium: BigDecimal,
    val totalWorkingDays: Int,
    val workingDaysUpTo15: Int,
    val actualWorkedDaysUpTo15: Int?,
    val actualWorkedDaysAfter15: Int?,
    val netMultiplier: BigDecimal,
) {
    fun toConfiguration(): CalculationConfiguration = CalculationConfiguration(
        configVersionId = "csv-import-${period.year}-${period.month.toString().padStart(2, '0')}",
        year = period.year,
        effectiveFrom = LocalDate.of(period.year, period.month, 1),
        salaryParameters = SalaryParameters(baseSalary, rankSalary),
        allowances = listOf(
            Allowance(AllowanceType.MONTHLY_BONUS, edpRate),
            Allowance(AllowanceType.STATE_SECRET, stateSecretRate),
            Allowance(AllowanceType.SPECIAL_CONDITIONS, specialConditionsRate),
            Allowance(AllowanceType.SENIORITY, seniorityRate),
        ),
        taxParameters = TaxParameters(netMultiplier),
        roundingRules = RoundingRules(),
    )

    fun toWorkCalendar(): WorkCalendar = WorkCalendar(
        period = period,
        totalWorkingDays = totalWorkingDays,
        workingDaysUpTo15 = workingDaysUpTo15,
        actualWorkedDaysUpTo15 = actualWorkedDaysUpTo15 ?: workingDaysUpTo15,
        actualWorkedDaysAfter15 = actualWorkedDaysAfter15 ?: (totalWorkingDays - workingDaysUpTo15),
    )
}

data class CsvRowError(val lineNumber: Int, val rawLine: String, val message: String)

data class CsvParseResult(val rows: List<CsvHistoryRow>, val errors: List<CsvRowError>)

data class CsvImportPreview(
    val rows: List<CsvHistoryRow>,
    val errors: List<CsvRowError>,
    /** Периоды, для которых уже есть сохранённый CALCULATED-расчёт — при стратегии KEEP_EXISTING эти строки будут пропущены. */
    val conflictingPeriods: List<CalculationPeriod>,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

data class CsvImportResult(val importedCount: Int, val skippedCount: Int)

private fun parseDecimal(raw: String, lineNumber: Int, column: String): BigDecimal {
    val normalized = raw.trim().replace(",", ".")
    return try {
        BigDecimal(normalized)
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("строка $lineNumber: не удалось разобрать число в столбце «$column»: «$raw»")
    }
}

private fun parseOptionalInt(raw: String, lineNumber: Int, column: String): Int? {
    if (raw.isBlank()) return null
    return raw.trim().toIntOrNull()
        ?: throw IllegalArgumentException("строка $lineNumber: не удалось разобрать целое число в столбце «$column»: «$raw»")
}

private fun parseInt(raw: String, lineNumber: Int, column: String): Int =
    parseOptionalInt(raw, lineNumber, column)
        ?: throw IllegalArgumentException("строка $lineNumber: столбец «$column» обязателен")

fun parseCsvHistory(content: String): CsvParseResult {
    val lines = content.lines().mapIndexed { idx, line -> (idx + 1) to line }
        .filter { (_, line) -> line.isNotBlank() }

    if (lines.isEmpty()) return CsvParseResult(emptyList(), emptyList())

    // Первая непустая строка считается заголовком и пропускается независимо от точного текста —
    // это позволяет пользователю чуть менять регистр/пробелы в шапке, не ломая импорт.
    val dataLines = lines.drop(1)

    val rows = mutableListOf<CsvHistoryRow>()
    val errors = mutableListOf<CsvRowError>()

    for ((lineNumber, rawLine) in dataLines) {
        val cols = rawLine.split(";").map { it.trim() }
        try {
            if (cols.size < CsvHistoryTemplate.COLUMNS.size - 3) {
                // допускаем отсутствие 3 необязательных последних столбцов, если пользователь их обрезал
                throw IllegalArgumentException(
                    "ожидается минимум ${CsvHistoryTemplate.COLUMNS.size - 3} столбцов, получено ${cols.size}"
                )
            }
            fun col(i: Int): String = cols.getOrElse(i) { "" }

            val year = parseInt(col(0), lineNumber, "год")
            val month = parseInt(col(1), lineNumber, "месяц")
            val period = try {
                CalculationPeriod(year, month)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("строка $lineNumber: ${e.message}")
            }

            val row = CsvHistoryRow(
                lineNumber = lineNumber,
                period = period,
                baseSalary = parseDecimal(col(2), lineNumber, "оклад"),
                rankSalary = parseDecimal(col(3), lineNumber, "классный_чин"),
                edpRate = parseDecimal(col(4), lineNumber, "коэф_едп"),
                stateSecretRate = parseDecimal(col(5), lineNumber, "коэф_гостайна"),
                specialConditionsRate = parseDecimal(col(6), lineNumber, "коэф_особые_условия"),
                seniorityRate = parseDecimal(col(7), lineNumber, "коэф_выслуга"),
                premium = parseDecimal(col(8), lineNumber, "премия"),
                totalWorkingDays = parseInt(col(9), lineNumber, "рабочих_дней"),
                workingDaysUpTo15 = parseInt(col(10), lineNumber, "дней_до_15"),
                actualWorkedDaysUpTo15 = parseOptionalInt(col(11), lineNumber, "отработано_до_15"),
                actualWorkedDaysAfter15 = parseOptionalInt(col(12), lineNumber, "отработано_после_15"),
                netMultiplier = col(13).takeIf { it.isNotBlank() }
                    ?.let { parseDecimal(it, lineNumber, "коэф_на_руки") }
                    ?: BigDecimal("0.87"),
            )
            // Валидируем WorkCalendar (рабочие/отработанные дни) ДО добавления строки в rows —
            // иначе некорректные значения "отработано_до_15"/"отработано_после_15" (например,
            // больше плановых) привели бы к необработанному исключению позже, при apply(),
            // а не к понятной ошибке уже в предпросмотре.
            row.toWorkCalendar()
            rows += row
        } catch (e: IllegalArgumentException) {
            errors += CsvRowError(lineNumber, rawLine, e.message ?: "ошибка разбора строки")
        } catch (e: Exception) {
            errors += CsvRowError(lineNumber, rawLine, "непредвиденная ошибка: ${e.message}")
        }
    }

    // Дубликаты периода внутри самого файла — тоже ошибка разбора, а не тихий перезапис.
    val byPeriod = rows.groupBy { it.period }
    val duplicateErrors = byPeriod.filterValues { it.size > 1 }.flatMap { (period, dupRows) ->
        dupRows.drop(1).map { CsvRowError(it.lineNumber, "", "строка ${it.lineNumber}: период $period уже встречался в этом файле (строка ${dupRows.first().lineNumber})") }
    }
    val dedupedRows = byPeriod.values.map { it.first() }

    return CsvParseResult(dedupedRows.sortedBy { it.period }, errors + duplicateErrors)
}

class CsvHistoryImportService(
    private val salaryHistoryRepository: SalaryHistoryRepository,
    private val engine: SalaryCalculationEngine,
) {

    suspend fun preview(csvContent: String): CsvImportPreview {
        val parsed = parseCsvHistory(csvContent)
        val existingCalculatedPeriods = salaryHistoryRepository.getAll()
            .filter { it.source == ValueSource.CALCULATED }
            .map { CalculationPeriod(it.year, it.month) }
            .toSet()
        val conflicts = parsed.rows.map { it.period }.filter { it in existingCalculatedPeriods }
        return CsvImportPreview(parsed.rows, parsed.errors, conflicts)
    }

    /**
     * @param strategy KEEP_EXISTING — периоды из conflictingPeriods пропускаются;
     *                 OVERWRITE — все строки предпросмотра пересчитываются и сохраняются.
     */
    suspend fun apply(preview: CsvImportPreview, strategy: ImportStrategy, now: Instant = Instant.now()): CsvImportResult {
        val rowsToApply = if (strategy == ImportStrategy.KEEP_EXISTING) {
            val conflictSet = preview.conflictingPeriods.toSet()
            preview.rows.filterNot { it.period in conflictSet }
        } else {
            preview.rows
        }
        val skipped = preview.rows.size - rowsToApply.size

        val sorted = rowsToApply.sortedBy { it.period }
        val entities = mutableListOf<SalaryHistoryEntity>()

        for ((index, row) in sorted.withIndex()) {
            val previousRow = sorted.getOrNull(index - 1)?.takeIf { it.period == row.period.previous() }
            entities += buildEntity(row, previousRow, now)
        }

        salaryHistoryRepository.upsertAll(entities)
        return CsvImportResult(importedCount = entities.size, skippedCount = skipped)
    }

    private fun buildEntity(row: CsvHistoryRow, previousRow: CsvHistoryRow?, now: Instant): SalaryHistoryEntity {
        val config = row.toConfiguration()
        val calendar = row.toWorkCalendar()

        val accrued = engine.calculateAccrued(config)
        val netMonthlyBase = engine.calculateNetMonthlyBase(config)
        val ndfl = accrued.subtract(netMonthlyBase)
        val dailyRate = engine.calculateDailyRate(netMonthlyBase, calendar, config)

        val midMonthAmount = config.roundingRules.mround(dailyRate.multiply(BigDecimal(calendar.actualWorkedDaysUpTo15)))

        // Выплата за 2-ю половину ПРЕДЫДУЩЕГО месяца считается по конфигурации ЭТОГО предыдущего
        // месяца (его собственный оклад/коэффициенты на тот момент), а не текущей строки —
        // см. комментарий к файлу выше.
        val startOfMonthAmount = if (previousRow != null) {
            val prevConfig = previousRow.toConfiguration()
            val prevCalendar = previousRow.toWorkCalendar()
            val prevNetMonthlyBase = engine.calculateNetMonthlyBase(prevConfig)
            val prevDailyRate = engine.calculateDailyRate(prevNetMonthlyBase, prevCalendar, prevConfig)
            prevConfig.roundingRules.mround(prevDailyRate.multiply(BigDecimal(prevCalendar.actualWorkedDaysAfter15)))
        } else {
            null
        }

        val totalPayout = midMonthAmount.add(startOfMonthAmount ?: BigDecimal.ZERO).add(row.premium)

        val allowancesJson = AllowancesJson.encode(
            config.allowances.associate { it.type.name to it.amount(config.salaryParameters.baseSalary) }
        )

        return SalaryHistoryEntity(
            year = row.period.year,
            month = row.period.month,
            source = ValueSource.CALCULATED,
            configVersionId = config.configVersionId,
            baseSalary = row.baseSalary,
            rankSalary = row.rankSalary,
            allowancesJson = allowancesJson,
            accrued = accrued,
            ndfl = ndfl,
            premium = row.premium,
            totalPayout = totalPayout,
            midMonthPaymentDate = null,
            midMonthPaymentAmount = midMonthAmount,
            startOfMonthPaymentDate = null,
            startOfMonthPaymentAmount = startOfMonthAmount,
            note = "Импортировано из CSV (ретроспективные данные)",
            recordedAt = now,
        )
    }
}
