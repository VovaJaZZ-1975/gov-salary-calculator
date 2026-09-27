package ru.govsalary.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.govsalary.data.fakes.FakeSalaryHistoryDao
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.SalaryHistoryRepository
import java.math.BigDecimal
import java.time.Instant

/** Мастер-промпт Фазы 3, п.12 "Repository" + п.4 "Actual vs Calculated не должны смешиваться". */
class SalaryHistoryRepositoryTest {

    private fun entity(year: Int, month: Int, source: ValueSource, total: String) = SalaryHistoryEntity(
        year = year, month = month, source = source, configVersionId = "2026-baseline-v1",
        baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"), allowancesJson = "{}",
        accrued = BigDecimal("94029.8"), ndfl = BigDecimal("12223.874"), premium = BigDecimal("23997"),
        totalPayout = BigDecimal(total), midMonthPaymentDate = null, midMonthPaymentAmount = null,
        startOfMonthPaymentDate = null, startOfMonthPaymentAmount = null, recordedAt = Instant.EPOCH,
    )

    @Test
    fun `calculated and actual values for the same period coexist without overwriting each other`() = runTest {
        val repo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        repo.saveCalculated(entity(2026, 2, ValueSource.CALCULATED, "127043.83"))
        repo.saveActual(entity(2026, 2, ValueSource.ACTUAL, "127000.00"))

        val comparison = repo.observeMonthComparison(2026, 2).first()
        assertEquals(BigDecimal("127043.83"), comparison.calculated?.totalPayout)
        assertEquals(BigDecimal("127000.00"), comparison.actual?.totalPayout)
        assertEquals(true, comparison.hasDiscrepancy())
    }

    @Test
    fun `saveCalculated rejects an entity with source ACTUAL`() = runTest {
        val repo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        try {
            repo.saveCalculated(entity(2026, 1, ValueSource.ACTUAL, "1"))
            assert(false) { "ожидалось IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            // ожидаемо
        }
    }

    @Test
    fun `year comparison always returns 12 months even if some are missing data`() = runTest {
        val repo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        repo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "21814.92"))

        val yearView = repo.observeYearComparison(2026).first()
        assertEquals(12, yearView.size)
        assertEquals(BigDecimal("21814.92"), yearView.first { it.month == 1 }.calculated?.totalPayout)
        assertNull(yearView.first { it.month == 2 }.calculated)
    }

    @Test
    fun `no discrepancy reported when only one source has data`() = runTest {
        val repo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        repo.saveCalculated(entity(2026, 3, ValueSource.CALCULATED, "97806.90"))
        val comparison = repo.observeMonthComparison(2026, 3).first()
        assertEquals(false, comparison.hasDiscrepancy())
    }
}
