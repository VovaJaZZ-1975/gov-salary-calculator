package ru.govsalary.calculation.model

import java.math.BigDecimal

/**
 * Полный результат расчёта за один месяц. Объединяет базовое начисление (W21) и итог за месяц
 * (строка 32) — excel-analysis.md, разделы 4 и 5.
 *
 * НДФЛ (мастер-промпт Фазы 2, п.4) в исходном Excel отдельной строкой не выделен — присутствует
 * только как неявный множитель 0.87 в формуле W21 (open-questions.md, п.1). Здесь он вычисляется
 * как разность accrued - netMonthlyBase и приводится для прозрачности отчёта, но НЕ является
 * самостоятельной величиной, зафиксированной в Excel отдельной формулой — это следствие того же
 * единственного коэффициента netMultiplier из CalculationConfiguration.
 */
data class SalaryCalculationResult(
    val period: CalculationPeriod,
    val configVersionId: String,

    /** Сумма начислений до применения netMultiplier: оклад + классный чин + все надбавки. */
    val accrued: BigDecimal,

    /** accrued - netMonthlyBase. Условный НДФЛ, вычисленный из netMultiplier (см. класс-комментарий). */
    val ndfl: BigDecimal,

    /** Базовая расчётная сумма "на руки" за условный полный месяц. Формула W21: accrued * netMultiplier. */
    val netMonthlyBase: BigDecimal,

    /** Дневная ставка данного месяца. Формула AAn24: MROUND(netMonthlyBase / totalWorkingDays, 0.01). */
    val dailyRate: BigDecimal,

    /** Обе внутримесячные выплаты (аванс текущего месяца + окончательная выплата за предыдущий). */
    val payments: List<Payment>,

    /** Премия за месяц (строка 31). */
    val premium: BigDecimal,

    /** Итого к выплате за месяц (строка 32): сумма payments.amount + premium. */
    val totalPayout: BigDecimal,
)
