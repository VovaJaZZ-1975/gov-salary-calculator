package ru.govsalary.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.govsalary.data.local.db.dao.SalaryHistoryDao
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource

/**
 * Пара "расчётное / фактическое" значение за один период (мастер-промпт Фазы 3, п.4).
 * Намеренно НЕ содержит единого "объединённого" значения — вызывающий код (UI, Фаза 4)
 * сам решает, что показать, но всегда видит источник каждого из двух возможных значений.
 */
data class PeriodComparison(
    val year: Int,
    val month: Int,
    val calculated: SalaryHistoryEntity?,
    val actual: SalaryHistoryEntity?,
) {
    /** true, если оба значения есть и расходятся более чем на 0.01 (например, факт отличается от плана). */
    fun hasDiscrepancy(): Boolean {
        val c = calculated?.totalPayout ?: return false
        val a = actual?.totalPayout ?: return false
        return c.subtract(a).abs() > java.math.BigDecimal("0.01")
    }
}

class SalaryHistoryRepository(private val dao: SalaryHistoryDao) {

    suspend fun saveCalculated(entity: SalaryHistoryEntity) {
        require(entity.source == ValueSource.CALCULATED) { "saveCalculated ожидает source=CALCULATED" }
        dao.upsert(entity)
    }

    suspend fun saveActual(entity: SalaryHistoryEntity) {
        require(entity.source == ValueSource.ACTUAL) { "saveActual ожидает source=ACTUAL" }
        dao.upsert(entity)
    }

    fun observeYear(year: Int): Flow<List<SalaryHistoryEntity>> = dao.observeYear(year)

    fun observeAvailableYears(): Flow<List<Int>> = dao.observeAvailableYears()

    /** Ретроспектива по году: разбивка помесячно с явным разделением источника (мастер-промпт Фазы 3, п.3). */
    fun observeYearComparison(year: Int): Flow<List<PeriodComparison>> =
        dao.observeYear(year).map { rows ->
            (1..12).map { month ->
                PeriodComparison(
                    year = year,
                    month = month,
                    calculated = rows.firstOrNull { it.month == month && it.source == ValueSource.CALCULATED },
                    actual = rows.firstOrNull { it.month == month && it.source == ValueSource.ACTUAL },
                )
            }
        }

    fun observeMonthComparison(year: Int, month: Int): Flow<PeriodComparison> =
        dao.observeMonth(year, month).map { rows ->
            PeriodComparison(
                year = year,
                month = month,
                calculated = rows.firstOrNull { it.source == ValueSource.CALCULATED },
                actual = rows.firstOrNull { it.source == ValueSource.ACTUAL },
            )
        }

    /** Сравнение двух произвольных периодов (мастер-промпт Фазы 3, п.3 "сравнения периодов"). */
    suspend fun comparePeriods(
        yearA: Int, monthA: Int,
        yearB: Int, monthB: Int,
        source: ValueSource = ValueSource.ACTUAL,
    ): Pair<SalaryHistoryEntity?, SalaryHistoryEntity?> =
        dao.get(yearA, monthA, source) to dao.get(yearB, monthB, source)

    suspend fun getAll(): List<SalaryHistoryEntity> = dao.getAll()

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun upsertAll(entities: List<SalaryHistoryEntity>) = dao.upsertAll(entities)
}
