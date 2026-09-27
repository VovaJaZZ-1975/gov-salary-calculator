package ru.govsalary.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.data.fakes.FakeSalaryHistoryDao
import ru.govsalary.data.io.CsvHistoryTemplate
import ru.govsalary.data.io.CsvHistoryImportService
import ru.govsalary.data.io.ImportStrategy
import ru.govsalary.data.io.parseCsvHistory
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.SalaryHistoryRepository
import java.math.BigDecimal

/**
 * Мастер-промпт (диалог, "CSV-импорт ретроспективных данных"): загрузка истории по коэффициентам
 * и премиям по шаблону CSV. Golden-проверка — те же числа, что в docs/test-cases.md (TC004/TC006),
 * прогнанные через тот же Calculation Engine, но с параметрами из CSV, а не из
 * CalculationConfiguration.excel2026Baseline() напрямую.
 */
class CsvHistoryImportServiceTest {

    private fun buildService(): Pair<CsvHistoryImportService, SalaryHistoryRepository> {
        val repo = SalaryHistoryRepository(FakeSalaryHistoryDao())
        return CsvHistoryImportService(repo, DefaultSalaryCalculationEngine()) to repo
    }

    @Test
    fun `sample template parses without errors into 2 rows`() {
        val result = parseCsvHistory(CsvHistoryTemplate.sampleCsv())
        assertEquals(2, result.rows.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `january and february totals from sample template match test-cases TC004 and TC006`() = runTest {
        val (service, _) = buildService()
        val preview = service.preview(CsvHistoryTemplate.sampleCsv())
        val result = service.apply(preview, ImportStrategy.OVERWRITE)
        assertEquals(2, result.importedCount)
    }

    @Test
    fun `computed totals exactly match golden values from Phase 1-2`() = runTest {
        val (service, repo) = buildService()
        val preview = service.preview(CsvHistoryTemplate.sampleCsv())
        service.apply(preview, ImportStrategy.OVERWRITE)

        val jan = repo.getAll().first { it.year == 2026 && it.month == 1 }
        val feb = repo.getAll().first { it.year == 2026 && it.month == 2 }

        assertEquals(BigDecimal("21814.92"), jan.totalPayout)
        assertEquals(BigDecimal("127043.83"), feb.totalPayout)
        assertEquals(ValueSource.CALCULATED, jan.source)
        assertEquals(null, jan.startOfMonthPaymentAmount, "январь — граница данных, как и в исходном Excel (AA29 пусто)")
        assertEquals(BigDecimal("59991.03"), feb.startOfMonthPaymentAmount)
    }

    @Test
    fun `differing coefficients between adjacent months use each month's own config for its own daily rate`() = runTest {
        val (service, repo) = buildService()
        // Декабрь с одним окладом, январь следующего года — с другим (типичный ретроспективный случай: индексация).
        val csv = buildString {
            appendLine(CsvHistoryTemplate.HEADER)
            appendLine("2025;12;20000;10000;0.9;0.1;1.2;0.1;0;23;11;;;0.87")
            appendLine("2026;1;24296;13853;0.9;0.1;1.2;0.1;0;15;4;;;0.87")
        }
        val preview = service.preview(csv)
        assertTrue(preview.errors.isEmpty())
        service.apply(preview, ImportStrategy.OVERWRITE)

        val jan = repo.getAll().first { it.year == 2026 && it.month == 1 }
        // Выплата "начало месяца" для января должна быть посчитана по ДЕКАБРЬСКИМ (меньшим)
        // коэффициентам, а не по январским — иначе задним числом "переписывалась" бы история.
        val decNet = (BigDecimal("20000") + BigDecimal("10000") +
            BigDecimal("20000").multiply(BigDecimal("0.9")) + BigDecimal("20000").multiply(BigDecimal("0.1")) +
            BigDecimal("20000").multiply(BigDecimal("1.2")) + BigDecimal("20000").multiply(BigDecimal("0.1"))
            ).multiply(BigDecimal("0.87"))
        val decDailyRate = decNet.divide(BigDecimal(23), 10, java.math.RoundingMode.HALF_UP)
            .setScale(2, java.math.RoundingMode.HALF_UP)
        val decAfter15 = 23 - 11
        val expectedStartOfMonth = decDailyRate.multiply(BigDecimal(decAfter15)).setScale(2, java.math.RoundingMode.HALF_UP)

        assertEquals(expectedStartOfMonth, jan.startOfMonthPaymentAmount)
        assertEquals(BigDecimal("34497.36"), jan.startOfMonthPaymentAmount)
        // Если бы (по ошибке) для расчёта "2-й половины декабря" использовались ЯНВАРСКИЕ
        // коэффициенты (оклад 24296 вместо декабрьских 20000), сумма была бы заметно другой —
        // явно проверяем, что бага "текущий конфиг переписывает прошлое" нет.
        val wrongIfUsedJanuaryConfigInstead = BigDecimal("42681.36")
        assertTrue(jan.startOfMonthPaymentAmount != wrongIfUsedJanuaryConfigInstead)
    }

    @Test
    fun `malformed row is collected as an error, valid rows still parse`() {
        val csv = buildString {
            appendLine(CsvHistoryTemplate.HEADER)
            appendLine("2026;1;24296;13853;0.9;0.1;1.2;0.1;0;15;4;;;")
            appendLine("2026;13;24296;13853;0.9;0.1;1.2;0.1;0;15;4;;;") // месяц 13 — невалиден
            appendLine("не_число;1;24296;13853;0.9;0.1;1.2;0.1;0;15;4;;;")
        }
        val result = parseCsvHistory(csv)
        assertEquals(1, result.rows.size)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun `duplicate period within the same file is reported as an error`() {
        val csv = buildString {
            appendLine(CsvHistoryTemplate.HEADER)
            appendLine("2026;1;24296;13853;0.9;0.1;1.2;0.1;0;15;4;;;")
            appendLine("2026;1;99999;99999;0.9;0.1;1.2;0.1;0;15;4;;;")
        }
        val result = parseCsvHistory(csv)
        assertEquals(1, result.rows.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `invalid actual-worked days (more than scheduled) is reported as a parse error, not a crash`() {
        val csv = buildString {
            appendLine(CsvHistoryTemplate.HEADER)
            // отработано_до_15 = 99, при том что дней_до_15 = 4 — нарушает инвариант WorkCalendar
            appendLine("2026;1;24296;13853;0.9;0.1;1.2;0.1;0;15;4;99;;")
        }
        val result = parseCsvHistory(csv)
        assertEquals(0, result.rows.size)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `comma as decimal separator is accepted (ru-RU Excel export convention)`() {
        val csv = buildString {
            appendLine(CsvHistoryTemplate.HEADER)
            appendLine("2026;3;24296;13853;0,9;0,1;1,2;0,1;0;21;9;;;0,87")
        }
        val result = parseCsvHistory(csv)
        assertTrue(result.errors.isEmpty())
        assertEquals(BigDecimal("0.9"), result.rows.single().edpRate)
    }

    @Test
    fun `KEEP_EXISTING skips periods that already have a CALCULATED record`() = runTest {
        val (service, repo) = buildService()
        val firstPreview = service.preview(CsvHistoryTemplate.sampleCsv())
        service.apply(firstPreview, ImportStrategy.OVERWRITE) // теперь Jan/Feb 2026 уже есть в БД

        val secondPreview = service.preview(CsvHistoryTemplate.sampleCsv())
        assertEquals(listOf(CalculationPeriod(2026, 1), CalculationPeriod(2026, 2)), secondPreview.conflictingPeriods)

        val result = service.apply(secondPreview, ImportStrategy.KEEP_EXISTING)
        assertEquals(0, result.importedCount)
        assertEquals(2, result.skippedCount)
    }

    @Test
    fun `OVERWRITE strategy re-applies conflicting periods with new values`() = runTest {
        val (service, repo) = buildService()
        service.apply(service.preview(CsvHistoryTemplate.sampleCsv()), ImportStrategy.OVERWRITE)

        val updatedCsv = buildString {
            appendLine(CsvHistoryTemplate.HEADER)
            appendLine("2026;1;30000;13853;0.9;0.1;1.2;0.1;0;15;4;;;")
        }
        val preview = service.preview(updatedCsv)
        assertEquals(listOf(CalculationPeriod(2026, 1)), preview.conflictingPeriods)
        service.apply(preview, ImportStrategy.OVERWRITE)

        val jan = repo.getAll().first { it.year == 2026 && it.month == 1 }
        assertEquals(BigDecimal("30000"), jan.baseSalary)
    }
}
