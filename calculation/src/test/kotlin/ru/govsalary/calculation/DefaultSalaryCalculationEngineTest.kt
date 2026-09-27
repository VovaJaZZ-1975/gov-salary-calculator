package ru.govsalary.calculation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.engine.DefaultSalaryCalculationEngine
import ru.govsalary.calculation.model.Allowance
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.PaymentType
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.WorkCalendar
import java.math.BigDecimal

/**
 * Юнит-тесты покрывают список из мастер-промпта Фазы 2, п.8:
 * базовый оклад, классный чин, каждая надбавка, ЕДП, выслуга, особые условия, гостайна,
 * премия, дневная ставка, полный месяц, неполный месяц, аванс, окончательная выплата, НДФЛ, округление.
 */
class DefaultSalaryCalculationEngineTest {

    private val engine = DefaultSalaryCalculationEngine()
    private val config = CalculationConfiguration.excel2026Baseline()

    // --- Базовый оклад и классный чин ---

    @Test
    fun `accrued includes base salary and rank salary`() {
        val accrued = engine.calculateAccrued(config)
        // 24296 + 13853 + (все надбавки) — проверяем именно наличие базовых слагаемых через изолированную конфигурацию.
        val configNoAllowances = config.copy(allowances = emptyList())
        assertEquals(BigDecimal("38149"), engine.calculateAccrued(configNoAllowances))
        // с надбавками сумма должна быть строго больше
        assert(accrued > BigDecimal("38149"))
    }

    // --- Каждая надбавка отдельно (ЕДП, гостайна, особые условия, выслуга) ---

    @Test
    fun `monthly bonus allowance (EDP) equals baseSalary times rate`() {
        val allowance = config.allowance(AllowanceType.MONTHLY_BONUS)!!
        assertEquals(BigDecimal("21866.4"), allowance.amount(config.salaryParameters.baseSalary))
    }

    @Test
    fun `state secret allowance equals baseSalary times rate`() {
        val allowance = config.allowance(AllowanceType.STATE_SECRET)!!
        assertEquals(BigDecimal("2429.6"), allowance.amount(config.salaryParameters.baseSalary))
    }

    @Test
    fun `special conditions allowance equals baseSalary times rate`() {
        val allowance = config.allowance(AllowanceType.SPECIAL_CONDITIONS)!!
        assertEquals(BigDecimal("29155.2"), allowance.amount(config.salaryParameters.baseSalary))
    }

    @Test
    fun `seniority allowance equals baseSalary times rate`() {
        val allowance = config.allowance(AllowanceType.SENIORITY)!!
        assertEquals(BigDecimal("2429.6"), allowance.amount(config.salaryParameters.baseSalary))
    }

    @Test
    fun `allowance rate never multiplies rankSalary`() {
        // Регрессия на распространённую ошибку: в Excel множителем служит ТОЛЬКО W14 (baseSalary).
        val allowance = Allowance(AllowanceType.MONTHLY_BONUS, BigDecimal("0.9"))
        val amountFromBaseOnly = allowance.amount(config.salaryParameters.baseSalary)
        val amountIfRankIncluded = allowance.amount(config.salaryParameters.baseSalary.add(config.salaryParameters.rankSalary))
        assert(amountFromBaseOnly != amountIfRankIncluded)
        assertEquals(BigDecimal("21866.4"), amountFromBaseOnly)
    }

    // --- НДФЛ / netMonthlyBase ---

    @Test
    fun `net monthly base applies tax multiplier to full accrued sum`() {
        val net = engine.calculateNetMonthlyBase(config)
        assertEquals(BigDecimal("81805.926"), net)
    }

