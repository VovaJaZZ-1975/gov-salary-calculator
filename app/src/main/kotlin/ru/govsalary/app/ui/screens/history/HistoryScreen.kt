package ru.govsalary.app.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.govsalary.app.di.rememberAppContainer
import ru.govsalary.app.domain.HistoryUseCases
import ru.govsalary.app.ui.components.EmptyState
import ru.govsalary.app.ui.components.MoneyText
import ru.govsalary.app.ui.components.SectionCard
import ru.govsalary.app.ui.components.formatMoney
import ru.govsalary.app.ui.navigation.LocalWindowWidthSizeClass
import ru.govsalary.app.ui.theme.GovSalaryTheme
import ru.govsalary.data.local.db.AllowancesJson
import ru.govsalary.data.local.db.entity.SalaryHistoryEntity
import ru.govsalary.data.local.db.entity.ValueSource
import ru.govsalary.data.repository.PeriodComparison
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val MONTH_NAMES = (1..12).map { m ->
    LocalDate.of(2000, m, 1).month.getDisplayName(TextStyle.FULL, Locale("ru")).replaceFirstChar { it.uppercase() }
}

/** Мастер-промпт Фазы 4, п.15: ретроспектива по году/месяцу, master-detail на планшете. */
@Composable
fun HistoryRoute() {
    val container = rememberAppContainer()
    val useCases = container.historyUseCases
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val comparisons by useCases.observeYear(year).collectAsStateWithLifecycle(initialValue = emptyList())
    val isTablet = LocalWindowWidthSizeClass.current != WindowWidthSizeClass.Compact

    HistoryScreen(year, comparisons, isTablet, onYearChange = { year = it })
}

@Composable
fun HistoryScreen(
    year: Int,
    comparisons: List<PeriodComparison>,
    isTablet: Boolean,
    onYearChange: (Int) -> Unit,
) {
    var selectedMonth by remember(year) { mutableStateOf<Int?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        YearSelector(year, onYearChange)
        Spacer(Modifier.height(12.dp))

        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                MonthList(
                    comparisons, Modifier.weight(1f),
                    selectedMonth = selectedMonth ?: comparisons.firstOrNull { it.calculated != null || it.actual != null }?.month,
                    onSelect = { selectedMonth = it },
                )
                Column(Modifier.weight(2f).padding(start = 16.dp)) {
                    val month = selectedMonth ?: comparisons.firstOrNull { it.calculated != null || it.actual != null }?.month
                    val comparison = comparisons.firstOrNull { it.month == month }
                    if (comparison == null) {
                        EmptyState("Выберите месяц слева")
                    } else {
                        MonthDetails(comparison)
                    }
                }
            }
        } else {
            if (selectedMonth == null) {
                MonthList(comparisons, Modifier.fillMaxSize(), selectedMonth = null, onSelect = { selectedMonth = it })
            } else {
                val comparison = comparisons.first { it.month == selectedMonth }
                Column(Modifier.fillMaxSize()) {
                    androidx.compose.material3.TextButton(onClick = { selectedMonth = null }) { Text("← Назад к списку месяцев") }
                    MonthDetails(comparison)
                }
            }
        }
    }
}

@Composable
private fun YearSelector(year: Int, onYearChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { onYearChange(year - 1) }) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий год")
        }
        Text("$year", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
        androidx.compose.material3.IconButton(onClick = { onYearChange(year + 1) }) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий год")
        }
    }
}

@Composable
private fun MonthList(
    comparisons: List<PeriodComparison>,
    modifier: Modifier,
    selectedMonth: Int?,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(modifier) {
        items(comparisons) { comparison ->
            val hasData = comparison.calculated != null || comparison.actual != null
            ListItem(
                headlineContent = {
                    Text(
                        MONTH_NAMES[comparison.month - 1],
                        fontWeight = if (comparison.month == selectedMonth) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                supportingContent = {
                    val amount = comparison.actual?.totalPayout ?: comparison.calculated?.totalPayout
                    Text(if (amount != null) formatMoney(amount) else "Нет данных")
                },
                modifier = Modifier.let { if (hasData) it.clickable { onSelect(comparison.month) } else it },
            )
            Divider()
        }
    }
}

@Composable
private fun MonthDetails(comparison: PeriodComparison) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(MONTH_NAMES[comparison.month - 1] + " " + comparison.year, style = MaterialTheme.typography.headlineMedium) }
        item {
            EntitySection("Расчётное значение (Calculation Engine)", comparison.calculated)
        }
        item {
            EntitySection("Фактическое значение (введено вручную/импорт)", comparison.actual)
        }
        if (comparison.hasDiscrepancy()) {
            item {
                SectionCard(title = "⚠ Расхождение") {
                    Text(
                        "Расчётное и фактическое значения различаются — проверьте исходные параметры.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntitySection(title: String, entity: SalaryHistoryEntity?) {
    SectionCard(title = title) {
        if (entity == null) {
            Text("Нет данных из этого источника", style = MaterialTheme.typography.bodyMedium)
            return@SectionCard
        }
        LabeledMoney("Начислено", entity.accrued)
        LabeledMoney("НДФЛ", entity.ndfl)
        LabeledMoney("К выплате", entity.totalPayout)
        LabeledMoney("Премия", entity.premium)
        val allowances = runCatching { AllowancesJson.decode(entity.allowancesJson) }.getOrDefault(emptyMap())
        if (allowances.isNotEmpty()) {
            Text("Надбавки:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            allowances.forEach { (name, amount) -> LabeledMoney(name, amount) }
        }
        entity.configVersionId?.let {
            Text("Версия модели: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LabeledMoney(label: String, amount: BigDecimal) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        MoneyText(amount, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "History - Phone", widthDp = 360, heightDp = 780, showBackground = true)
@Composable
private fun HistoryPreviewPhone() {
    GovSalaryTheme { HistoryScreen(2026, previewComparisons(), isTablet = false, onYearChange = {}) }
}

@Preview(name = "History - Tablet Master-Detail", widthDp = 1000, heightDp = 800, showBackground = true)
@Composable
private fun HistoryPreviewTablet() {
    GovSalaryTheme { HistoryScreen(2026, previewComparisons(), isTablet = true, onYearChange = {}) }
}

private fun previewComparisons(): List<PeriodComparison> = (1..12).map { month ->
    PeriodComparison(
        year = 2026, month = month,
        calculated = SalaryHistoryEntity(
            year = 2026, month = month, source = ValueSource.CALCULATED, configVersionId = "2026-baseline-v1",
            baseSalary = BigDecimal("24296"), rankSalary = BigDecimal("13853"), allowancesJson = "{}",
            accrued = BigDecimal("94029.8"), ndfl = BigDecimal("12223.874"), premium = BigDecimal("23997"),
            totalPayout = BigDecimal("100000"), midMonthPaymentDate = null, midMonthPaymentAmount = null,
            startOfMonthPaymentDate = null, startOfMonthPaymentAmount = null,
            recordedAt = java.time.Instant.EPOCH,
        ),
        actual = null,
    )
}
