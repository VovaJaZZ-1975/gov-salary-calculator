package ru.govsalary.calculation.model

/**
 * Производственный календарь месяца.
 * Источник структуры: excel-analysis.md, раздел 5.1 (строки 25-27).
 *
 * В самом Excel-файле числа рабочих дней — константы без формулы и без источника
 * (open-questions.md, п.8). Этот модуль их не вычисляет и не хранит справочник праздников —
 * WorkCalendar только принимает уже готовые числа (ручной ввод или внешний справочник,
 * подключаемый на уровне data-слоя, вне зоны ответственности calculation-модуля).
 *
 * Поддержка "фактически отработанных дней" / неполного месяца (мастер-промпт Фазы 2, п.7) —
 * это расширение относительно исходного Excel: сам файл всегда предполагает полную отработку
 * всех дней. По умолчанию actualWorkedDaysUpTo15/After15 совпадают с плановыми значениями,
 * поэтому golden-тесты (test-cases.md) воспроизводятся один в один без изменений.
 */
data class WorkCalendar(
    val period: CalculationPeriod,
    /** Всего рабочих дней в месяце по календарю. Строка 25. */
    val totalWorkingDays: Int,
    /** Рабочих дней с 1 по 15 число включительно. Строка 26. */
    val workingDaysUpTo15: Int,
    /** Фактически отработано дней с 1 по 15 число (для неполного месяца). По умолчанию = workingDaysUpTo15. */
    val actualWorkedDaysUpTo15: Int = workingDaysUpTo15,
    /** Фактически отработано дней после 15 числа (для неполного месяца). По умолчанию = workingDaysAfter15. */
    val actualWorkedDaysAfter15: Int = totalWorkingDays - workingDaysUpTo15,
) {
    init {
        require(totalWorkingDays >= 0) { "Число рабочих дней не может быть отрицательным ($period)" }
        require(workingDaysUpTo15 in 0..totalWorkingDays) {
            "Рабочих дней до 15 числа ($workingDaysUpTo15) должно быть в пределах 0..$totalWorkingDays ($period)"
        }
        require(actualWorkedDaysUpTo15 in 0..workingDaysUpTo15) {
            "Фактически отработано до 15 числа не может превышать плановых $workingDaysUpTo15 ($period)"
        }
        require(actualWorkedDaysAfter15 in 0..workingDaysAfter15) {
            "Фактически отработано после 15 числа не может превышать плановых $workingDaysAfter15 ($period)"
        }
    }

    /** Рабочих дней после 15 числа. Строка 27: `=<всего>-<до 15>`. Производная величина, в Excel не хранится отдельно. */
    val workingDaysAfter15: Int get() = totalWorkingDays - workingDaysUpTo15

    /** true, если фактически отработаны не все плановые дни месяца (неполный месяц: отпуск, больничный, приём/увольнение в середине месяца и т.п.). */
    val isPartialMonth: Boolean
        get() = actualWorkedDaysUpTo15 != workingDaysUpTo15 || actualWorkedDaysAfter15 != workingDaysAfter15
}
