# droid-forge — Architecture Plan

Living design document. Decisions made in conversation are marked **DECIDED**.

---

## 0. Concept

Single git repo. `skeleton` branch is a maintained, tested Android project. Every app is a branch
(`app/<name>`) forked from `skeleton`. CI builds APK on push, manual `release.yml` publishes to
GitHub Releases. Install via sideload — no Play Store required.

Built on Termux + proot-distro + opencode. AI writes the app-specific code; the skeleton provides
working, battle-tested platform infrastructure so opencode never has to solve the same Android
boilerplate twice.

---

## 1. Stack

**DECIDED: Pure Kotlin + Jetpack Compose. No React Native.**

| Concern | Pure Kotlin | React Native |
|---|---|---|
| CI build time | ~3-4 min | ~10-15 min (npm + bundler + Gradle) |
| APK size | ~4-6 MB | ~25-40 MB |
| Platform API access | Direct | Needs NativeModule bridge |
| Networking (BLE/TLS/mDNS) | Native, no bridge | Already Kotlin anyway |

**Gradle**: Groovy `.gradle` + `gradle/libs.versions.toml`. Not Kotlin DSL — AI training data for
Groovy is much larger.

**Min SDK**: 26 (Android 8.0, ~95% of devices).

**ABI targets**: `arm64-v8a,armeabi-v7a` only. `x86`/`x86_64` are emulator-only — excluding them
halves APK size for FFmpeg/AI apps.

---

## 2. Design Principles

Every interface and class in the skeleton follows these rules. Opencode inherits them by example.

### Interface Segregation

No fat interfaces. Each interface does one thing. A transport doesn't know about crypto. A crypto
layer doesn't know about transports. A discovery mechanism doesn't know about connections.

```
IDiscovery      — find peers
ISignaling      — exchange connection offers  
ITransport      — send/receive bytes
IFraming        — encode/decode message boundaries
ICipher         — encrypt/decrypt a frame
IIdentity       — persist and load key material
```

A P2P chat app composes: `NsdDiscovery + NostrSignaling + WebRtcTransport + NoiseFraming + AesCipher`
A LAN tracker composes:  `NsdDiscovery + DirectTcpTransport + LengthPrefixFraming + AesCipher`
A co-watch app composes: `NostrSignaling + WebRtcTransport + NoopCipher` (WebRTC encrypts itself)

No component knows about the others.

### Liskov Substitution

Every implementation of an interface must be substitutable without changing caller behaviour.
Concretely: `NsdDiscovery` and `NostrSignaling` both implement `ISignaling` (for the signaling
phase of a WebRTC handshake). Swapping one for the other changes nothing in the connection
establishment code.

This means: no implementation may add preconditions (e.g. "must call init() first") that the
interface doesn't declare, and no implementation may return values the interface doesn't promise.
Lifecycle methods (`connect`, `disconnect`) are declared on the interface so all implementations
honour the same contract.

### Open/Closed

The skeleton's components are open for extension, closed for modification.

- Adding a new transport (QUIC, BLE) = implement `ITransport`, wire it in. No changes to existing code.
- Adding a new cipher (ChaCha20, Double Ratchet) = implement `ICipher`. Framing and transport unchanged.
- Adding a new discovery mechanism (Bluetooth LE scan, DHT) = implement `IDiscovery`. Unchanged callers.

The `P2PSession` class (Section 5) is the composition root. It takes `IDiscovery`, `ISignaling`,
`ITransport`, `IFraming`, `ICipher` as constructor parameters. None of these know about each other.

### Composition over Inheritance

`NetworkService` (foreground service base) is **not** a class to extend. It is a lifecycle manager
that takes a `ServiceWorker` (functional interface) as a dependency:

```kotlin
// BAD — inheritance
class MyBleService : NetworkService() { ... }  // couples to base class internals

// GOOD — composition
val service = NetworkService(worker = BleWorker(bleHelper))
```

`BaseViewModel` follows the same pattern — it holds an `AppState` flow and exposes emit/collect.
It does not know what state looks like. The concrete ViewModel holds the business logic and calls
`emit()` on the shared state machine.

