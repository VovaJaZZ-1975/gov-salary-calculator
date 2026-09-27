package ru.govsalary.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/** Финансовая цель (мастер-промпт Фазы 3, п.1 "FinancialGoal" и п.5). */
@Entity(tableName = "financial_goal")
data class FinancialGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String?,
    val targetAmount: BigDecimal,
    val currentAmount: BigDecimal,
    val createdAt: LocalDate,
    val targetDate: LocalDate?,
    val status: GoalStatus,
)
