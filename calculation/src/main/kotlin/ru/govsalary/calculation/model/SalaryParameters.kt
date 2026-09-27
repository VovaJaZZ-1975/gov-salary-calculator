package ru.govsalary.calculation.model

import java.math.BigDecimal

/**
 * Базовые оклады сотрудника.
 * Источник: excel-analysis.md, раздел 3 (ячейки W14, W15).
 *
 * Денежные суммы — только BigDecimal (см. мастер-промпт Фазы 2, п.2). Double/Float запрещены.
 */
data class SalaryParameters(
    /** Должностной оклад. Ячейка W14. Является базой для расчёта всех надбавок (Allowance). */
    val baseSalary: BigDecimal,
    /** Оклад за классный чин. Ячейка W15. В формулы надбавок НЕ входит — суммируется отдельно. */
    val rankSalary: BigDecimal,
) {
    init {
        require(baseSalary.signum() >= 0) { "Должностной оклад не может быть отрицательным" }
        require(rankSalary.signum() >= 0) { "Оклад за классный чин не может быть отрицательным" }
    }
}
