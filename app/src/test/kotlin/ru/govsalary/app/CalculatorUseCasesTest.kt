package ru.govsalary.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.govsalary.app.domain.CalculatorInput
import ru.govsalary.app.domain.CalculatorUseCases
import ru.govsalary.app.domain.GoalsAndSavingsUseCases
import ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine
import ru.govsalary.data.domain.ForecastService
import ru.govsalary.data.local.db.dao.FinancialGoalDao
import ru.govsalary.data.local.db.dao.SalaryHistoryDao
import ru.govsalary.data.local.db.dao.SavingsPeriodDao
import ru.govsalary.data.local.db.dao.SavingsPlanDao
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.SavingsType
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Мастер-промпт Фазы 4, п.27 (Unit tests: "Calculation Engine", "Savings", "Forecast" — на этот
 * раз с точки зрения app-слоя UseCase, а не самого движка/domain-сервиса, которые уже покрыты
 * тестами Фазы 2 и Фазы 3 — здесь проверяется именно провод UI -> UseCase -> Engine/Repository).
 *
 * Фейки DAO объявлены прямо в этом файле (минимальные, только под нужды теста), а не переиспользуют
 * data/src/test/.../fakes/FakeDaos.kt — Gradle test-исходники одного модуля не видны другому модулю
 * без отдельно настроенных test fixtures, которые не добавлялись в этой фазе, чтобы не усложнять
 * без необходимости.
 */
class CalculatorUseCasesTest {

    private class NoOpSalaryHistoryDao : SalaryHistoryDao {
        val saved = mutableListOf<SalaryHistoryEntity>()
        override suspend fun upsert(entity: SalaryHistoryEntity) { saved += entity }
        override suspend fun upsertAll(entities: List<SalaryHistoryEntity>) { saved += entities }
        override fun observeYear(year: Int) = MutableStateFlow(saved.filter { it.year == year }).map { it }
        override fun observeMonth(year: Int, month: Int) = MutableStateFlow(saved.filter { it.year == year && it.month == month }).map { it }
        override suspend fun get(year: Int, month: Int, source: ValueSource) = saved.firstOrNull { it.year == year && it.month == month && it.source == source }
        override fun observeAvailableYears() = MutableStateFlow(saved.map { it.year }.distinct()).map { it }
        override suspend fun getAll() = saved.toList()
        override fun observeRange(fromYear: Int, toYear: Int) = MutableStateFlow(saved.filter { it.year in fromYear..toYear }).map { it }
        override suspend fun delete(year: Int, month: Int, source: ValueSource) { saved.removeAll { it.year == year && it.month == month && it.source == source } }
        override suspend fun deleteAll() { saved.clear() }
    }

    @Test
    fun `real-time calculation matches Excel golden value for January 2026 (test-cases md TC004)`() {
        val useCases = CalculatorUseCases(DefaultSalaryCalculationEngine(), SalaryHistoryRepository(NoOpSalaryHistoryDao()))
        val input = CalculatorInput(
            year = 2026, month = 1, baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"),
            monthlyBonusRate = BigDecimal("0.9"), seniorityRate = BigDecimal("0.1"), specialConditionsRate = BigDecimal("1.2"),
            stateSecretRate = BigDecimal("0.1"), premium = BigDecimal.ZERO, totalWorkingDays = 15, workingDaysUpTo15 = 4,
            actualWorkedDaysUpTo15 = 4, actualWorkedDaysAfter15 = 11,
            previousMonthTotalWorkingDays = null, previousMonthWorkingDaysUpTo15 = null, previousMonthActualWorkedDaysAfter15 = null,
        )
        val result = useCases.calculate(input)
        assertEquals(BigDecimal("21814.92"), result.totalPayout) // docs/test-cases.md, Test Case 004/007
    }

    @Test
    fun `saveAsCalculated persists result through the repository with source CALCULATED`() = runTest {
        val dao = NoOpSalaryHistoryDao()
        val useCases = CalculatorUseCases(DefaultSalaryCalculationEngine(), SalaryHistoryRepository(dao))
        val input = CalculatorInput(
            year = 2026, month = 2, baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"),
            monthlyBonusRate = BigDecimal("0.9"), seniorityRate = BigDecimal("0.1"), specialConditionsRate = BigDecimal("1.2"),
            stateSecretRate = BigDecimal("0.1"), premium = BigDecimal("23997"), totalWorkingDays = 19, workingDaysUpTo15 = 10,
            actualWorkedDaysUpTo15 = 10, actualWorkedDaysAfter15 = 9,
            previousMonthTotalWorkingDays = 15, previousMonthWorkingDaysUpTo15 = 4, previousMonthActualWorkedDaysAfter15 = 11,
        )
        val result = useCases.calculate(input)
        useCases.saveAsCalculated(input, result)

        assertEquals(1, dao.saved.size)
        assertEquals(ValueSource.CALCULATED, dao.saved.single().source)
        assertEquals(BigDecimal("127043.83"), dao.saved.single().totalPayout) // docs/test-cases.md, Test Case 006
    }
}

