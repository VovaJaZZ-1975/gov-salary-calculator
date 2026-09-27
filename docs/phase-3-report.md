# Отчёт по Фазе 3

## Ограничения выполнения (важно прочитать перед остальным отчётом)

В этой офлайн-песочнице нет интернета, нет Gradle, нет Kotlin-компилятора и нет Android SDK
(проверено командами `which/apt-get` — см. переписку Фазы 2). Поэтому `./gradlew test` и
`./gradlew assembleDebug` в буквальном смысле здесь выполнить нельзя — это было верно уже в
Фазе 2 и остаётся верным для Фазы 3, но для Room/Android-модуля риск выше, чем для чистого
Kotlin-модуля `:calculation`, — assembleDebug требует ещё и Android SDK, которого нет и не может
появиться в этой среде ни при каких условиях (в отличие от Gradle/Kotlin, для которых теоретически
нужен только интернет).

Что сделано вместо реального прогона, честно и по частям:
- Формулы, не зависящие от Room/Android (SavingsCalculator, прогноз даты в ForecastService,
  процент изменения в WhatIfService), независимо перепроверены на Python/Decimal — см. вывод
  команд в истории диалога. Расхождений не найдено.
- Тесты, которым для проверки самой бизнес-логики не нужен реальный SQLite (Repository, Savings,
  Forecast, WhatIf, Import/Export), написаны через **фейковые DAO** (`data/src/test/.../fakes/FakeDaos.kt`) —
  это обычные Kotlin-классы в памяти, реализующие те же интерфейсы DAO. Они не требуют Room/Robolectric
  для запуска — достаточно обычного JVM + JUnit5, но скомпилировать даже это здесь нельзя (нет kotlinc).
- Один тест-файл (`SalaryHistoryDaoRoomTest.kt`) — это **настоящий** тест поверх Room/SQLite
  (composite primary key, TypeConverters, REPLACE-стратегия). Ему для запуска нужен Robolectric +
  Android-зависимости — это единственный файл в проекте, честно помеченный как "не проверен
  вручную вообще", в отличие от остального, что хотя бы прогнано через Python-эквивалент.

**Что нужно от вас:** прогнать `./gradlew :calculation:test :data:testDebugUnitTest` и
`./gradlew :data:assembleDebug` в реальной Android-среде и прислать результат.

## Проверка перед началом (требование мастер-промпта)

`docs/excel-analysis.md`, `docs/test-cases.md`, `docs/architecture.md` перечитаны.
Существующий Calculation Engine (`:calculation`) **не переписывался** — модуль `:data` только
подключает его как зависимость (`implementation(project(":calculation"))`) и использует готовые
`SalaryCalculationEngine`, `WhatIfCalculator`, `CalculationConfiguration` без изменений.

## Созданные Entity (4)

| Entity | Таблица | Ключевая особенность |
|---|---|---|
| `SalaryHistoryEntity` | `salary_history` | Первичный ключ `(year, month, source)` — CALCULATED и ACTUAL физически разные строки (п.4 мастер-промпта) |
| `FinancialGoalEntity` | `financial_goal` | targetAmount/currentAmount, статус (`GoalStatus`) |
| `SavingsPlanEntity` | `savings_plan` | 4 типа накопления (`SavingsType`), внешний ключ на `FinancialGoalEntity` (SET_NULL) |
| `SavingsPeriodEntity` | `savings_period` | Диапазон дат с собственной суммой, внешний ключ на `SavingsPlanEntity` (CASCADE) |

## Созданные DAO (4)

`SalaryHistoryDao`, `FinancialGoalDao`, `SavingsPlanDao`, `SavingsPeriodDao` — все методы чтения возвращают `Flow`, записи — `suspend fun`.

## Созданные Repository (3) + domain-сервисы (3) + IO (1)

