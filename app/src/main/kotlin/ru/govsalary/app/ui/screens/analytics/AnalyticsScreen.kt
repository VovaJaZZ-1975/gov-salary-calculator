package ru.govsalary.app.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.SimpleBarChart
import ru.govsalary.app.ui.navigation.LocalWindowWidthSizeClass
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.local.db.AllowancesJson
import ru.govsalary.data.repository.PeriodComparison
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_SHORT = (1..12).map {
    LocalDate.of(2000, it, 1).month.getDisplayName(TextStyle.SHORT, Locale("ru"))
}

/** Мастер-промпт Фазы 4, п.14: зарплата по месяцам/годам, премии, надбавки, накопления. Адаптивная сетка графиков. */
@Composable
fun AnalyticsRoute() {
    val container = rememberAppContainer()
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val comparisons by container.historyUseCases.observeYear(year).collectAsStateWithLifecycle(initialValue = emptyList())
    val availableYears by container.historyUseCases.observeAvailableYears().collectAsStateWithLifecycle(initialValue = emptyList())
    val isTablet = LocalWindowWidthSizeClass.current != WindowWidthSizeClass.Compact

    AnalyticsScreen(year, comparisons, availableYears, isTablet)
}

@Composable
fun AnalyticsScreen(
    year: Int,
    comparisons: List<PeriodComparison>,
    availableYears: List<Int>,
    isTablet: Boolean,
) {
    val monthlyTotals = (1..12).map { m ->
        val c = comparisons.firstOrNull { it.month == m }
        MONTH_SHORT[m - 1] to (c?.actual?.totalPayout ?: c?.calculated?.totalPayout ?: BigDecimal.ZERO)
    }
    val monthlyPremiums = (1..12).map { m ->
        val c = comparisons.firstOrNull { it.month == m }
        MONTH_SHORT[m - 1] to (c?.actual?.premium ?: c?.calculated?.premium ?: BigDecimal.ZERO)
    }
    val allowanceTotals = comparisons
        .mapNotNull { it.actual ?: it.calculated }
        .flatMap { runCatching { AllowancesJson.decode(it.allowancesJson) }.getOrDefault(emptyMap()).entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { (_, values) -> values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) } }
        .map { (k, v) -> k to v }

    val columns = if (isTablet) GridCells.Fixed(2) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(title = "Зарплата по месяцам, $year") {
                SimpleBarChart(monthlyTotals)
            }
        }
        item {
            SectionCard(title = "Премии по месяцам, $year") {
                SimpleBarChart(monthlyPremiums)
            }
        }
        item {
            SectionCard(title = "Надбавки за год (сумма по типам)") {
                if (allowanceTotals.isEmpty()) {
                    androidx.compose.material3.Text(
                        "Нет сохранённых расчётов за $year год",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    SimpleBarChart(allowanceTotals)
                }
            }
        }
        item {
            SectionCard(title = "Зарплата по годам") {
                if (availableYears.size <= 1) {
                    androidx.compose.material3.Text(
                        "Недостаточно лет с данными для сравнения — история пока содержит только $year",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    androidx.compose.material3.Text(
                        "Доступные годы: " + availableYears.joinToString(),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview(name = "Analytics - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun AnalyticsPreviewPhone() {
    GovSalaryTheme { AnalyticsScreen(2026, emptyList(), listOf(2026), isTablet = false) }
}

@Preview(name = "Analytics - Tablet (2 charts per row)", widthDp = 1000, heightDp = 800, showBackground = true)
@Composable
private fun AnalyticsPreviewTablet() {
    GovSalaryTheme { AnalyticsScreen(2026, emptyList(), listOf(2025, 2026), isTablet = true) }
}
