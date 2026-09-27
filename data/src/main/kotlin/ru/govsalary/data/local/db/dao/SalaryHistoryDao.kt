package ru.govsalary.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource

@Dao
interface SalaryHistoryDao {

    /** REPLACE осознанно: (year, month, source) — естественный ключ факта/расчёта, повторная запись = актуализация. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SalaryHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SalaryHistoryEntity>)

    @Query("SELECT * FROM salary_history WHERE year = :year ORDER BY month ASC")
    fun observeYear(year: Int): Flow<List<SalaryHistoryEntity>>

    @Query("SELECT * FROM salary_history WHERE year = :year AND month = :month")
    fun observeMonth(year: Int, month: Int): Flow<List<SalaryHistoryEntity>>

    @Query("SELECT * FROM salary_history WHERE year = :year AND month = :month AND source = :source LIMIT 1")
    suspend fun get(year: Int, month: Int, source: ValueSource): SalaryHistoryEntity?

    @Query("SELECT DISTINCT year FROM salary_history ORDER BY year ASC")
    fun observeAvailableYears(): Flow<List<Int>>

    @Query("SELECT * FROM salary_history ORDER BY year ASC, month ASC")
    suspend fun getAll(): List<SalaryHistoryEntity>

    @Query("SELECT * FROM salary_history WHERE year BETWEEN :fromYear AND :toYear ORDER BY year ASC, month ASC")
    fun observeRange(fromYear: Int, toYear: Int): Flow<List<SalaryHistoryEntity>>

    @Query("DELETE FROM salary_history WHERE year = :year AND month = :month AND source = :source")
    suspend fun delete(year: Int, month: Int, source: ValueSource)

    @Query("DELETE FROM salary_history")
    suspend fun deleteAll()
}
