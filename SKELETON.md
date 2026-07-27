# SKELETON.md — opencode contract

Read this before writing any code. Everything listed under "Pre-built" already exists and is tested.
Do not regenerate, reimport, or rewrite these components.

---

## Pre-built — use these, do not regenerate

### Core

| Component | Signature | Notes |
|---|---|---|
| `AppSettings` | `AppSettings.getInstance(context)` | SharedPreferences singleton, init'd in App.kt |
| `PermissionHelper` | `PermissionHelper(activity).request(permission, onGranted, onDenied)` | Single permission |
| `PermissionHelper` | `PermissionHelper(activity).requestMultiple(permissions, onAllGranted, onDenied)` | Multiple |
| `UriHelper` | `suspend UriHelper.copyToCache(context, uri): File` | SAF URI → File in cache dir |
| `ShareHelper` | `ShareHelper.shareFile(context, file, mimeType)` | FileProvider-aware share sheet |
| `IntentHelper` | `IntentHelper.handleShareIntent(intent): List<Uri>` | ACTION_SEND / ACTION_SEND_MULTIPLE |
| `BaseViewModel<S, I>` | `emit(AppState.Success(data))`, `handle(intent: I)` | Extend with your state + intent types |
| `AppEventBus` | `AppEventBus.emit(event)` / `AppEventBus.events.collect {}` | Typed broadcast bus |
| `UseCase<In, Out>` | `suspend fun execute(input: In): Result<Out>` | Implement for each orchestrated workflow |
| `AppTheme` | `AppTheme { }` | Material3 theme, wrap top-level Composable |
| `AppScaffold` | `AppScaffold(title, actions, showLoading) { content }` | Scaffold + TopAppBar + loading indicator |
| `NotificationHelper` | `NotificationHelper.createChannels(context)` | Called from App.kt — do not call again |
| `CrashLogger` | `CrashLogger.init(context)` | Called from App.kt — do not call again |
| `BaseWorker` | Extend `CoroutineWorker`, call `reportProgress(percent)` | StateFlow progress |
| `AppScope` | `AppScope.launch { }` | Application SupervisorJob CoroutineScope |

### Network interfaces — compose these, never instantiate directly in business logic

```kotlin
IDiscovery   // find peers (LAN or internet)
ISignaling   // exchange WebRTC SDP/ICE
ITransport   // raw byte I/O
IFraming     // message boundary encoding
ICipher      // encrypt / decrypt one frame
IIdentity    // persist and use key material
```

The interfaces are in `network/interfaces/`. **Never modify existing interfaces.** Add new interfaces if needed.

### Network implementations — wire into P2PSession only

| Class | Constructor | When to use |
|---|---|---|
| `NsdDiscovery` | `NsdDiscovery(context, serviceType)` | mDNS, LAN only |
| `NostrDiscovery` | `NostrDiscovery(relayUrl, topic)` | Internet peer discovery via Nostr relay |
| `NostrSignaling` | `NostrSignaling(relayUrl)` | WebRTC SDP/ICE exchange via Nostr |
| `DirectSignaling` | `DirectSignaling()` | Offline, QR/clipboard, or in-process |
| `TcpTransport` | `TcpTransport()` | Direct TCP, LAN |
| `WebSocketTransport` | `WebSocketTransport(url)` | OkHttp WebSocket |
| `WebRtcTransport` | `WebRtcTransport(stunServers)` | P2P with NAT traversal — add `io.getstream:webrtc-android` dep first |
| `LengthPrefixFraming` | `LengthPrefixFraming()` | 4-byte big-endian length prefix |
| `AesGcmCipher` | `AesGcmCipher(key)` | Stateless AES-256-GCM, key established out-of-band |
| `NoiseCipher` | `NoiseCipher(identity)` | Noise_XX stateful handshake, mutual auth |
| `NoopCipher` | `NoopCipher()` | No-op — use when transport already encrypts (WebRTC DataChannel) |
| `Ed25519Identity` | `Ed25519Identity(filesDir)` | Persistent keypair — Nostr identity + Noise static key + DID key |

### Session

```kotlin
P2PSession(discovery, signaling, transport, framing, cipher)
```

This is the **only** place where the six interfaces are wired together. Business logic, ViewModels,
and use-case classes receive a `P2PSession` — they never hold individual interface references.

#### Example compositions

