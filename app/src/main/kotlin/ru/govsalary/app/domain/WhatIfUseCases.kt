package ru.govsalary.app.domain

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.config.TaxParameters
import ru.govsalary.calculation.model.Allowance
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.SalaryParameters
import ru.govsalary.calculation.model.WorkCalendar
import ru.govsalary.calculation.whatif.ScenarioOverrides
import ru.govsalary.data.domain.WhatIfResult
import ru.govsalary.data.domain.WhatIfService
import java.math.BigDecimal

/**
 * What-If (мастер-промпт Фазы 4, п.18). Сценарий существует только в памяти этого вызова —
 * ни один параметр не проходит через Repository/Room (см. WhatIfService.kt, Фаза 3: класс
 * физически не имеет ссылки ни на один Repository).
 */
class WhatIfUseCases(private val service: WhatIfService) {

    fun run(current: CalculatorInput, scenarioOverrides: ScenarioOverrides): WhatIfResult {
        val config = CalculationConfiguration(
            configVersionId = "${current.year}-whatif-base",
            year = current.year,
            effectiveFrom = java.time.LocalDate.of(current.year, 1, 1),
            salaryParameters = SalaryParameters(current.baseSalary, current.rankSalary),
            allowances = listOf(
                Allowance(AllowanceType.MONTHLY_BONUS, current.monthlyBonusRate),
                Allowance(AllowanceType.STATE_SECRET, current.stateSecretRate),
                Allowance(AllowanceType.SPECIAL_CONDITIONS, current.specialConditionsRate),
                Allowance(AllowanceType.SENIORITY, current.seniorityRate),
            ),
            taxParameters = TaxParameters(netMultiplier = BigDecimal("0.87")),
        )
        val calendar = WorkCalendar(
            period = CalculationPeriod(current.year, current.month),
            totalWorkingDays = current.totalWorkingDays,
            workingDaysUpTo15 = current.workingDaysUpTo15,
        )
        val previousCalendar = if (current.previousMonthTotalWorkingDays != null && current.previousMonthWorkingDaysUpTo15 != null) {
            WorkCalendar(
                period = CalculationPeriod(current.year, current.month).previous(),
                totalWorkingDays = current.previousMonthTotalWorkingDays,
                workingDaysUpTo15 = current.previousMonthWorkingDaysUpTo15,
            )
        } else null

        return service.run(
            currentConfig = config,
            currentPremium = Premium(CalculationPeriod(current.year, current.month), current.premium),
            overrides = scenarioOverrides,
            calendar = calendar,
            previousMonthCalendar = previousCalendar,
        )
    }
}
