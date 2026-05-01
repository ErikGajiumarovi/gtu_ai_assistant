# GTU AI Assistant Project Guide

## Назначение документа

Этот файл является основным рабочим соглашением по проекту. Все дальнейшие архитектурные, организационные и кодовые решения должны ему соответствовать, если отдельно не будет согласовано и зафиксировано новое правило.

## Корневая структура

```text
/
|- backend/
|  |- app/
|  |- presentation/
|  |- domain/
|  |- application/
|  |- infrastructure/
|- frontend/
|- PROJECT_GUIDE.md
```

## Общая архитектура backend

Backend строится на:

- Kotlin
- Ktor
- Multimodule
- DDD
- EDA
- Clean Architecture
- Hexagonal Architecture

### Смысл слоев

- `domain` содержит доменные сущности, value objects, бизнес-правила, а также `in/out ports`.
- `application` содержит реализации `in ports`, orchestration use cases, координацию доменных сценариев.
- `presentation` содержит Ktor REST API, request/response DTO, mapping transport -> application.
- `infrastructure` содержит реализации `out ports`: БД, HTTP-клиенты, AI-интеграции, очереди, внешние системы.
- `app` содержит вход в приложение, сборку модулей, DI, конфигурацию и запуск веб-сервера.

## Жесткие архитектурные правила

### 1. Зависимости между модулями

Допустимые зависимости:

- `domain` -> ни от кого
- `application` -> `domain`
- `presentation` -> `domain`, `application`
- `infrastructure` -> `domain`
- `app` -> `presentation`, `application`, `infrastructure`, `domain`

Запрещено:

- `domain` зависеть от Ktor, Exposed, Koin, PostgreSQL driver, Koog, HTTP-клиентов
- `application` зависеть от транспортного слоя и деталей инфраструктуры
- `presentation` содержать бизнес-логику use case уровня
- `infrastructure` содержать orchestration бизнес-сценариев

### 2. Ports-first подход

Входящие и исходящие порты являются основным контрактом между слоями.

- `in ports` описывают сценарии приложения
- `out ports` описывают зависимости приложения от внешнего мира
- реализации портов находятся вне `domain`

### 3. Ошибки и контракт возврата

Главное правило проекта:

- все `in ports` и `out ports` всегда возвращают `Either`
- `Throwable` никогда не является частью публичного контракта `in ports`
- `in ports` возвращают ошибки, специфичные для конкретного use case
- `out ports` возвращают только `InfrastructureError(cause: Throwable)`
- для работы с `Either` предпочитается Arrow DSL: `either {}`, `ensure`, `bind`, `raise`, `Either.catch`

Базовый контракт:

```kotlin
fun interface CreateAssistantUseCase {
    suspend operator fun invoke(command: CreateAssistantCommand): Either<CreateAssistantError, CreateAssistantResult>
}

data class CreateAssistantCommand(...)

data class CreateAssistantResult(...)

data class InfrastructureError(
    val cause: Throwable
)
```

Правила детализации:

- для каждого use case создается свой тип ошибки `...UseCaseError`
- ошибки `in ports` не должны быть generic-типа `Throwable`, `Exception`, `Error`, `Any`
- `out ports` не протаскивают наружу детали конкретной БД, HTTP-клиента или AI SDK
- адаптеры инфраструктуры ловят исключения и конвертируют их в `InfrastructureError`

Пример:

```kotlin
sealed interface CreateAssistantError {
    data object InvalidName : CreateAssistantError
    data object PromptIsBlank : CreateAssistantError
    data class PersistenceFailed(
        val reason: InfrastructureError
    ) : CreateAssistantError
}

interface CreateAssistantUseCase {
    suspend operator fun invoke(command: CreateAssistantCommand): Either<CreateAssistantError, CreateAssistantResult>
}

data class CreateAssistantCommand(...)

data class CreateAssistantResult(
    val assistant: Assistant
)

fun interface SaveAssistantPort {
    suspend operator fun invoke(assistant: Assistant): Either<InfrastructureError, Assistant>
}
```

### 4. Исключения

- бросать исключения между слоями запрещено как способ бизнес-коммуникации
- исключения допускаются только внутри инфраструктурной реализации
- перед выходом из адаптера инфраструктуры исключение должно быть преобразовано в `Either.Left(InfrastructureError(...))`

### 5. DDD границы