---

## 3. Core Interfaces

```kotlin
// Discovery: find peers on local network or internet
interface IDiscovery {
    fun start()
    fun stop()
    val peers: Flow<PeerInfo>  // emits as peers appear/disappear
}

// Signaling: exchange WebRTC SDP offers and ICE candidates
// Used only for WebRTC setup — not needed for direct TCP
interface ISignaling {
    suspend fun publish(peerId: String, offer: SignalEnvelope)
    fun incoming(peerId: String): Flow<SignalEnvelope>
    suspend fun close()
}

// Transport: raw byte I/O, no framing, no crypto
interface ITransport {
    suspend fun connect(peer: PeerInfo)
    suspend fun disconnect()
    suspend fun send(bytes: ByteArray)
    val incoming: Flow<ByteArray>
    val isConnected: StateFlow<Boolean>
}

// Framing: message boundary encoding (length-prefix, delimiter, etc.)
interface IFraming {
    fun encode(payload: ByteArray): ByteArray
    fun decode(stream: Flow<ByteArray>): Flow<ByteArray>
}

// Cipher: encrypt/decrypt one message at a time
// Stateless (AES-GCM) or stateful (Noise, Double Ratchet) — both fit here
interface ICipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}

// Identity: load and persist key material
interface IIdentity {
    fun publicKey(): ByteArray
    fun sign(data: ByteArray): ByteArray
    fun verify(data: ByteArray, sig: ByteArray, theirPublicKey: ByteArray): Boolean
}
```

Implementations in the skeleton:

| Interface | Implementations |
|---|---|
| `IDiscovery` | `NsdDiscovery` (mDNS/LAN), `NostrDiscovery` (internet) |
| `ISignaling` | `NostrSignaling`, `DirectSignaling` (QR/clipboard for offline exchange) |
| `ITransport` | `TcpTransport`, `WebRtcTransport`, `WebSocketTransport` |
| `IFraming` | `LengthPrefixFraming` (4-byte big-endian length header) |
| `ICipher` | `AesGcmCipher` (stateless), `NoiseCipher` (stateful handshake), `NoopCipher` |
| `IIdentity` | `Ed25519Identity` (file-backed) |

---

## 4. Skeleton File Structure

```
skeleton/
  scripts/
    rename-package.sh         # sed across manifest + build.gradle + all .kt files
    gen-icon.sh               # SVG → adaptive icon density buckets
  android/
    gradle/libs.versions.toml
    app/
      build.gradle            # abiFilters: arm64-v8a,armeabi-v7a
      proguard-rules.pro
      src/main/
        AndroidManifest.xml   # permissions commented, FileProvider wired, Share intents commented
        res/xml/file_paths.xml
        assets/styles/
          greyscale.json      # MapLibre greyscale/low-power style
        java/com/forge/skeleton/
          App.kt
          MainActivity.kt
          MainScreen.kt
          MainViewModel.kt
          ui/
            AppTheme.kt
            AppScaffold.kt
          base/
            AppState.kt       # sealed class: Loading | Success<T> | Error
            AppScope.kt       # application SupervisorJob CoroutineScope
            BaseViewModel.kt
          settings/
            AppSettings.kt
          permission/
            PermissionHelper.kt
          file/
            UriHelper.kt
            ShareHelper.kt
            IntentHelper.kt   # ACTION_SEND / ACTION_SEND_MULTIPLE handler
          notification/
            NotificationHelper.kt
          crash/
            CrashLogger.kt
          work/
            BaseWorker.kt     # CoroutineWorker + StateFlow progress
          network/
            model/
              PeerInfo.kt
              SignalEnvelope.kt
            interfaces/
              IDiscovery.kt
              ISignaling.kt
              ITransport.kt
              IFraming.kt
              ICipher.kt
              IIdentity.kt
            discovery/
              NsdDiscovery.kt       # mDNS — NsdManager, LAN only
              NostrDiscovery.kt     # Nostr relay — internet peer discovery
            signaling/
              NostrSignaling.kt     # SDP/ICE exchange via Nostr relay
              DirectSignaling.kt    # QR / clipboard — fully offline
            transport/
              TcpTransport.kt
              WebSocketTransport.kt
              WebRtcTransport.kt    # wraps io.getstream:webrtc-android (optional dep)
            framing/
              LengthPrefixFraming.kt
            session/
              P2PSession.kt         # composition root: wires IDiscovery+ISignaling+ITransport+IFraming+ICipher
              ServiceWorker.kt      # functional interface for NetworkService
              NetworkService.kt     # foreground service, takes ServiceWorker by composition
          crypto/
            AesGcmCipher.kt         # implements ICipher, stateless AES-256-GCM
            NoiseCipher.kt          # implements ICipher, Noise_XX stateful handshake
            NoopCipher.kt           # implements ICipher, plaintext (WebRTC already encrypted)
            Ed25519Identity.kt      # implements IIdentity, file-backed key pair
          ble/
            BLEHelper.kt
          maps/
            LocationHelper.kt       # FusedLocationProvider as Flow<Location>
  docs/
    crypto.md                 # Signal/Noise/Double Ratchet explained
    blockchain.md             # feasibility analysis
    maps.md                   # OSM, MapLibre, routing, GTFS-RT
    co-watch.md               # synchronized video playback design
    optional-deps.md          # FFmpeg, Signal, ONNX, Room, WebRTC — copy-paste blocks
    ai-inference.md           # on-device model pattern
  SKELETON.md
  .github/workflows/
    build.yml
    release.yml
```

