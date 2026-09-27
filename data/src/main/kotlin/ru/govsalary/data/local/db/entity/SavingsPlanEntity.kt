package ru.govsalary.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/**
 * План накопления (мастер-промпт Фазы 3, п.1 "SavingsPlan" и п.2 "Типы накоплений").
 *
 * - `amount` используется для типа FIXED_AMOUNT как значение по умолчанию для периодов,
 *   не покрытых ни одной SavingsPeriodEntity (см. SavingsCalculator.kt).
 * - `percent` используется для SALARY_PERCENT / PREMIUM_PERCENT (доля, например 0.10 = 10%).
 * - для REMAINING_AMOUNT оба поля игнорируются движком (SavingsCalculator берёт остаток).
 */
@Entity(
    tableName = "savings_plan",
    foreignKeys = [
        ForeignKey(
            entity = FinancialGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("goalId")],
)
data class SavingsPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Финансовая цель, на которую направлен план. Может быть null — план "просто накопление".
     *  Индекс объявлен один раз — в @Entity.indices выше (см. также ошибку сборки:
     *  "multiple indices with name index_savings_plan_goalId" при одновременном
     *  @ColumnInfo(index = true) и indices = [Index("goalId")]). */
    val goalId: Long?,
    val type: SavingsType,
    val amount: BigDecimal?,
    val percent: BigDecimal?,
    val startPeriod: LocalDate,
    val endPeriod: LocalDate?,
    val priority: Int,
    val isActive: Boolean,
)
