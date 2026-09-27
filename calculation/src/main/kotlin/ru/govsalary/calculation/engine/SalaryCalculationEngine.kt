package ru.govsalary.calculation.engine

import ru.govsalary.calculation.config.CalculationConfiguration
import ru.govsalary.calculation.model.Premium
import ru.govsalary.calculation.model.SalaryCalculationResult
import ru.govsalary.calculation.model.WorkCalendar
import java.math.BigDecimal

/**
 * Контракт расчётного движка. Не зависит от Android/Compose/UI (мастер-промпт Фазы 2, требование "ГЛАВНАЯ ЦЕЛЬ").
 *
 * ВАЖНО: расчёт итога за месяц (calculateMonthlyResult) требует календарь ПРЕДЫДУЩЕГО месяца,
 * т.к. выплата "начало месяца (4 число)" зависит от дневной ставки и дней предыдущего месяца
 * (excel-analysis.md, раздел 5.3). Если предыдущий календарь не передан (граница данных,
 * open-questions.md, п.3), эта часть выплаты считается равной нулю, что буквально
 * воспроизводит поведение ячейки AA29 (пустая формула для января 2026 в исходном файле).
 */
interface SalaryCalculationEngine {

    /** Сумма начислений без учёта налогового множителя: baseSalary + rankSalary + сумма всех надбавок. */
    fun calculateAccrued(config: CalculationConfiguration): BigDecimal

    /** Формула W21: accrued * netMultiplier. Без округления (как в Excel). */
    fun calculateNetMonthlyBase(config: CalculationConfiguration): BigDecimal

    /** Формула AAn24: MROUND(netMonthlyBase / calendar.totalWorkingDays, roundingRules.step). */
    fun calculateDailyRate(netMonthlyBase: BigDecimal, calendar: WorkCalendar, config: CalculationConfiguration): BigDecimal

    /**
     * Полный расчёт результата за месяц [calendar.period].
     *
     * @param previousMonthCalendar календарь предыдущего месяца (может отсутствовать на границе данных)
     * @param premium премия за расчётный месяц (Premium.none(period), если не назначена)
     */
    fun calculateMonthlyResult(
        config: CalculationConfiguration,
        calendar: WorkCalendar,
        previousMonthCalendar: WorkCalendar?,
        premium: Premium,
    ): SalaryCalculationResult
}