---

## 5. P2PSession — The Composition Root

`P2PSession` is the only place where the interfaces are wired together. It knows about all of them.
Nothing else does.

```kotlin
class P2PSession(
    private val discovery: IDiscovery,
    private val signaling: ISignaling,
    private val transport: ITransport,
    private val framing:   IFraming,
    private val cipher:    ICipher,
) {
    val messages: Flow<ByteArray> = transport.incoming
        .let { framing.decode(it) }
        .map { cipher.decrypt(it) }

    suspend fun send(payload: ByteArray) {
        val encrypted = cipher.encrypt(payload)
        val framed    = framing.encode(encrypted)
        transport.send(framed)
    }

    fun startDiscovery() = discovery.start()

    suspend fun connectTo(peer: PeerInfo) = transport.connect(peer)

    suspend fun close() {
        discovery.stop()
        transport.disconnect()
        signaling.close()
    }
}
```

### Example: LAN group tracker

```kotlin
P2PSession(
    discovery  = NsdDiscovery(context, serviceType = "_tracker._tcp"),
    signaling  = DirectSignaling(),   // LAN — no signaling needed, connect directly
    transport  = TcpTransport(),
    framing    = LengthPrefixFraming(),
    cipher     = AesGcmCipher(groupKey),
)
```

### Example: Internet P2P chat

```kotlin
P2PSession(
    discovery  = NostrDiscovery(relayUrl = "wss://relay.damus.io", topic = roomId),
    signaling  = NostrSignaling(relayUrl = "wss://relay.damus.io"),
    transport  = WebRtcTransport(stunServers = listOf("stun:stun.l.google.com:19302")),
    framing    = LengthPrefixFraming(),
    cipher     = NoiseCipher(identity),   // Noise_XX handshake over the DataChannel
)
```

### Example: Co-watch (WebRTC handles encryption)

```kotlin
P2PSession(
    discovery  = NostrDiscovery(relayUrl, topic = watchRoomId),
    signaling  = NostrSignaling(relayUrl),
    transport  = WebRtcTransport(stunServers),
    framing    = LengthPrefixFraming(),
    cipher     = NoopCipher(),   // DTLS-SRTP already encrypts WebRTC DataChannel
)
```

---

## 6. Peer Discovery and Signaling

**DECIDED: mDNS for LAN, Nostr for internet. No TURN. No Cloudflare Workers.**

### LAN: mDNS (NsdManager)

`NsdDiscovery` advertises `_<serviceType>._tcp.local` and listens for the same. Peers on the same
WiFi appear in `peers: Flow<PeerInfo>` with their local IP and port. Direct TCP connection follows
— no signaling needed.

