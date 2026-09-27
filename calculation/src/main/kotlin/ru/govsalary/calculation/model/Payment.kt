package ru.govsalary.calculation.model

import java.math.BigDecimal

/**
 * Тип отдельной выплаты в месяце. Соответствует строкам 29/30 в excel-analysis.md, раздел 5.3.
 *
 * MID_MONTH ("конец месяца (19 число)" в Excel, но по сути выплата за ПЕРВУЮ половину текущего
 * месяца) — в терминах трудового законодательства это "аванс".
 * START_OF_MONTH ("начало месяца (4 число)", выплата за ВТОРУЮ половину предыдущего месяца) —
 * это "окончательная выплата" (окончательный расчёт) за предыдущий месяц.
 */
enum class PaymentType(val payrollTermRu: String, val excelRow: Int) {
    MID_MONTH_ADVANCE("Аванс (за 1-ю половину текущего месяца, выплата ~19 числа)", 30),
    START_OF_MONTH_FINAL("Окончательная выплата (за 2-ю половину предыдущего месяца, выплата ~4 числа)", 29),
}

/**
 * Отдельная выплата — результат применения дневной ставки к числу дней половины месяца.
 * Формулы: excel-analysis.md, раздел 5.3 (`MROUND(ставка*дни, 0.01)`).
 */
data class Payment(
    val type: PaymentType,
    /** Период, ЗА который начислена данная часть выплаты (не период фактической выдачи денег). */
    val forPeriod: CalculationPeriod,
    val dailyRateUsed: BigDecimal,
    val daysUsed: Int,
    val amount: BigDecimal,
)
