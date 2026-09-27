package ru.govsalary.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Период плана накопления с собственной суммой (мастер-промпт Фазы 3, п.1 "SavingsPeriod").
 * Пример из промпта:
 *   01.01-31.03 -> 10 000; 01.04-30.06 -> 20 000; 01.07-31.07 -> 50 000; 01.08-31.12 -> 15 000
 *
 * Диапазоны одного плана не должны пересекаться — проверяется в SavingsPlanRepository при записи,
 * а не в самой сущности (Room-сущность остаётся простым носителем данных).
 */
@Entity(
    tableName = "savings_period",
    foreignKeys = [
        ForeignKey(
            entity = SavingsPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class SavingsPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Сумма для этого периода. Переопределяет SavingsPlanEntity.amount для FIXED_AMOUNT на этот диапазон дат. */
    val amount: BigDecimal,
) {
    fun contains(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(endDate)
}