Android's `NsdManager` has subtle threading requirements: registration and unregistration must
happen on the same thread, and callbacks are delivered on an internal thread. `NsdDiscovery`
encapsulates this correctly. Opencode does not touch it.

### Internet: Nostr + WebRTC + STUN

**Nostr** is a decentralised relay protocol. Any Nostr relay (dozens exist, free, no account
required) can relay signed JSON events. The app generates an Ed25519 keypair on install — that IS
the Nostr identity. No signup.

Signaling flow:
```
Alice                        Nostr Relay                     Bob
  |-- publish SDP offer -------->|                             |
  |   (event kind=25000,         |                             |
  |    to=bob's pubkey,          |-- relay to bob's sub ------>|
  |    encrypted with bob's key) |                             |
  |                              |<-- SDP answer + ICE --------|
  |<-- relay to alice's sub -----|                             |
  |                              |                             |
  |<======= WebRTC DataChannel (DTLS-SRTP) ===================>|
  |             (Nostr relay is done, no longer involved)      |
```

Once WebRTC connects, the Nostr relay is no longer in the path. It only brokered the handshake.

**STUN**: `stun:stun.l.google.com:19302` (free, no account). Discovers public IP for ICE candidates.

**No TURN**: symmetric NAT users (~20%) cannot connect. Acceptable tradeoff — no relay server cost
or infrastructure to maintain.

### Why Nostr works for this

- No account — app generates keypair, that's the identity
- Multiple public relays — app tries several, no single point of failure
- Messages are signed by sender's keypair — the SDP offer is verifiable
- The same keypair can be the app's cryptographic identity (IIdentity) — one key for everything

---

## 7. Crypto

**DECIDED: Noise_XX for synchronous P2P (online both sides). Signal/X3DH documented but not
pre-built — add per-app when async offline messaging is required.**

### What's in the skeleton

**`AesGcmCipher`** — stateless. Takes a symmetric key, encrypts each frame with a random 12-byte
nonce prepended to the ciphertext. No state to manage. Use when the key is established out-of-band
(group pre-shared key, or derived from a Noise handshake).

**`NoiseCipher`** — stateful. Implements `ICipher` but internally runs a Noise_XX handshake on
first use, then uses the resulting session keys for all subsequent encrypt/decrypt calls. The
handshake is transparent to `P2PSession` — it just calls `encrypt`/`decrypt`.

**`Ed25519Identity`** — generates an Ed25519 keypair on first run, persists it to the app's private
files directory. Same keypair is used as Nostr identity (for signaling) and as the Noise static key
(for session encryption). One key, two roles.

**`NoopCipher`** — identity cipher. WebRTC's DataChannel is already encrypted with DTLS-SRTP.
Encrypting again wastes CPU. `NoopCipher` satisfies `ICipher` without doing anything.

### Noise_XX explained briefly

Three-message handshake. Both sides start knowing only their own static keypair.

```
Alice → Bob:  E_alice (ephemeral public key, cleartext)
Bob → Alice:  E_bob + encrypt(S_bob)   // Bob's static key, encrypted under DH(e_bob, E_alice)
Alice → Bob:  encrypt(S_alice)         // Alice's static key, encrypted under accumulated DH state
--- handshake complete ---
Both sides derive two symmetric keys (one per direction) from the accumulated DH outputs.
```

Result: mutual authentication, forward secrecy (ephemeral keys discarded after handshake), identity
hiding from passive observers.

