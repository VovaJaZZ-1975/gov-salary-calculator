# Архитектура приложения «Калькулятор зарплаты госслужащего»

Документ описывает целевую архитектуру Android-приложения. **UI на этой фазе не реализуется** — ниже только структура и контракты.

## 1. Общая схема слоёв

```
Android App
│
├── presentation        (Фаза 2+, вне рамок текущего документа)
│
├── domain
│   └── calculation      — Calculation Engine: чистый Kotlin, без зависимостей от Android
│       ├── model         — доменные сущности (см. раздел 3)
│       ├── engine         — реализация формул из excel-analysis.md
│       └── config         — версионируемая конфигурация коэффициентов (см. раздел 5)
│
├── data
│   ├── Room               — хранение истории расчётов и версий коэффициентов
│   ├── DataStore           — пользовательские настройки (текущий год/версия по умолчанию и т. п.)
│   └── Import/Export        — перенос данных (например, из исходного Excel)
│
└── core                    — общие утилиты (округление MROUND-эквивалент, форматирование денег и т. д.)
```

**Ключевое требование:** `domain.calculation` не должен знать о Jetpack Compose, Android SDK или UI-состояниях — он тестируется как обычный Kotlin-модуль/юнит.

## 2. Технологический стек (предусмотреть архитектурно, не реализовывать на этой фазе)

- Kotlin
- Jetpack Compose + Material 3 (presentation — later)
- MVVM
- Clean Architecture (presentation → domain → data)
- Repository pattern
- Room (персистентность)
- DataStore (настройки)
- Coroutines + StateFlow
- Navigation Compose (presentation — later)

## 3. Доменная модель (черновик, из раздела формул `excel-analysis.md`)

```kotlin
// domain.calculation.model

data class SalaryCoefficients(
    val versionId: String,          // см. раздел 5 — историчность
    val effectiveFrom: LocalDate,
    val baseSalary: BigDecimal,          // "Должностной оклад", W14
    val rankSalary: BigDecimal,          // "оклад за классный чин", W15
    val monthlyBonusRate: BigDecimal,    // ЕДП, X16
    val stateSecretRate: BigDecimal,     // "за гостайну", X17
    val specialConditionsRate: BigDecimal, // "за особые условия", X18
    val seniorityRate: BigDecimal,       // "за выслугу лет", X19
    val netMultiplier: BigDecimal        // множитель 0.87 — см. open-questions.md п.1,
                                           // ДОЛЖЕН быть параметром, а не константой в коде
)

data class MonthCalendar(
    val year: Int,
    val month: Int,               // 1..12
    val workingDays: Int,          // строка 25
    val workingDaysUpTo15: Int,    // строка 26
    // workingDaysAfter15 — производная величина (workingDays - workingDaysUpTo15), не хранится отдельно
)

data class MonthlyBonus(
    val year: Int,
    val month: Int,
    val amount: BigDecimal          // строка 31, вводится вручную, может отсутствовать
)

data class MonthlyPayout(
    val year: Int,
    val month: Int,
    val startOfMonthPayment: BigDecimal?,  // строка 29 — за 2-ю половину предыдущего месяца; null на границе периода (см. open-questions.md п.3)
    val midMonthPayment: BigDecimal,        // строка 30 — за 1-ю половину текущего месяца
    val bonus: BigDecimal,
    val total: BigDecimal
)
```

## 4. Calculation Engine — контракт

```kotlin
interface SalaryCalculationEngine {
    fun calculateBaseTotal(coefficients: SalaryCoefficients): BigDecimal          // формула W21
    fun calculateDailyRate(baseTotal: BigDecimal, calendar: MonthCalendar): BigDecimal  // формула AAn24
    fun calculateMonthlyPayout(
        previousMonthDailyRate: BigDecimal?,       // null для граничного месяца
        previousMonthCalendar: MonthCalendar?,
        currentMonthDailyRate: BigDecimal,
        currentMonthCalendar: MonthCalendar,
        bonus: BigDecimal
    ): MonthlyPayout
}
```

Все формулы этого контракта должны быть покрыты тестами из `test-cases.md` (Test Case 001–009) до перехода к следующей фазе.

Округление — единая функция-обёртка над MROUND-эквивалентом (`round to nearest 0.01`), не разбросанная по коду инлайн-вызовами `BigDecimal.setScale`.

## 5. Историчность и версионирование (раздел 9 мастер-промпта)

Требование: **старый сохранённый расчёт не должен автоматически изменяться после изменения коэффициентов для нового года.**

Проектное решение:

1. `SalaryCoefficients` — неизменяемая (immutable) версия конфигурации с полями `versionId` и `effectiveFrom`. Новая версия = новая запись, старые не редактируются, только помечаются неактивными.
2. `Room`-таблица `salary_coefficient_versions` хранит все исторические версии коэффициентов (в том числе на будущее — обнаруженный в Excel пустой блок `P2:X10`, возможно, предназначен именно для «следующей» версии — см. `open-questions.md`, пункт 7).
3. `Room`-таблица `calculation_results` хранит **результат** каждого выполненного расчёта со ссылкой на `versionId`, использованный при расчёте, — то есть результат не пересчитывается «на лету» при каждом открытии экрана, а сохраняется как факт.
4. Смена коэффициентов создаёт новую версию с новой `effectiveFrom`; расчёты за периоды до этой даты продолжают ссылаться на старую версию.
5. `MonthCalendar` (рабочие дни) тоже версионируется по году — в Excel это ручной ввод без формулы (см. `open-questions.md`, пункт 8), поэтому в приложении это отдельная таблица `year_calendars`, редактируемая пользователем или (в будущем) заполняемая из справочника производственного календаря.

## 6. Что намеренно не включено в эту фазу

- Импорт исторических данных из примечаний к ячейкам (2021–2025 гг.) — зависит от ответа на `open-questions.md`, пункт 9.
- Обработка «орфанных» формул столбцов `M`/`N` — их назначение не определено (`open-questions.md`, пункт 5), в доменную модель не переносились.
- Любой UI, ViewModel, экраны, навигация, анимации — согласно критическому правилу мастер-промпта.
