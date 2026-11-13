# Реестр HTTP-клиентов поставщиков

Сервис на Spring, который хранит HTTP-клиенты нескольких поставщиков и проверяет через них, доступен ли номер телефона. Подключается как библиотека или запускается из консоли.

## Требования

- Java 17
- Gradle 
- JUnit

## Сборка и тесты

```bash
./gradlew test
```

В Windows:

```bat
gradlew.bat test
```

В тестах сеть подменяется через `MockRestServiceServer`, реальных HTTP-запросов нет. Сборка компилируется с `-Xlint:all -Werror`: любое предупреждение компилятора считается ошибкой.

## Запуск

Приложение консольное, у него два режима.

### Демо: показать, что всё работает

```bash
./gradlew run
```

Без аргументов приложение само поднимает на свободном локальном порту демо-сервер поставщиков. Затем оно записывает JSON-файл с поставщиками, загружает его через Spring и прогоняет набор проверок с ожидаемыми результатами: доступен, недоступен, ошибка `500`, неверный ключ, неизвестный поставщик, некорректный номер. После этого набор поставщиков заменяется готовым списком, и проверки повторяются. Пример вывода:

```
Service registry demo
=====================

1. Demo provider server started         http://127.0.0.1:58653/
2. Providers file written               C:\...\providers-2269915861896581704.json
[INFO] Provider set replaced: [alpha, beta, broken]
3. Providers loaded from file           [alpha, beta, broken]

4. Checking phone numbers
   #         PROVIDER  PHONE          EXPECTED     ACTUAL
   1   OK    alpha     79990000001    AVAILABLE    AVAILABLE
   2   OK    alpha     +79990000001   AVAILABLE    AVAILABLE
   3   OK    alpha     79990000002    UNAVAILABLE  UNAVAILABLE
   4   OK    beta      79990000001    AVAILABLE    AVAILABLE
   5   OK    beta      79990000002    UNAVAILABLE  UNAVAILABLE
   6   OK    beta      79990000500    ERROR        ERROR InternalServerError: Provider beta responded with status 500
   7   OK    broken    79990000001    ERROR        ERROR Unauthorized: Provider broken responded with status 401
   8   OK    gamma     79990000001    ERROR        ERROR UnknownProviderException: Unknown provider: gamma
   9   OK    alpha     12-34          ERROR        ERROR IllegalArgumentException: Invalid phone number: 12-34

[INFO] Provider set replaced: [gamma]
5. Provider set replaced with a list    [gamma]

   #         PROVIDER  PHONE          EXPECTED     ACTUAL
   10  OK    gamma     79990000001    AVAILABLE    AVAILABLE
   11  OK    gamma     79990000002    UNAVAILABLE  UNAVAILABLE
   12  OK    alpha     79990000001    ERROR        ERROR UnknownProviderException: Unknown provider: alpha

Result: 12 of 12 checks passed. The service works as expected.
```

Демо-сервер отвечает по последним цифрам номера: `…0001` → `200 {"status":"ok"}`, `…0500` → `500`, остальные → `404`. Неверный API-ключ → `401`. Демо завершается с кодом `0`, если все проверки совпали с ожиданием.

### Проверка номера у настоящего поставщика

```bash
./gradlew run --args="providers.json alpha 79991234567"
```

или через собранный дистрибутив:

```bash
./gradlew installDist
build/install/service-registry/bin/service-registry providers.json alpha 79991234567
```

В Windows — `build\install\service-registry\bin\service-registry.bat`.

Аргументы: путь к JSON-файлу с поставщиками, идентификатор поставщика, номер телефона. Результат печатается в stdout, ошибки и лог — в stderr.

```
Providers file:   providers.json
Loaded providers: [alpha, beta]
Checking phone 79991234567 with provider alpha...
Result: AVAILABLE
```

| Код выхода | Значение |
|---|---|
| `0` | номер доступен (`AVAILABLE`) или демо прошло успешно |
| `1` | номер недоступен (`UNAVAILABLE`) |
| `2` | неверные аргументы |
| `3` | ошибка: файл, неизвестный поставщик, некорректный номер, HTTP-ошибка, сеть, таймаут; в демо — проверка не совпала с ожиданием |

Настройки передаются системными свойствами через `JAVA_OPTS`:

```bash
JAVA_OPTS="-Dregistry.read-timeout-ms=2000 -Dregistry.api-key-header=Authorization" \
  build/install/service-registry/bin/service-registry providers.json alpha 79991234567
```

## Поставщик

У поставщика три поля: идентификатор, базовый URL и API-ключ.

```java
new Provider("alpha", "https://alpha.example.com", "secret-key");
```

Правила проверки при создании:

