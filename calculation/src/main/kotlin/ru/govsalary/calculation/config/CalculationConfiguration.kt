package ru.govsalary.calculation.config

import ru.govsalary.calculation.model.Allowance
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.SalaryParameters
import java.time.LocalDate

/**
 * Версионируемая конфигурация расчётной модели на год (architecture.md, раздел 5).
 *
 * Требование мастер-промпта Фазы 2, п.6: изменение конфигурации 2027 года не должно
 * менять результаты, сохранённые за 2026 год. Этот класс — immutable value object:
 * новая версия = новый экземпляр с новым [configVersionId], старые экземпляры не мутируются.
 * Персистентность версий (хранение истории) — ответственность data-слоя (architecture.md,
 * раздел 5, п.2-3), вне зоны ответственности calculation-модуля.
 */
data class CalculationConfiguration(
    val configVersionId: String,
    val year: Int,
    val effectiveFrom: LocalDate,
    val salaryParameters: SalaryParameters,
    val allowances: List<Allowance>,
    val taxParameters: TaxParameters,
    val roundingRules: RoundingRules = RoundingRules(),
) {
    init {
        require(allowances.map { it.type }.toSet().size == allowances.size) {
            "Дублирующиеся типы надбавок в конфигурации $configVersionId"
        }
    }

    fun allowance(type: AllowanceType): Allowance? = allowances.firstOrNull { it.type == type }

    companion object {
        /**
         * Конфигурация 2026 года, буквально воспроизводящая блок P13:X21 из
         * "Расчет ЗП (2026).xlsx" (excel-analysis.md, раздел 3). Используется в golden-тестах.
         */
        fun excel2026Baseline(): CalculationConfiguration = CalculationConfiguration(
            configVersionId = "2026-baseline-v1",
            year = 2026,
            effectiveFrom = LocalDate.of(2026, 1, 1),
            salaryParameters = SalaryParameters(
                baseSalary = java.math.BigDecimal("24296"),
                rankSalary = java.math.BigDecimal("13853"),
            ),
            allowances = listOf(
                Allowance(AllowanceType.MONTHLY_BONUS, java.math.BigDecimal("0.9")),
                Allowance(AllowanceType.STATE_SECRET, java.math.BigDecimal("0.1")),
                Allowance(AllowanceType.SPECIAL_CONDITIONS, java.math.BigDecimal("1.2")),
                Allowance(AllowanceType.SENIORITY, java.math.BigDecimal("0.1")),
            ),
            taxParameters = TaxParameters(netMultiplier = java.math.BigDecimal("0.87")),
        )
    }
}