/** Мастер-промпт Фазы 4, п.27: тест "Savings"/"Forecast" на уровне app-слоя (What-If использует estimateMonthlySaving). */
class GoalsAndSavingsUseCasesEstimateTest {

    private class InMemorySavingsPlanDao : SavingsPlanDao {
        val plans = mutableListOf<SavingsPlanEntity>()
        override suspend fun insert(entity: SavingsPlanEntity): Long { plans += entity.copy(id = plans.size + 1L); return plans.last().id }
        override suspend fun update(entity: SavingsPlanEntity) {}
        override suspend fun delete(entity: SavingsPlanEntity) {}
        override fun observeActive() = MutableStateFlow(plans.filter { it.isActive }).map { it }
        override fun observeAll() = MutableStateFlow(plans.toList()).map { it }
        override fun observeByGoal(goalId: Long) = MutableStateFlow(plans.filter { it.goalId == goalId }).map { it }
    }

    private class EmptySavingsPeriodDao : SavingsPeriodDao {
        override suspend fun insert(entity: SavingsPeriodEntity) = 1L
        override suspend fun insertAll(entities: List<SavingsPeriodEntity>) = emptyList<Long>()
        override suspend fun delete(entity: SavingsPeriodEntity) {}
        override fun observeByPlan(planId: Long): Flow<List<SavingsPeriodEntity>> = MutableStateFlow(emptyList())
        override suspend fun getByPlan(planId: Long) = emptyList<SavingsPeriodEntity>()
    }

    @Test
    fun `estimateMonthlySaving sums active plans using the same formulas as the data layer`() = runTest {
        val planDao = InMemorySavingsPlanDao()
        planDao.plans += SavingsPlanEntity(
            id = 1, name = "10%", goalId = null, type = SavingsType.SALARY_PERCENT, amount = null,
            percent = BigDecimal("0.1"), startPeriod = LocalDate.of(2026, 1, 1), endPeriod = null, priority = 0, isActive = true,
        )
        val savingsRepo = SavingsPlanRepository(planDao, EmptySavingsPeriodDao())
        val goalRepo = FinancialGoalRepository(NoOpFinancialGoalDao())
        val useCases = GoalsAndSavingsUseCases(goalRepo, savingsRepo, ForecastService(SalaryHistoryRepository(NoOpSalaryHistoryDaoStub()), goalRepo, savingsRepo))

        val result = useCases.estimateMonthlySaving(LocalDate.of(2026, 3, 1), BigDecimal("97806.90"), BigDecimal("23997"))
        assertEquals(BigDecimal("9780.690"), result) // 10% от 97806.90, см. data/src/test SavingsCalculatorTest
    }

    private class NoOpFinancialGoalDao : FinancialGoalDao {
        override suspend fun insert(entity: FinancialGoalEntity) = 1L
        override suspend fun update(entity: FinancialGoalEntity) {}
        override suspend fun delete(entity: FinancialGoalEntity) {}
        override fun observeAll() = MutableStateFlow(emptyList<FinancialGoalEntity>()).map { it }
        override fun observeByStatus(status: GoalStatus) = MutableStateFlow(emptyList<FinancialGoalEntity>()).map { it }
        override fun observeById(id: Long) = MutableStateFlow<FinancialGoalEntity?>(null).map { it }
        override suspend fun updateCurrentAmount(id: Long, amount: BigDecimal) {}
    }

    private class NoOpSalaryHistoryDaoStub : SalaryHistoryDao {
        override suspend fun upsert(entity: SalaryHistoryEntity) {}
        override suspend fun upsertAll(entities: List<SalaryHistoryEntity>) {}
        override fun observeYear(year: Int) = MutableStateFlow(emptyList<SalaryHistoryEntity>()).map { it }
        override fun observeMonth(year: Int, month: Int) = MutableStateFlow(emptyList<SalaryHistoryEntity>()).map { it }
        override suspend fun get(year: Int, month: Int, source: ValueSource): SalaryHistoryEntity? = null
        override fun observeAvailableYears() = MutableStateFlow(emptyList<Int>()).map { it }
        override suspend fun getAll() = emptyList<SalaryHistoryEntity>()
        override fun observeRange(fromYear: Int, toYear: Int) = MutableStateFlow(emptyList<SalaryHistoryEntity>()).map { it }
        override suspend fun delete(year: Int, month: Int, source: ValueSource) {}
        override suspend fun deleteAll() {}
    }
}