```kotlin
// LAN group tracker
P2PSession(
    discovery  = NsdDiscovery(context, "_tracker._tcp"),
    signaling  = DirectSignaling(),
    transport  = TcpTransport(),
    framing    = LengthPrefixFraming(),
    cipher     = AesGcmCipher(groupKey),
)

// Internet P2P chat
P2PSession(
    discovery  = NostrDiscovery("wss://relay.damus.io", roomId),
    signaling  = NostrSignaling("wss://relay.damus.io"),
    transport  = WebRtcTransport(listOf("stun:stun.l.google.com:19302")),
    framing    = LengthPrefixFraming(),
    cipher     = NoiseCipher(identity),
)

// Co-watch (WebRTC already encrypted)
P2PSession(
    discovery  = NostrDiscovery(relayUrl, watchRoomId),
    signaling  = NostrSignaling(relayUrl),
    transport  = WebRtcTransport(stunServers),
    framing    = LengthPrefixFraming(),
    cipher     = NoopCipher(),
)
```

### Foreground service

```kotlin
// Do NOT subclass NetworkService. Inject work via ServiceWorker.
service.attach(ServiceWorker { scope ->
    tcpTransport.incoming.collect { bytes -> process(bytes) }
})
```

### BLE

`BLEHelper` — scan, connect, subscribe to notifications. See `ble/BLEHelper.kt`.

### Location

```kotlin
LocationHelper(context).locations  // Flow<Location>, 5-second interval
```

---

## You fill in

These are the only files opencode should create or modify for a new app:

1. **Rename package**: `bash scripts/rename-package.sh com.forge.APPNAME AppName`
2. **`res/values/strings.xml`**: set `app_name` to your app's display name
3. **`AndroidManifest.xml`**: uncomment the permissions your app needs (list is in the file)
4. **`MainScreen.kt`**: replace the skeleton placeholder with your Composable UI
5. **`MainViewModel.kt`**: replace `BaseViewModel<String>` with your state type and logic
6. **`libs.versions.toml` + `app/build.gradle`**: add app-specific deps (see `docs/optional-deps.md`)
7. **Compose `P2PSession`** in your ViewModel or a use-case class — not in `MainActivity`

For room persistence, maps, video, WebRTC, BLE, or AI inference — copy the relevant block from
`docs/optional-deps.md`.

---

## Do not touch

The following are frozen. Opencode must not modify them.

- `network/interfaces/` — never modify existing interfaces, add a new file if you need a new one
- `network/discovery/`, `network/signaling/`, `network/transport/`, `network/framing/`
- `crypto/`, `ble/`, `permission/`, `file/`, `notification/`, `crash/`
- `FileProvider` config (authority = `${applicationId}.fileprovider`)
- `.github/workflows/build.yml` and `release.yml`

---

## Communication boundaries

| Mechanism | When to use |
|---|---|
| `suspend fun` | Caller needs the result before proceeding |
| `Flow<T>` | Continuous stream — producer runs independently of consumer |
| `AppEventBus.emit(event)` | Broadcast fact — sender does not know who listens |

Never use `GlobalScope`, `runBlocking` on main thread, or raw callbacks where a coroutine fits.

## Coordination boundaries

| Pattern | When to use |
|---|---|
| `UseCase<In, Out>` | Steps have a defined order; failure in one step affects others |
| `AppEventBus` subscriber | Reaction to a fact, independent of other reactions |

**Rule**: orchestrate the workflow (UseCase), choreograph the side effects (AppEventBus).
A UseCase emits AppEvents at the end. Subscribers react independently.

## UI contract

- UI reads `state: StateFlow<AppState<S>>` only — never repositories or DAOs directly
- UI sends `viewModel.handle(intent)` only — no other ViewModel method calls
- State is immutable — no mutable fields, no side effects in state classes
- `AppState.Loading` → `AppScaffold(showLoading = true)`, not a local boolean

See `docs/architecture.md` for the full design with examples.

---

## Key invariants

- **Money**: always `Long` (cents). Never `Float` or `Double`.
- **Database/IO**: always `withContext(Dispatchers.IO)` or DAO `Flow`. Never main thread.
- **Coroutine scope**: `AppScope` or `viewModelScope`. Never `GlobalScope`.
- **Strings**: always `res/values/strings.xml`. No hardcoded user-visible strings in Kotlin.
- **Interfaces**: if a new implementation uses only 3 of 8 interface methods, the interface is too
  fat — split it before implementing.
- **Inheritance**: if you are writing `class Foo : Bar()`, question it. Inject `Bar` instead.
