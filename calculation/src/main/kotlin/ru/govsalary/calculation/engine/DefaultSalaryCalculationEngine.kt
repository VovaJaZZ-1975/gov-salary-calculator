package ru.govsalary.calculation.engine

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.model.Payment
import ru.govsalary.calculation.model.PaymentType
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.SalaryCalculationResult
import ru.govsalary.calculation.model.WorkCalendar
import java.math.BigDecimal

/**
 * Буквальная реализация формул из excel-analysis.md. Ничего не упрощено и не заменено
 * приближением (мастер-промпт Фазы 1, п.3 и Фазы 2, п.4).
 */
class DefaultSalaryCalculationEngine : SalaryCalculationEngine {

    override fun calculateAccrued(config: CalculationConfiguration): BigDecimal {
        val base = config.salaryParameters.baseSalary
        val rank = config.salaryParameters.rankSalary
        val allowancesSum = config.allowances.fold(BigDecimal.ZERO) { acc, allowance ->
            acc.add(allowance.amount(base))
        }
        // Формула W21 суммирует W14:W20, т.е. включает и ещё не назначенную "Премия" (W20),
        // которая в базовом блоке всегда 0 (excel-analysis.md, раздел 3) — здесь она сознательно
        // не прибавляется, т.к. Premium — отдельная помесячная сущность (excel-analysis.md, раздел 5.4).
        return base.add(rank).add(allowancesSum)
    }

    override fun calculateNetMonthlyBase(config: CalculationConfiguration): BigDecimal {
        val accrued = calculateAccrued(config)
        return accrued.multiply(config.taxParameters.netMultiplier)
    }

    override fun calculateDailyRate(
        netMonthlyBase: BigDecimal,
        calendar: WorkCalendar,
        config: CalculationConfiguration,
    ): BigDecimal {
        require(calendar.totalWorkingDays > 0) {
            "Нулевое число рабочих дней в месяце ${calendar.period} — деление на ноль, как и в Excel формула AAn24 в этом случае некорректна"
        }
        val raw = netMonthlyBase.divide(
            BigDecimal(calendar.totalWorkingDays),
            10, // высокая промежуточная точность перед MROUND, как это делает Excel внутри
            java.math.RoundingMode.HALF_UP,
        )
        return config.roundingRules.mround(raw)
    }

    override fun calculateMonthlyResult(
        config: CalculationConfiguration,
        calendar: WorkCalendar,
        previousMonthCalendar: WorkCalendar?,
        premium: Premium,
    ): SalaryCalculationResult {
        require(premium.period == calendar.period) {
            "Период премии (${premium.period}) не совпадает с периодом календаря (${calendar.period})"
        }

        val accrued = calculateAccrued(config)
        val netMonthlyBase = calculateNetMonthlyBase(config)
        val ndfl = accrued.subtract(netMonthlyBase)
        val dailyRate = calculateDailyRate(netMonthlyBase, calendar, config)

        val payments = mutableListOf<Payment>()

        // Строка 30: выплата "19 числа" — за 1-ю половину ТЕКУЩЕГО месяца (аванс).
        // Использует фактически отработанные дни (WorkCalendar.actualWorkedDaysUpTo15) —
        // по умолчанию совпадает с плановыми, что воспроизводит Excel один в один.
        val midMonthAmount = config.roundingRules.mround(
            dailyRate.multiply(BigDecimal(calendar.actualWorkedDaysUpTo15))
        )
        payments += Payment(
            type = PaymentType.MID_MONTH_ADVANCE,
            forPeriod = calendar.period,
            dailyRateUsed = dailyRate,
            daysUsed = calendar.actualWorkedDaysUpTo15,
            amount = midMonthAmount,
        )

        // Строка 29: выплата "4 числа" — за 2-ю половину ПРЕДЫДУЩЕГО месяца (окончательная выплата).
        // Если предыдущий календарь неизвестен — сумма 0, как для AA29 в исходном файле
        // (open-questions.md, п.3), запись в payments не добавляется вовсе, чтобы явно отличать
        // "0 из-за отсутствия данных" от "0 из-за нулевой дневной ставки/дней".
        if (previousMonthCalendar != null) {
            val prevNetMonthlyBase = netMonthlyBase // база (W21) в рамках одной конфигурации неизменна для всех месяцев
            val prevDailyRate = calculateDailyRate(prevNetMonthlyBase, previousMonthCalendar, config)
            val startOfMonthAmount = config.roundingRules.mround(
                prevDailyRate.multiply(BigDecimal(previousMonthCalendar.actualWorkedDaysAfter15))
            )
            payments += Payment(
                type = PaymentType.START_OF_MONTH_FINAL,
                forPeriod = previousMonthCalendar.period,
                dailyRateUsed = prevDailyRate,
                daysUsed = previousMonthCalendar.actualWorkedDaysAfter15,
                amount = startOfMonthAmount,
            )
        }

        val paymentsSum = payments.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.amount) }
        val totalPayout = paymentsSum.add(premium.amount)

        return SalaryCalculationResult(
            period = calendar.period,
            configVersionId = config.configVersionId,
            accrued = accrued,
            ndfl = ndfl,
            netMonthlyBase = netMonthlyBase,
            dailyRate = dailyRate,
            payments = payments,
            premium = premium.amount,
            totalPayout = totalPayout,
        )
    }
}
