package ru.govsalary.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.govsalary.data.domain.SavingsCalculator
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.SavingsType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Мастер-промпт Фазы 3, п.12: тесты Savings. Покрывает все 4 типа накопления (п.2) и
 * пример из промпта (четыре периода с разными суммами в течение года).
 */
class SavingsCalculatorTest {

    private fun plan(
        type: SavingsType,
        amount: BigDecimal? = null,
        percent: BigDecimal? = null,
        start: LocalDate = LocalDate.of(2026, 1, 1),
        end: LocalDate? = null,
        active: Boolean = true,
    ) = SavingsPlanEntity(
        id = 1, name = "test", goalId = null, type = type, amount = amount, percent = percent,
        startPeriod = start, endPeriod = end, priority = 0, isActive = active,
    )

    @Test
    fun `FIXED_AMOUNT uses plan default when no period matches`() {
        val result = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.FIXED_AMOUNT, amount = BigDecimal("5000")),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 5, 1),
            monthlySalaryTotal = BigDecimal("100000"),
            monthlyPremium = BigDecimal.ZERO,
        )
        assertEquals(BigDecimal("5000"), result)
    }

    @Test
    fun `FIXED_AMOUNT with four non-overlapping periods matches master prompt example`() {
        val savingsPlan = plan(SavingsType.FIXED_AMOUNT, amount = BigDecimal("0"))
        val periods = listOf(
            SavingsPeriodEntity(1, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), BigDecimal("10000")),
            SavingsPeriodEntity(2, 1, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), BigDecimal("20000")),
            SavingsPeriodEntity(3, 1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), BigDecimal("50000")),
            SavingsPeriodEntity(4, 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), BigDecimal("15000")),
        )
        val expectations = mapOf(
            LocalDate.of(2026, 2, 1) to "10000",
            LocalDate.of(2026, 5, 1) to "20000",
            LocalDate.of(2026, 7, 1) to "50000",
            LocalDate.of(2026, 11, 1) to "15000",
        )
        expectations.forEach { (date, expected) ->
            val result = SavingsCalculator.calculateMonthlySaving(
                savingsPlan, periods, date, BigDecimal("100000"), BigDecimal.ZERO,
            )
            assertEquals(BigDecimal(expected), result, "период $date")
        }
    }

    @Test
    fun `SALARY_PERCENT multiplies total payout by percent`() {
        val result = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.SALARY_PERCENT, percent = BigDecimal("0.1")),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 3, 1),
            monthlySalaryTotal = BigDecimal("97806.90"),
            monthlyPremium = BigDecimal("23997"),
        )
        assertEquals(BigDecimal("9780.690"), result)
    }

    @Test
    fun `PREMIUM_PERCENT multiplies premium by percent, not salary`() {
        val result = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.PREMIUM_PERCENT, percent = BigDecimal("0.5")),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 8, 1),
            monthlySalaryTotal = BigDecimal("125643.56"),
            monthlyPremium = BigDecimal("44007"),
        )
        assertEquals(BigDecimal("22003.5"), result)
    }

    @Test
    fun `REMAINING_AMOUNT subtracts mandatory expenses and floors at zero`() {
        val withRemainder = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.REMAINING_AMOUNT),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 4, 1),
            monthlySalaryTotal = BigDecimal("111646.19"),
            monthlyPremium = BigDecimal.ZERO,
            mandatoryExpenses = BigDecimal("80000"),
        )
        assertEquals(BigDecimal("31646.19"), withRemainder)

        val negative = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.REMAINING_AMOUNT),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 4, 1),
            monthlySalaryTotal = BigDecimal("50000"),
            monthlyPremium = BigDecimal.ZERO,
            mandatoryExpenses = BigDecimal("80000"),
        )
        assertEquals(BigDecimal.ZERO, negative)
    }

    @Test
    fun `inactive plan always yields zero`() {
        val result = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.FIXED_AMOUNT, amount = BigDecimal("5000"), active = false),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 5, 1),
            monthlySalaryTotal = BigDecimal("100000"),
            monthlyPremium = BigDecimal.ZERO,
        )
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `plan outside its start-end range yields zero`() {
        val result = SavingsCalculator.calculateMonthlySaving(
            plan = plan(SavingsType.FIXED_AMOUNT, amount = BigDecimal("5000"), start = LocalDate.of(2027, 1, 1)),
            periods = emptyList(),
            periodDate = LocalDate.of(2026, 12, 1),
            monthlySalaryTotal = BigDecimal("100000"),
            monthlyPremium = BigDecimal.ZERO,
        )
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `total monthly saving sums multiple active plans by priority`() {
        val plan1 = plan(SavingsType.FIXED_AMOUNT, amount = BigDecimal("10000")).copy(id = 1, priority = 0)
        val plan2 = plan(SavingsType.SALARY_PERCENT, percent = BigDecimal("0.05")).copy(id = 2, priority = 1)
        val plan3Inactive = plan(SavingsType.FIXED_AMOUNT, amount = BigDecimal("99999"), active = false).copy(id = 3)

        val total = SavingsCalculator.calculateTotalMonthlySaving(
            plans = listOf(plan1, plan2, plan3Inactive),
            periodsByPlan = emptyMap(),
            periodDate = LocalDate.of(2026, 6, 1),
            monthlySalaryTotal = BigDecimal("106008.00"),
            monthlyPremium = BigDecimal("23997"),
        )
        // 10000 + 106008.00*0.05 = 10000 + 5300.40 = 15300.40
        assertEquals(BigDecimal("15300.4000"), total)
    }
}
