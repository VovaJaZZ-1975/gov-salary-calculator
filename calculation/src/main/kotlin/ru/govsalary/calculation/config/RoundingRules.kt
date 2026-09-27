package ru.govsalary.calculation.config

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Правила округления, вынесенные из кода в конфигурацию (мастер-промпт Фазы 2, п.5).
 *
 * В Excel единственная явная функция округления — MROUND(x, 0.01) (excel-analysis.md, раздел 6),
 * применяемая к дневной ставке и к обеим внутримесячным выплатам. Сумма начисления (W21)
 * округлению не подвергается. По умолчанию step=0.01 воспроизводит именно это поведение.
 */
data class RoundingRules(
    /** Шаг округления MROUND, например 0.01 (до копейки). */
    val step: BigDecimal = BigDecimal("0.01"),
    /**
     * Режим округления. Excel MROUND округляет "половину от шага" от нуля (round half away from zero).
     * Для положительных денежных сумм (единственный случай в этой предметной области) это эквивалентно HALF_UP.
     */
    val roundingMode: RoundingMode = RoundingMode.HALF_UP,
) {
    /**
     * Аналог Excel MROUND(value, step) для value >= 0.
     * Эквивалентность HALF_UP доказана в excel-analysis.md / test-cases.md на всех проверенных значениях.
     */
    fun mround(value: BigDecimal): BigDecimal {
        require(value.signum() >= 0) { "mround в этой модели поддерживает только неотрицательные суммы: $value" }
        if (step.signum() == 0) return value
        // steps = round(value / step) до целого; результат = steps * step.
        // Scale(steps)=0, Scale(step)=step.scale() => Scale(result)=step.scale() автоматически (BigDecimal.multiply).
        val steps = value.divide(step, 0, roundingMode)
        return steps.multiply(step)
    }
}
