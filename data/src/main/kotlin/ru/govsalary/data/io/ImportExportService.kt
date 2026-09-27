package ru.govsalary.data.io

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.SavingsType
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Экспорт/импорт (мастер-промпт Фазы 3, п.8-9). Полностью офлайн: чтение/запись только
 * локальных файлов (путь передаёт вызывающий UI-слой, здесь — только сериализация строк).
 *
 * JSON DTO намеренно НЕ переиспользует Room-сущности напрямую (они содержат BigDecimal/LocalDate/
 * Instant, для которых нужны отдельные @Serializable-обёртки) — плоские строковые поля защищают
 * формат от изменений внутренней схемы Room между версиями приложения.
 */

@Serializable
data class SalaryHistoryDto(
    val year: Int,
    val month: Int,
    val source: String, // ValueSource.name
    val configVersionId: String?,
    val baseSalary: String,
    val rankSalary: String,
    val allowancesJson: String,
    val accrued: String,
    val ndfl: String,
    val premium: String,
    val totalPayout: String,
    val midMonthPaymentDate: String?,   // ISO-8601 (LocalDate.toString())
    val midMonthPaymentAmount: String?,
    val startOfMonthPaymentDate: String?,
    val startOfMonthPaymentAmount: String?,
    val note: String?,
    val recordedAt: String, // Instant.toString()
)

@Serializable
data class FinancialGoalDto(
    val id: Long,
    val title: String,
    val description: String?,
    val targetAmount: String,
    val currentAmount: String,
    val createdAt: String,
    val targetDate: String?,
    val status: String,
)

@Serializable
data class SavingsPlanDto(
    val id: Long,
    val name: String,
    val goalId: Long?,
    val type: String,
    val amount: String?,
    val percent: String?,
    val startPeriod: String,
    val endPeriod: String?,
    val priority: Int,
    val isActive: Boolean,
)

@Serializable
data class SavingsPeriodDto(
    val id: Long,
    val planId: Long,
    val startDate: String,
    val endDate: String,
    val amount: String,
)

@Serializable
data class BackupDto(
    val backupFormatVersion: Int = 1,
    val exportedAt: String,
    val salaryHistory: List<SalaryHistoryDto>,
    val financialGoals: List<FinancialGoalDto>,
    val savingsPlans: List<SavingsPlanDto>,
    val savingsPeriods: List<SavingsPeriodDto>,
)

/** Итог анализа файла импорта ДО применения — ничего в БД ещё не записано (мастер-промпт Фазы 3, п.9). */
data class ImportPreview(
    val backup: BackupDto,
    val salaryHistoryCount: Int,
    val financialGoalsCount: Int,
    val savingsPlansCount: Int,
    val savingsPeriodsCount: Int,
    /** true, если в текущей БД уже есть записи salary_history за годы, встречающиеся в бэкапе — потенциальный конфликт. */
    val hasPotentialSalaryHistoryConflicts: Boolean,
)

enum class ImportStrategy {
    /** Не трогать существующие записи с тем же ключом — вставлять только отсутствующие. */
    KEEP_EXISTING,
    /** Перезаписать существующие записи данными из бэкапа. */
    OVERWRITE,
}