- каждый агрегат должен иметь четкую ответственность
- инварианты агрегата защищаются в `domain`
- use case orchestration живет в `application`
- transport DTO не должны попадать в `domain`
- persistence entities не должны попадать в `domain`

### 6. EDA подход

- доменные события моделируются явно
- событие является частью языка домена
- публикация внешних событий выполняется через `out ports`
- инфраструктура отвечает за конкретную доставку событий

## Модули и минимальные зависимости

Нужно использовать только минимально необходимые библиотеки в каждом модуле.

### `domain`

Назначение:

- entities
- value objects
- domain services
- `in/out ports`
- доменные события

Допустимые библиотеки:

- `arrow-core`

Ограничения:

- без Ktor
- без Koin
- без Exposed
- без PostgreSQL driver
- без `ktor-client`
- без `koog`

### `application`

Назначение:

- реализация `in ports`
- orchestration нескольких `out ports`
- транзакционные и сценарные правила уровня use case

Допустимые библиотеки:

- `arrow-core`
- `kotlinx-coroutines-core`

Ограничения:

- без Ktor
- без Exposed
- без JDBC driver
- без прямого доступа к web/API деталям

### `presentation`

Назначение:

- Ktor routing
- REST handlers
- request/response models
- mapping API <-> application

Допустимые библиотеки:

- `ktor-server-*`
- при необходимости `arrow-core`

Ограничения:

- без SQL/Exposed
- без JDBC driver
- без реализации бизнес-правил

### `infrastructure`

Назначение:

- репозитории
- SQL-доступ
- внешние HTTP-клиенты
- AI adapters
- event bus adapters

Допустимые библиотеки:

- `arrow-core`
- `kotlinx-coroutines-core`
- `ktor-client-*`
- `exposed-*`
- `postgresql`
- `koog`

Ограничения:

- без Ktor server API
- без HTTP endpoint логики

### `app`

Назначение:

- `main`
- wiring модулей
- Koin DI
- запуск Ktor сервера
- конфигурация приложения

Допустимые библиотеки:

- `koin-*`
- `ktor-server-*`
- зависимости на внутренние модули проекта

## Детальный контракт `domain`

`domain` является самым строгим слоем проекта. Он описывает язык предметной области и не зависит ни от transport, ни от persistence, ни от DI, ни от framework-specific деталей.

### Состав `domain`

В `domain` разрешено определять только:

- `AggregateRoot`
- доменные сущности
- value objects
- доменные ошибки
- доменные события
- `in ports`
- `out ports`
- чистые доменные модели и доменные сервисы

В `domain` запрещено определять:

- DTO транспортного слоя
- ORM table/entity модели
- SQL-специфичные типы
- HTTP-контракты
- конфигурационные модели framework-уровня

### Базовый абстрактный класс агрегата

Каждый агрегат должен наследоваться от общего абстрактного корня агрегата. Рекомендуемое имя: `AggregateRoot<ID : Any>`.

Требования:

- `ID` всегда является generic-параметром с ограничением `Any`
- агрегат всегда содержит `id`
- агрегат всегда содержит `version`
- `version` используется для optimistic locking на уровне `application + infrastructure`
- `equals` и `hashCode` должны быть переопределены и опираться на identity агрегата
- агрегат не должен зависеть от БД-механизмов, но обязан хранить `version` как часть своего состояния

Базовый контракт:

```kotlin
abstract class AggregateRoot<ID : Any>(
    open val id: ID,
    open val version: Long
) {
    final override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AggregateRoot<*>
        return id == other.id
    }

    final override fun hashCode(): Int = id.hashCode()
}
```

Правило по `version`:

- при каждом успешном изменении агрегата ожидается изменение версии
- проверка конфликтов версий выполняется вне `domain`, но поле `version` живет в агрегате
- `version` не является технической деталью инфраструктуры, это часть concurrency-контракта модели

### Доменные ошибки

В `domain` существует базовая категория ошибок `DomainError`.

Назначение `DomainError`:

- ошибки инвариантов
- ошибки валидации value objects
- ошибки создания и изменения доменных сущностей

Ограничения:

- `DomainError` не используется как ошибка `in port`
- `DomainError` не используется как ошибка `out port`
- `DomainError` не должен содержать `Throwable`
- `DomainError` не описывает transport, SQL, HTTP, JSON, framework или DI ошибки

Рекомендация по форме:

```kotlin
sealed interface DomainError
```

