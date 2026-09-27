package ru.govsalary.data.local.db

import androidx.room.TypeConverter
import ru.govsalary.data.local.db.entity.GoalStatus
import ru.govsalary.data.local.db.entity.SavingsType
import ru.govsalary.data.local.db.entity.ValueSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Room по умолчанию не умеет BigDecimal/LocalDate/Instant/enum — регистрируем конвертеры
 * централизованно, чтобы денежные суммы никогда не проходили через Double/Float (мастер-промпт
 * Фазы 2, п.2, актуально и для персистентности).
 */
class Converters {

    @TypeConverter
    fun bigDecimalToString(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun stringToBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun localDateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    @TypeConverter
    fun instantToEpochMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun valueSourceToString(value: ValueSource?): String? = value?.name

    @TypeConverter
    fun stringToValueSource(value: String?): ValueSource? = value?.let { ValueSource.valueOf(it) }

    @TypeConverter
    fun savingsTypeToString(value: SavingsType?): String? = value?.name

    @TypeConverter
    fun stringToSavingsType(value: String?): SavingsType? = value?.let { SavingsType.valueOf(it) }

    @TypeConverter
    fun goalStatusToString(value: GoalStatus?): String? = value?.name

    @TypeConverter
    fun stringToGoalStatus(value: String?): GoalStatus? = value?.let { GoalStatus.valueOf(it) }
}

/**
 * Сериализация карты "надбавка -> сумма" в JSON для SalaryHistoryEntity.allowancesJson.
 * Простой ручной формат (без kotlinx.serialization в этом файле, чтобы не тянуть зависимость
 * в TypeConverters) — `{"MONTHLY_BONUS":"21866.4","STATE_SECRET":"2429.6",...}`.
 */
object AllowancesJson {
    fun encode(allowances: Map<String, BigDecimal>): String =
        allowances.entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"$k\":\"${v.toPlainString()}\""
        }

    fun decode(json: String): Map<String, BigDecimal> {
        val trimmed = json.trim().removePrefix("{").removeSuffix("}").trim()
        if (trimmed.isEmpty()) return emptyMap()
        return trimmed.split(",").associate { entry ->
            val (key, value) = entry.split(":", limit = 2)
            key.trim().removeSurrounding("\"") to BigDecimal(value.trim().removeSurrounding("\""))
        }
    }
}
