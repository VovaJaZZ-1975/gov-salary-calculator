package ru.govsalary.data.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import ru.govsalary.data.local.db.dao.FinancialGoalDao
import ru.govsalary.data.local.db.dao.SalaryHistoryDao
import ru.govsalary.data.local.db.dao.SavingsPeriodDao
import ru.govsalary.data.local.db.dao.SavingsPlanDao
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.SavingsPeriodEntity
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.ValueSource
import java.math.BigDecimal

/**
 * Фейковые DAO — реализуют те же интерфейсы (Room-аннотации на исполнение не влияют, это просто
 * метаданные для kapt/ksp), но хранят данные в памяти через MutableStateFlow.
 *
 * Это позволяет тестировать SalaryHistoryRepository / FinancialGoalRepository / SavingsPlanRepository /
 * ForecastService / WhatIfService / ImportExportService на чистом JVM БЕЗ реального Room/SQLite —
 * то есть без Robolectric и без Android-эмулятора. Настоящий тест самого Room/SQLite (SQL-запросы,
 * composite PK, TypeConverters) потребовал бы Robolectric/Android-окружения — такого файла в этом
 * наборе тестов сейчас нет (см. docs/build-status.md), т.к. это единственное, что нельзя было
 * проверить даже вручную (Decimal-реплика формул тут не помогает — это не арифметика, а Room API).
 */
class FakeSalaryHistoryDao : SalaryHistoryDao {
    private val state = MutableStateFlow<List<SalaryHistoryEntity>>(emptyList())

    override suspend fun upsert(entity: SalaryHistoryEntity) {
        state.value = state.value.filterNot { it.year == entity.year && it.month == entity.month && it.source == entity.source } + entity
    }

    override suspend fun upsertAll(entities: List<SalaryHistoryEntity>) {
        entities.forEach { upsert(it) }
    }

    override fun observeYear(year: Int) = state.asFilteredFlow { it.year == year }

    override fun observeMonth(year: Int, month: Int) = state.asFilteredFlow { it.year == year && it.month == month }

    override suspend fun get(year: Int, month: Int, source: ValueSource): SalaryHistoryEntity? =
        state.value.firstOrNull { it.year == year && it.month == month && it.source == source }

    override fun observeAvailableYears() = state.asMappedFlow { list -> list.map { it.year }.distinct().sorted() }

    override suspend fun getAll(): List<SalaryHistoryEntity> = state.value.sortedWith(compareBy({ it.year }, { it.month }))

    override fun observeRange(fromYear: Int, toYear: Int) = state.asFilteredFlow { it.year in fromYear..toYear }

    override suspend fun delete(year: Int, month: Int, source: ValueSource) {
        state.value = state.value.filterNot { it.year == year && it.month == month && it.source == source }
    }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}

class FakeFinancialGoalDao : FinancialGoalDao {
    private val state = MutableStateFlow<List<FinancialGoalEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entity: FinancialGoalEntity): Long {
        val id = if (entity.id != 0L) entity.id else nextId++
        state.value = state.value + entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: FinancialGoalEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(entity: FinancialGoalEntity) {
        state.value = state.value.filterNot { it.id == entity.id }
    }

    override fun observeAll() = state.asFilteredFlow { true }

    override fun observeByStatus(status: GoalStatus) = state.asFilteredFlow { it.status == status }

    override fun observeById(id: Long) = state.asMappedFlow { list -> list.firstOrNull { it.id == id } }

    override suspend fun updateCurrentAmount(id: Long, amount: BigDecimal) {
        state.value = state.value.map { if (it.id == id) it.copy(currentAmount = amount) else it }
    }
}

class FakeSavingsPlanDao : SavingsPlanDao {
    private val state = MutableStateFlow<List<SavingsPlanEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entity: SavingsPlanEntity): Long {
        val id = if (entity.id != 0L) entity.id else nextId++
        state.value = state.value + entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: SavingsPlanEntity) {
        state.value = state.value.map { if (it.id == entity.id) entity else it }
    }

    override suspend fun delete(entity: SavingsPlanEntity) {
        state.value = state.value.filterNot { it.id == entity.id }
    }

    override fun observeActive() = state.asFilteredFlow { it.isActive }
    override fun observeAll() = state.asFilteredFlow { true }
    override fun observeByGoal(goalId: Long) = state.asFilteredFlow { it.goalId == goalId }
}

class FakeSavingsPeriodDao : SavingsPeriodDao {
    private val state = MutableStateFlow<List<SavingsPeriodEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entity: SavingsPeriodEntity): Long {
        val id = if (entity.id != 0L) entity.id else nextId++
        state.value = state.value + entity.copy(id = id)
        return id
    }

    override suspend fun insertAll(entities: List<SavingsPeriodEntity>): List<Long> = entities.map { insert(it) }

    override suspend fun delete(entity: SavingsPeriodEntity) {
        state.value = state.value.filterNot { it.id == entity.id }
    }

    override fun observeByPlan(planId: Long) = state.asFilteredFlow { it.planId == planId }

    override suspend fun getByPlan(planId: Long): List<SavingsPeriodEntity> = state.value.filter { it.planId == planId }
}

// --- вспомогательные расширения для StateFlow<List<T>> -> Flow<List<T>>/Flow<R> с фильтром/маппингом ---
private fun <T> StateFlow<List<T>>.asFilteredFlow(predicate: (T) -> Boolean): Flow<List<T>> =
    map { list -> list.filter(predicate) }

private fun <T, R> StateFlow<List<T>>.asMappedFlow(transform: (List<T>) -> R): Flow<R> =
    map { list -> transform(list) }