- `SalaryHistoryRepository` — плюс `PeriodComparison` (calculated vs actual, `hasDiscrepancy()`).
- `FinancialGoalRepository`, `SavingsPlanRepository` (с проверкой пересечения `SavingsPeriod`).
- `SavingsCalculator` — object, 4 типа накопления, чистая функция.
- `ForecastService` — реактивный пайплайн `Salary → SavingsPlan → MonthlySaving → GoalProgress → ForecastDate` через `Flow.combine`.
- `WhatIfService` — обёртка над `:calculation.whatif.WhatIfCalculator` (Фаза 2), ничего не сохраняет (не принимает ни одного Repository/Dao в конструктор).
- `ImportExportService` — JSON full backup, CSV, `previewImport`/`applyImport` с двумя стратегиями (`KEEP_EXISTING`/`OVERWRITE`).
- `AppSettingsDataStore` — тема, выбранный год/месяц, настройки отображения (Preferences DataStore, НЕ используется для истории/целей).

## Результат тестирования (см. раздел "Ограничения выполнения" выше)

| Файл | Тестов | Способ проверки в этой среде |
|---|---|---|
| `DefaultSalaryCalculationEngineTest` (Фаза 2) | 19 | Python/Decimal реплика (Фаза 2) |
| `ExcelGoldenTest` (Фаза 2) | 9 | Python/Decimal реплика, 0.00 ₽ расхождение по 12 месяцам |
| `WhatIfCalculatorTest` (Фаза 2) | 4 | логическая проверка |
| `SavingsCalculatorTest` | 9 | Python/Decimal реплика — все значения совпали |
| `WhatIfServiceTest` | 4 | Python/Decimal реплика (net-сумма сценария), остальное — логический разбор |
| `SalaryHistoryRepositoryTest` | 4 | Fake DAO, логический разбор (без реального запуска) |
| `ForecastServiceTest` | 4 | Python-проверка дат/процента прогресса — совпало (`60.00`, `2026-07-01`) |
| `ImportExportServiceTest` | 5 | логический разбор JSON round-trip |
| `SalaryHistoryDaoRoomTest` | 3 | **не проверялось независимо** — нужен реальный Room/SQLite |
| **Итого** | **60** (32 из Фазы 2 + 28 новых) | — |

## Совпадение с Excel / обнаруженные расхождения

Расхождений с `docs/test-cases.md` не появилось — Calculation Engine из Фазы 2 не менялся.
Savings/Forecast/WhatIf/Import-Export — новая функциональность, у которой нет golden-данных
в Excel-файле (в нём никогда не было целей/накоплений), поэтому для неё нет "расхождения с Excel"
как понятия — есть только внутренняя логическая проверка (Python) и требование прогона
JUnit в реальной среде.

## Статусы по чек-листу мастер-промпта

| Пункт | Статус |
|---|---|
| Room (сущности, DAO, миграции не требовались — version=1) | Реализовано, не скомпилировано в этой среде |
| DataStore (тема/период/отображение) | Реализовано |
| Actual vs Calculated (не смешиваются) | Реализовано на уровне схемы (составной PK) и `PeriodComparison` |
| Retrospective (год/месяц/сравнение периодов) | Реализовано (`observeYearComparison`, `comparePeriods`) |
| Финансовые цели + прогресс + прогноз | Реализовано, пример "Отпуск 60%" из промпта воспроизведён тестом |
| Автоматический пересчёт прогноза | Реализовано через `Flow.combine` (реактивно, без ручного триггера) |
| What-If (не пишет в основную БД) | Реализовано архитектурно — `WhatIfService` не содержит ни одной ссылки на Repository/Dao |
| Экспорт JSON/CSV | Реализовано |
| Импорт с подтверждением (`previewImport`/`applyImport`) | Реализовано |
| Offline (без сети) | Обеспечено на уровне зависимостей — ни одной сетевой библиотеки в `data/build.gradle.kts` |
| UI | НЕ создавался |

**ФАЗА 3 ЗАВЕРШЕНА (с оговоркой о невозможности реального прогона Gradle/Android в этой среде). Жду промпт Фазы 4.**
