package ru.govsalary.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.WorkCalendar
import ru.govsalary.calculation.whatif.ScenarioOverrides
import ru.govsalary.data.domain.WhatIfService
import java.math.BigDecimal

/** Мастер-промпт Фазы 3, п.12 "What-If". Проверяет пайплайн из п.7: копия -> изменение -> сравнение. */
class WhatIfServiceTest {

    private val service = WhatIfService()
    private val config = CalculationConfiguration.excel2026Baseline()
    private val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)

    @Test
    fun `scenario with higher base salary produces positive difference and percent change`() {
        val result = service.run(
            currentConfig = config,
            currentPremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(baseSalary = BigDecimal("30000")),
            calendar = jan,
            previousMonthCalendar = null,
        )

        assertEquals(true, result.difference.signum() > 0)
        assertEquals(true, result.scenarioTotalPayout > result.currentTotalPayout)
        assertEquals(result.difference, result.scenarioTotalPayout.subtract(result.currentTotalPayout))
        assertEquals(true, result.percentChange != null && result.percentChange!!.signum() > 0)
    }

    @Test
    fun `identical scenario yields zero difference and zero percent change`() {
        val result = service.run(
            currentConfig = config,
            currentPremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(),
            calendar = jan,
            previousMonthCalendar = null,
        )
        // BigDecimal.equals() учитывает scale ("0.00" != 0 при равном числовом значении) —
        // сравниваем через compareTo, а не assertEquals(BigDecimal.ZERO, ...).
        assertEquals(0, result.difference.compareTo(BigDecimal.ZERO))
        assertEquals(BigDecimal("0.0000"), result.percentChange)
    }

    @Test
    fun `original configuration is not mutated by running a scenario`() {
        val originalBaseSalary = config.salaryParameters.baseSalary
        service.run(
            currentConfig = config,
            currentPremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(
                baseSalary = BigDecimal("999999"),
                allowanceRateOverrides = mapOf(AllowanceType.MONTHLY_BONUS to BigDecimal("5")),
            ),
            calendar = jan,
            previousMonthCalendar = null,
        )
        assertEquals(originalBaseSalary, config.salaryParameters.baseSalary)
    }

    @Test
    fun `percent change is null when current total payout is zero`() {
        val zeroConfig = config.copy(
            salaryParameters = config.salaryParameters.copy(baseSalary = BigDecimal.ZERO, rankSalary = BigDecimal.ZERO),
            allowances = emptyList(),
        )
        val result = service.run(
            currentConfig = zeroConfig,
            currentPremium = Premium.none(jan.period),
            overrides = ScenarioOverrides(premiumOverride = BigDecimal("1000")),
            calendar = jan,
            previousMonthCalendar = null,
        )
        assertNull(result.percentChange)
    }
}
