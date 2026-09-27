package ru.govsalary.calculation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.WorkCalendar
import ru.govsalary.calculation.whatif.ScenarioOverrides
import ru.govsalary.calculation.whatif.WhatIfCalculator
import ru.govsalary.calculation.whatif.WhatIfScenario
import java.math.BigDecimal

class WhatIfCalculatorTest {

    private val engine = DefaultSalaryCalculationEngine()
    private val calculator = WhatIfCalculator(engine)
    private val baseConfig = CalculationConfiguration.excel2026Baseline()
    private val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)

    @Test
    fun `scenario with higher base salary increases total payout versus current`() {
        val scenario = WhatIfScenario(
            baseConfig = baseConfig,
            basePremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(baseSalary = BigDecimal("30000")),
        )
        val comparison = calculator.compare(scenario, jan, previousMonthCalendar = null)

        assert(comparison.scenario.totalPayout > comparison.current.totalPayout)
        assertEquals(BigDecimal("24296"), baseConfig.salaryParameters.baseSalary, "исходная конфигурация не должна мутировать")
    }

    @Test
    fun `scenario does not mutate base configuration object`() {
        val scenario = WhatIfScenario(
            baseConfig = baseConfig,
            basePremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(
                allowanceRateOverrides = mapOf(AllowanceType.MONTHLY_BONUS to BigDecimal("1.5")),
            ),
        )
        val resolved = scenario.resolvedConfiguration()

        assertNotEquals(baseConfig.configVersionId, resolved.configVersionId)
        assertEquals(BigDecimal("0.9"), baseConfig.allowance(AllowanceType.MONTHLY_BONUS)!!.rate)
        assertEquals(BigDecimal("1.5"), resolved.allowance(AllowanceType.MONTHLY_BONUS)!!.rate)
    }

    @Test
    fun `scenario premium override replaces base premium without touching original`() {
        val basePremium = Premium(jan.period, BigDecimal("5000"))
        val scenario = WhatIfScenario(
            baseConfig = baseConfig,
            basePremium = basePremium,
            overrides = ScenarioOverrides(premiumOverride = BigDecimal("15000")),
        )
        val comparison = calculator.compare(scenario, jan, previousMonthCalendar = null)

        assertEquals(BigDecimal("5000"), comparison.current.premium)
        assertEquals(BigDecimal("15000"), comparison.scenario.premium)
        assertEquals(BigDecimal("5000"), basePremium.amount, "исходный Premium не должен мутировать")
    }

    @Test
    fun `no overrides means scenario equals current`() {
        val scenario = WhatIfScenario(
            baseConfig = baseConfig,
            basePremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(),
        )
        val comparison = calculator.compare(scenario, jan, previousMonthCalendar = null)

        assertEquals(comparison.current.totalPayout, comparison.scenario.totalPayout)
        // BigDecimal.equals() учитывает scale (0.00 != 0 несмотря на равное числовое значение) —
        // сравниваем через compareTo, а не assertEquals(BigDecimal.ZERO, ...).
        assertEquals(0, comparison.totalPayoutDelta.compareTo(BigDecimal.ZERO))
    }
}
