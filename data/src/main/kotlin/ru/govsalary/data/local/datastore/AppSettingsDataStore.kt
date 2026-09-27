package ru.govsalary.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore — только для лёгких настроек приложения (мастер-промпт Фазы 3, п.10):
 * тема, выбранный год/месяц, настройки отображения, пользовательские параметры UI.
 *
 * НЕ используется для исторических данных зарплаты/накоплений/целей — это ответственность
 * Room (см. AppDatabase.kt), как явно требует пункт 10 мастер-промпта.
 */
enum class AppTheme { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val selectedYear: Int,
    val selectedMonth: Int,
    /** Показывать ли одновременно ACTUAL и CALCULATED значения в списках (мастер-промпт Фазы 3, п.4). */
    val showActualAndCalculatedSideBySide: Boolean = true,
    /** Число знаков после запятой в UI (не влияет на расчёт — только на отображение). */
    val displayDecimalPlaces: Int = 2,
)

class AppSettingsDataStore(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SELECTED_YEAR = intPreferencesKey("selected_year")
        val SELECTED_MONTH = intPreferencesKey("selected_month")
        val SHOW_ACTUAL_AND_CALCULATED = booleanPreferencesKey("show_actual_and_calculated")
        val DISPLAY_DECIMAL_PLACES = intPreferencesKey("display_decimal_places")
    }

    /** По умолчанию — текущий год/месяц на момент первого запуска; далее хранится выбор пользователя. */
    fun observe(defaultYear: Int, defaultMonth: Int): Flow<AppSettings> =
        dataStore.data.map { prefs ->
            AppSettings(
                theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
                selectedYear = prefs[Keys.SELECTED_YEAR] ?: defaultYear,
                selectedMonth = prefs[Keys.SELECTED_MONTH] ?: defaultMonth,
                showActualAndCalculatedSideBySide = prefs[Keys.SHOW_ACTUAL_AND_CALCULATED] ?: true,
                displayDecimalPlaces = prefs[Keys.DISPLAY_DECIMAL_PLACES] ?: 2,
            )
        }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setSelectedPeriod(year: Int, month: Int) {
        require(month in 1..12) { "Месяц должен быть в диапазоне 1..12" }
        dataStore.edit {
            it[Keys.SELECTED_YEAR] = year
            it[Keys.SELECTED_MONTH] = month
        }
    }

    suspend fun setShowActualAndCalculatedSideBySide(value: Boolean) {
        dataStore.edit { it[Keys.SHOW_ACTUAL_AND_CALCULATED] = value }
    }

    suspend fun setDisplayDecimalPlaces(value: Int) {
        require(value in 0..4) { "Некорректное число знаков после запятой: $value" }
        dataStore.edit { it[Keys.DISPLAY_DECIMAL_PLACES] = value }
    }
}
