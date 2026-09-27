package ru.govsalary.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.domain.GoalsAndSavingsUseCases
import ru.govsalary.app.domain.HistoryUseCases
import ru.govsalary.app.ui.components.AnimatedProgressBar
import ru.govsalary.app.ui.components.MoneyText
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.navigation.LocalWindowWidthSizeClass
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.domain.GoalProgress
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.PeriodComparison
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class DashboardUiState(
    val comparison: PeriodComparison? = null,
    val goalsProgress: List<GoalProgress> = emptyList(),
)

class DashboardViewModel(
    historyUseCases: HistoryUseCases,
    goalsUseCases: GoalsAndSavingsUseCases,
) : ViewModel() {

    private val today = LocalDate.now()

    private val comparisonFlow = historyUseCases.observeMonth(today.year, today.monthValue)

    private val goalsProgressFlow = goalsUseCases.observeActiveGoals().flatMapLatest { goals ->
        if (goals.isEmpty()) flowOf(emptyList())
        else combine(goals.map { g -> goalsUseCases.observeGoalProgress(g.id.toInt(), today.year, today.monthValue) }) { arr ->
            arr.filterNotNull().toList()
        }
    }

    val state = combine(comparisonFlow, goalsProgressFlow) { comparison, goals ->
        DashboardUiState(comparison = comparison, goalsProgress = goals)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())
}

@Composable
fun DashboardRoute(navController: NavHostController) {
    val container = rememberAppContainer()
    val viewModel = remember { DashboardViewModel(container.historyUseCases, container.goalsAndSavingsUseCases) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isTablet = LocalWindowWidthSizeClass.current != WindowWidthSizeClass.Compact
    DashboardScreen(state, isTablet)
}

/** Мастер-промпт Фазы 4, п.8-9: смартфон — 1 колонка, планшет — 2-3 колонки, без жёстких размеров. */
@Composable
fun DashboardScreen(state: DashboardUiState, isTablet: Boolean) {
    val columns = if (isTablet) GridCells.Adaptive(minSize = 280.dp) else GridCells.Fixed(1)
    val current = state.comparison?.actual ?: state.comparison?.calculated

    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = "Текущий доход" + (state.comparison?.let { sourceLabel(it) } ?: "")) {
                if (current == null) {
                    Text("Нет данных за текущий месяц — выполните расчёт на вкладке «Расчёт»", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LabeledMoney("Начислено", current.accrued)
                    LabeledMoney("НДФЛ", current.ndfl)
                    LabeledMoney("К выплате", current.totalPayout)
                    LabeledMoney("Премия", current.premium)
                }
            }
        }
        item {
            SectionCard(title = "Ближайшая выплата") {
                if (current == null) {
                    Text("—", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LabeledMoney("Аванс", current.midMonthPaymentAmount ?: BigDecimal.ZERO)
                    LabeledMoney("Основная выплата", current.startOfMonthPaymentAmount ?: BigDecimal.ZERO)
                    LabeledMoney("Премия", current.premium)
                }
            }
        }
        item {
            SectionCard(title = "Накопления") {
                if (state.goalsProgress.isEmpty()) {
                    Text("Нет активных планов накопления", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val totalCurrent = state.goalsProgress.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.currentAmount) }
                    val totalMonthly = state.goalsProgress.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.projectedMonthlySaving) }
                    LabeledMoney("Накоплено всего", totalCurrent)
                    LabeledMoney("В этом месяце", totalMonthly)
                }
            }
        }
        item {
            SectionCard(title = "Финансовые цели") {
                if (state.goalsProgress.isEmpty()) {
                    Text("Целей пока нет — добавьте на вкладке «Цели»", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.goalsProgress.forEach { GoalMiniCard(it) }
                    }
                }
            }
        }
    }
}

private fun sourceLabel(comparison: PeriodComparison): String = when {
    comparison.actual != null -> " (факт)"
    comparison.calculated != null -> " (расчёт)"
    else -> ""
}

@Composable
private fun LabeledMoney(label: String, amount: BigDecimal) {
    Column(Modifier.padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(amount, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun GoalMiniCard(progress: GoalProgress) {
    Column(Modifier.fillMaxWidth()) {
        Text(progress.goalTitle, style = MaterialTheme.typography.titleMedium)
        AnimatedProgressBar(
            progressFraction = progress.progressPercent.divide(BigDecimal(100), 4, RoundingMode.HALF_UP).toFloat(),
        )
        Text(
            "${progress.progressPercent}% • осталось ${MoneyFormatting.diff(progress.targetAmount, progress.currentAmount)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private object MoneyFormatting {
    fun diff(target: BigDecimal, current: BigDecimal): String =
        ru.govsalary.app.ui.components.formatMoney(target.subtract(current).max(BigDecimal.ZERO))
}

@Preview(name = "Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun DashboardPreviewPhone() {
    GovSalaryTheme { DashboardScreen(previewState(), isTablet = false) }
}

@Preview(name = "Tablet", widthDp = 1000, heightDp = 800, showBackground = true)
@Composable
private fun DashboardPreviewTablet() {
    GovSalaryTheme { DashboardScreen(previewState(), isTablet = true) }
}

@Preview(name = "Empty state", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun DashboardPreviewEmpty() {
    GovSalaryTheme { DashboardScreen(DashboardUiState(), isTablet = false) }
}

private fun previewState() = DashboardUiState(
    comparison = null,
    goalsProgress = listOf(
        GoalProgress(1, "Отпуск", BigDecimal("300000"), BigDecimal("180000"), BigDecimal("60.00"), BigDecimal("15000"), LocalDate.now().plusMonths(8)),
    ),
)