    @Test
    fun `ndfl equals accrued minus net monthly base`() {
        val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)
        val result = engine.calculateMonthlyResult(config, jan, previousMonthCalendar = null, premium = Premium.none(jan.period))
        assertEquals(result.accrued.subtract(result.netMonthlyBase), result.ndfl)
        // accrued = 94029.8, net = 81805.926 => ndfl = 12223.874
        assertEquals(BigDecimal("12223.874"), result.ndfl)
    }

    // --- Дневная ставка и округление (MROUND) ---

    @Test
    fun `daily rate for january is rounded to nearest kopeck via mround`() {
        val net = engine.calculateNetMonthlyBase(config)
        val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)
        val rate = engine.calculateDailyRate(net, jan, config)
        assertEquals(BigDecimal("5453.73"), rate)
    }

    @Test
    fun `daily rate for february differs from january despite same base`() {
        val net = engine.calculateNetMonthlyBase(config)
        val feb = WorkCalendar(CalculationPeriod(2026, 2), totalWorkingDays = 19, workingDaysUpTo15 = 10)
        val rate = engine.calculateDailyRate(net, feb, config)
        assertEquals(BigDecimal("4305.58"), rate)
    }

    // --- Полный месяц: аванс + окончательная выплата + премия ---

    @Test
    fun `full month result combines advance, final payment from previous month, and premium`() {
        val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)
        val feb = WorkCalendar(CalculationPeriod(2026, 2), totalWorkingDays = 19, workingDaysUpTo15 = 10)
        val premiumFeb = Premium(feb.period, BigDecimal("23997"))

        val result = engine.calculateMonthlyResult(config, feb, previousMonthCalendar = jan, premium = premiumFeb)

        assertEquals(2, result.payments.size)
        val advance = result.payments.first { it.type == PaymentType.MID_MONTH_ADVANCE }
        val finalPay = result.payments.first { it.type == PaymentType.START_OF_MONTH_FINAL }

        assertEquals(BigDecimal("43055.80"), advance.amount)
        assertEquals(BigDecimal("59991.03"), finalPay.amount)
        assertEquals(BigDecimal("127043.83"), result.totalPayout)
    }

    // --- Аванс (MID_MONTH_ADVANCE) в изоляции ---

    @Test
    fun `advance uses current month daily rate and days up to 15`() {
        val net = engine.calculateNetMonthlyBase(config)
        val aug = WorkCalendar(CalculationPeriod(2026, 8), totalWorkingDays = 21, workingDaysUpTo15 = 10)
        val rate = engine.calculateDailyRate(net, aug, config)
        val advance = config.roundingRules.mround(rate.multiply(BigDecimal(aug.workingDaysUpTo15)))
        assertEquals(BigDecimal("38955.20"), advance)
    }

    // --- Окончательная выплата (START_OF_MONTH_FINAL) в изоляции ---

    @Test
    fun `final payment uses previous month daily rate and days after 15`() {
        val net = engine.calculateNetMonthlyBase(config)
        val jul = WorkCalendar(CalculationPeriod(2026, 7), totalWorkingDays = 23, workingDaysUpTo15 = 11)
        val rate = engine.calculateDailyRate(net, jul, config)
        val finalPay = config.roundingRules.mround(rate.multiply(BigDecimal(jul.workingDaysAfter15)))
        assertEquals(BigDecimal("42681.36"), finalPay)
    }

    @Test
    fun `no final payment when previous month calendar is unknown (year boundary)`() {
        val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)
        val result = engine.calculateMonthlyResult(config, jan, previousMonthCalendar = null, premium = Premium.none(jan.period))

        assertEquals(1, result.payments.size)
        assertEquals(PaymentType.MID_MONTH_ADVANCE, result.payments.single().type)
        assertEquals(BigDecimal("21814.92"), result.totalPayout)
    }

    // --- Премия ---

    @Test
    fun `premium is added on top of both payments`() {
        val may = WorkCalendar(CalculationPeriod(2026, 5), totalWorkingDays = 19, workingDaysUpTo15 = 9)
        val apr = WorkCalendar(CalculationPeriod(2026, 4), totalWorkingDays = 22, workingDaysUpTo15 = 11)
        val premiumMay = Premium(may.period, BigDecimal("28347"))
        val result = engine.calculateMonthlyResult(config, may, apr, premiumMay)
        assertEquals(BigDecimal("28347"), result.premium)
        assertEquals(BigDecimal("108000.17"), result.totalPayout)
    }

    @Test
    fun `premium of zero is a valid explicit value, not absence of data`() {
        val jan = WorkCalendar(CalculationPeriod(2026, 1), totalWorkingDays = 15, workingDaysUpTo15 = 4)
        val premium = Premium.none(jan.period)
        assertEquals(BigDecimal.ZERO, premium.amount)
        val result = engine.calculateMonthlyResult(config, jan, null, premium)
        assertEquals(BigDecimal.ZERO, result.premium)
    }

    // --- Неполный месяц (фактически отработанные дни) ---

    @Test
    fun `partial month reduces advance proportionally to actually worked days`() {
        val net = engine.calculateNetMonthlyBase(config)
        val mar = WorkCalendar(
            period = CalculationPeriod(2026, 3),
            totalWorkingDays = 21,
            workingDaysUpTo15 = 9,
            actualWorkedDaysUpTo15 = 5, // сотрудник отработал только 5 из 9 плановых дней (например, часть в отпуске)
        )
        assert(mar.isPartialMonth)
        val rate = engine.calculateDailyRate(net, mar, config)
        val premium = Premium.none(mar.period)
        val result = engine.calculateMonthlyResult(config, mar, previousMonthCalendar = null, premium = premium)
        val expectedAdvance = config.roundingRules.mround(rate.multiply(BigDecimal(5)))
        assertEquals(expectedAdvance, result.payments.single().amount)
        assert(result.payments.single().amount < config.roundingRules.mround(rate.multiply(BigDecimal(9))))
    }

    @Test
    fun `full month (default) equals scheduled working days`() {
        val calendar = WorkCalendar(CalculationPeriod(2026, 6), totalWorkingDays = 21, workingDaysUpTo15 = 10)
        assertEquals(false, calendar.isPartialMonth)
        assertEquals(10, calendar.actualWorkedDaysUpTo15)
        assertEquals(11, calendar.actualWorkedDaysAfter15)
    }

    // --- Валидация WorkCalendar ---

    @Test
    fun `workingDaysAfter15 is derived, not stored`() {
        val calendar = WorkCalendar(CalculationPeriod(2026, 7), totalWorkingDays = 23, workingDaysUpTo15 = 11)
        assertEquals(12, calendar.workingDaysAfter15)
    }
}
