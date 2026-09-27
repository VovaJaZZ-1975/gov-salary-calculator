package ru.govsalary.app.ui.screens.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.formatMoney
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.domain.GoalProgress
import ru.govsalary.data.repository.PeriodComparison
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_NAMES = (1..12).map { m ->
    LocalDate.of(2000, m, 1).month.getDisplayName(TextStyle.FULL, Locale("ru")).replaceFirstChar { it.uppercase() }
}

/** Финансовый календарь (мастер-промпт Фазы 4, п.19): аванс/выплата/премия/накопление/достижение цели по месяцам. */
@Composable
fun CalendarRoute() {
    val container = rememberAppContainer()
    val year = LocalDate.now().year
    val comparisons by container.historyUseCases.observeYear(year).collectAsStateWithLifecycle(initialValue = emptyList())
    val goals by container.goalsAndSavingsUseCases.observeActiveGoals().collectAsStateWithLifecycle(initialValue = emptyList())
    val goalsProgress by remember(goals) {
        if (goals.isEmpty()) flowOf(emptyList())
        else combine(goals.map { g -> container.goalsAndSavingsUseCases.observeGoalProgress(g.id.toInt(), year, LocalDate.now().monthValue) }) { it.filterNotNull().toList() }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    CalendarScreen(year, comparisons, goalsProgress)
}

@Composable
fun CalendarScreen(year: Int, comparisons: List<PeriodComparison>, goalsProgress: List<GoalProgress>) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("$year год", style = MaterialTheme.typography.headlineMedium) }
        items((1..12).toList()) { month ->
            val comparison = comparisons.firstOrNull { it.month == month }
            val entity = comparison?.actual ?: comparison?.calculated
            val achievedThisMonth = goalsProgress.filter {
                it.forecastDate?.year == year && it.forecastDate?.monthValue == month
            }

            SectionCard(title = MONTH_NAMES[month - 1]) {
                if (entity == null && achievedThisMonth.isEmpty()) {
                    Text("Нет запланированных событий", style = MaterialTheme.typography.bodyMedium)
                } else {
                    entity?.startOfMonthPaymentAmount?.let { CalendarLine("4 число — окончательная выплата", it) }
                    entity?.midMonthPaymentAmount?.let { CalendarLine("19 число — аванс", it) }
                    entity?.premium?.takeIf { it.signum() > 0 }?.let { CalendarLine("Премия", it) }
                    achievedThisMonth.forEach {
                        Text("🎯 Прогноз достижения цели «${it.goalTitle}»", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLine(label: String, amount: BigDecimal) {
    Column(Modifier.fillMaxWidth()) {
        Text("$label: ${formatMoney(amount)}", style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Calendar - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun CalendarPreview() {
    GovSalaryTheme { CalendarScreen(2026, emptyList(), emptyList()) }
}