Дальше конкретные доменные ошибки могут быть:

- общими, если инвариант действительно общий для домена
- локальными для конкретного value object или конкретной сущности

### Value objects

Все value objects определяются в `domain` и являются обертками над примитивами или над маленькими структурированными значениями с внутренней валидацией.

Правила:

- value object должен иметь `private constructor`
- создание value object должно идти через фабричный метод
- фабричный метод `create(...)` возвращает `Either<DomainError, Self>`
- должен существовать `fromTrusted(...)`, который создает объект без валидации
- `fromTrusted(...)` разрешен только для trusted data path, в первую очередь для маппинга данных из БД
- value object должен быть immutable
- value object не должен expose-ить возможность обойти валидацию извне, кроме контролируемого `fromTrusted`

Рекомендуемый шаблон:

```kotlin
data class AssistantName private constructor(
    val value: String
) {
    companion object {
        fun create(value: String): Either<DomainError, AssistantName> =
            either {
                val normalizedValue = value.trim()

                ensure(normalizedValue.isNotBlank()) { AssistantNameError.Blank }
                ensure(normalizedValue.length <= 200) { AssistantNameError.TooLong }

                AssistantName(normalizedValue)
            }

        fun fromTrusted(value: String): AssistantName =
            AssistantName(value)
    }
}
```

### Доменные сущности

Все доменные сущности определяются только в `domain`.

Правила:

- каждая доменная сущность должна иметь `private constructor`
- создание сущности выполняется только через `create(...)`
- `create(...)` возвращает `Either<DomainError, Self>`
- каждая доменная сущность должна иметь `fromTrusted(...)`
- `fromTrusted(...)` возвращает сущность напрямую, без `Either`
- `fromTrusted(...)` используется только в trusted data path, прежде всего на стороне базы данных
- сущность не должна принимать сырые невалидированные primitive values, если вместо них существует value object
- все поля сущности должны быть либо value objects, либо малыми доменными моделями, оформленными как `data class`

Важно:

- `create(...)` нужен для защиты инвариантов при обычном бизнес-создании
- `fromTrusted(...)` нужен только для реконструкции уже существующего состояния из trusted source
- `fromTrusted(...)` нельзя использовать в `presentation` и обычной application orchestration логике

Рекомендуемый шаблон сущности:

```kotlin
class Assistant private constructor(
    override val id: AssistantId,
    override val version: Long,
    val name: AssistantName,
    val prompt: AssistantPrompt
) : AggregateRoot<AssistantId>(id, version) {

    companion object {
        fun create(
            id: AssistantId,
            version: Long,
            name: AssistantName,
            prompt: AssistantPrompt
        ): Either<DomainError, Assistant> =
            either {
                ensure(version >= 0L) { AssistantError.InvalidVersion }

                Assistant(
                    id = id,
                    version = version,
                    name = name,
                    prompt = prompt
                )
            }

        fun fromTrusted(
            id: AssistantId,
            version: Long,
            name: AssistantName,
            prompt: AssistantPrompt
        ): Assistant =
            Assistant(
                id = id,
                version = version,
                name = name,
                prompt = prompt
            )
    }
}
```

### Правило по полям доменных сущностей

Каждое поле доменной сущности должно быть одним из следующих типов:

- value object
- другая доменная модель малого размера в форме `data class`
- коллекция из доменных типов
- идентификатор другого доменного объекта, если связь нужна только по identity

Запрещено по умолчанию:

- хранить в сущностях сырые `String`, `Int`, `Long`, `UUID` и другие primitive-like значения без причины
- тащить transport/persistence модели внутрь сущности
- использовать mutable public state

Если примитив остается примитивом, для этого должна быть явная причина, зафиксированная в проектном решении.

### Порты в `domain`

И `in ports`, и `out ports` определяются в `domain`.

Правила:

- `in ports` описывают доступные бизнес-сценарии
- `out ports` описывают необходимые внешние зависимости домена/application
- `in ports` возвращают `Either<UseCaseSpecificError, Result>`
- `out ports` возвращают `Either<InfrastructureError, Result>`
- `DomainError` не должен утекать наружу как финальный контракт use case
- каждый `in port` хранит свой локальный контракт в одном файле:
  `port + command/query + result + error`
