package ru.govsalary.app.ui.screens.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.domain.CalculatorInput
import ru.govsalary.app.domain.CalculatorUseCases
import ru.govsalary.app.ui.components.AnimatedMoneyValue
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.navigation.LocalWindowWidthSizeClass
import ru.govsalary.app.ui.theme.AppThemeMode
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.calculation.model.PaymentType
import ru.govsalary.calculation.model.SalaryCalculationResult
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Состояние экрана "Расчёт" (мастер-промпт Фазы 4, п.10). Поля — точное соответствие
 * excel-analysis.md: оклад, классный чин, ЕДП, выслуга, особые условия, гостайна, премия,
 * рабочие дни, отработанные дни.
 */
data class CalculatorUiState(
    val year: Int,
    val month: Int,
    val baseSalary: String = "24296",
    val rankSalary: String = "13853",
    val monthlyBonusPercent: String = "90",   // ЕДП, в процентах для удобства ввода (0.9 = 90%)
    val seniorityPercent: String = "10",
    val specialConditionsPercent: String = "120",
    val stateSecretPercent: String = "10",
    val premium: String = "0",
    val totalWorkingDays: String = "22",
    val workingDaysUpTo15: String = "11",
    val actualWorkedDaysUpTo15: String = "",   // пусто = равно плановым (полный месяц, WorkCalendar.kt)
    val actualWorkedDaysAfter15: String = "",
    val result: SalaryCalculationResult? = null,
    val error: String? = null,
)

/** UI -> ViewModel -> UseCase -> Calculation Engine -> Result -> UI (мастер-промпт Фазы 4, п.11). */
class CalculatorViewModel(private val useCases: CalculatorUseCases) : ViewModel() {

    private val today = LocalDate.now()
    private val _state = MutableStateFlow(CalculatorUiState(year = today.year, month = today.monthValue))
    val state: StateFlow<CalculatorUiState> = _state.asStateFlow()

    init {
        recalculate()
    }

    fun update(transform: (CalculatorUiState) -> CalculatorUiState) {
        _state.value = transform(_state.value)
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val input = s.toInputOrNull()
        if (input == null) {
            _state.value = s.copy(result = null, error = "Проверьте введённые значения")
            return
        }
        try {
            val result = useCases.calculate(input)
            _state.value = _state.value.copy(result = result, error = null)
        } catch (e: IllegalArgumentException) {
            _state.value = _state.value.copy(result = null, error = e.message)
        }
    }

    fun saveAsCalculated() {
        val s = _state.value
        val input = s.toInputOrNull() ?: return
        val result = s.result ?: return
        viewModelScope.launch { useCases.saveAsCalculated(input, result) }
    }
}

private fun CalculatorUiState.toInputOrNull(): CalculatorInput? {
    val base = baseSalary.toMoneyOrNull() ?: return null
    val rank = rankSalary.toMoneyOrNull() ?: return null
    val edp = monthlyBonusPercent.toMoneyOrNull()?.movePointLeft(2) ?: return null
    val seniority = seniorityPercent.toMoneyOrNull()?.movePointLeft(2) ?: return null
    val special = specialConditionsPercent.toMoneyOrNull()?.movePointLeft(2) ?: return null
    val secret = stateSecretPercent.toMoneyOrNull()?.movePointLeft(2) ?: return null
    val premiumValue = premium.toMoneyOrNull() ?: return null
    val totalDays = totalWorkingDays.toIntOrNull() ?: return null
    val upTo15 = workingDaysUpTo15.toIntOrNull() ?: return null
    if (upTo15 > totalDays || totalDays <= 0) return null
    val actualUpTo15 = actualWorkedDaysUpTo15.toIntOrNull() ?: upTo15
    val actualAfter15 = actualWorkedDaysAfter15.toIntOrNull() ?: (totalDays - upTo15)

    return CalculatorInput(
        year = year, month = month, baseSalary = base, rankSalary = rank,
        monthlyBonusRate = edp, seniorityRate = seniority, specialConditionsRate = special,
        stateSecretRate = secret, premium = premiumValue, totalWorkingDays = totalDays,
        workingDaysUpTo15 = upTo15, actualWorkedDaysUpTo15 = actualUpTo15, actualWorkedDaysAfter15 = actualAfter15,
        previousMonthTotalWorkingDays = null, previousMonthWorkingDaysUpTo15 = null,
        previousMonthActualWorkedDaysAfter15 = null,
    )
}

private fun String.toMoneyOrNull(): BigDecimal? = this.trim().replace(',', '.').toBigDecimalOrNull()

@Composable
fun CalculatorRoute() {
    val container = rememberAppContainer()
    val viewModel = remember { CalculatorViewModel(container.calculatorUseCases) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    CalculatorScreen(
        state = state,
        isTablet = windowWidthSizeClass != WindowWidthSizeClass.Compact,
        onUpdate = viewModel::update,
        onSave = viewModel::saveAsCalculated,
    )
}

@Composable
fun CalculatorScreen(
    state: CalculatorUiState,
    isTablet: Boolean,
    onUpdate: ((CalculatorUiState) -> CalculatorUiState) -> Unit,
    onSave: () -> Unit,
) {
    if (isTablet) {
        // Мастер-промпт Фазы 4, п.13: параметры слева, результат справа.
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { ParametersForm(state, onUpdate, onSave) }
            }
            LazyColumn(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { ResultSection(state) }
            }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ParametersForm(state, onUpdate, onSave) }
            item { ResultSection(state) }
        }
    }
}

