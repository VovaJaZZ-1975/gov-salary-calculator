package ru.govsalary.data.local.db.entity

import androidx.room.Entity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * История зарплаты (мастер-промпт Фазы 3, п.1). Поля буквально перечислены в промпте:
 * год, месяц, входные параметры, начислено, НДФЛ, к выплате, премии, надбавки, даты выплат,
 * версия расчётной модели.
 *
 * ВАЖНО (п.4): расчётное значение (source=CALCULATED, результат SalaryCalculationEngine) и
 * фактическое значение (source=ACTUAL, введено пользователем/импортировано) — это ДВЕ РАЗНЫЕ
 * строки с одинаковым (year, month). Они физически не могут перезаписать друг друга
 * (primary key включает source), поэтому пользователь всегда видит, откуда взято значение
 * (SalaryHistoryRepository.getComparison, см. SalaryHistoryRepository.kt).
 */
@Entity(
    tableName = "salary_history",
    primaryKeys = ["year", "month", "source"],
)
data class SalaryHistoryEntity(
    val year: Int,
    val month: Int,
    val source: ValueSource,

    /** Версия расчётной модели (CalculationConfiguration.configVersionId из :calculation). Для source=ACTUAL — версия на момент фиксации факта, если известна, иначе null. */
    val configVersionId: String?,

    // --- Входные параметры ---
    val baseSalary: BigDecimal,
    val rankSalary: BigDecimal,
    /** Сериализованный список надбавок (тип -> сумма), см. Converters.kt. Хранится как есть, без пересчёта. */
    val allowancesJson: String,

    // --- Результат ---
    /** Начислено (accrued, до налога). */
    val accrued: BigDecimal,
    /** НДФЛ (см. open-questions.md, п.1 — условная величина для source=CALCULATED). */
    val ndfl: BigDecimal,
    /** Премия за период. */
    val premium: BigDecimal,
    /** К выплате (итого за месяц). */
    val totalPayout: BigDecimal,

    // --- Даты и суммы фактических выплат ---
    val midMonthPaymentDate: LocalDate?,
    val midMonthPaymentAmount: BigDecimal?,
    val startOfMonthPaymentDate: LocalDate?,
    val startOfMonthPaymentAmount: BigDecimal?,

    /** Произвольная заметка (например, "импортировано из Расчет ЗП (2026).xlsx, ячейка AB32"). */
    val note: String? = null,

    val recordedAt: Instant,
)
