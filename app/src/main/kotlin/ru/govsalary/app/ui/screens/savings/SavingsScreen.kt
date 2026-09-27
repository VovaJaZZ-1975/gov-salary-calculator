package ru.govsalary.app.ui.screens.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.domain.GoalsAndSavingsUseCases
import ru.govsalary.app.ui.components.EmptyState
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.formatMoney
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.local.db.entity.SavingsPlanEntity
import ru.govsalary.data.local.db.entity.SavingsType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Мастер-промпт Фазы 4, п.17: 4 типа накопления + разные суммы по периодам (SavingsPeriod),
 * пример из промпта: янв-мар 10000, апр-июн 20000, июл 50000, авг-дек 15000.
 */
data class SavingsUiState(val plans: List<SavingsPlanEntity> = emptyList())

class SavingsViewModel(private val useCases: GoalsAndSavingsUseCases) : ViewModel() {

    val state = useCases.observeActivePlans()
        .map { plans -> SavingsUiState(plans) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SavingsUiState())

    fun createPlan(name: String, type: SavingsType, amount: BigDecimal?, percent: BigDecimal?) {
        viewModelScope.launch {
            useCases.createPlan(
                SavingsPlanEntity(
                    name = name, goalId = null, type = type, amount = amount, percent = percent,
                    startPeriod = LocalDate.now().withDayOfMonth(1), endPeriod = null, priority = 0, isActive = true,
                )
            )
        }
    }

    fun addPeriod(planId: Long, start: LocalDate, end: LocalDate, amount: BigDecimal) {
        viewModelScope.launch { useCases.addPeriod(ru.govsalary.data.local.db.entity.SavingsPeriodEntity(planId = planId, startDate = start, endDate = end, amount = amount)) }
    }
}

@Composable
fun SavingsRoute() {
    val container = rememberAppContainer()
    val viewModel = remember { SavingsViewModel(container.goalsAndSavingsUseCases) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SavingsScreen(
        state = state,
        onCreatePlan = viewModel::createPlan,
        observePeriods = { planId -> container.goalsAndSavingsUseCases.observePeriods(planId) },
        onAddPeriod = viewModel::addPeriod,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsScreen(
    state: SavingsUiState,
    onCreatePlan: (String, SavingsType, BigDecimal?, BigDecimal?) -> Unit,
    observePeriods: (Long) -> kotlinx.coroutines.flow.Flow<List<ru.govsalary.data.local.db.entity.SavingsPeriodEntity>>,
    onAddPeriod: (Long, LocalDate, LocalDate, BigDecimal) -> Unit,
) {
    var showAddPlanDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddPlanDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить план накопления")
            }
        },
    ) { padding ->
        if (state.plans.isEmpty()) {
            EmptyState(
                "Нет активных планов накопления. Поддерживаются: фиксированная сумма, процент от зарплаты, " +
                    "процент от премии, остаток после расходов — а также разные суммы по периодам года.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.plans) { plan -> PlanCard(plan, observePeriods, onAddPeriod) }
            }
        }
    }

    if (showAddPlanDialog) {
        AddPlanDialog(onDismiss = { showAddPlanDialog = false }, onConfirm = { name, type, amount, percent ->
            onCreatePlan(name, type, amount, percent)
            showAddPlanDialog = false
        })
    }
}

