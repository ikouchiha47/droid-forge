# Architecture — Communication, Coordination, and UI

Three decisions every app in this repo must make explicitly. The skeleton enforces the
boundaries; each app branch fills in the domain-specific parts.

---

## 1. Communication — Sync vs Async

### Sync (`suspend fun`)

Use when the caller needs the result before it can proceed. The coroutine suspends
and resumes when the result is ready. Failures propagate as exceptions or `Result<T>`.

```kotlin
// DAO query — caller waits for the row
suspend fun loadUser(id: Long): User

// Use-case — caller waits for the full operation
suspend fun execute(input: CreateOrderInput): Result<Order>
```

### Async stream (`Flow<T>`)

Use for continuous data where the producer runs independently of the consumer.
The consumer collects at its own pace; back-pressure is handled by the Flow machinery.

```kotlin
// Location updates — producer runs on its own schedule
val locations: Flow<Location>

// Incoming P2P messages — arrive whenever the peer sends
val incoming: Flow<ByteArray>

// DB query result that updates when the table changes
fun observeOrders(): Flow<List<Order>>
```

### Async broadcast (`AppEventBus`)

Use for facts that have already happened and any number of subscribers may care about.
No caller-receiver relationship. No back-pressure. The sender does not know who listens.

```kotlin
AppEventBus.emit(AppEvent.PeerConnected(peer))
AppEventBus.emit(AppEvent.MessageReceived(from, payload))
```

Subscribers collect in their own scope:

```kotlin
viewModelScope.launch {
    AppEventBus.events
        .filterIsInstance<AppEvent.PeerConnected>()
        .collect { peer -> handle(MyIntent.PeerArrived(peer)) }
}
```

### Decision rule

| Question | Answer → mechanism |
|---|---|
| Does the caller need the result? | Yes → `suspend fun` |
| Is the data a continuous stream? | Yes → `Flow<T>` |
| Is this a fact broadcast to anyone who cares? | Yes → `AppEventBus` |

Never use `GlobalScope`, `runBlocking` on the main thread, or raw callbacks where
a coroutine or Flow fits.

---

## 2. Coordination — Orchestration vs Choreography

### Orchestration

A single class owns a workflow. It calls services in order, handles failure, and decides
what to do next. Anyone reading it can follow the sequence.

Use when:
- The steps have a defined order with failure modes between them
- A step failing means previous steps must be compensated (rollback)
- The workflow spans more than one service

```kotlin
class ConnectToPeerUseCase(
    private val session: P2PSession,
    private val bus: AppEventBus,
) : UseCase<PeerInfo, Unit> {
    override suspend fun execute(input: PeerInfo): Result<Unit> = runCatching {
        session.connectTo(input)          // step 1: transport connects
        // step 2: Noise handshake happens inside NoiseCipher on first send
        AppEventBus.emit(AppEvent.PeerConnected(input))  // broadcast the fact
    }
}
```

The ViewModel calls the use-case. The use-case owns the sequence. The use-case emits
events when the workflow produces facts others care about.

### Choreography

No central coordinator. Each component subscribes to events it cares about and reacts
independently. The sequence is implicit — it emerges from who subscribes to what.

Use when:
- The reaction is genuinely independent (no ordering dependency)
- Adding a new reactor should not require changing existing code (OCP)
- The fact is relevant to multiple unrelated components

```kotlin
// NotificationService reacts to PeerConnected — independently
AppEventBus.events
    .filterIsInstance<AppEvent.PeerConnected>()
    .collect { peer -> showNotification("${peer.id} connected") }

// SyncService also reacts — knows nothing about NotificationService
AppEventBus.events
    .filterIsInstance<AppEvent.PeerConnected>()
    .collect { peer -> startSync(peer) }
```

### Decision rule

| Question | Answer → pattern |
|---|---|
| Do the steps have a defined order? | Yes → Orchestration (`UseCase`) |
| Does failure in step N require undoing step N-1? | Yes → Orchestration |
| Is this a reaction to a fact, independent of other reactions? | Yes → Choreography (`AppEventBus`) |
| Would adding a new reaction require touching existing code? | Yes → wrong, use Choreography |

In practice: **orchestrate the workflow, choreograph the side effects**.

The use-case runs the sequence and emits `AppEvent` facts at the end. Other components
subscribe to those facts and react. The use-case never knows about them.

---

## 3. UI — State as render function (MVI)

The UI is a pure function of state: `UI = f(State)`. No UI component holds its own
data. No direct calls from UI into business logic except through intents.

```
User action → Intent → ViewModel.handle() → new State → UI re-renders
```

### AppIntent

Each screen defines its own sealed intent class:

```kotlin
sealed class OrderIntent : AppIntent {
    object LoadOrders : OrderIntent()
    data class CancelOrder(val id: Long) : OrderIntent()
    data class FilterByStatus(val status: Status) : OrderIntent()
}
```

### ViewModel

```kotlin
class OrderViewModel(
    private val loadOrders: LoadOrdersUseCase,
    private val cancelOrder: CancelOrderUseCase,
) : BaseViewModel<OrderState, OrderIntent>() {

    override fun handle(intent: OrderIntent) {
        when (intent) {
            is OrderIntent.LoadOrders -> load()
            is OrderIntent.CancelOrder -> cancel(intent.id)
            is OrderIntent.FilterByStatus -> filter(intent.status)
        }
    }

    private fun load() {
        emit(AppState.Loading)
        viewModelScope.launch {
            loadOrders.execute(NoInput)
                .onSuccess { emit(AppState.Success(it)) }
                .onFailure { emit(AppState.Error(it.message ?: "")) }
        }
    }
}
```

### Composable

```kotlin
@Composable
fun OrderScreen(viewModel: OrderViewModel) {
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = stringResource(R.string.orders),
        showLoading = state is AppState.Loading,
    ) {
        when (val s = state) {
            is AppState.Loading -> { /* scaffold handles it */ }
            is AppState.Success -> OrderList(
                orders = s.data.orders,
                onCancel = { id -> viewModel.handle(OrderIntent.CancelOrder(id)) },
            )
            is AppState.Error -> ErrorView(
                message = s.message,
                onRetry = { viewModel.handle(OrderIntent.LoadOrders) },
            )
        }
    }
}
```

### Rules

- UI reads `state` only. It never reads from repositories, DAOs, or use-cases directly.
- UI sends `intent` only. It never calls ViewModel methods directly except `handle()`.
- State is a value — no mutability, no side effects in the state class.
- `AppState.Loading` is shown by `AppScaffold(showLoading = true)` — not with a local boolean.

---

## How the three work together

```
User taps "Connect"
  → ConnectIntent.Connect(peer)
  → ViewModel.handle()
  → ConnectToPeerUseCase.execute(peer)   ← orchestration
      → session.connectTo(peer)
      → AppEventBus.emit(PeerConnected)  ← fact broadcast
  → ViewModel emits AppState.Success(ConnectedState)
  → UI re-renders

Meanwhile (choreography):
  NotificationService ← listens for PeerConnected → shows notification
  AnalyticsService    ← listens for PeerConnected → logs event
  SyncService         ← listens for PeerConnected → starts sync
```

No component in the "meanwhile" block knows about the ViewModel, the use-case, or
each other. They react to the same fact independently.
