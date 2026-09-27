package ru.govsalary.calculation.model

import java.math.BigDecimal

/**
 * Премия за конкретный расчётный период (строка 31, диапазон AA31:AL31 в excel-analysis.md, раздел 5.4).
 *
 * В Excel премия вводится вручную помесячно и НЕ участвует в формуле базовой суммы (W21) —
 * она прибавляется отдельно при расчёте итога за месяц (строка 32). См. open-questions.md, п.4:
 * для месяцев, где премия ещё не назначена, использовать amount = BigDecimal.ZERO, а не null,
 * чтобы отличать явно введённый ноль (январь 2026, AA31=0) от "премия ещё не известна" на уровне
 * вызывающего кода (см. WhatIfScenario / Repository, вне зоны ответственности этого модуля).
 */
data class Premium(
    val period: CalculationPeriod,
    val amount: BigDecimal,
) {
    init {
        require(amount.signum() >= 0) { "Премия не может быть отрицательной (период $period)" }
    }

    companion object {
        fun none(period: CalculationPeriod): Premium = Premium(period, BigDecimal.ZERO)
    }
}
