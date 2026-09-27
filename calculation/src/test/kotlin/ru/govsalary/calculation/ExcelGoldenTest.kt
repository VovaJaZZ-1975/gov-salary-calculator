package ru.govsalary.calculation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.WorkCalendar
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Golden-тесты: один тест на каждый Test Case из docs/test-cases.md (мастер-промпт Фазы 2, п.9).
 * Допустимое расхождение — 0.01 ₽ (используется assertMoneyEquals с явным допуском,
 * т.к. Фаза 1 не устанавливала иного правила — phase-1-report.md).
 *
 * Календарь 2026 года (рабочие дни по месяцам) взят буквально из excel-analysis.md, раздел 5.1
 * (строки 25-26 исходного файла), не пересчитывается.
 */
class ExcelGoldenTest {

    private val engine = DefaultSalaryCalculationEngine()
    private val config = CalculationConfiguration.excel2026Baseline()
    private val tolerance = BigDecimal("0.01")

    private fun assertMoneyEquals(expected: BigDecimal, actual: BigDecimal, message: String) {
        val diff = expected.subtract(actual).abs()
        assert(diff <= tolerance) {
            "$message: ожидалось $expected, получено $actual, расхождение $diff превышает допуск $tolerance"
        }
    }

    // Календарь 2026 года, excel-analysis.md раздел 5.1 (totalWorkingDays, workingDaysUpTo15)
    private val calendars: Map<Int, WorkCalendar> = mapOf(
        1 to WorkCalendar(CalculationPeriod(2026, 1), 15, 4),
        2 to WorkCalendar(CalculationPeriod(2026, 2), 19, 10),
        3 to WorkCalendar(CalculationPeriod(2026, 3), 21, 9),
        4 to WorkCalendar(CalculationPeriod(2026, 4), 22, 11),
        5 to WorkCalendar(CalculationPeriod(2026, 5), 19, 9),
        6 to WorkCalendar(CalculationPeriod(2026, 6), 21, 10),
        7 to WorkCalendar(CalculationPeriod(2026, 7), 23, 11),
        8 to WorkCalendar(CalculationPeriod(2026, 8), 21, 10),
        9 to WorkCalendar(CalculationPeriod(2026, 9), 22, 11),
        10 to WorkCalendar(CalculationPeriod(2026, 10), 22, 11),
        11 to WorkCalendar(CalculationPeriod(2026, 11), 20, 9),
        12 to WorkCalendar(CalculationPeriod(2026, 12), 22, 11),
    )

    // Премии по месяцам, excel-analysis.md раздел 5.4. Окт-дек не введены в Excel -> 0 (open-questions.md, п.4).
    private val premiums: Map<Int, BigDecimal> = mapOf(
        1 to BigDecimal.ZERO, 2 to BigDecimal("23997"), 3 to BigDecimal("23997"),
        4 to BigDecimal("23997"), 5 to BigDecimal("28347"), 6 to BigDecimal("23997"),
        7 to BigDecimal("23997"), 8 to BigDecimal("44007"), 9 to BigDecimal("23997"),
        10 to BigDecimal.ZERO, 11 to BigDecimal.ZERO, 12 to BigDecimal.ZERO,
    )

    private fun premiumFor(month: Int): Premium = Premium(CalculationPeriod(2026, month), premiums.getValue(month))

    private fun previousCalendar(month: Int): WorkCalendar? =
        if (month == 1) null else calendars.getValue(month - 1)

    // --- Test Case 001 ---
    @Test
    fun `TC001 - base accrual total`() {
        val net = engine.calculateNetMonthlyBase(config)
        assertMoneyEquals(BigDecimal("81805.926"), net, "TC001 netMonthlyBase (W21)")
    }

    // --- Test Case 002 ---
    @Test
    fun `TC002 - daily rate january`() {
        val net = engine.calculateNetMonthlyBase(config)
        val rate = engine.calculateDailyRate(net, calendars.getValue(1), config)
        assertMoneyEquals(BigDecimal("5453.73"), rate, "TC002 daily rate January")
    }

    // --- Test Case 003 ---
    @Test
    fun `TC003 - daily rate february`() {
        val net = engine.calculateNetMonthlyBase(config)
        val rate = engine.calculateDailyRate(net, calendars.getValue(2), config)
        assertMoneyEquals(BigDecimal("4305.58"), rate, "TC003 daily rate February")
    }

    // --- Test Case 004 ---
    @Test
    fun `TC004 - mid-month payment january (19th)`() {
        val result = engine.calculateMonthlyResult(config, calendars.getValue(1), previousCalendar(1), premiumFor(1))
        assertMoneyEquals(BigDecimal("21814.92"), result.payments.single().amount, "TC004 mid-month payment January")
    }

    // --- Test Case 005 ---
    @Test
    fun `TC005 - start-of-month payment february (4th, for 2nd half of january)`() {
        val result = engine.calculateMonthlyResult(config, calendars.getValue(2), previousCalendar(2), premiumFor(2))
        val startOfMonth = result.payments.first { it.type.excelRow == 29 }
        assertMoneyEquals(BigDecimal("59991.03"), startOfMonth.amount, "TC005 start-of-month payment February")
    }

    // --- Test Case 006 ---
    @Test
    fun `TC006 - february total with premium`() {
        val result = engine.calculateMonthlyResult(config, calendars.getValue(2), previousCalendar(2), premiumFor(2))
        assertMoneyEquals(BigDecimal("127043.83"), result.totalPayout, "TC006 February total")
    }

    // --- Test Case 007 ---
    @Test
    fun `TC007 - january total at year boundary (no december 2025 data)`() {
        val result = engine.calculateMonthlyResult(config, calendars.getValue(1), previousCalendar(1), premiumFor(1))
        assertEquals(1, result.payments.size, "TC007: на границе года должна быть только 1 выплата (без данных за декабрь 2025)")
        assertMoneyEquals(BigDecimal("21814.92"), result.totalPayout, "TC007 January total")
    }

    // --- Test Case 008 ---
    @Test
    fun `TC008 - august total (highest premium month)`() {
        val result = engine.calculateMonthlyResult(config, calendars.getValue(8), previousCalendar(8), premiumFor(8))
        assertMoneyEquals(BigDecimal("125643.56"), result.totalPayout, "TC008 August total")
    }

    // --- Test Case 009 --- полная годовая сводка
    @Test
    fun `TC009 - full year summary matches Excel row 32 (AA32-AL32)`() {
        val expected = mapOf(
            1 to "21814.92", 2 to "127043.83", 3 to "97806.90", 4 to "111646.19",
            5 to "108000.17", 6 to "106008.00", 7 to "105972.30", 8 to "125643.56",
            9 to "107750.67", 10 to "81805.90", 11 to "77715.65", 12 to "85896.25",
        )
        for (month in 1..12) {
            val result = engine.calculateMonthlyResult(config, calendars.getValue(month), previousCalendar(month), premiumFor(month))
            assertMoneyEquals(
                BigDecimal(expected.getValue(month)).setScale(2, RoundingMode.HALF_UP),
                result.totalPayout.setScale(2, RoundingMode.HALF_UP),
                "TC009 месяц $month",
            )
        }
    }
}
