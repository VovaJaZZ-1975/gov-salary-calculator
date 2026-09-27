package ru.govsalary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity

@Dao
interface SavingsPlanDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SavingsPlanEntity): Long

    @Update
    suspend fun update(entity: SavingsPlanEntity)

    @Delete
    suspend fun delete(entity: SavingsPlanEntity)

    @Query("SELECT * FROM savings_plan WHERE isActive = 1 ORDER BY priority ASC")
    fun observeActive(): Flow<List<SavingsPlanEntity>>

    @Query("SELECT * FROM savings_plan ORDER BY priority ASC")
    fun observeAll(): Flow<List<SavingsPlanEntity>>

    @Query("SELECT * FROM savings_plan WHERE goalId = :goalId ORDER BY priority ASC")
    fun observeByGoal(goalId: Long): Flow<List<SavingsPlanEntity>>
}

@Dao
interface SavingsPeriodDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SavingsPeriodEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<SavingsPeriodEntity>): List<Long>

    @Delete
    suspend fun delete(entity: SavingsPeriodEntity)

    @Query("SELECT * FROM savings_period WHERE planId = :planId ORDER BY startDate ASC")
    fun observeByPlan(planId: Long): Flow<List<SavingsPeriodEntity>>

    @Query("SELECT * FROM savings_period WHERE planId = :planId ORDER BY startDate ASC")
    suspend fun getByPlan(planId: Long): List<SavingsPeriodEntity>
}
