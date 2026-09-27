package ru.govsalary.data.domain

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.WorkCalendar
import ru.govsalary.calculation.whatif.ScenarioOverrides
import ru.govsalary.calculation.whatif.WhatIfCalculator
import ru.govsalary.calculation.whatif.WhatIfScenario
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * WhatIfService (мастер-промпт Фазы 3, п.7): получает текущие параметры, создаёт копию,
 * изменяет параметры, выполняет Calculation Engine, сравнивает результаты. Обёртка над
 * :calculation.whatif.WhatIfCalculator, которая уже реализована в Фазе 2 (см. Phase 2,
 * WhatIfCalculator.kt) — Calculation Engine НЕ переписывается (требование Фазы 3, преамбула).
 *
 * Результат НИКОГДА не сохраняется в Room — этот класс не принимает и не вызывает ни один
 * Repository или Dao, физически не может записать в основную историю (мастер-промпт Фазы 3, п.7:
 * "Ничего не сохранять в основную историю").
 */
data class WhatIfResult(
    val currentTotalPayout: BigDecimal,
    val scenarioTotalPayout: BigDecimal,
    val difference: BigDecimal,
    /** Процент изменения относительно текущего варианта. Null, если текущий вариант равен 0 (деление на ноль). */
    val percentChange: BigDecimal?,
)

class WhatIfService(
    private val calculator: WhatIfCalculator = WhatIfCalculator(ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine()),
) {

    fun run(
        currentConfig: CalculationConfiguration,
        currentPremium: Premium,
        overrides: ScenarioOverrides,
        calendar: WorkCalendar,
        previousMonthCalendar: WorkCalendar?,
    ): WhatIfResult {
        // 1. получить текущие параметры — переданы вызывающим кодом (currentConfig/currentPremium)
        // 2-3. создать копию и изменить параметры — делает WhatIfScenario.resolvedConfiguration()/resolvedPremium()
        //      (data class .copy(), исходный currentConfig не мутируется — см. Phase 2 тесты WhatIfCalculatorTest)
        val scenario = WhatIfScenario(
            baseConfig = currentConfig,
            basePremium = currentPremium,
            overrides = overrides,
        )
        // 4. выполнить Calculation Engine
        val comparison = calculator.compare(scenario, calendar, previousMonthCalendar)

        // 5. сравнить результаты
        val current = comparison.current.totalPayout
        val scenarioTotal = comparison.scenario.totalPayout
        val difference = comparison.totalPayoutDelta
        val percentChange = if (current.signum() != 0) {
            difference.multiply(BigDecimal(100)).divide(current, 4, RoundingMode.HALF_UP)
        } else {
            null
        }

        return WhatIfResult(
            currentTotalPayout = current,
            scenarioTotalPayout = scenarioTotal,
            difference = difference,
            percentChange = percentChange,
        )
    }
}