@Composable
private fun PlanCard(
    plan: SavingsPlanEntity,
    observePeriods: (Long) -> kotlinx.coroutines.flow.Flow<List<ru.govsalary.data.local.db.entity.SavingsPeriodEntity>>,
    onAddPeriod: (Long, LocalDate, LocalDate, BigDecimal) -> Unit,
) {
    val periods by observePeriods(plan.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddPeriod by remember { mutableStateOf(false) }

    SectionCard(title = plan.name) {
        Text(typeLabel(plan.type), style = MaterialTheme.typography.bodyMedium)
        when (plan.type) {
            SavingsType.FIXED_AMOUNT -> Text("По умолчанию: ${formatMoney(plan.amount ?: BigDecimal.ZERO)}/мес")
            SavingsType.SALARY_PERCENT -> Text("${plan.percent?.multiply(BigDecimal(100))}% от зарплаты")
            SavingsType.PREMIUM_PERCENT -> Text("${plan.percent?.multiply(BigDecimal(100))}% от премии")
            SavingsType.REMAINING_AMOUNT -> Text("Остаток после обязательных расходов")
        }
        if (plan.type == SavingsType.FIXED_AMOUNT) {
            Spacer(Modifier.height(8.dp))
            Text("Периоды с отдельной суммой:", style = MaterialTheme.typography.titleMedium)
            if (periods.isEmpty()) {
                Text("Периодов нет — используется сумма по умолчанию весь год", style = MaterialTheme.typography.bodyMedium)
            } else {
                periods.sortedBy { it.startDate }.forEach {
                    Text("${it.startDate} — ${it.endDate}: ${formatMoney(it.amount)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = { showAddPeriod = true }) { Text("+ Добавить период") }
        }
    }

    if (showAddPeriod) {
        AddPeriodDialog(
            onDismiss = { showAddPeriod = false },
            onConfirm = { start, end, amount ->
                onAddPeriod(plan.id, start, end, amount)
                showAddPeriod = false
            },
        )
    }
}

private fun typeLabel(type: SavingsType): String = when (type) {
    SavingsType.FIXED_AMOUNT -> "Фиксированная сумма"
    SavingsType.SALARY_PERCENT -> "Процент от зарплаты"
    SavingsType.PREMIUM_PERCENT -> "Процент от премии"
    SavingsType.REMAINING_AMOUNT -> "Остаток после расходов"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlanDialog(onDismiss: () -> Unit, onConfirm: (String, SavingsType, BigDecimal?, BigDecimal?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SavingsType.FIXED_AMOUNT) }
    var value by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый план накопления") },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Название плана") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton(onClick = { expanded = true }) { Text(typeLabel(type)) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        SavingsType.entries.forEach { t ->
                            DropdownMenuItem(text = { Text(typeLabel(t)) }, onClick = { type = t; expanded = false })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val valueLabel = if (type == SavingsType.FIXED_AMOUNT) "Сумма в месяц, ₽" else "Процент (например, 10 = 10%)"
                val valueIcon = if (type == SavingsType.FIXED_AMOUNT) Icons.Filled.Payments else Icons.Filled.Percent
                OutlinedTextField(
                    value, { value = it }, label = { Text(valueLabel) }, singleLine = true,
                    leadingIcon = { Icon(valueIcon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = value.trim().replace(',', '.').toBigDecimalOrNull() ?: return@Button
                if (name.isBlank()) return@Button
                when (type) {
                    SavingsType.FIXED_AMOUNT -> onConfirm(name.trim(), type, parsed, null)
                    else -> onConfirm(name.trim(), type, null, parsed.movePointLeft(2))
                }
            }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPeriodDialog(onDismiss: () -> Unit, onConfirm: (LocalDate, LocalDate, BigDecimal) -> Unit) {
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Период с отдельной суммой") },
        text = {
            Column {
                OutlinedTextField(
                    start, { start = it }, label = { Text("Начало (ГГГГ-ММ-ДД)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    end, { end = it }, label = { Text("Конец (ГГГГ-ММ-ДД)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Сумма, ₽") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val s = runCatching { LocalDate.parse(start.trim()) }.getOrNull() ?: return@Button
                val e = runCatching { LocalDate.parse(end.trim()) }.getOrNull() ?: return@Button
                val a = amount.trim().replace(',', '.').toBigDecimalOrNull() ?: return@Button
                onConfirm(s, e, a)
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Preview(name = "Savings - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun SavingsPreview() {
    GovSalaryTheme {
        SavingsScreen(
            state = SavingsUiState(
                listOf(
                    SavingsPlanEntity(1, "На отпуск", null, SavingsType.FIXED_AMOUNT, BigDecimal("15000"), null, LocalDate.now(), null, 0, true),
                ),
            ),
            onCreatePlan = { _, _, _, _ -> },
            observePeriods = { kotlinx.coroutines.flow.flowOf(emptyList()) },
            onAddPeriod = { _, _, _, _ -> },
        )
    }
}