- все поля обязательны и не могут быть пустыми;
- `id` без пробелов в начале и в конце, иначе поиск по идентификатору молча не находил бы поставщика;
- `baseUrl` — абсолютный URL со схемой `http` или `https`, с хостом, без логина и пароля, query и fragment;
- `apiKey` состоит только из видимых ASCII-символов (без пробелов, переводов строки и кириллицы): ключ уходит в HTTP-заголовок, и так нельзя подставить лишние заголовки или получить ошибку уже при запросе.

`toString()` скрывает API-ключ, поэтому объект можно безопасно писать в логи.

## Как устроен запрос

Для проверки номера выполняется запрос:

```
GET {baseUrl}/subscribers/{phone}
X-Api-Key: {apiKey}        (имя заголовка настраивается)
Accept: application/json
```

Номер телефона: от 1 до 15 цифр (0–9), перед ними допускается один `+`, как в E.164. Пример: `79991234567` или `+79991234567`. Иначе выбрасывается `IllegalArgumentException`, и запрос не отправляется. Номер кодируется в пути: `+7999...` уходит как `%2B7999...`.

| Ответ поставщика | Результат |
|---|---|
| `200` с телом `{"status":"ok"}` | `true`, номер доступен |
| `200` с другим значением `status`, пустым телом или `null` | `false` |
| `200` с телом, которое не является JSON-объектом | `RestClientException` |
| тело ответа больше 64 КБ | `RestClientException` |
| `404` | `false`, номер недоступен |
| другие `4xx` и `5xx` | `HttpClientErrorException` / `HttpServerErrorException` |
| другие `2xx` и `3xx` | `RestClientResponseException` с кодом ответа |
| сетевые ошибки и таймауты | `ResourceAccessException` |

Тело ответа разбирается как JSON независимо от заголовка `Content-Type`. Лишние поля игнорируются, но мусор после JSON и повторяющиеся ключи считаются ошибкой (`RestClientException`). Редиректы не выполняются. Экранированные символы в `baseUrl` (например, `%20`) передаются как есть, без повторного кодирования.

Если поставщика с таким идентификатором нет в реестре, выбрасывается `UnknownProviderException`.

## Настройки

По умолчанию таймаут соединения 5 секунд, таймаут ответа 10 секунд, ключ передаётся в заголовке `X-Api-Key`. В Spring-конфигурации это меняется свойствами:

| Свойство | По умолчанию | Назначение |
|---|---|---|
| `registry.providers-file` | не задано | путь к JSON-файлу с поставщиками, загружается при старте контекста |
| `registry.connect-timeout-ms` | `5000` | таймаут соединения, мс |
| `registry.read-timeout-ms` | `10000` | таймаут ответа, мс |
| `registry.api-key-header` | `X-Api-Key` | имя HTTP-заголовка для API-ключа, например `Authorization` |

## Использование

### Через Spring

```java
try (var context = new AnnotationConfigApplicationContext(RegistryConfiguration.class)) {
    ProviderRegistry registry = context.getBean(ProviderRegistry.class);
    registry.replaceProviders(List.of(
            new Provider("alpha", "https://alpha.example.com", "alpha-key"),
            new Provider("beta", "https://beta.example.com/api", "beta-key")
    ));
    boolean available = registry.isPhoneAvailable("alpha", "79991234567");
}
```

`RegistryConfiguration` объявляет только бины `ProviderClientFactory` и `ProviderRegistry`. Общие бины `ObjectMapper` и `RestClient.Builder` она не регистрирует, поэтому её можно подключить через `@Import` в приложение на Spring Boot без конфликтов.

### Без Spring-контекста

```java
ProviderRegistry registry = new ProviderRegistry(ProviderClientFactory.withDefaultTimeouts());
```

или со своими таймаутами:

```java
ProviderRegistry registry = new ProviderRegistry(
        ProviderClientFactory.withTimeouts(Duration.ofSeconds(2), Duration.ofSeconds(5))
                .withApiKeyHeader("Authorization"));
```

### Замена набора поставщиков

Набор поставщиков всегда заменяется целиком: старые клиенты удаляются, создаются новые. Замена атомарна. Если в новом списке есть дубликаты идентификаторов или источник не смог прочитать данные, выбрасывается исключение, а прежний набор остаётся без изменений. Вызовы, начатые до замены, завершаются на старом наборе.

Готовым списком:

```java
registry.replaceProviders(List.of(provider1, provider2));
```

Из JSON-файла на диске:

```java
registry.replaceProviders(new JsonFileProviderSource(Path.of("providers.json")));
```

Можно передать любую свою реализацию `ProviderSource`.

### Формат JSON-файла

```json
[
  {
    "id": "alpha",
    "baseUrl": "https://alpha.example.com",
    "apiKey": "alpha-key"
  },
  {
    "id": "beta",
    "baseUrl": "https://beta.example.com/api",
    "apiKey": "beta-key"
  }
]
```

