package ru.govsalary.data.domain

import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.SavingsType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Вычисляет сумму накопления за месяц по одному плану (мастер-промпт Фазы 3, п.2).
 * Чистая функция — не обращается к БД, принимает уже загруженные SavingsPeriodEntity.
 */
object SavingsCalculator {

    /**
     * @param plan план накопления
     * @param periods периоды плана (SavingsPeriodEntity.planId == plan.id), может быть пустым
     * @param periodDate любая дата внутри расчётного месяца (обычно 1-е число)
     * @param monthlySalaryTotal totalPayout за месяц (нужен для SALARY_PERCENT/REMAINING_AMOUNT)
     * @param monthlyPremium премия за месяц (нужна для PREMIUM_PERCENT)
     * @param mandatoryExpenses обязательные расходы за месяц (нужны для REMAINING_AMOUNT)
     */
    fun calculateMonthlySaving(
        plan: SavingsPlanEntity,
        periods: List<SavingsPeriodEntity>,
        periodDate: LocalDate,
        monthlySalaryTotal: BigDecimal,
        monthlyPremium: BigDecimal,
        mandatoryExpenses: BigDecimal = BigDecimal.ZERO,
    ): BigDecimal {
        if (!plan.isActive) return BigDecimal.ZERO
        if (periodDate.isBefore(plan.startPeriod)) return BigDecimal.ZERO
        plan.endPeriod?.let { if (periodDate.isAfter(it)) return BigDecimal.ZERO }

        return when (plan.type) {
            SavingsType.FIXED_AMOUNT -> {
                val override = periods.firstOrNull { it.contains(periodDate) }
                override?.amount ?: plan.amount ?: BigDecimal.ZERO
            }

            SavingsType.SALARY_PERCENT -> {
                val percent = plan.percent ?: BigDecimal.ZERO
                monthlySalaryTotal.multiply(percent)
            }

            SavingsType.PREMIUM_PERCENT -> {
                val percent = plan.percent ?: BigDecimal.ZERO
                monthlyPremium.multiply(percent)
            }

            SavingsType.REMAINING_AMOUNT -> {
                val remaining = monthlySalaryTotal.subtract(mandatoryExpenses)
                if (remaining.signum() > 0) remaining else BigDecimal.ZERO
            }
        }
    }

    /** Сумма накопления за месяц по НЕСКОЛЬКИМ активным планам, отсортированным по приоритету. */
    fun calculateTotalMonthlySaving(
        plans: List<SavingsPlanEntity>,
        periodsByPlan: Map<Long, List<SavingsPeriodEntity>>,
        periodDate: LocalDate,
        monthlySalaryTotal: BigDecimal,
        monthlyPremium: BigDecimal,
        mandatoryExpenses: BigDecimal = BigDecimal.ZERO,
    ): BigDecimal =
        plans.filter { it.isActive }
            .sortedBy { it.priority }
            .fold(BigDecimal.ZERO) { acc, plan ->
                acc.add(
                    calculateMonthlySaving(
                        plan = plan,
                        periods = periodsByPlan[plan.id].orEmpty(),
                        periodDate = periodDate,
                        monthlySalaryTotal = monthlySalaryTotal,
                        monthlyPremium = monthlyPremium,
                        mandatoryExpenses = mandatoryExpenses,
                    )
                )
            }
}
