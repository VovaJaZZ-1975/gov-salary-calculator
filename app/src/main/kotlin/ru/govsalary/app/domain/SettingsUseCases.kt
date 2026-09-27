package ru.govsalary.app.domain

import kotlinx.coroutines.flow.Flow
import ru.govsalary.data.local.datastore.AppSettings
import ru.govsalary.data.local.datastore.AppSettingsDataStore
import ru.govsalary.data.local.datastore.AppTheme
import java.time.LocalDate

/** Настройки (мастер-промпт Фазы 4, п.20) — только DataStore, без Room (Фаза 3, п.10). */
class SettingsUseCases(private val store: AppSettingsDataStore) {

    fun observe(): Flow<AppSettings> {
        val now = LocalDate.now()
        return store.observe(defaultYear = now.year, defaultMonth = now.monthValue)
    }

    suspend fun setTheme(theme: AppTheme) = store.setTheme(theme)
    suspend fun setSelectedPeriod(year: Int, month: Int) = store.setSelectedPeriod(year, month)
    suspend fun setShowActualAndCalculatedSideBySide(value: Boolean) = store.setShowActualAndCalculatedSideBySide(value)
}