Ошибки чтения:

- файл не найден или не читается — `UncheckedIOException`;
- JSON некорректен, есть неизвестные поля, повторяющиеся ключи, мусор после JSON, `null` в списке или поставщик не проходит проверку — `IllegalArgumentException`;
- файл содержит `null` — пустой список.

## Структура

```
ru.registry
├── Application                  консольная точка входа: демо или проверка номера
├── ConsoleLogFormatter          однострочный формат логов: [INFO] сообщение
├── config
│   ├── RegistryConfiguration    Spring-конфигурация
│   └── RegistryContext          запуск Spring-контекста с файлом поставщиков
├── provider
│   ├── Provider                 данные поставщика и их проверка
│   ├── ProviderSource           источник списка поставщиков
│   └── JsonFileProviderSource   чтение поставщиков из JSON-файла
├── client
│   ├── ProviderClientFactory    создание клиентов, таймауты, заголовок для ключа
│   ├── ProviderClient           HTTP-клиент одного поставщика
│   └── SubscriberStatus         ответ поставщика (внутренний)
├── service
│   ├── ProviderRegistry         реестр клиентов, замена набора и проверка номера
│   └── UnknownProviderException неизвестный идентификатор поставщика
├── demo
│   ├── Demo                     демо-сценарий с таблицей проверок
│   └── DemoProviderServer       локальный демо-сервер поставщиков
└── json
    └── JsonReaders              строгие настройки разбора JSON
```

Зависимости между пакетами идут в одну сторону, без циклов: `Application` → `demo` → `config` → `service` → `client` → `provider` → `json`.

`ProviderClient` создаётся только через `ProviderClientFactory`: его конструктор закрыт внутри пакета `client`. `SubscriberStatus` наружу не виден. Все классы `final`. Каждая замена набора поставщиков пишется в лог (уровень INFO, только идентификаторы, без ключей).

## Тесты

- `ProviderClientTest`: ответ `200 {"status":"ok"}`, `404`, другие значения `status`, пустое тело и `null`, тело не в JSON, мусор после JSON, повторяющиеся ключи, слишком большое тело, ответ без `application/json`, неожиданные `2xx`/`3xx`, ошибки `4xx` и `5xx`, кодировка тела ошибки, настраиваемый заголовок для ключа, сетевая ошибка, некорректные номера, кодирование номера и `baseUrl`.
- `ProviderRegistryTest`: успешный ответ и `404` через реестр, неизвестный поставщик, `null`-аргументы, пустой реестр, замена набора, дубликаты, сбой источника, проверка таймаутов.
- `ProviderTest`: проверка `id`, URL, API-ключа, скрытие ключа в `toString()`.
- `JsonFileProviderSourceTest`: чтение файла, UTF-8 BOM, пустой список и `null`, отсутствующий файл, директория вместо файла, некорректный JSON, неизвестные поля, неполные данные, `null` в списке.
- `RegistryConfigurationTest`: Spring-контекст с файлом поставщиков и без него, заголовок для ключа по умолчанию и из свойства.
- `ApplicationTest`: аргументы, отсутствующий и некорректный файл, неизвестный поставщик, некорректный номер. Сеть не используется.

## Принятые решения

В задании не описаны некоторые детали. Ниже — как они решены:

- **Передача API-ключа.** По умолчанию в заголовке `X-Api-Key`; имя заголовка настраивается (`registry.api-key-header` или `withApiKeyHeader`).
- **Формат номера.** От 1 до 15 цифр, допускается `+` в начале (E.164). Номер, который не подходит под формат, отклоняется до запроса, чтобы не отправить запрос на чужой путь.
- **`200` без `"status":"ok"`.** Номер недоступен (`false`): доступность означает только `{"status":"ok"}`, сравнение строгое, с учётом регистра.
- **`2xx` (кроме `200`) и `3xx`.** Исключение с кодом ответа: такой ответ не описан протоколом, поэтому до вызывающего кода доходит ошибка, а не догадка.
- **Spring без Spring Boot.** Веб-приложение не нужно, поэтому используется Spring Framework 6.2; таймауты задаются в миллисекундах.
- **Демо-режим.** Демо делает настоящие HTTP-запросы, но только к локальному демо-серверу внутри того же процесса. В тестах реальных HTTP-запросов нет, как требует задание, поэтому демо проверяется запуском `./gradlew run`.
- **Без регулярных выражений и шаблонов форматирования.** Проверки номера и имени заголовка, вывод таблицы и формат логов написаны явным кодом, чтобы их было легко читать.
- **`gradlew` и `gradlew.bat`** не изменены: это генерируемые скрипты Gradle с лицензионным заголовком Apache 2.0, который нужно сохранять.

## Вне рамок задачи

Веб-интерфейс, OAuth, повторные попытки и база данных не реализованы.
