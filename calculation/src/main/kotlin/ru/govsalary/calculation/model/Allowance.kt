package ru.govsalary.calculation.model

import java.math.BigDecimal

/**
 * Виды надбавок, найденных в блоке начисления P13:X21 (excel-analysis.md, раздел 3-4).
 * Порядок enum соответствует порядку строк в Excel (14..19, кроме BASE/RANK, которые не надбавки).
 */
enum class AllowanceType(val excelLabel: String, val sourceCell: String) {
    MONTHLY_BONUS("Ежемесячное денежное поощрение (ЕДП)", "W16/X16"),
    STATE_SECRET("За гостайну", "W17/X17"),
    SPECIAL_CONDITIONS("За особые условия гражданской службы", "W18/X18"),
    SENIORITY("За выслугу лет на гражданской службе", "W19/X19"),
}

/**
 * Надбавка: коэффициент × должностной оклад (SalaryParameters.baseSalary).
 * Важно: во всех формулах Excel (`=$W$14*X<n>`) множителем служит ИМЕННО должностной оклад,
 * а не сумма оклад+классный чин. Оклад за классный чин (rankSalary) в надбавки не входит.
 */
data class Allowance(
    val type: AllowanceType,
    /** Коэффициент из столбца X (например, 0.9 для ЕДП). Безразмерная величина. */
    val rate: BigDecimal,
) {
    init {
        require(rate.signum() >= 0) { "Коэффициент надбавки ${type.excelLabel} не может быть отрицательным" }
    }

    /** Сумма надбавки = baseSalary * rate (формула из excel-analysis.md, раздел 4). Без округления — Excel его не применяет здесь. */
    fun amount(baseSalary: BigDecimal): BigDecimal = baseSalary.multiply(rate)
}