What it does NOT provide: per-message forward secrecy (that's the Double Ratchet), offline
messaging (that's X3DH + prekeys).

### Signal / Double Ratchet (documented, not pre-built)

See `docs/crypto.md` for full explanation. Summary of when you need it:

- **Both online**: Noise_XX is sufficient and simpler.
- **Offline messaging**: need X3DH + prekeys. Prekeys must be stored somewhere Bob's peers can
  fetch them. Options (in order of decentralisation):
  1. Nostr event — Bob publishes a signed prekey bundle as a Nostr event. Alice fetches it.
     No server. One-time prekeys aren't truly single-use (anyone can fetch the same event) but
     signed prekeys rotate weekly. Acceptable for personal apps.
  2. Self-hosted endpoint — a single static file server (nginx, GitHub Pages) serving Bob's
     current signed prekey JSON. Bob rotates it periodically. Stateless, no compute.
  3. Per-app decision — skeleton documents the interface, implementation chosen per app.

### Key hierarchy

```
Ed25519 keypair (IIdentity)
  ├── Nostr identity (pubkey = Nostr npub, signs relay events)
  ├── Noise static key (used in Noise_XX handshake)
  └── DID key (did:key:z... — the public key IS the decentralised identity)
```

One keypair. Three uses. No separate key management per protocol.

---

## 8. NetworkService — Composition not Inheritance

`NetworkService` is a foreground Android `Service` that manages lifecycle. It does not know what
work it's doing. Work is injected as a `ServiceWorker`.

```kotlin
fun interface ServiceWorker {
    suspend fun run(scope: CoroutineScope)
}

class NetworkService : Service() {
    private var worker: ServiceWorker? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun attach(worker: ServiceWorker) { this.worker = worker }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        scope.launch { worker?.run(this) }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel() }
    override fun onBind(intent: Intent?) = binder
}
```

Usage:
```kotlin
// BLE tracking
service.attach(ServiceWorker { scope ->
    bleHelper.connect(address, scope)
    bleHelper.notifications.collect { frame -> process(frame) }
})

// TCP server
service.attach(ServiceWorker { scope ->
    tcpServer.accept(port, scope).collect { conn -> handleConnection(conn) }
})
```

No subclassing. `NetworkService` never changes when new service types are added.

---

## 9. Protocols

**DECIDED: WebRTC (STUN only, no TURN) for internet P2P. mDNS + direct TCP for LAN.**

| Use case | Protocol | Notes |
|---|---|---|
| LAN discovery | mDNS (NsdManager) | Built-in, no deps |
| Internet peer discovery + signaling | Nostr relay | No account, decentralised |
| LAN data (tracker, co-watch) | TCP via TcpTransport | Simple, reliable |
| Internet P2P (chat, co-watch) | WebRTC DataChannel | STUN NAT traversal, DTLS encrypted |
| Video/audio call | WebRTC MediaStream | Same transport, add media tracks |
| REST APIs (flights, weather, OSM) | HTTP/2 (OkHttp) | Standard |
| Real-time location (server-based) | MQTT | QoS 1, pub-sub, tiny overhead |
| BLE peripheral | GATT via BLEHelper | From wristturn-app |

### WebRTC library

`io.getstream:webrtc-android` (~20MB AAR). Documented in `docs/optional-deps.md`. Not in skeleton's
`build.gradle` — only apps that need P2P or video add it.

### QUIC

Documented in `docs/optional-deps.md`. Android's Cronet supports it. Useful for file transfer over
mobile where connection migration (WiFi → LTE) matters. Not in skeleton.

---

## 10. Maps and Location

### Map rendering

**MapLibre Android** — vector tiles, offline capable, open source. Not in skeleton `build.gradle`
(adds ~10MB). Documented in `docs/optional-deps.md` with ready-made `libs.versions.toml` entry.

**Greyscale style** — pre-built as `assets/styles/greyscale.json`. Every map app gets this for
free even if MapLibre isn't in the skeleton. The style JSON is just a text file.

**Offline tiles** — Protomaps `.pmtiles` format. Single file per region, downloaded once.
Pattern documented in `docs/maps.md`.

### Routing and travel data

See `docs/maps.md`. Summary:

| Data | Source | Notes |
|---|---|---|
| Geocoding | Nominatim (OSM) | Free, self-hostable |
| Routing | OSRM public API | Rate-limited, self-hostable |
| POI search | Overpass API | Full OSM query language |
| Flights (live positions) | OpenSky Network | Free ADS-B |
| Flights (search/pricing) | Amadeus API | Free sandbox 2000 req/month |
| Transit schedules | GTFS feeds | Every major agency publishes these |
| Transit real-time | GTFS-RT | Protobuf, vehicle positions + delays |
| Weather | Open-Meteo | Completely free, no API key |

### Group location sharing

Binary message format (32 bytes/update):
```
userId (4) | lat (8) | lng (8) | accuracy (4) | timestamp (8) | [encrypted with group AES key]
```

Transport: on LAN use `TcpTransport` + `NsdDiscovery`. Over internet use MQTT (broker-based,
everyone subscribes to `trips/<roomId>/locations/#`) or WebRTC mesh.

Location data is always E2E encrypted with `AesGcmCipher` before sending. Server/broker sees only
ciphertext.

---

## 11. Co-watching Video

**Components**

- Player: `androidx.media3:media3-exoplayer`
- Sync channel: `WebRtcTransport` DataChannel (reliable, ordered) for P2P, or WebSocket to a
  relay for server-based
- Chat: second DataChannel or same channel with message type tag
- Encryption: `NoopCipher` (WebRTC DataChannel is DTLS-SRTP encrypted)

**Sync protocol**

Leader (room creator) broadcasts at 1Hz:
```json
{ "type": "sync", "pos": 142.3, "playing": true, "at": 1721234567890 }
```

Follower applies:
```
target = sync.pos + (localNow - sync.at) / 1000.0
if |current - target| > 0.5s → seek(target)
else if drift > 0.1s → setPlaybackSpeed(1.02)  // catch up imperceptibly
```

Clock offset computed at session start via 4-round-trip NTP exchange (±20ms on LAN, ±100ms internet).

**Share target**: app appears in Android share sheet for `video/*` and `application/x-mpegurl` (HLS
playlists). `IntentHelper` extracts the URI and hands it to the player.

---

## 12. Android Share Target

### Receiving (appear in share sheet)

`IntentHelper.kt` handles `ACTION_SEND` and `ACTION_SEND_MULTIPLE`. Reads URIs, copies them to
app cache via `UriHelper`, then hands to the app's processing flow.

Manifest intent filters are commented out by default. Opencode uncomments the MIME types needed:

```xml
<!-- Uncomment for image sharing: -->
<!-- <data android:mimeType="image/*" /> -->
<!-- Uncomment for video: -->
<!-- <data android:mimeType="video/*" /> -->
<!-- Uncomment for any file: -->
<!-- <data android:mimeType="*/*" /> -->
```

### Direct Share shortcuts

`ShortcutManager.setDynamicShortcuts()` exposes contacts/rooms as top-level share targets. Update
shortcuts when contacts change. Pattern documented in `docs/share-targets.md`.

---

## 13. Blockchain

**DECIDED: Tier 5 (documented only). Too app-specific for skeleton.**

What is feasible on mobile (no node, no mining):

| Capability | Library | Notes |
|---|---|---|
| Wallet keygen + signing | `web3j` | BIP39 mnemonic → HD derivation → address |
| Read chain state | `web3j` JSON-RPC | `eth_call`, `eth_getBalance` via public RPC |
| IPFS content | `java-ipfs-http-client` | Store/fetch by CID, pin via Pinata |
| DID identity | `did:key` (no lib needed) | Ed25519 pubkey → `did:key:z...` |
| Signed messages | `web3j` | Prove ownership without on-chain tx |

**DID alignment**: The `Ed25519Identity` keypair in the skeleton is the same keypair that becomes
`did:key:z<base58-encoded-pubkey>`. No extra library. The skeleton's identity layer is DID-ready
by design.

See `docs/blockchain.md` for full analysis including payment channels and ZK proof verification.

---

## 14. Optional Deps Reference

All in `docs/optional-deps.md`. Copy-paste blocks including ProGuard rules.

| Dep | Size | When |
|---|---|---|
| `io.getstream:webrtc-android` | ~20MB | Any P2P / video feature |
| `ffmpeg-kit-android-min` | ~15MB/ABI | Video/audio processing |
| `org.tensorflow:tensorflow-lite` | ~1MB + model | On-device vision inference |
| `com.microsoft.onnxruntime:onnxruntime-android` | ~5MB | General on-device inference |
| `llama.cpp` (JNI) | varies | On-device LLM (Phi-3, Gemma-2B) |
| `androidx.room:room-runtime` | ~500KB | SQLite ORM |
| `org.maplibre.gl:android-sdk` | ~10MB | Vector maps |
| `noise-java` | ~50KB | Noise_XX if rolling your own |
| `libsignal-client` | ~5MB | Full X3DH + Double Ratchet |
| `web3j` | ~3MB | Ethereum wallet/RPC |
| `org.eclipse.paho.client.mqttv3` | ~200KB | MQTT location sharing |

---

## 15. CI / Release

### build.yml

- Push to `skeleton` → tests only (skeleton is not an installable app)
- Push to `app/**` → tests + `assembleRelease` + upload APK artifact (30-day retention)

### release.yml (manual)

`workflow_dispatch` inputs: `branch` (e.g. `app/img-to-pdf`), `tag` (e.g. `v1.0.0`).
Checks out that branch, rebuilds clean, runs tests, publishes GitHub Release tagged
`{branch}-{tag}` (e.g. `app/img-to-pdf-v1.0.0`).

---

## 16. SKELETON.md — opencode contract

```markdown
## Pre-built — use these, do not regenerate

### Core
- AppSettings — SharedPreferences singleton, already init'd in App.kt
- PermissionHelper(activity).request(permission, onGranted, onDenied)
- PermissionHelper(activity).requestMultiple(permissions, onAllGranted, onDenied)
- UriHelper.copyToCache(context, uri): File
- ShareHelper.shareFile(context, file, mimeType)
- IntentHelper.handleShareIntent(intent): List<Uri>
- BaseViewModel<S> — extend, call emit(AppState.Success(data))
- AppTheme { } — wrap top-level Composable
- AppScaffold(title, actions) { content }
- NotificationHelper.createChannels(context) — called from App.kt
- CrashLogger.init(context) — called from App.kt
- BaseWorker — extend CoroutineWorker, report via StateFlow

### Network interfaces (compose these, never instantiate directly in business logic)
- IDiscovery, ISignaling, ITransport, IFraming, ICipher, IIdentity

### Network implementations
- NsdDiscovery(context, serviceType) — mDNS, LAN
- NostrDiscovery(relayUrl, topic) — internet via Nostr relay
- NostrSignaling(relayUrl) — WebRTC SDP/ICE exchange via Nostr
- DirectSignaling() — offline, QR/clipboard
- TcpTransport() — direct TCP
- WebSocketTransport(url) — OkHttp WebSocket
- WebRtcTransport(stunServers) — P2P with NAT traversal (needs webrtc-android dep)
- LengthPrefixFraming() — 4-byte length header framing
- AesGcmCipher(key) — stateless AES-256-GCM
- NoiseCipher(identity) — Noise_XX stateful handshake
- NoopCipher() — no-op, for WebRTC (already encrypted)
- Ed25519Identity(filesDir) — persistent keypair, also Nostr identity + DID key

### Session
- P2PSession(discovery, signaling, transport, framing, cipher) — wire these together here only

### Service
- NetworkService — foreground service, call service.attach(ServiceWorker { scope -> ... })

### BLE
- BLEHelper — scan/connect/notify lifecycle

### Location
- LocationHelper(context).locations: Flow<Location>

## You fill in
1. scripts/rename-package.sh com.forge.APPNAME AppName
2. res/values/strings.xml: app_name
3. AndroidManifest.xml: uncomment permissions + intent-filters
4. MainScreen.kt: your Composable
5. MainViewModel.kt: extend BaseViewModel<YourState>
6. libs.versions.toml + build.gradle: add app-specific deps (docs/optional-deps.md)
7. Compose P2PSession in your ViewModel or use-case layer — not in MainActivity

## Do not touch
- network/interfaces/ — never modify existing interfaces, add new ones if needed
- network/discovery/, network/signaling/, network/transport/, network/framing/
- crypto/, ble/, permission/, file/, notification/, crash/
- FileProvider config (authority = com.forge.APPNAME.fileprovider)
- build.yml / release.yml
```