@Composable
private fun ParametersForm(
    state: CalculatorUiState,
    onUpdate: ((CalculatorUiState) -> CalculatorUiState) -> Unit,
    onSave: () -> Unit,
) {
    SectionCard(title = "Параметры расчёта — ${state.month}/${state.year}") {
        NumberField("Должностной оклад, ₽", state.baseSalary, Icons.Filled.Payments) { v -> onUpdate { it.copy(baseSalary = v) } }
        NumberField("Оклад за классный чин, ₽", state.rankSalary, Icons.Filled.MilitaryTech) { v -> onUpdate { it.copy(rankSalary = v) } }
        NumberField("ЕДП, % от оклада", state.monthlyBonusPercent, Icons.Filled.CardGiftcard) { v -> onUpdate { it.copy(monthlyBonusPercent = v) } }
        NumberField("Выслуга лет, % от оклада", state.seniorityPercent, Icons.Filled.Schedule) { v -> onUpdate { it.copy(seniorityPercent = v) } }
        NumberField("Особые условия, % от оклада", state.specialConditionsPercent, Icons.Filled.Shield) { v -> onUpdate { it.copy(specialConditionsPercent = v) } }
        NumberField("Гостайна, % от оклада", state.stateSecretPercent, Icons.Filled.Lock) { v -> onUpdate { it.copy(stateSecretPercent = v) } }
        NumberField("Премия, ₽", state.premium, Icons.Filled.EmojiEvents) { v -> onUpdate { it.copy(premium = v) } }
        NumberField("Рабочих дней в месяце", state.totalWorkingDays, Icons.Filled.DateRange) { v -> onUpdate { it.copy(totalWorkingDays = v) } }
        NumberField("Рабочих дней до 15 числа", state.workingDaysUpTo15, Icons.Filled.CalendarViewWeek) { v -> onUpdate { it.copy(workingDaysUpTo15 = v) } }
        NumberField("Отработано до 15 (пусто = все)", state.actualWorkedDaysUpTo15, Icons.Filled.CheckCircle) { v -> onUpdate { it.copy(actualWorkedDaysUpTo15 = v) } }
        NumberField("Отработано после 15 (пусто = все)", state.actualWorkedDaysAfter15, Icons.Filled.CheckCircleOutline) { v -> onUpdate { it.copy(actualWorkedDaysAfter15 = v) } }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, enabled = state.result != null) { Text("Сохранить как расчётное значение") }
    }
}

@Composable
private fun NumberField(label: String, value: String, leadingIcon: ImageVector, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null, // декоративная иконка — смысл уже передан текстом label
                tint = MaterialTheme.colorScheme.secondary, // золотой акцент темы (Theme.kt) — визуально отличает поля друг от друга
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun ResultSection(state: CalculatorUiState) {
    val result = state.result
    SectionCard(title = "Результат") {
        if (result == null) {
            Text("Введите корректные параметры для расчёта", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        val advance = result.payments.firstOrNull { it.type == PaymentType.MID_MONTH_ADVANCE }
        val finalPay = result.payments.firstOrNull { it.type == PaymentType.START_OF_MONTH_FINAL }

        AnimatedMoneyValue(result.accrued, "Начислено")
        Spacer(Modifier.height(8.dp))
        AnimatedMoneyValue(result.ndfl, "НДФЛ")
        Spacer(Modifier.height(8.dp))
        AnimatedMoneyValue(result.totalPayout, "К выплате")
        Spacer(Modifier.height(8.dp))
        AnimatedMoneyValue(advance?.amount ?: BigDecimal.ZERO, "Аванс (19 число)")
        Spacer(Modifier.height(8.dp))
        AnimatedMoneyValue(finalPay?.amount ?: BigDecimal.ZERO, "Основная выплата (4 число)")
        Spacer(Modifier.height(8.dp))
        AnimatedMoneyValue(result.premium, "Премия")
    }
}

// ---------- Compose Previews (мастер-промпт Фазы 4, п.24) ----------

private val previewState = CalculatorUiState(year = 2026, month = 2)

@Preview(name = "Phone Compact Portrait", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun CalculatorPreviewPhonePortrait() {
    GovSalaryTheme { CalculatorScreen(previewState, isTablet = false, onUpdate = {}, onSave = {}) }
}

@Preview(name = "Phone Compact Landscape", widthDp = 780, heightDp = 360, showBackground = true)
@Composable
private fun CalculatorPreviewPhoneLandscape() {
    GovSalaryTheme { CalculatorScreen(previewState, isTablet = false, onUpdate = {}, onSave = {}) }
}

@Preview(name = "Tablet Medium Portrait", widthDp = 700, heightDp = 900, showBackground = true)
@Composable
private fun CalculatorPreviewTabletPortrait() {
    GovSalaryTheme { CalculatorScreen(previewState, isTablet = true, onUpdate = {}, onSave = {}) }
}

@Preview(name = "Tablet Expanded Landscape", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
private fun CalculatorPreviewTabletLandscape() {
    GovSalaryTheme { CalculatorScreen(previewState, isTablet = true, onUpdate = {}, onSave = {}) }
}

@Preview(name = "Dark Theme", widthDp = 360, heightDp = 780, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CalculatorPreviewDark() {
    GovSalaryTheme(themeMode = AppThemeMode.DARK) {
        CalculatorScreen(previewState, isTablet = false, onUpdate = {}, onSave = {})
    }
}

@Preview(name = "Large Font", widthDp = 360, heightDp = 780, fontScale = 1.8f, showBackground = true)
@Composable
private fun CalculatorPreviewLargeFont() {
    GovSalaryTheme { CalculatorScreen(previewState, isTablet = false, onUpdate = {}, onSave = {}) }
}
