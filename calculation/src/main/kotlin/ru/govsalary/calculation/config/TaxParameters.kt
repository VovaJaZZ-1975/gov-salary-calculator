package ru.govsalary.calculation.config

import java.math.BigDecimal

/**
 * Налоговые параметры. См. open-questions.md, п.1.
 *
 * В Excel НДФЛ представлен единственным множителем 0.87 в формуле W21 — без отдельной ставки,
 * без учёта прогрессивной шкалы НДФЛ (13%/15% с 2021 года для дохода свыше 5 млн ₽/год).
 * Пока этот вопрос не прояснён пользователем, netMultiplier — единственный источник истины
 * (буквальное соответствие Excel), а effectiveTaxRate — производная величина только для отчётности.
 *
 * НЕ реализует прогрессивную шкалу и НЕ пересчитывает netMultiplier динамически —
 * это было бы домысливанием правила, которого нет в исходном файле (запрещено мастер-промптом Фазы 1, п.3).
 */
data class TaxParameters(
    /** Множитель "к выплате" от суммы начислений. Соответствует Excel-константе 0.87 (ячейка W21, формула). */
    val netMultiplier: BigDecimal,
) {
    init {
        require(netMultiplier.signum() > 0 && netMultiplier <= BigDecimal.ONE) {
            "netMultiplier должен быть в диапазоне (0, 1], получено: $netMultiplier"
        }
    }

    /** Условная эффективная ставка налога = 1 - netMultiplier. Только для отображения, не для расчёта. */
    val effectiveTaxRate: BigDecimal get() = BigDecimal.ONE.subtract(netMultiplier)
}
