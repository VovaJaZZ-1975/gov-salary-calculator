package ru.govsalary.calculation.whatif

import ru.govsalary.calculation.engine.SalaryCalculationEngine
import ru.govsalary.calculation.model.SalaryCalculationResult
import ru.govsalary.calculation.model.WorkCalendar
import java.math.BigDecimal

/** Результат сравнения "как есть" (Current) и альтернативного сценария (Scenario). */
data class WhatIfComparison(
    val current: SalaryCalculationResult,
    val scenario: SalaryCalculationResult,
) {
    val totalPayoutDelta: BigDecimal get() = scenario.totalPayout.subtract(current.totalPayout)
    val accruedDelta: BigDecimal get() = scenario.accrued.subtract(current.accrued)
}

/**
 * Выполняет параллельный расчёт Current vs Scenario (мастер-промпт Фазы 2, п.10).
 * Чистая функция: не имеет побочных эффектов, ничего не сохраняет — этот модуль вообще
 * не подключает Room/DataStore (architecture.md, раздел 1), поэтому требование
 * "What-If не должен изменять основную БД" выполняется архитектурно, а не дополнительной проверкой.
 */
class WhatIfCalculator(private val engine: SalaryCalculationEngine) {

    fun compare(
        scenario: WhatIfScenario,
        calendar: WorkCalendar,
        previousMonthCalendar: WorkCalendar?,
    ): WhatIfComparison {
        val currentResult = engine.calculateMonthlyResult(
            config = scenario.baseConfig,
            calendar = calendar,
            previousMonthCalendar = previousMonthCalendar,
            premium = scenario.basePremium,
        )
        val scenarioResult = engine.calculateMonthlyResult(
            config = scenario.resolvedConfiguration(),
            calendar = calendar,
            previousMonthCalendar = previousMonthCalendar,
            premium = scenario.resolvedPremium(),
        )
        return WhatIfComparison(current = currentResult, scenario = scenarioResult)
    }
}
