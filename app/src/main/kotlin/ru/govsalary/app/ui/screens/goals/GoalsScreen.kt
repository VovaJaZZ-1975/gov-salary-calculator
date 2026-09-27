package ru.govsalary.app.ui.screens.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.domain.GoalsAndSavingsUseCases
import ru.govsalary.app.ui.components.AnimatedProgressBar
import ru.govsalary.app.ui.components.EmptyState
import ru.govsalary.app.ui.components.MoneyText
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.formatMoney
import ru.govsalary.app.ui.navigation.Destination
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.domain.GoalProgress
import ru.govsalary.data.local.db.entity.FinancialGoalEntity
import ru.govsalary.data.local.db.entity.GoalStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** Мастер-промпт Фазы 4, п.16: несколько целей (Автомобиль/Отпуск/Ремонт/Резерв), прогресс, прогноз. */
data class GoalsUiState(
    val goalsWithProgress: List<Pair<FinancialGoalEntity, GoalProgress?>> = emptyList(),
)

class GoalsViewModel(private val useCases: GoalsAndSavingsUseCases) : ViewModel() {

    private val today = LocalDate.now()

    val state = useCases.observeGoals().flatMapLatest { goals ->
        if (goals.isEmpty()) {
            flowOf(GoalsUiState(emptyList()))
        } else {
            combine(goals.map { g -> useCases.observeGoalProgress(g.id.toInt(), today.year, today.monthValue) }) { progresses ->
                GoalsUiState(goals.zip(progresses.toList()))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoalsUiState())

    fun addGoal(title: String, targetAmount: BigDecimal, targetDate: LocalDate?) {
        viewModelScope.launch {
            useCases.createGoal(
                FinancialGoalEntity(
                    title = title, description = null, targetAmount = targetAmount,
                    currentAmount = BigDecimal.ZERO, createdAt = LocalDate.now(),
                    targetDate = targetDate, status = GoalStatus.ACTIVE,
                )
            )
        }
    }

    fun addContribution(goal: FinancialGoalEntity, amount: BigDecimal) {
        viewModelScope.launch { useCases.addContribution(goal.id, amount, goal.currentAmount) }
    }
}

@Composable
fun GoalsRoute(navController: NavHostController) {
    val container = rememberAppContainer()
    val viewModel = remember { GoalsViewModel(container.goalsAndSavingsUseCases) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoalsScreen(
        state = state,
        onAddGoal = viewModel::addGoal,
        onAddContribution = viewModel::addContribution,
        onOpenSavings = { navController.navigate(Destination.SAVINGS.route) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    state: GoalsUiState,
    onAddGoal: (String, BigDecimal, LocalDate?) -> Unit,
    onAddContribution: (FinancialGoalEntity, BigDecimal) -> Unit,
    onOpenSavings: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить цель")
            }
        },
    ) { padding ->
        if (state.goalsWithProgress.isEmpty()) {
            EmptyState(
                "Пока нет финансовых целей. Например: новый автомобиль, отпуск, ремонт, резерв — нажмите «+», чтобы добавить первую.",
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.goalsWithProgress) { (goal, progress) ->
                    GoalCard(goal, progress, onAddContribution, onOpenSavings)
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(onDismiss = { showAddDialog = false }, onConfirm = { title, amount, date ->
            onAddGoal(title, amount, date)
            showAddDialog = false
        })
    }
}

@Composable
private fun GoalCard(
    goal: FinancialGoalEntity,
    progress: GoalProgress?,
    onAddContribution: (FinancialGoalEntity, BigDecimal) -> Unit,
    onOpenSavings: () -> Unit,
) {
    SectionCard(title = goal.title) {
        goal.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Цель", style = MaterialTheme.typography.bodyMedium); MoneyText(goal.targetAmount) }
            Column { Text("Накоплено", style = MaterialTheme.typography.bodyMedium); MoneyText(goal.currentAmount) }
        }
        Spacer(Modifier.height(8.dp))
        val fraction = progress?.progressPercent?.divide(BigDecimal(100), 4, RoundingMode.HALF_UP)?.toFloat() ?: 0f
        AnimatedProgressBar(fraction)
        Spacer(Modifier.height(4.dp))
        val remaining = goal.targetAmount.subtract(goal.currentAmount).max(BigDecimal.ZERO)
        Text(
            "${progress?.progressPercent ?: BigDecimal.ZERO}% • осталось ${formatMoney(remaining)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        goal.targetDate?.let { Text("Целевая дата: $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            "Прогноз: " + (progress?.forecastDate?.toString() ?: "нет активного плана накопления"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        progress?.let {
            Text(
                "Необходимо в месяц (по текущему плану): ${formatMoney(it.projectedMonthlySaving)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAddContribution(goal, BigDecimal("1000")) }) { Text("+ 1000 ₽ вручную") }
            TextButton(onClick = onOpenSavings) { Text("План накопления") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(onDismiss: () -> Unit, onConfirm: (String, BigDecimal, LocalDate?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая финансовая цель") },
        text = {
            Column {
                OutlinedTextField(
                    title, { title = it }, label = { Text("Название (например, «Отпуск»)") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Целевая сумма, ₽") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = amount.trim().replace(',', '.').toBigDecimalOrNull()
                    if (title.isNotBlank() && parsed != null && parsed.signum() > 0) {
                        onConfirm(title.trim(), parsed, null)
                    }
                },
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Preview(name = "Goals - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun GoalsPreview() {
    GovSalaryTheme {
        GoalsScreen(
            state = GoalsUiState(
                listOf(
                    FinancialGoalEntity(1, "Отпуск", "Море", BigDecimal("300000"), BigDecimal("180000"), LocalDate.now(), null, GoalStatus.ACTIVE) to
                        GoalProgress(1, "Отпуск", BigDecimal("300000"), BigDecimal("180000"), BigDecimal("60.00"), BigDecimal("15000"), LocalDate.now().plusMonths(8)),
                ),
            ),
            onAddGoal = { _, _, _ -> }, onAddContribution = { _, _ -> }, onOpenSavings = {},
        )
    }
}

@Preview(name = "Goals - Empty", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun GoalsPreviewEmpty() {
    GovSalaryTheme { GoalsScreen(GoalsUiState(), { _, _, _ -> }, { _, _ -> }, {}) }
}
