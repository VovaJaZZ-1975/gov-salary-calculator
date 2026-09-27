package ru.govsalary.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Единое форматирование денег во всём приложении. */
fun formatMoney(amount: BigDecimal): String {
    val symbols = DecimalFormatSymbols(Locale("ru", "RU")).apply { groupingSeparator = ' '; decimalSeparator = ',' }
    val format = DecimalFormat("#,##0.00", symbols)
    return format.format(amount.setScale(2, RoundingMode.HALF_UP)) + " ₽"
}

@Composable
fun MoneyText(amount: BigDecimal, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleLarge) {
    Text(text = formatMoney(amount), modifier = modifier, style = style)
}

/** Карточка-раздел с заголовком — базовый строительный блок Dashboard/Analytics/История. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** Прогресс цели с анимацией заполнения (мастер-промпт Фазы 4, п.8: "анимированные progress indicators"). */
@Composable
fun AnimatedProgressBar(progressFraction: Float, modifier: Modifier = Modifier) {
    val target = progressFraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, animationSpec = tween(durationMillis = 600), label = "progress")
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .semantics { contentDescription = "Прогресс: ${(target * 100).toInt()}%" },
        color = MaterialTheme.colorScheme.secondary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/** Значение с анимированным появлением/сменой (результат расчёта, Фаза 4 п.11, 22). */
@Composable
fun AnimatedMoneyValue(amount: BigDecimal, label: String, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(targetValue = amount.toFloat(), animationSpec = tween(400), label = "money")
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = formatMoney(BigDecimal.valueOf(animated.toDouble())),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Простейший столбчатый график без внешних зависимостей (Canvas) — экран "Аналитика"
 * (мастер-промпт Фазы 4, п.14). Адаптивен по ширине родительского контейнера.
 */
@Composable
fun SimpleBarChart(
    values: List<Pair<String, BigDecimal>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.secondary,
) {
    val maxValue = values.maxOfOrNull { it.second.toFloat() }?.takeIf { it > 0f } ?: 1f
    val description = "Столбчатая диаграмма: " + values.joinToString { "${it.first} ${formatMoney(it.second)}" }
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            if (values.isEmpty()) return@Canvas
            val barWidth = size.width / (values.size * 1.5f)
            val gap = barWidth * 0.5f
            values.forEachIndexed { index, (_, value) ->
                val barHeight = (value.toFloat() / maxValue) * size.height
                val left = index * (barWidth + gap) + gap / 2
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { (label, _) ->
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Пустое состояние с понятным текстом — вместо "пустого экрана" без объяснения (запрещено п.2 мастер-промпта). */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
