package ru.govsalary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus

@Dao
interface FinancialGoalDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FinancialGoalEntity): Long

    @Update
    suspend fun update(entity: FinancialGoalEntity)

    @Delete
    suspend fun delete(entity: FinancialGoalEntity)

    @Query("SELECT * FROM financial_goal ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FinancialGoalEntity>>

    @Query("SELECT * FROM financial_goal WHERE status = :status ORDER BY targetDate ASC")
    fun observeByStatus(status: GoalStatus): Flow<List<FinancialGoalEntity>>

    @Query("SELECT * FROM financial_goal WHERE id = :id")
    fun observeById(id: Long): Flow<FinancialGoalEntity?>

    @Query("UPDATE financial_goal SET currentAmount = :amount WHERE id = :id")
    suspend fun updateCurrentAmount(id: Long, amount: java.math.BigDecimal)
}
