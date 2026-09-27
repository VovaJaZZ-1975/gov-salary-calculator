package ru.govsalary.app.ui.screens.forecast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.ui.components.AnimatedProgressBar
import ru.govsalary.app.ui.components.EmptyState
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.formatMoney
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.domain.GoalProgress
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Прогноз (мастер-промпт Фазы 4, пункты "Прогноз" в дашборде/навигации + Фаза 3, п.5-6).
 * Реактивно обновляется вслед за ForecastService.observeGoalProgress (Flow.combine) —
 * ручного "Обновить" не требуется.
 */
@Composable
fun ForecastRoute() {
    val container = rememberAppContainer()
    val today = LocalDate.now()
    val goals by container.goalsAndSavingsUseCases.observeActiveGoals().collectAsStateWithLifecycle(initialValue = emptyList())
    val progressFlow = remember(goals) {
        if (goals.isEmpty()) flowOf(emptyList())
        else combine(goals.map { g -> container.goalsAndSavingsUseCases.observeGoalProgress(g.id.toInt(), today.year, today.monthValue) }) { it.filterNotNull().toList() }
    }
    val progressList by progressFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    ForecastScreen(progressList)
}

@Composable
fun ForecastScreen(progressList: List<GoalProgress>) {
    if (progressList.isEmpty()) {
        EmptyState("Нет активных целей с планом накопления — прогноз появится после создания цели и плана накопления.")
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(progressList) { progress -> ForecastCard(progress) }
    }
}

@Composable
private fun ForecastCard(progress: GoalProgress) {
    SectionCard(title = progress.goalTitle) {
        val fraction = progress.progressPercent.divide(BigDecimal(100), 4, RoundingMode.HALF_UP).toFloat()
        AnimatedProgressBar(fraction)
        Text("Прогресс: ${progress.progressPercent}%", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Цель: ${formatMoney(progress.targetAmount)} • Накоплено: ${formatMoney(progress.currentAmount)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Осталось накопить: ${formatMoney(progress.targetAmount.subtract(progress.currentAmount).max(BigDecimal.ZERO))}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Текущий план даёт: ${formatMoney(progress.projectedMonthlySaving)}/мес",
            style = MaterialTheme.typography.bodyMedium,
        )
        val forecastText = progress.forecastDate?.let {
            val months = ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), it)
            "Прогнозная дата достижения: $it (примерно через $months мес.)"
        } ?: "Прогноз недоступен — нет активного плана накопления с положительной суммой"
        Text(forecastText, style = MaterialTheme.typography.titleMedium)
    }
}

@Preview(name = "Forecast - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun ForecastPreview() {
    GovSalaryTheme {
        ForecastScreen(
            listOf(GoalProgress(1, "Отпуск", BigDecimal("300000"), BigDecimal("180000"), BigDecimal("60.00"), BigDecimal("15000"), LocalDate.now().plusMonths(8))),
        )
    }
}

@Preview(name = "Forecast - Empty", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun ForecastPreviewEmpty() {
    GovSalaryTheme { ForecastScreen(emptyList()) }
}
