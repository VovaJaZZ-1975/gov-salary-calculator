package ru.govsalary.app.domain

import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.PeriodComparison
import ru.govsalary.data.repository.SalaryHistoryRepository
import java.time.Instant

/** История и ретроспектива (мастер-промпт Фазы 4, п.15). */
class HistoryUseCases(private val repository: SalaryHistoryRepository) {

    fun observeYear(year: Int): Flow<List<PeriodComparison>> = repository.observeYearComparison(year)

    fun observeAvailableYears(): Flow<List<Int>> = repository.observeAvailableYears()

    fun observeMonth(year: Int, month: Int): Flow<PeriodComparison> = repository.observeMonthComparison(year, month)

    /** Ввод факта пользователем вручную (мастер-промпт Фазы 3, п.4: "Actual"). */
    suspend fun saveActual(entity: SalaryHistoryEntity) {
        require(entity.source == ValueSource.ACTUAL)
        repository.saveActual(entity.copy(recordedAt = Instant.now()))
    }
}