- для `in ports` по умолчанию предпочитается `fun interface` с `suspend operator fun invoke(...)`
- `Repository` как паттерн не используется
- `out ports` моделируются явно: `Find*Port`, `Save*Port`, `Exists*Port`, `Update*Port`
- каждый `Find*Port` обязан использовать strategy pattern

Связь `DomainError` и `UseCaseError`:

- внутри `application` допускается преобразование `DomainError` в use-case-specific ошибку
- наружу из `in port` отдается только ошибка текущего сценария
- наружу из `out port` отдается только `InfrastructureError`

### Trusted / untrusted data path

В проекте есть два разных режима создания доменной модели:

- `create(...)` для untrusted input
- `fromTrusted(...)` для trusted input

`create(...)` используется для:

- входных данных из API
- данных, пришедших из внешних систем
- любых данных, которые еще должны пройти доменную проверку

`fromTrusted(...)` используется для:

- реконструкции сущности из БД
- загрузки уже проверенного snapshot состояния
- внутренних мапперов инфраструктуры, где источник данных считается trusted

Главное ограничение:

- `fromTrusted(...)` не является общей альтернативой `create(...)`
- если данные пришли не из trusted source, использовать `fromTrusted(...)` запрещено

## Детальный контракт `application`

`application` реализует входные порты и orchestrates use cases поверх доменной модели. Этот слой не содержит transport-логики и не содержит инфраструктурных деталей.

### Состав `application`

В `application` разрешено определять:

- `...UseCaseImpl`
- application-level helper functions
- маппинг `DomainError -> UseCaseError`
- orchestration нескольких `out ports`
- координацию optimistic locking flow на уровне сценария

В `application` запрещено определять:

- Ktor routes, request/response DTO
- SQL, Exposed, JDBC, HTTP client implementation
- direct framework IO logic
- реализации `out ports`

### Роль `UseCaseImpl`

Каждая реализация входного порта:

- находится в `application`
- реализует один конкретный `in port`
- использует только зависимости, переданные через конструктор
- в качестве внешних зависимостей использует только `out ports`
- может использовать только функции, определенные в этом же модуле `application`
- может использовать доменные сущности, value objects, доменные сервисы и другие модели из `domain`

Жесткое ограничение:

- `UseCaseImpl` не может ходить напрямую в БД, HTTP, AI SDK, event bus или любые другие внешние системы
- `UseCaseImpl` не может зависеть от конкретных infrastructure-адаптеров
- все внешние вызовы идут только через `out ports`, переданные через конструктор

### Контракт вызова use case

Все функции портов должны быть `suspend`.

Это правило относится к:

- `in ports`
- `out ports`
- реализациям `UseCaseImpl`

Базовая форма:

```kotlin
fun interface CreateAssistantUseCase {
    suspend operator fun invoke(command: CreateAssistantCommand): Either<CreateAssistantError, CreateAssistantResult>
}
```

### Входные данные `application`

На вход `application` должны приходить уже подготовленные доменные типы.

Правила:

- все данные, передаваемые в use case из `presentation`, уже должны быть завернуты в `ValueObject`
- `presentation` отвечает за первичную сборку command/query моделей use case
- `application` не должно работать с сырыми transport primitives, если для них уже определены value objects в `domain`
- если use case принимает `command` или `query`, их поля должны быть доменными типами либо composition из доменных типов

Следствие:

- в normal flow `UseCaseImpl` не создает value objects из raw API input
- проверка корректности raw transport input начинается до входа в бизнес-сценарий

### Работа с ошибками

`application` отвечает за преобразование внутренних ошибок в финальную ошибку конкретного use case.

Правила:

- ошибки из `out ports` должны быть преобразованы через `mapLeft` в ошибку текущего use case
- `DomainError` также должен быть преобразован в ошибку текущего use case
- наружу из `UseCaseImpl` возвращается только `Either<UseCaseError, Result>`
- `InfrastructureError` не должен утекать наружу напрямую из `in port`
- `DomainError` не должен утекать наружу напрямую из `in port`

Рекомендуемый шаблон:

```kotlin
class CreateAssistantUseCaseImpl(
    private val saveAssistantPort: SaveAssistantPort
) : CreateAssistantUseCase {

    override suspend fun invoke(
        command: CreateAssistantCommand
    ): Either<CreateAssistantError, CreateAssistantResult> =
        either {
            val assistant = Assistant
                .create(
                    id = command.id,
                    version = 0L,
                    name = command.name,
                    prompt = command.prompt
                )
                .mapLeft(CreateAssistantError::InvalidDomainState)
                .bind()

            val persistedAssistant = saveAssistantPort
                .invoke(assistant)
                .mapLeft(CreateAssistantError::PersistenceFailed)
                .bind()

            CreateAssistantResult(
                assistant = persistedAssistant
            )
        }
}
```

