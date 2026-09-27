# Security Check

Проверка выполнена перед упаковкой ZIP (мастер-промпт Фазы 4, п.39-40) командой:

```
grep -rniE "api[_-]?key|secret|password|token|credential|BEGIN (RSA|PRIVATE|OPENSSH)|keystore" \
  --include="*.kts" --include="*.gradle" --include="*.xml" --include="*.properties" --include="*.json" .
find . -iname "*.jks" -o -iname "*.keystore" -o -iname "*.p12" -o -iname "*.pem" -o -iname "*.key"
find . -iname "local.properties"
```

по всем `*.properties`, `*.json`, `*.xml`, `*.gradle`, `*.kts` в проекте.

## Результаты

| Проверка | Статус |
|---|---|
| Secrets scan (API keys, tokens, passwords, credentials в конфигурационных файлах) | **PASS** — совпадений не найдено |
| Private keys (`*.pem`, `*.key`) | **PASS** — не найдено |
| Keystore (`*.jks`, `*.keystore`, `*.p12`) | **PASS** — не найдено, подписывающий ключ в проект не добавлялся |
| Credentials (`local.properties`, встроенные пароли/логины) | **PASS** — `local.properties` в проекте отсутствует (это правильно — он персональный для каждой машины и в `.gitignore`) |
| Personal data (в исходном коде и docs) | **PASS** — код и `docs/` содержат только числовые коэффициенты (оклад, ставки надбавок и т.п.) без имён, дат рождения, адресов и т.п. |
| Excel sensitive data (`Расчет ЗП (2026).xlsx`) | **FAIL → устранено**: файл содержит реальное ФИО автора и историю фактических выплат за 2021-2025 гг. в примечаниях к ячейкам (см. `docs/excel-analysis.md`, раздел 9). Файл **исключён** из ZIP и добавлен в `.gitignore` (по имени файла); обезличенное описание — `sample-data/README.md` |

## Что было сделано с найденной проблемой (Excel-файл)

1. Файл `Расчет_ЗП__2026_.xlsx` не включён в архив `SalaryCalculatorGov-2026-GitHub.zip`.
2. Добавлен в `.gitignore` по обоим вариантам имени (с подчёркиваниями и с пробелами/скобками),
   чтобы случайное копирование файла в корень проекта на компьютере пользователя не привело к
   его коммиту.
3. Вместо файла — `sample-data/README.md` с пояснением и указанием на уже существующий
   обезличенный набор коэффициентов `CalculationConfiguration.excel2026Baseline()`.

## Что НЕ входит в эту проверку

Эта проверка — текстовый grep по конфигурационным файлам и поиск файлов типичных форматов
секретов. Она не заменяет полноценный security-аудит (SAST, проверку зависимостей на уязвимости
и т.п.) — таких инструментов в этой офлайн-песочнице нет.
