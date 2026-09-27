package ru.govsalary.calculation.model

/**
 * Расчётный период — год и месяц.
 * Соответствует столбцам AA..AL листа "2026" (excel-analysis.md, раздел 5).
 */
data class CalculationPeriod(
    val year: Int,
    val month: Int, // 1..12
) : Comparable<CalculationPeriod> {

    init {
        require(month in 1..12) { "Месяц должен быть в диапазоне 1..12, получено: $month" }
    }

    /** Предыдущий период (нужен для формулы выплаты "начало месяца (4 число)", см. excel-analysis.md п.5.3). */
    fun previous(): CalculationPeriod =
        if (month == 1) CalculationPeriod(year - 1, 12) else CalculationPeriod(year, month - 1)

    override fun compareTo(other: CalculationPeriod): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    override fun toString(): String = "$year-${month.toString().padStart(2, '0')}"
}
