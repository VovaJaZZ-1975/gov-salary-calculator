package ru.govsalary.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.govsalary.data.fakes.FakeFinancialGoalDao
import ru.govsalary.data.fakes.FakeSalaryHistoryDao
import ru.govsalary.data.fakes.FakeSavingsPeriodDao
import ru.govsalary.data.fakes.FakeSavingsPlanDao
import ru.govsalary.data.io.ImportExportService
import ru.govsalary.data.io.ImportStrategy
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.FinancialGoalRepository
import ru.govsalary.data.repository.SalaryHistoryRepository
import ru.govsalary.data.repository.SavingsPlanRepository
import java.math.BigDecimal
import java.time.Instant

/** Мастер-промпт Фазы 3, п.12 "Import"/"Export". Проверяет п.9: импорт не должен уничтожать данные без подтверждения. */
class ImportExportServiceTest {

    private fun entity(year: Int, month: Int, source: ValueSource, total: String) = SalaryHistoryEntity(
        year = year, month = month, source = source, configVersionId = "2026-baseline-v1",
        baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"), allowancesJson = "{}",
        accrued = BigDecimal("94029.8"), ndfl = BigDecimal("12223.874"), premium = BigDecimal("23997"),
        totalPayout = BigDecimal(total), midMonthPaymentDate = null, midMonthPaymentAmount = null,
        startOfMonthPaymentDate = null, startOfMonthPaymentAmount = null, recordedAt = Instant.EPOCH,
    )

    private fun buildService(): Pair<ImportExportService, SalaryHistoryRepository> {
        val salaryRepo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        val goalRepo = FinancialGoalRepository(FakeFinancialGoalDao())
        val savingsRepo = SavingsPlanRepository(FakeSavingsPlanDao(), FakeSavingsPeriodDao())
        return ImportExportService(salaryRepo, goalRepo, savingsRepo) to salaryRepo
    }

    @Test
    fun `JSON export then preview round-trips salary history count`() = runTest {
        val (service, salaryRepo) = buildService()
        salaryRepo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "21814.92"))
        salaryRepo.saveCalculated(entity(2026, 2, ValueSource.CALCULATED, "127043.83"))

        val json = service.exportJson(goals = emptyList(), plans = emptyList(), periods = emptyList())
        val preview = service.previewImport(json)

        assertEquals(2, preview.salaryHistoryCount)
        assertEquals(0, preview.financialGoalsCount)
    }

    @Test
    fun `preview does not modify the database`() = runTest {
        val (service, salaryRepo) = buildService()
        salaryRepo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "21814.92"))
        val json = service.exportJson(goals = emptyList(), plans = emptyList(), periods = emptyList())

        val (otherService, otherRepo) = buildService() // пустая БД
        otherService.previewImport(json)

        assertEquals(0, otherRepo.getAll().size, "previewImport не должен писать в БД до applyImport")
    }

    @Test
    fun `applyImport with KEEP_EXISTING does not overwrite existing records`() = runTest {
        val (service, salaryRepo) = buildService()
        salaryRepo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "999999.99")) // текущее значение

        val backupJson = run {
            val (exportService, exportRepo) = buildService()
            exportRepo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "21814.92")) // старое значение из бэкапа
            exportService.exportJson(goals = emptyList(), plans = emptyList(), periods = emptyList())
        }

        val preview = service.previewImport(backupJson)
        assertTrue(preview.hasPotentialSalaryHistoryConflicts)
        service.applyImport(preview, ImportStrategy.KEEP_EXISTING)

        val result = salaryRepo.getAll().single()
        assertEquals(BigDecimal("999999.99"), result.totalPayout, "KEEP_EXISTING не должен перезаписывать текущее значение")
    }

    @Test
    fun `applyImport with OVERWRITE replaces existing records`() = runTest {
        val (service, salaryRepo) = buildService()
        salaryRepo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "999999.99"))

        val backupJson = run {
            val (exportService, exportRepo) = buildService()
            exportRepo.saveCalculated(entity(2026, 1, ValueSource.CALCULATED, "21814.92"))
            exportService.exportJson(goals = emptyList(), plans = emptyList(), periods = emptyList())
        }

        val preview = service.previewImport(backupJson)
        service.applyImport(preview, ImportStrategy.OVERWRITE)

        val result = salaryRepo.getAll().single()
        assertEquals(BigDecimal("21814.92"), result.totalPayout)
    }

    @Test
    fun `CSV export contains header and one row per record separated by semicolons`() {
        val (service, _) = buildService()
        val csv = service.exportCsv(
            listOf(
                entity(2026, 1, ValueSource.CALCULATED, "21814.92"),
                entity(2026, 2, ValueSource.ACTUAL, "127000.00"),
            )
        )
        val lines = csv.lines()
        assertEquals("year;month;source;accrued;ndfl;premium;totalPayout;configVersionId", lines[0])
        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(lines[1].contains("21814.92"))
    }
}
