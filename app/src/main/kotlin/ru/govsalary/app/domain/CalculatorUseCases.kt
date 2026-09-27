package ru.govsalary.app.domain

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.config.TaxParameters
import ru.govsalary.calculation.engine.SalaryCalculationEngine
import ru.govsalary.calculation.model.Allowance
import ru.govsalary.calculation.model.AllowanceType
import ru.govsalary.calculation.model.CalculationPeriod
import ru.govsalary.calculation.model.PaymentType
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.SalaryCalculationResult
import ru.govsalary.calculation.model.SalaryParameters
import ru.govsalary.calculation.model.WorkCalendar
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.SalaryHistoryRepository
import java.math.BigDecimal
import java.time.Instant

/**
 * Все параметры экрана "Расчёт" (мастер-промпт Фазы 4, п.10), в терминах Excel-модели
 * (excel-analysis.md). UI собирает этот объект из полей ввода и НИЧЕГО не считает сам.
 */
data class CalculatorInput(
    val year: Int,
    val month: Int,
    val baseSalary: BigDecimal,
    val rankSalary: BigDecimal,
    val monthlyBonusRate: BigDecimal,   // ЕДП
    val seniorityRate: BigDecimal,      // выслуга лет
    val specialConditionsRate: BigDecimal,
    val stateSecretRate: BigDecimal,
    val premium: BigDecimal,
    val totalWorkingDays: Int,
    val workingDaysUpTo15: Int,
    val actualWorkedDaysUpTo15: Int,
    val actualWorkedDaysAfter15: Int,
    val previousMonthTotalWorkingDays: Int?,
    val previousMonthWorkingDaysUpTo15: Int?,
    val previousMonthActualWorkedDaysAfter15: Int?,
)

/**
 * UseCase-слой между ViewModel и Calculation Engine / Repository (мастер-промпт Фазы 4, п.11, 25-26).
 * ViewModel вызывает только методы этого класса; ни Compose, ни ViewModel не импортируют
 * ru.govsalary.calculation.engine.* напрямую.
 */
class CalculatorUseCases(
    private val engine: SalaryCalculationEngine,
    private val salaryHistoryRepository: SalaryHistoryRepository,
) {

    /** Реал-тайм расчёт (мастер-промпт Фазы 4, п.11) — чистая функция, ничего не сохраняет. */
    fun calculate(input: CalculatorInput): SalaryCalculationResult {
        val config = input.toConfiguration()
        val calendar = WorkCalendar(
            period = CalculationPeriod(input.year, input.month),
            totalWorkingDays = input.totalWorkingDays,
            workingDaysUpTo15 = input.workingDaysUpTo15,
            actualWorkedDaysUpTo15 = input.actualWorkedDaysUpTo15,
            actualWorkedDaysAfter15 = input.actualWorkedDaysAfter15,
        )
        val previousCalendar = if (
            input.previousMonthTotalWorkingDays != null &&
            input.previousMonthWorkingDaysUpTo15 != null
        ) {
            WorkCalendar(
                period = CalculationPeriod(input.year, input.month).previous(),
                totalWorkingDays = input.previousMonthTotalWorkingDays,
                workingDaysUpTo15 = input.previousMonthWorkingDaysUpTo15,
                actualWorkedDaysAfter15 = input.previousMonthActualWorkedDaysAfter15
                    ?: (input.previousMonthTotalWorkingDays - input.previousMonthWorkingDaysUpTo15),
            )
        } else null

        return engine.calculateMonthlyResult(
            config = config,
            calendar = calendar,
            previousMonthCalendar = previousCalendar,
            premium = Premium(CalculationPeriod(input.year, input.month), input.premium),
        )
    }

    /** Сохраняет результат расчёта как SalaryHistoryEntity(source=CALCULATED) — вызывается явно по действию пользователя. */
    suspend fun saveAsCalculated(input: CalculatorInput, result: SalaryCalculationResult) {
        val allowancesMap = mapOf(
            AllowanceType.MONTHLY_BONUS.name to input.monthlyBonusRate.multiply(input.baseSalary),
            AllowanceType.STATE_SECRET.name to input.stateSecretRate.multiply(input.baseSalary),
            AllowanceType.SPECIAL_CONDITIONS.name to input.specialConditionsRate.multiply(input.baseSalary),
            AllowanceType.SENIORITY.name to input.seniorityRate.multiply(input.baseSalary),
        )
        val advance = result.payments.firstOrNull { it.type == PaymentType.MID_MONTH_ADVANCE }
        val finalPay = result.payments.firstOrNull { it.type == PaymentType.START_OF_MONTH_FINAL }

        salaryHistoryRepository.saveCalculated(
            SalaryHistoryEntity(
                year = input.year,
                month = input.month,
                source = ValueSource.CALCULATED,
                configVersionId = result.configVersionId,
                baseSalary = input.baseSalary,
                rankSalary = input.rankSalary,
                allowancesJson = ru.govsalary.data.local.db.AllowancesJson.encode(allowancesMap),
                accrued = result.accrued,
                ndfl = result.ndfl,
                premium = result.premium,
                totalPayout = result.totalPayout,
                midMonthPaymentDate = null,
                midMonthPaymentAmount = advance?.amount,
                startOfMonthPaymentDate = null,
                startOfMonthPaymentAmount = finalPay?.amount,
                note = "Рассчитано в приложении",
                recordedAt = Instant.now(),
            )
        )
    }
}

private fun CalculatorInput.toConfiguration(): CalculationConfiguration =
    CalculationConfiguration(
        configVersionId = "$year-manual-input",
        year = year,
        effectiveFrom = java.time.LocalDate.of(year, 1, 1),
        salaryParameters = SalaryParameters(baseSalary = baseSalary, rankSalary = rankSalary),
        allowances = listOf(
            Allowance(AllowanceType.MONTHLY_BONUS, monthlyBonusRate),
            Allowance(AllowanceType.STATE_SECRET, stateSecretRate),
            Allowance(AllowanceType.SPECIAL_CONDITIONS, specialConditionsRate),
            Allowance(AllowanceType.SENIORITY, seniorityRate),
        ),
        taxParameters = TaxParameters(netMultiplier = BigDecimal("0.87")), // см. open-questions.md, п.1
    )
