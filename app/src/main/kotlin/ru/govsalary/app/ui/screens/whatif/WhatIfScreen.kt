package ru.govsalary.app.ui.screens.whatif

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Payments
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
import ru.govsalary.app.domain.GoalsAndSavingsUseCases
import ru.govsalary.app.domain.WhatIfUseCases
import ru.govsalary.app.ui.components.MoneyText
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.formatMoney
import ru.govsalary.app.ui.navigation.LocalWindowWidthSizeClass
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.calculation.whatif.ScenarioOverrides
import ru.govsalary.data.domain.WhatIfResult
import java.math.BigDecimal
import java.time.LocalDate

/**
 * What-If (мастер-промпт Фазы 4, п.18). ВАЖНО: этот экран и его ViewModel не вызывают ни одного
 * *Repository для ЗАПИСИ — все данные существуют только в памяти этого экрана (ScenarioUiState),
 * что буквально гарантируется тем, что WhatIfUseCases/WhatIfService физически не принимают
 * ни одного Repository (см. Фаза 3, WhatIfService.kt).
 */
data class WhatIfUiState(
    val currentBaseSalary: String = "24296",
    val scenarioBaseSalary: String = "24296",
    val currentPremium: String = "0",
    val scenarioPremium: String = "0",
    val result: WhatIfResult? = null,
    val savingsDelta: BigDecimal? = null,
)

class WhatIfViewModel(
    private val useCases: WhatIfUseCases,
    private val goalsUseCases: GoalsAndSavingsUseCases,
) : ViewModel() {

    private val today = LocalDate.now()
    private val _state = MutableStateFlow(WhatIfUiState())
    val state: StateFlow<WhatIfUiState> = _state.asStateFlow()

    fun update(transform: (WhatIfUiState) -> WhatIfUiState) {
        _state.value = transform(_state.value)
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val currentBase = s.currentBaseSalary.toMoneyOrNull() ?: return
        val scenarioBase = s.scenarioBaseSalary.toMoneyOrNull() ?: return
        val currentPremium = s.currentPremium.toMoneyOrNull() ?: return
        val scenarioPremium = s.scenarioPremium.toMoneyOrNull() ?: return

        val baseInput = CalculatorInput(
            year = today.year, month = today.monthValue, baseSalary = currentBase, rankSalary = BigDecimal("13853"),
            monthlyBonusRate = BigDecimal("0.9"), seniorityRate = BigDecimal("0.1"), specialConditionsRate = BigDecimal("1.2"),
            stateSecretRate = BigDecimal("0.1"), premium = currentPremium, totalWorkingDays = 22, workingDaysUpTo15 = 11,
            actualWorkedDaysUpTo15 = 11, actualWorkedDaysAfter15 = 11,
            previousMonthTotalWorkingDays = null, previousMonthWorkingDaysUpTo15 = null, previousMonthActualWorkedDaysAfter15 = null,
        )
        val overrides = ScenarioOverrides(
            baseSalary = if (scenarioBase != currentBase) scenarioBase else null,
            premiumOverride = if (scenarioPremium != currentPremium) scenarioPremium else null,
        )
        val result = useCases.run(baseInput, overrides)

        viewModelScope.launch {
            // Оценка "изменение накоплений в месяц" — read-only расчёт через тот же SavingsCalculator,
            // что и ForecastService (Фаза 3), без единой записи в Room (п.18: "работает только в памяти").
            val currentSaving = goalsUseCases.estimateMonthlySaving(LocalDate.now(), result.currentTotalPayout, currentPremium)
            val scenarioSaving = goalsUseCases.estimateMonthlySaving(LocalDate.now(), result.scenarioTotalPayout, scenarioPremium)
            _state.value = _state.value.copy(result = result, savingsDelta = scenarioSaving.subtract(currentSaving))
        }
    }
}

private fun String.toMoneyOrNull(): BigDecimal? = trim().replace(',', '.').toBigDecimalOrNull()

@Composable
fun WhatIfRoute() {
    val container = rememberAppContainer()
    val viewModel = remember { WhatIfViewModel(container.whatIfUseCases, container.goalsAndSavingsUseCases) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTablet = LocalWindowWidthSizeClass.current != WindowWidthSizeClass.Compact
    WhatIfScreen(state, isTablet, viewModel::update)
}

@Composable
fun WhatIfScreen(state: WhatIfUiState, isTablet: Boolean, onUpdate: ((WhatIfUiState) -> WhatIfUiState) -> Unit) {
    val content = @Composable {
        SectionCard(title = "Текущая зарплата") {
            NumberField("Должностной оклад, ₽", state.currentBaseSalary, Icons.Filled.Payments) { v -> onUpdate { it.copy(currentBaseSalary = v) } }
            NumberField("Премия, ₽", state.currentPremium, Icons.Filled.EmojiEvents) { v -> onUpdate { it.copy(currentPremium = v) } }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Новая зарплата (сценарий)") {
            NumberField("Должностной оклад, ₽", state.scenarioBaseSalary, Icons.Filled.Payments) { v -> onUpdate { it.copy(scenarioBaseSalary = v) } }
            NumberField("Премия, ₽", state.scenarioPremium, Icons.Filled.EmojiEvents) { v -> onUpdate { it.copy(scenarioPremium = v) } }
        }
        Spacer(Modifier.height(12.dp))
        ResultCard(state)
    }

    if (isTablet) {
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            LazyColumn(Modifier.weight(1f)) { item { content() } }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { content() } }
    }
}

@Composable
private fun ResultCard(state: WhatIfUiState) {
    val result = state.result ?: return
    SectionCard(title = "Сравнение") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Текущий вариант", style = MaterialTheme.typography.bodyMedium); MoneyText(result.currentTotalPayout) }
            Column { Text("Новый вариант", style = MaterialTheme.typography.bodyMedium); MoneyText(result.scenarioTotalPayout) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Разница: ${formatMoney(result.difference)}", style = MaterialTheme.typography.titleMedium)
        Text(
            "Изменение: " + (result.percentChange?.let { "${it}%" } ?: "н/д (текущая сумма равна нулю)"),
            style = MaterialTheme.typography.bodyMedium,
        )
        state.savingsDelta?.let {
            Spacer(Modifier.height(8.dp))
            Text("Изменение накоплений в месяц (по активным планам): ${formatMoney(it)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "Сценарий существует только на этом экране — реальные данные не изменены.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, leadingIcon: ImageVector, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null, // декоративная иконка — смысл уже передан текстом label
                tint = MaterialTheme.colorScheme.secondary,
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Preview(name = "WhatIf - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun WhatIfPreview() {
    GovSalaryTheme { WhatIfScreen(WhatIfUiState(), isTablet = false, onUpdate = {}) }
}
