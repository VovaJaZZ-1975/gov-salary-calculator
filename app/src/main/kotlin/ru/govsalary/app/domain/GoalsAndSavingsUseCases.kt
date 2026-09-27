package ru.govsalary.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import ru.govsalary.data.domain.ForecastService
import ru.govsalary.data.domain.GoalProgress
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.math.BigDecimal

/** Финансовые цели, накопления (в т.ч. SavingsPeriod) и прогноз — мастер-промпт Фазы 4, п.16-17. */
class GoalsAndSavingsUseCases(
    private val goalRepository: FinancialGoalRepository,
    private val savingsRepository: SavingsPlanRepository,
    private val forecastService: ForecastService,
) {
    // --- Цели ---
    fun observeGoals(): Flow<List<FinancialGoalEntity>> = goalRepository.observeAll()
    fun observeActiveGoals(): Flow<List<FinancialGoalEntity>> = goalRepository.observeActive()
    suspend fun createGoal(entity: FinancialGoalEntity): Long = goalRepository.create(entity)
    suspend fun updateGoal(entity: FinancialGoalEntity) = goalRepository.update(entity)
    suspend fun deleteGoal(entity: FinancialGoalEntity) = goalRepository.delete(entity)
    suspend fun addContribution(goalId: Long, amount: BigDecimal, currentAmount: BigDecimal) =
        goalRepository.addContribution(goalId, amount, currentAmount)

    // --- Накопления ---
    fun observeActivePlans(): Flow<List<SavingsPlanEntity>> = savingsRepository.observeActivePlans()
    fun observePlansForGoal(goalId: Long): Flow<List<SavingsPlanEntity>> = savingsRepository.observePlansForGoal(goalId)
    suspend fun createPlan(entity: SavingsPlanEntity): Long = savingsRepository.createPlan(entity)
    fun observePeriods(planId: Long): Flow<List<SavingsPeriodEntity>> = savingsRepository.observePeriods(planId)
    suspend fun addPeriod(entity: SavingsPeriodEntity) = savingsRepository.addPeriod(entity)

    // --- Прогноз (реактивно пересчитывается при изменении зарплаты/планов — Фаза 3, п.6) ---
    fun observeGoalProgress(goalId: Int, year: Int, month: Int): Flow<GoalProgress?> =
        forecastService.observeGoalProgress(goalId, year, month)

    /**
     * Разовая (не реактивная) оценка суммы накопления за месяц по ВСЕМ активным планам —
     * используется экранами История/Аналитика/What-If (мастер-промпт Фазы 4, п.15, 18) для
     * отображения "Накопления" за произвольный месяц без подписки на постоянный Flow.
     * Считает через тот же ru.govsalary.data.domain.SavingsCalculator, что и ForecastService —
     * формула не дублируется.
     */
    suspend fun estimateMonthlySaving(
        periodDate: java.time.LocalDate,
        monthlySalaryTotal: BigDecimal,
        monthlyPremium: BigDecimal,
    ): BigDecimal {
        val plans = savingsRepository.observeActivePlans().first()
        val periodsByPlan = plans.associate { it.id to savingsRepository.getPeriods(it.id) }
        return ru.govsalary.data.domain.SavingsCalculator.calculateTotalMonthlySaving(
            plans = plans,
            periodsByPlan = periodsByPlan,
            periodDate = periodDate,
            monthlySalaryTotal = monthlySalaryTotal,
            monthlyPremium = monthlyPremium,
        )
    }
}