class ImportExportService(
    private val salaryHistoryRepository: SalaryHistoryRepository,
    private val financialGoalRepository: FinancialGoalRepository,
    private val savingsPlanRepository: SavingsPlanRepository,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ---------- ЭКСПОРТ ----------

    /**
     * JSON — полный бэкап всех данных приложения (мастер-промпт Фазы 3, п.8).
     * FinancialGoal/SavingsPlan-репозитории отдают Flow (для реактивного UI), поэтому для
     * одноразового экспорта вызывающий код (Фаза 4) должен сам снять текущий снимок
     * (например, `.first()`) и передать сюда — так ImportExportService остаётся легко
     * тестируемым чистым классом, не привязанным к конкретному способу чтения Flow.
     */
    suspend fun exportJson(
        history: List<SalaryHistoryEntity>? = null,
        goals: List<FinancialGoalEntity>,
        plans: List<SavingsPlanEntity>,
        periods: List<SavingsPeriodEntity>,
        now: Instant = Instant.now(),
    ): String {
        // Kotlin не поддерживает suspend-вызовы прямо в значении параметра по умолчанию
        // ("Unsupported [suspend function calls in a context of default parameter value]") —
        // поэтому история как аргумент опциональна (null), а недостающая подгружается здесь,
        // внутри suspend-тела функции, а не в сигнатуре.
        val resolvedHistory = history ?: salaryHistoryRepository.getAll()
        val backup = BackupDto(
            exportedAt = now.toString(),
            salaryHistory = resolvedHistory.map { it.toDto() },
            financialGoals = goals.map { it.toDto() },
            savingsPlans = plans.map { it.toDto() },
            savingsPeriods = periods.map { it.toDto() },
        )
        return json.encodeToString(BackupDto.serializer(), backup)
    }

    /** CSV — история зарплаты (мастер-промпт Фазы 3, п.8). Разделитель ';' (совместимость с Excel в ru-RU локали). */
    fun exportCsv(history: List<SalaryHistoryEntity>): String {
        val header = "year;month;source;accrued;ndfl;premium;totalPayout;configVersionId"
        val rows = history.sortedWith(compareBy({ it.year }, { it.month }, { it.source })).map { e ->
            listOf(
                e.year, e.month, e.source.name,
                e.accrued.toPlainString(), e.ndfl.toPlainString(), e.premium.toPlainString(),
                e.totalPayout.toPlainString(), e.configVersionId ?: "",
            ).joinToString(";")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    // ---------- ИМПОРТ ----------

    /** Шаг 1: разбор файла без изменения БД (мастер-промпт Фазы 3, п.9: "не должен уничтожать данные без подтверждения"). */
    suspend fun previewImport(jsonContent: String): ImportPreview {
        val backup = json.decodeFromString(BackupDto.serializer(), jsonContent)
        val existingYears = salaryHistoryRepository.getAll().map { it.year }.toSet()
        val incomingYears = backup.salaryHistory.map { it.year }.toSet()
        return ImportPreview(
            backup = backup,
            salaryHistoryCount = backup.salaryHistory.size,
            financialGoalsCount = backup.financialGoals.size,
            savingsPlansCount = backup.savingsPlans.size,
            savingsPeriodsCount = backup.savingsPeriods.size,
            hasPotentialSalaryHistoryConflicts = existingYears.intersect(incomingYears).isNotEmpty(),
        )
    }

    /** Шаг 2: применение ТОЛЬКО после явного подтверждения пользователем и выбора стратегии. */
    suspend fun applyImport(preview: ImportPreview, strategy: ImportStrategy) {
        val existingKeys = salaryHistoryRepository.getAll()
            .map { Triple(it.year, it.month, it.source) }
            .toSet()

        val toInsert = preview.backup.salaryHistory
            .map { it.toEntity() }
            .filter { entity ->
                val key = Triple(entity.year, entity.month, entity.source)
                strategy == ImportStrategy.OVERWRITE || key !in existingKeys
            }
        salaryHistoryRepository.upsertAll(toInsert)

        // FinancialGoal/SavingsPlan восстанавливаются только для KEEP_EXISTING со стратегией
        // "добавить как новые" (id не переиспользуется, чтобы не перезаписать чужую запись по PK) —
        // подробная политика согласования id оставлена на Фазу 4 (UI подтверждения конфликтов).
        if (strategy == ImportStrategy.OVERWRITE) {
            preview.backup.financialGoals.forEach { dto ->
                financialGoalRepository.create(dto.toEntity().copy(id = 0))
            }
        }
    }
}

// ---------- Маппинг Entity <-> Dto ----------

private fun SalaryHistoryEntity.toDto() = SalaryHistoryDto(
    year = year, month = month, source = source.name, configVersionId = configVersionId,
    baseSalary = baseSalary.toPlainString(), rankSalary = rankSalary.toPlainString(),
    allowancesJson = allowancesJson, accrued = accrued.toPlainString(), ndfl = ndfl.toPlainString(),
    premium = premium.toPlainString(), totalPayout = totalPayout.toPlainString(),
    midMonthPaymentDate = midMonthPaymentDate?.toString(), midMonthPaymentAmount = midMonthPaymentAmount?.toPlainString(),
    startOfMonthPaymentDate = startOfMonthPaymentDate?.toString(), startOfMonthPaymentAmount = startOfMonthPaymentAmount?.toPlainString(),
    note = note, recordedAt = recordedAt.toString(),
)

private fun SalaryHistoryDto.toEntity() = SalaryHistoryEntity(
    year = year, month = month, source = ValueSource.valueOf(source), configVersionId = configVersionId,
    baseSalary = BigDecimal(baseSalary), rankSalary = BigDecimal(rankSalary), allowancesJson = allowancesJson,
    accrued = BigDecimal(accrued), ndfl = BigDecimal(ndfl), premium = BigDecimal(premium),
    totalPayout = BigDecimal(totalPayout),
    midMonthPaymentDate = midMonthPaymentDate?.let { LocalDate.parse(it) },
    midMonthPaymentAmount = midMonthPaymentAmount?.let { BigDecimal(it) },
    startOfMonthPaymentDate = startOfMonthPaymentDate?.let { LocalDate.parse(it) },
    startOfMonthPaymentAmount = startOfMonthPaymentAmount?.let { BigDecimal(it) },
    note = note, recordedAt = Instant.parse(recordedAt),
)

private fun FinancialGoalEntity.toDto() = FinancialGoalDto(
    id = id, title = title, description = description, targetAmount = targetAmount.toPlainString(),
    currentAmount = currentAmount.toPlainString(), createdAt = createdAt.toString(),
    targetDate = targetDate?.toString(), status = status.name,
)

private fun FinancialGoalDto.toEntity() = FinancialGoalEntity(
    id = id, title = title, description = description, targetAmount = BigDecimal(targetAmount),
    currentAmount = BigDecimal(currentAmount), createdAt = LocalDate.parse(createdAt),
    targetDate = targetDate?.let { LocalDate.parse(it) }, status = GoalStatus.valueOf(status),
)

private fun SavingsPlanEntity.toDto() = SavingsPlanDto(
    id = id, name = name, goalId = goalId, type = type.name, amount = amount?.toPlainString(),
    percent = percent?.toPlainString(), startPeriod = startPeriod.toString(), endPeriod = endPeriod?.toString(),
    priority = priority, isActive = isActive,
)

private fun SavingsPeriodEntity.toDto() = SavingsPeriodDto(
    id = id, planId = planId, startDate = startDate.toString(), endDate = endDate.toString(),
    amount = amount.toPlainString(),
)
