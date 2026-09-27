# Build Status

## Environment (где готовился этот проект)

- **OS:** Ubuntu 24.04.4 LTS (Linux, x86_64), офлайн-контейнер без доступа в интернет.
- **JDK:** OpenJDK 21.0.10 (Temurin/Ubuntu build) — установлен, доступен.
- **Gradle:** НЕ установлен (`which gradle` → не найдено), скачать не удалось (сеть недоступна —
  запрос к `services.gradle.org` возвращает `403 Forbidden` через прокси окружения).
- **Kotlin-компилятор (kotlinc):** НЕ установлен, скачать не удалось по той же причине.
- **Android SDK:** НЕ установлен (`ANDROID_HOME`/`ANDROID_SDK_ROOT` не заданы, `sdkmanager` отсутствует).
- **`apt-get install`** также не работает — репозитории Ubuntu отдают `403 Forbidden` для той же сети.

Из-за этого в среде, где готовился код, физически невозможно ни скомпилировать Kotlin
(нет компилятора), ни тем более собрать Android-проект (нет SDK) — независимо от корректности
самого кода.

## Commands (фактически выполненные, не предполагаемые)

```
$ which gradle kotlinc
(пусто — ничего не найдено)

$ ./gradlew test
ERROR: /home/claude/project/gradle/wrapper/gradle-wrapper.jar не найден.
```

`gradle-wrapper.jar` — бинарный файл, который Gradle обычно докачивает сам при первом запуске
`gradle wrapper` (требует интернет) или при первом Gradle Sync в Android Studio. В этой офлайн-среде
получить его неоткуда, поэтому он единственный намеренно отсутствует файл во всём наборе
`gradle/`/`gradlew`/`gradlew.bat` — остальные части wrapper (скрипты, `gradle-wrapper.properties`
с `distributionUrl` на Gradle 8.7) на месте и корректны.

`./gradlew assembleDebug` не запускался отдельно — он упёрся бы в ту же ошибку раньше, чем в
отсутствие Android SDK.

## Result

**NOT AVAILABLE** — сборка (`./gradlew test`, `./gradlew assembleDebug`) не может быть выполнена
в среде, где готовился код, по причинам среды (нет Gradle/Kotlin/Android SDK/интернета), а не из-за
известных ошибок в самом коде.

Это не выдуманный "PASS" — согласно мастер-промпту Фазы 4 (п.28, 52) такой результат
зафиксирован как есть.

## Компенсирующая проверка, выполненная в этой среде

Поскольку реальный Gradle/Kotlin-прогон был недоступен на всех предыдущих фазах, а не только в
Фазе 4, для проверки бизнес-логики использовалась независимая Python/Decimal-реплика тех же
формул (см. Фазу 2 и Фазу 3 диалога):
- 32 юнит-теста + 9 golden-тестов `:calculation` — сверены построчно, расхождений 0.00 ₽.
- Формулы `SavingsCalculator`, даты `ForecastService`, проценты `WhatIfService` (`:data`) —
  сверены, расхождений не найдено.
- Код `:app` (Compose UI, ViewModel, UseCase) синтаксически не проверялся компилятором вообще —
  для него **нет** эквивалентной Python-реплики (UI-код не сводится к чистой арифметике), поэтому
  его корректность **NOT VERIFIED** до реального `./gradlew build` в среде с Android SDK.

## Архитектурные отклонения (обнаружены при аудите Фазы 4, п.3-4)

При аудите перед началом работ обнаружено: несколько экранов `:app` напрямую импортируют типы
`:data` (Room-сущности `SalaryHistoryEntity`, `FinancialGoalEntity`, `SavingsPlanEntity` и
enum'ы `ValueSource`/`GoalStatus`/`SavingsType`) и `:calculation` (`SalaryCalculationResult`,
`PaymentType`, `ScenarioOverrides`, `CalculationConfiguration`) вместо отдельных UI-моделей,
что противоречит правилу мастер-промпта Фазы 4, п.25-26 ("Compose UI ↛ Room", "Compose UI ↛
Calculation Engine").

Файлы: `CalculatorScreen.kt`, `WhatIfScreen.kt`, `SettingsScreen.kt`, `GoalsScreen.kt`,
`HistoryScreen.kt`, `DashboardScreen.kt`, `SavingsScreen.kt`, `AnalyticsScreen.kt`.

Это не помешало бы приложению работать (сами вычисления по-прежнему выполняются только в
`Calculation Engine`/`UseCase`, ни один Composable их не производит — проверено: в этих файлах нет
формул, только чтение готовых полей), но нарушает требование строгой изоляции слоёв. Полный
рефакторинг (замена на UI-модели с маппингом в UseCase-слое) затрагивает ~1800 строк уже рабочего
UI-кода, которые в этой среде невозможно перекомпилировать и проверить после правки — поэтому
вместо рискованной правки "вслепую" это зафиксировано здесь как известное отклонение, а не скрыто
и не выдано за "PASS". Рекомендация на будущее: ввести `ui/model/` с плоскими UI-моделями и
`toUiModel()`-мапперами в слое `UseCase`.

## Required Environment (что нужно для реальной сборки)

- Android Studio (Koala 2024.1+ или новее) — самый простой путь: откроет проект, сам скачает
  Gradle 8.7, `gradle-wrapper.jar`, Android SDK (`compileSdk 34`) и все зависимости при Gradle Sync.
- Либо вручную: JDK 17+, Gradle 8.7, Android SDK с `platforms;android-34` и `build-tools`,
  переменная `ANDROID_HOME`, интернет-доступ к `google()`/`mavenCentral()`/`services.gradle.org`.
- Команда `gradle wrapper --gradle-version 8.7` (при наличии локального Gradle) восстановит
  отсутствующий `gradle-wrapper.jar` без Android Studio.
- Подписывающий keystore для release-сборки — в проекте отсутствует и не создавался (см.
  `docs/security-check.md`); для `assembleDebug` не требуется.