### Правило зависимостей внутри `application`

`UseCaseImpl` может использовать только:

- `out ports`, переданные через конструктор
- функции и классы, определенные в модуле `application`
- модели и правила из `domain`

`UseCaseImpl` не может использовать:

- классы из `presentation`
- классы из `infrastructure`
- framework-specific helpers из `app`

### Optimistic locking flow

`version` как поле живет в агрегате, но orchestration concurrency flow живет в `application`.

Это означает:

- `application` определяет, когда должна выполняться проверка версии в сценарии изменения
- `application` ожидает, что соответствующий `out port` поддерживает optimistic lock semantics
- конфликт версий преобразуется в use-case-specific ошибку

Пример направления маппинга:

- `InfrastructureError` при техническом сбое -> `UpdateAssistantError.PersistenceFailed`
- доменный конфликт инварианта -> `UpdateAssistantError.InvalidState`
- конфликт optimistic locking -> `UpdateAssistantError.VersionConflict`

### Границы IO и контекстов

`application` не управляет low-level IO dispatcher switching.

Правила:

- `application` не должно использовать `withContext(Dispatchers.IO)`
- IO-bound переключение контекста выполняется в `infrastructure`
- `application` работает через `suspend` контракты и не знает, каким именно образом адаптер выполняет IO

Правило для инфраструктуры:

- если infrastructure-адаптер выполняет блокирующую IO-операцию, он делает это через `withContext(Dispatchers.IO)`

## Детальный контракт `presentation`

`presentation` отвечает за прием transport-запроса, извлечение raw данных, преобразование их в доменные типы и вызов соответствующего `in port`.

### Состав `presentation`

В `presentation` разрешено определять:

- Ktor routes
- handlers / controllers
- request/response DTO
- transport-level mapping
- преобразование raw request data в доменные типы
- вызов `in ports`

В `presentation` запрещено определять:

- реализации `out ports`
- прямой доступ к БД
- прямой доступ к HTTP clients, AI SDK, event bus
- use case orchestration
- optimistic locking persistence logic

### Разрешенные зависимости

`presentation` может использовать только:

- `in ports`
- модели из `domain`
- доменные сущности
- value objects
- domain-level `data class`

`presentation` не может использовать:

- `out ports` напрямую
- классы и реализации из `infrastructure`
- прямые вызовы persistence adapters

Главное правило:

- `presentation` общается с бизнес-сценарием только через `in ports`

### Преобразование запроса в доменную модель

При получении HTTP-запроса `presentation`:

1. достает raw данные из request DTO
2. приводит их к соответствующим доменным типам
3. если преобразование успешно, вызывает use case
4. если преобразование неуспешно, завершает обработку до вызова use case

Разрешенные целевые типы преобразования:

- value objects
- доменные `data class`
- доменные сущности, если контракт use case действительно ожидает их на входе

Правила:

- raw transport primitives не должны передаваться в `application`, если для них уже существует доменный тип
- все преобразования в доменные типы выполняются через `create(...)`
- `presentation` не использует `fromTrusted(...)`
- `presentation` не имеет права обходить доменную валидацию

### Работа с `DomainError` в `presentation`

Если `create(...)` для value object, доменной `data class` или доменной сущности возвращает `Either.Left(DomainError)`, тогда:

- use case не вызывается
- `presentation` возвращает transport-ошибку на основе полученного `DomainError`
- источник ошибки считается pre-use-case validation, а не ошибкой `in port`

Это не противоречит правилу, что `in port` не возвращает `DomainError`, потому что в этом сценарии вызов `in port` вообще не произошел.

### Граница с `application`

Все, что `presentation` передает в `application`, уже должно быть приведено к доменным типам.

Следствие:

- `UseCaseImpl` не валидирует raw request payload
- `presentation` подготавливает `command/query` в терминах домена
- `application` получает уже собранные value objects и другие доменные модели

### Использование доменных сущностей в `presentation`

По умолчанию `presentation` в первую очередь создает:

- value objects
- доменные `data class`
- command/query модели use case

Создание доменной сущности в `presentation` допускается только если:

