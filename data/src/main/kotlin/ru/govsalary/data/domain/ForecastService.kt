package ru.govsalary.data.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Прогресс и прогноз по одной финансовой цели (мастер-промпт Фазы 3, п.5-6).
 *
 * Прогноз — это НОВАЯ функциональность приложения, которой нет в исходном Excel-файле
 * (в нём вообще нет понятия "цель" или "прогноз") — поэтому здесь не действует запрет
 * "не додумывай Excel-формулы" (мастер-промпт Фазы 1, п.3): формула прогноза — явное
 * архитектурное решение этого модуля, а не домысленное правило исходной модели.
 *
 * Допущение (должно быть явным для пользователя в UI Фазы 4): прогноз строится в
 * предположении, что projectedMonthlySaving остаётся постоянной суммой каждый месяц,
 * начиная с сегодняшнего дня — т.е. это НЕ учитывает будущую индексацию оклада, различия
 * дневной ставки по месяцам и т.п. Это простейшая линейная модель.
 */
data class GoalProgress(
    val goalId: Long,
    val goalTitle: String,
    val targetAmount: BigDecimal,
    val currentAmount: BigDecimal,
    val progressPercent: BigDecimal,
    val projectedMonthlySaving: BigDecimal,
    /** null, если прогноз невозможен (цель уже достигнута или накопление за месяц <= 0). */
    val forecastDate: LocalDate?,
)

class ForecastService(
    private val salaryHistoryRepository: SalaryHistoryRepository,
    private val financialGoalRepository: FinancialGoalRepository,
    private val savingsPlanRepository: SavingsPlanRepository,
    private val clock: () -> LocalDate = { LocalDate.now() },
) {

    /**
     * Реактивный пайплайн Salary -> SavingsPlan -> MonthlySaving -> GoalProgress -> ForecastDate.
     * Использует Flow.combine — при изменении зарплаты (новая запись в SalaryHistory), плана
     * накопления или самой цели пересчёт происходит автоматически, без ручного триггера
     * (мастер-промпт Фазы 3, п.6: "Прогноз должен автоматически пересчитываться").
     */
    fun observeGoalProgress(goalId: Int, year: Int, month: Int): Flow<GoalProgress?> {
        val goalFlow = financialGoalRepository.observeById(goalId.toLong())
        val plansFlow = savingsPlanRepository.observePlansForGoal(goalId.toLong())
        val salaryFlow = salaryHistoryRepository.observeMonthComparison(year, month)

        return combine(goalFlow, plansFlow, salaryFlow) { goal, plans, comparison ->
            if (goal == null) return@combine null
            // Приоритет: ACTUAL (факт) важнее CALCULATED для прогноза, если он уже есть за этот месяц.
            val salary = comparison.actual ?: comparison.calculated ?: return@combine noSalaryDataProgress(goal)

            val periodDate = LocalDate.of(year, month, 1)
            // combine{} выполняется в suspend-контексте, поэтому можно безопасно подгрузить
            // периоды каждого плана напрямую из репозитория (без дополнительного Flow.combine).
            val periodsByPlan = plans.associate { it.id to savingsPlanRepository.getPeriods(it.id) }
            val monthlySaving = SavingsCalculator.calculateTotalMonthlySaving(
                plans = plans,
                periodsByPlan = periodsByPlan,
                periodDate = periodDate,
                monthlySalaryTotal = salary.totalPayout,
                monthlyPremium = salary.premium,
            )

            buildProgress(goal, monthlySaving)
        }
    }

    private fun noSalaryDataProgress(goal: FinancialGoalEntity): GoalProgress =
        buildProgress(goal, BigDecimal.ZERO)

    private fun buildProgress(goal: FinancialGoalEntity, projectedMonthlySaving: BigDecimal): GoalProgress {
        val progressPercent = if (goal.targetAmount.signum() > 0) {
            goal.currentAmount
                .multiply(BigDecimal(100))
                .divide(goal.targetAmount, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val remaining = goal.targetAmount.subtract(goal.currentAmount)
        val forecastDate = when {
            remaining.signum() <= 0 -> null // цель уже достигнута
            projectedMonthlySaving.signum() <= 0 -> null // накопление отсутствует — прогноз невозможен
            else -> {
                val monthsNeeded = remaining
                    .divide(projectedMonthlySaving, 0, RoundingMode.CEILING)
                    .toInt()
                clock().plusMonths(monthsNeeded.toLong())
            }
        }

        return GoalProgress(
            goalId = goal.id,
            goalTitle = goal.title,
            targetAmount = goal.targetAmount,
            currentAmount = goal.currentAmount,
            progressPercent = progressPercent,
            projectedMonthlySaving = projectedMonthlySaving,
            forecastDate = forecastDate,
        )
    }
}
