package ru.govsalary.calculation.whatif

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.model.Allowance
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.SalaryParameters
import java.math.BigDecimal

/**
 * Набор точечных изменений поверх действующей конфигурации (мастер-промпт Фазы 2, п.10):
 * "оклад; коэффициенты; премия; надбавки; накопления".
 *
 * Все поля опциональны — не заданные оставляют значение базовой конфигурации без изменений.
 * "Накопления" (совокупный доход за период, влияющий, например, на прогрессивную ставку НДФЛ —
 * см. open-questions.md, п.1) в исходном Excel не рассчитываются и не хранятся, поэтому
 * сценарное поле-заглушка [accumulatedIncomeOverride] предусмотрено архитектурно, но
 * calculation-модуль на данный момент его не использует — это отражает то, что Excel-модель
 * такого расчёта не содержит (нельзя додумывать правило, которого нет — мастер-промпт Фазы 1, п.3).
 */
data class ScenarioOverrides(
    val baseSalary: BigDecimal? = null,
    val rankSalary: BigDecimal? = null,
    val allowanceRateOverrides: Map<AllowanceType, BigDecimal> = emptyMap(),
    val netMultiplierOverride: BigDecimal? = null,
    val premiumOverride: BigDecimal? = null,
    val accumulatedIncomeOverride: BigDecimal? = null,
)

/**
 * Сценарий What-If: базовая (сохранённая) конфигурация + точечные изменения.
 * WhatIfCalculator (см. WhatIfCalculator.kt) строит из него ВРЕМЕННУЮ конфигурацию и Premium,
 * ничего не записывая в основное хранилище (требование "не должен изменять основную БД" —
 * этот модуль вообще не содержит зависимостей на персистентность, см. architecture.md, раздел 1).
 */
data class WhatIfScenario(
    val baseConfig: CalculationConfiguration,
    val basePremium: Premium,
    val overrides: ScenarioOverrides,
) {
    fun resolvedConfiguration(): CalculationConfiguration {
        val salaryParameters = SalaryParameters(
            baseSalary = overrides.baseSalary ?: baseConfig.salaryParameters.baseSalary,
            rankSalary = overrides.rankSalary ?: baseConfig.salaryParameters.rankSalary,
        )
        val allowances = baseConfig.allowances.map { allowance ->
            val overrideRate = overrides.allowanceRateOverrides[allowance.type]
            if (overrideRate != null) allowance.copy(rate = overrideRate) else allowance
        }
        val taxParameters = if (overrides.netMultiplierOverride != null) {
            baseConfig.taxParameters.copy(netMultiplier = overrides.netMultiplierOverride)
        } else {
            baseConfig.taxParameters
        }
        return baseConfig.copy(
            // Отдельный versionId для сценария — чтобы результат нельзя было спутать с сохранённым расчётом.
            configVersionId = "${baseConfig.configVersionId}::what-if",
            salaryParameters = salaryParameters,
            allowances = allowances,
            taxParameters = taxParameters,
        )
    }

    fun resolvedPremium(): Premium =
        overrides.premiumOverride?.let { Premium(basePremium.period, it) } ?: basePremium
}