- это прямо требуется контрактом входного порта
- сущность создается через `create(...)`
- при ошибке немедленно возвращается transport-ответ на основе `DomainError`

`fromTrusted(...)` для сущностей и value objects в `presentation` запрещен.

### Рекомендуемый шаблон потока

```kotlin
suspend fun createAssistant(call: ApplicationCall) {
    val request = call.receive<CreateAssistantRequest>()

    val name = AssistantName.create(request.name)
    val prompt = AssistantPrompt.create(request.prompt)

    val command = either {
        CreateAssistantCommand(
            id = bind(AssistantId.create(request.id)),
            name = bind(name),
            prompt = bind(prompt)
        )
    }

    command.fold(
        ifLeft = { domainError ->
            call.respond(domainError.toHttpError())
        },
        ifRight = { validCommand ->
            createAssistantUseCase(validCommand).fold(
                ifLeft = { useCaseError -> call.respond(useCaseError.toHttpError()) },
                ifRight = { assistant -> call.respond(assistant.toResponse()) }
            )
        }
    )
}
```

## Правила проектирования API и use cases

- один use case = один явный `in port`
- у каждого use case свой `command/query` input и свой error type
- каждый `in port` хранит свой локальный контракт в одном файле
- `presentation` не работает напрямую с `infrastructure`
- `presentation` вызывает только `in ports`
- `application` работает с `out ports`, а не с конкретными адаптерами
- `presentation` передает в `application` уже собранные value objects, а не raw primitives
- `presentation` преобразует raw request data в domain types через `create(...)`
- если `create(...)` вернул `DomainError`, `presentation` завершает запрос без вызова use case
- `application` маппит `DomainError` и `InfrastructureError` в `UseCaseError`
- все функции `in/out ports` являются `suspend`

## Правила именования

- `...UseCase` для входящих портов
- `...UseCaseImpl` для реализаций в `application`
- `Find...Port`, `Save...Port`, `Exists...Port`, `Update...Port`, `...Client`, `...Publisher` для исходящих портов
- `...ClientImpl`, `...PublisherImpl`, `Find...PortImpl`, `Save...PortImpl`, `Exists...PortImpl`, `Update...PortImpl` для адаптеров `infrastructure`
- `...Command`, `...Query`, `...Result`, `...Error` для моделей use case уровня

## Первичная структура backend

```text
backend/
|- app/
|- presentation/
|- domain/
|- application/
|- infrastructure/
```

Следующий этап реализации backend:

1. Создать Gradle multimodule проект внутри `backend/`.
2. Подключить только нужные зависимости на каждый модуль.
3. Описать базовые `in/out ports`.
4. Добавить первый вертикальный use case целиком через все слои.

## Frontend

На текущем этапе `frontend/` резервируется как отдельная часть проекта. Детальная архитектура frontend будет зафиксирована отдельно, когда определим стек и delivery model.

## Непересматриваемые правила по умолчанию

Если дальше в работе явно не согласовано иное, считаем обязательными следующие правила:

- `Either` является обязательным контрактом для всех `in/out ports`
- для `Either` по умолчанию используется Arrow DSL, а не ручной imperative-style `when`
- `in port` ошибки всегда use-case-specific
- `out port` ошибки всегда `InfrastructureError`
- `DomainError` используется только внутри `domain` и при маппинге domain validation -> use case error
- все value objects имеют `private constructor`, `create(...)` и `fromTrusted(...)`
- все доменные сущности имеют `private constructor`, `create(...)` и `fromTrusted(...)`
- каждый агрегат наследуется от `AggregateRoot<ID : Any>` и содержит `id + version`
- каждый `in port` хранит `port + command/query + result + error` в одном файле
- `Repository` не используется; вместо него всегда explicit `out ports`
- каждый `Find*Port` использует strategy pattern
- `presentation` использует только `in ports` и модели `domain`, но не `out ports`
- `presentation` использует только `create(...)` для построения доменных типов из request data
- `presentation` не использует `fromTrusted(...)`
- `UseCaseImpl` использует только constructor-injected `out ports`, `application`-helpers и модели `domain`
- `application` не использует `withContext(Dispatchers.IO)`, это ответственность `infrastructure`
- все функции портов являются `suspend`
- зависимости между модулями направлены только внутрь архитектуры
- инфраструктурные детали не поднимаются в `domain`
- каждый модуль подключает только необходимые ему библиотеки
