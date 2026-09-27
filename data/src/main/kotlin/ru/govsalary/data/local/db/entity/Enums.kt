package ru.govsalary.data.local.db.entity

/**
 * Источник значения (мастер-промпт Фазы 3, п.4: "Actual vs Calculated").
 * Расчётное и фактическое значения хранятся как ОТДЕЛЬНЫЕ строки SalaryHistoryEntity
 * с одинаковыми (year, month), но разным source — никогда не сливаются в одну запись.
 */
enum class ValueSource {
    /** Получено из :calculation (SalaryCalculationEngine). */
    CALCULATED,
    /** Введено пользователем вручную или импортировано из внешнего источника (например, из Excel). */
    ACTUAL,
}

/** Тип накопления. Мастер-промпт Фазы 3, п.2. */
enum class SavingsType {
    /** Фиксированная сумма (константа или по SavingsPeriod). */
    FIXED_AMOUNT,
    /** Процент от зарплаты (totalPayout расчёта за период). */
    SALARY_PERCENT,
    /** Процент от премии за период. */
    PREMIUM_PERCENT,
    /** Остаток после обязательных расходов. */
    REMAINING_AMOUNT,
}

/** Статус финансовой цели. */
enum class GoalStatus {
    ACTIVE,
    ACHIEVED,
    PAUSED,
    CANCELLED,
}
