package ru.govsalary.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.govsalary.data.domain.ForecastService
import ru.govsalary.data.fakes.FakeFinancialGoalDao
import ru.govsalary.data.fakes.FakeSalaryHistoryDao
import ru.govsalary.data.fakes.FakeSavingsPeriodDao
import ru.govsalary.data.fakes.FakeSavingsPlanDao
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.SavingsType
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Мастер-промпт Фазы 3, п.12 "Forecast". Пример из п.5:
 * Отпуск, цель 300 000 ₽, накоплено 180 000 ₽, прогресс 60%.
 */
class ForecastServiceTest {

    @Test
    fun `progress percent matches master prompt example (60 percent)`() = runTest {
        val salaryRepo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        val goalRepo = FinancialGoalRepository(FakeFinancialGoalDao())
        val savingsRepo = SavingsPlanRepository(FakeSavingsPlanDao(), FakeSavingsPeriodDao())
        val service = ForecastService(salaryRepo, goalRepo, savingsRepo, clock = { LocalDate.of(2026, 3, 1) })

        val goalId = goalRepo.create(
            FinancialGoalEntity(
                title = "Отпуск", description = null, targetAmount = BigDecimal("300000"),
                currentAmount = BigDecimal("180000"), createdAt = LocalDate.of(2026, 1, 1),
                targetDate = null, status = GoalStatus.ACTIVE,
            )
        )
        salaryRepo.saveCalculated(
            SalaryHistoryEntity(
                year = 2026, month = 3, source = ValueSource.CALCULATED, configVersionId = "2026-baseline-v1",
                baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"), allowancesJson = "{}",
                accrued = BigDecimal("94029.8"), ndfl = BigDecimal("12223.874"), premium = BigDecimal("23997"),
                totalPayout = BigDecimal("97806.90"), midMonthPaymentDate = null, midMonthPaymentAmount = null,
                startOfMonthPaymentDate = null, startOfMonthPaymentAmount = null, recordedAt = Instant.EPOCH,
            )
        )

        val progress = service.observeGoalProgress(goalId.toInt(), 2026, 3).first()
        assertNotNull(progress)
        assertEquals(BigDecimal("60.00"), progress!!.progressPercent)
    }

    @Test
    fun `forecast date is null when goal already achieved`() = runTest {
        val salaryRepo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        val goalRepo = FinancialGoalRepository(FakeFinancialGoalDao())
        val savingsRepo = SavingsPlanRepository(FakeSavingsPlanDao(), FakeSavingsPeriodDao())
        val service = ForecastService(salaryRepo, goalRepo, savingsRepo, clock = { LocalDate.of(2026, 3, 1) })

        val goalId = goalRepo.create(
            FinancialGoalEntity(
                title = "Достигнуто", description = null, targetAmount = BigDecimal("100000"),
                currentAmount = BigDecimal("100000"), createdAt = LocalDate.of(2026, 1, 1),
                targetDate = null, status = GoalStatus.ACHIEVED,
            )
        )
        val progress = service.observeGoalProgress(goalId.toInt(), 2026, 3).first()
        assertNull(progress!!.forecastDate)
    }

    @Test
    fun `forecast date is null when there is no active saving plan (zero monthly saving)`() = runTest {
        val salaryRepo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        val goalRepo = FinancialGoalRepository(FakeFinancialGoalDao())
        val savingsRepo = SavingsPlanRepository(FakeSavingsPlanDao(), FakeSavingsPeriodDao())
        val service = ForecastService(salaryRepo, goalRepo, savingsRepo, clock = { LocalDate.of(2026, 3, 1) })

        val goalId = goalRepo.create(
            FinancialGoalEntity(
                title = "Без плана", description = null, targetAmount = BigDecimal("300000"),
                currentAmount = BigDecimal("180000"), createdAt = LocalDate.of(2026, 1, 1),
                targetDate = null, status = GoalStatus.ACTIVE,
            )
        )
        val progress = service.observeGoalProgress(goalId.toInt(), 2026, 3).first()
        assertNull(progress!!.forecastDate)
    }

    @Test
    fun `forecast recalculates automatically when a new fixed-amount plan is added for the goal`() = runTest {
        val salaryRepo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        val goalRepo = FinancialGoalRepository(FakeFinancialGoalDao())
        val savingsRepo = SavingsPlanRepository(FakeSavingsPlanDao(), FakeSavingsPeriodDao())
        val service = ForecastService(salaryRepo, goalRepo, savingsRepo, clock = { LocalDate.of(2026, 1, 1) })

        val goalId = goalRepo.create(
            FinancialGoalEntity(
                title = "Отпуск", description = null, targetAmount = BigDecimal("60000"),
                currentAmount = BigDecimal("0"), createdAt = LocalDate.of(2026, 1, 1),
                targetDate = null, status = GoalStatus.ACTIVE,
            )
        )
        salaryRepo.saveCalculated(
            SalaryHistoryEntity(
                year = 2026, month = 1, source = ValueSource.CALCULATED, configVersionId = "2026-baseline-v1",
                baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"), allowancesJson = "{}",
                accrued = BigDecimal("94029.8"), ndfl = BigDecimal("12223.874"), premium = BigDecimal.ZERO,
                totalPayout = BigDecimal("21814.92"), midMonthPaymentDate = null, midMonthPaymentAmount = null,
                startOfMonthPaymentDate = null, startOfMonthPaymentAmount = null, recordedAt = Instant.EPOCH,
            )
        )

        savingsRepo.createPlan(
            SavingsPlanEntity(
                name = "Фикс. 10000/мес", goalId = goalId, type = SavingsType.FIXED_AMOUNT,
                amount = BigDecimal("10000"), percent = null, startPeriod = LocalDate.of(2026, 1, 1),
                endPeriod = null, priority = 0, isActive = true,
            )
        )

        val progress = service.observeGoalProgress(goalId.toInt(), 2026, 1).first()
        // 60000 / 10000 = 6 месяцев от 2026-01-01
        assertEquals(LocalDate.of(2026, 7, 1), progress!!.forecastDate)
        assertEquals(BigDecimal("10000"), progress.projectedMonthlySaving)
    }
}
