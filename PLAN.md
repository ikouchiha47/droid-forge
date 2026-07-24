# droid-forge — Architecture Plan

Living design document. Update as decisions are made.

---

## 0. Concept

Single git repo. `skeleton` branch is a maintained, tested Android project. Every app is a branch
(`app/<name>`) forked from `skeleton`. CI builds APK on push, manual `release.yml` publishes to
GitHub Releases. Install via sideload — no Play Store required.

Built on Termux + proot-distro + opencode. AI writes the app-specific code; the skeleton provides
working, battle-tested platform infrastructure so opencode never has to solve the same Android
boilerplate twice.

---

## 1. Stack Decision

**Pure Kotlin + Jetpack Compose. No React Native.**

| Concern | Pure Kotlin | React Native |
|---|---|---|
| CI build time | ~3-4 min | ~10-15 min (npm + bundler + Gradle) |
| APK size | ~4-6 MB | ~25-40 MB |
| Platform API access | Direct — no bridge | Needs NativeModule for anything below JS |
| opencode quality | Good (Compose well-represented) | Very good (TSX excellent) |
| Networking (BLE/TLS/mDNS) | Native Kotlin, no bridge needed | Already in Kotlin anyway (see wristturn-app) |

The wristturn-app and CodeKeyboard both show that all the hard platform work is Kotlin regardless of
the UI layer. The RN bridge adds overhead without adding capability for utility and networking apps.

**Gradle**: Groovy `.gradle` files + `gradle/libs.versions.toml` for deps. Not Kotlin DSL — AI
training data for Groovy Gradle is much larger and more reliable.

**Min SDK**: 26 (Android 8.0, ~95% of devices). Unlocks: `NsdManager` stability, `JobScheduler`,
proper foreground service APIs, `AudioFocusRequest`.

---

## 2. Skeleton Structure

```
skeleton/
  scripts/
    rename-package.sh         # sed across manifest + build.gradle + all .kt files
    gen-icon.sh               # SVG → adaptive icon density buckets (requires Inkscape or rsvg)
  android/
    gradle/libs.versions.toml
    app/
      build.gradle            # abiFilters: arm64-v8a,armeabi-v7a (not x86 — emulator-only)
      proguard-rules.pro      # baseline + sections for each optional dep
      src/main/
        AndroidManifest.xml   # permissions commented, FileProvider wired, Share intents commented
        res/xml/file_paths.xml
        java/com/forge/skeleton/
          App.kt              # Application subclass
          MainActivity.kt
          MainScreen.kt       # placeholder Composable
          MainViewModel.kt
          ui/
            AppTheme.kt
            AppScaffold.kt    # Scaffold + TopAppBar + LoadingOverlay
          base/
            BaseViewModel.kt
            AppState.kt       # sealed class: Loading | Success<T> | Error
            AppScope.kt       # application-level SupervisorJob CoroutineScope
          settings/
            AppSettings.kt    # SharedPreferences singleton (proven pattern from CodeKeyboard)
          permission/
            PermissionHelper.kt
          file/
            UriHelper.kt      # copy content URI to processable File (SAF)
            ShareHelper.kt    # ShareCompat wrapper, FileProvider-aware
          notification/
            NotificationHelper.kt  # channel creation, required Android 8+
          crash/
            CrashLogger.kt    # write crash to file — no Play Store = no Firebase
          work/
            BaseWorker.kt     # CoroutineWorker with StateFlow progress — for FFmpeg/AI
          network/            # present, not wired into MainActivity by default
            ITransport.kt
            NsdHelper.kt      # mDNS via NsdManager (built-in, no deps)
            TcpServer.kt      # coroutine-based, length-prefix framed
            TcpClient.kt
            NetworkService.kt # foreground service base — subclass for BLE/TCP/WebSocket
            SecureChannel.kt  # TLS TOFU: RSA keygen, self-signed cert (BouncyCastle), persist identity
            WebSocketTransport.kt
          ble/
            BLEHelper.kt      # scan→connect→GATT→notify lifecycle (from wristturn-app)
          crypto/
            CryptoHelper.kt   # AES-256-GCM, HKDF — Android JCE, zero deps
            IdentityStore.kt  # persist/load RSA/EC key pairs to files
          maps/
            MapLibreHelper.kt # MapLibre initialisation, offline region download
            LocationHelper.kt # FusedLocationProvider wrapper with Flow
  docs/
    signal-ratchet.md         # THIS FILE — crypto protocol design
    blockchain.md             # what is actually feasible on mobile
    maps-realtime.md          # OSM, MapLibre, routing, GTFS-RT
    protocols.md              # WebRTC, QUIC, MQTT, Matrix — when to use what
    co-watch.md               # synchronized video playback design
    optional-deps.md          # FFmpeg, Signal, ONNX, Room — copy-paste blocks
    ai-inference.md           # on-device model pattern (WorkManager + StateFlow)
  SKELETON.md                 # opencode contract — what's pre-built, what to fill in
  .github/workflows/
    build.yml                 # push to app/** → test + build + artifact
    release.yml               # workflow_dispatch → publish GitHub Release
```

---

## 3. Capability Tiers

### Tier 1 — Core (always present, pre-built, tested)

Every app gets these. Opencode uses them, never rewrites them.

- `AppSettings` — SharedPreferences singleton. Same pattern as CodeKeyboard's `KeyboardSettings`.
- `PermissionHelper` — wraps `ActivityResultLauncher` correctly. This breaks when written ad-hoc.
- `BaseViewModel<S>` + `AppState<T>` — enforced pattern. Every screen is `Loading | Success | Error`.
- `UriHelper.copyToCache(ctx, uri)` — copy SAF content URI to processable File.
- `ShareHelper.shareFile(ctx, file, mimeType)` — correct FileProvider-aware share.
- `NotificationHelper` — channel creation boilerplate (Android 8+ requirement).
- `CrashLogger` — `Thread.setDefaultUncaughtExceptionHandler` → writes crash log to file.
- `AppScaffold` — Scaffold + TopAppBar + LoadingOverlay composable.
- `BaseWorker` — `CoroutineWorker` with progress StateFlow for long operations (conversion, inference).

### Tier 2 — Network (in skeleton, not wired by default)

Drawn from wristturn-app patterns, ported to pure Kotlin.

- `NsdHelper` — `NsdManager` advertise + discover. Pure Android API, no deps. The register/unregister
  lifecycle has subtle threading issues; pre-build it once correctly.
- `TcpServer` / `TcpClient` — coroutine-based with length-prefix framing (same concept as
  `frameMessage()` in wristturn-app's `AndroidTVRemoteClient`).
- `NetworkService` — foreground service base class. Same structure as wristturn-app's
  `BLEForegroundService` minus BLE specifics. Subclass for any long-running network operation.
- `SecureChannel` — RSA keygen, self-signed X.509 via BouncyCastle (bundled with Android via
  reflection, exactly as wristturn-app does it), persistent identity to files, TOFU trust model.
- `WebSocketTransport` — OkHttp WebSocket wrapped in `ITransport` + `Flow<ByteArray>`.
- `ITransport` — the `IDeviceAdapter` pattern from wristturn-app, generalised:
  ```kotlin
  interface ITransport {
      suspend fun connect()
      suspend fun disconnect()
      suspend fun send(frame: ByteArray)
      val incoming: Flow<ByteArray>
      val isConnected: StateFlow<Boolean>
  }
  ```

### Tier 3 — BLE (in skeleton, not wired by default)

`BLEHelper` — scan → connect → `discoverServices` → `getCharacteristic` → CCCD write (enable
notifications) → `onCharacteristicChanged`. Directly from wristturn-app's `BLEForegroundService`,
stripped of wristturn-specific UUIDs. The CCCD write is the step everyone forgets.

### Tier 4 — Maps (in skeleton, not wired by default)

`MapLibreHelper` — MapLibre Android initialisation, offline region manager, style loading (including
a pre-built greyscale/vector style for low-power mode).

`LocationHelper` — `FusedLocationProviderClient` wrapped as `Flow<Location>` with permission check.

### Tier 5 — Heavy optional deps (documented only, NOT in build.gradle)

See `docs/optional-deps.md`. These have real costs and are app-specific.

- **FFmpeg** (`ffmpeg-kit-android-min`): ~15 MB per ABI. Add only when needed.
- **Signal Protocol** (`libsignal-client`): adds Signal's full X3DH + Double Ratchet.
- **Noise Protocol** (`noise-java`): simpler alternative to Signal for synchronous P2P.
- **ONNX Runtime** (`onnxruntime-android`): on-device AI inference.
- **LiteRT/TFLite** (`tensorflow-lite`): smaller, faster inference for vision tasks.
- **llama.cpp** (JNI): on-device LLM inference (Phi-3-mini, Gemma-2B).
- **Room**: SQLite ORM, when SharedPreferences isn't enough.
- **MapLibre Android SDK**: vector maps, offline-capable.
- **web3j**: Ethereum wallet + contract interaction.

---

## 4. Crypto — Signal Protocol & Double Ratchet

### TODO: Discussion still in progress

#### What forward secrecy actually means

Compromise of a long-term private key does not reveal past session messages. Each message is
encrypted with a key that is derived from an ephemeral key exchange and then discarded. The attacker
who steals your key today cannot decrypt messages from last week.

#### Double Ratchet algorithm (what Signal uses)

Two interleaved ratchets:

**Symmetric-key ratchet (KDF chain)**
A chain key `CK` is fed through a KDF to produce a message key `MK` and the next chain key. The
message key encrypts one message, then is discarded. Compromise of `CK[n]` reveals `MK[n]` and
forward but not `MK[0..n-1]` (forward secrecy).

```
CK[0] → KDF → MK[0], CK[1]
CK[1] → KDF → MK[1], CK[2]
...
```

**Diffie-Hellman ratchet (asymmetric)**
On every message exchange, both parties generate a new DH key pair. The DH output seeds a new KDF
chain, producing a new `CK`. This provides **break-in recovery**: even if an attacker compromises
the current chain, the next DH ratchet step produces a new chain they cannot derive.

The combination: FS (past messages safe if current state compromised) + break-in recovery (future
messages safe after compromise is detected and ratchet advances).

#### X3DH — key agreement that bootstraps the Double Ratchet

Allows Alice to send to Bob when Bob is **offline** (async messaging):

- Bob publishes to a server: identity key `IK_B`, signed prekey `SPK_B`, one-time prekeys `OPK_B`
- Alice computes a shared secret from: `DH(IK_A, SPK_B)`, `DH(EK_A, IK_B)`, `DH(EK_A, SPK_B)`,
  `DH(EK_A, OPK_B)` — combines 3-4 DH outputs via HKDF
- Shared secret initialises the Double Ratchet

**The server requirement problem for P2P**: X3DH needs a prekey server so Bob's prekeys are
available when he's offline. Options for P2P:
1. **Skip X3DH, use synchronous DH**: both online → direct ephemeral key exchange. Simpler, loses
   async capability.
2. **Minimal prekey server**: a single HTTPS endpoint (could be a GitHub Gist, a static file, a
   Cloudflare Worker) that holds Bob's signed prekeys. Bob rotates them periodically. Stateless.
3. **IPFS for prekeys**: Bob publishes prekeys to IPFS, Alice fetches by CID. Fully decentralised.
4. **Matrix as transport**: Matrix rooms handle key distribution natively via Olm/Megolm (which
   is Signal's Double Ratchet). Self-host a Synapse or use element.io.

#### Alternative: Noise Protocol (simpler, often sufficient)

The Noise Protocol Framework defines composable handshake patterns. For synchronous P2P:

**Noise_XX**: both parties authenticate their static keys during handshake, then communicate.
- Both sides have long-term static keys
- Ephemeral keys generated per-session → FS within a session
- No prekey server needed
- 3 messages to complete handshake

**Noise_IK**: Alice knows Bob's static key in advance (retrieved out-of-band), faster (2 messages).

For group chat: Noise establishes pairwise channels; you need a separate layer for group key
agreement (e.g., MLS — Messaging Layer Security, RFC 9420, the IETF standard for group E2E).

#### MLS (Messaging Layer Security) — group forward secrecy

RFC 9420. Designed for groups. Each epoch has a group secret; adding/removing members or advancing
the ratchet produces a new epoch with a new key. Past epochs are discarded. Library: `openmls`
(Rust, no Android port yet). Realistically: for group chat you either use Signal's sender-key
protocol (pairwise session → encrypt-once with sender key, distribute sender key to group) or wait
for MLS libraries to mature.

#### Decision to make

- **Synchronous P2P only** → Noise_XX (simpler, no server)
- **Async P2P + forward secrecy** → Signal (X3DH + Double Ratchet), minimal prekey server
- **Federated group chat** → Matrix (Olm = Double Ratchet, Megolm = sender-key for groups)
- **All three** → Wire protocol (open source, uses Proteus which is Signal-derived)

**TODO: Decide which profile(s) the skeleton should pre-build**

---

## 5. Blockchain — What Is Actually Feasible on Mobile

Not trying to run a node. Feasible = read/write chain state, sign transactions, verify proofs.

### What works well

**Wallet (key management)**
Generate BIP39 mnemonic → BIP32 HD derivation → Ethereum or Bitcoin addresses. Sign transactions
locally. Library: `web3j` (Ethereum), `bitcoinj` (Bitcoin). Zero network required for key ops.

**Read chain state via RPC**
`eth_call`, `eth_getBalance`, `eth_getLogs` via JSON-RPC to a public endpoint (Infura, Alchemy,
public RPCs). Polling or WebSocket subscription for events. Useful for: check if address holds a
token, read contract state, verify payment received.

**IPFS / content-addressed storage**
Store app data (profiles, messages, files) on IPFS. Addressed by content hash (CID), not location.
Library: `java-ipfs-http-client` — talks to an IPFS node (local Kubo instance or a pinning service
like Pinata/Web3.Storage). Useful for: decentralised profile pictures, file sharing in P2P apps,
immutable published content.

**Decentralised Identity (DID)**
`did:ethr` or `did:key` — a public key IS your identity. No username, no server. Verify someone's
identity by checking their DID document on-chain or via a well-known resolution endpoint. Combines
well with the Signal IdentityStore — the Signal identity key IS the DID key.

**NFT / token gating**
Check `balanceOf(address)` on an ERC-20/ERC-721 contract. Simple `eth_call`. Could gate app
features (e.g., group chat room access requires holding a specific token). 

**Payment channels / state channels**
Pre-fund a channel on-chain, exchange signed state updates off-chain, settle on-chain later.
Bitcoin Lightning Network is the most mature. Ethereum has Connext, Raiden. Complex to implement
but enables microtransactions without per-transaction gas fees. Realistic use: pay-per-use features
in a P2P app.

**Signing messages / proofs**
Sign arbitrary data with an Ethereum private key (`eth_sign`). Prove identity or intent without an
on-chain transaction. Verify signature in Kotlin with `web3j`. Useful for: "prove you own this
address", challenges/responses in auth flows.

### What is NOT feasible

- Running a full node (100GB+ storage)
- Real-time on-chain events (12s block time on Ethereum mainnet, finality is longer)
- Mining
- Complex ZK proof generation on-device (verification is fine, generation is too slow)

### Most useful patterns for this app factory

1. **DID as identity** in P2P chat — no username server, verify via public key
2. **IPFS for content** — share files P2P without a server, persist group chat media
3. **Read-only RPC** — check token balances, verify payments
4. **Signed messages** — lightweight auth without a backend

**TODO: Which of these to include in the skeleton's crypto/ or a new web3/ package?**

---

## 6. Maps, Travel Apps, Real-time Location

### Map rendering

**MapLibre Android** — vector tile renderer, open source (MapLibre GL JS fork), MIT license.
Renders Mapbox-compatible style JSON. Supports offline regions (download tiles for a bounding box).

**Tile sources**
- OpenStreetMap raster tiles: free, rate-limited, raster (not ideal for custom styles)
- OpenMapTiles (vector): self-hostable, or use free tiers (Maptiler free tier = 100k req/month)
- Protomaps: single `.pmtiles` file containing entire world vector data. Download once, read locally.
  **Best option for fully offline maps.** File size: ~100MB (planet), downloadable by region.

**Styles**
- Default OSM style: cluttered, not designed for mobile
- Good defaults: MapLibre's `demotiles`, Maptiler's `Basic` style
- **Greyscale/low-power style**: a simple style JSON that turns off satellite imagery, uses grey for
  land, black/white for roads and labels. Vector tiles → renders at any zoom without pixelation.
  Pre-build this in the skeleton as `assets/styles/greyscale.json`.

**Custom style for low-power mode**
```json
{ "layers": [
  { "id": "background", "type": "background", "paint": { "background-color": "#e8e8e8" }},
  { "id": "roads", "type": "line", "source-layer": "transportation",
    "paint": { "line-color": "#888", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 1, 16, 4] }},
  { "id": "labels", "type": "symbol", "source-layer": "place",
    "layout": { "text-field": "{name}" }, "paint": { "text-color": "#111" }}
]}
```

### Routing

- **OSRM** (Open Source Routing Machine): HTTP API, self-hostable. Fastest for car routing.
- **Valhalla**: multi-modal (car, bike, foot, transit), self-hostable. Slightly slower but more
  capable. Used by OpenTripPlanner.
- **GraphHopper**: Java, embeddable in Android. Can run on-device for a region. ~50MB per region.
- **Public APIs**: Project OSRM demo server (rate-limited), Geoapify (generous free tier).

For travel apps: use public APIs first, self-host if needed.

### Travel data

**Flights**
- Amadeus API (free sandbox: 2000 req/month, flight search, pricing)
- Duffel API (developer-friendly, real airline content)
- Skyscanner: no public API (scraped → fragile, against ToS)
- OpenSky Network: real-time ADS-B flight positions, free

**Transit**
- GTFS: standard format for transit schedules. Every major transit agency publishes this.
- GTFS-RT: real-time updates (vehicle positions, delays, alerts) via Protocol Buffers.
- OpenTripPlanner: open source multimodal router, consumes GTFS + OSM.
- TransitLand: aggregated GTFS feeds for global transit.

**Points of Interest**
- Overpass API: query OSM data (`amenity=restaurant within 500m of lat/lon`). Powerful.
- Nominatim: OSM geocoding (address → coordinates, coordinates → address). Self-hostable.

**Weather**
- Open-Meteo: completely free, no API key, hourly forecasts globally. Best option.
- OpenWeatherMap: free tier 1000 req/day.

### Group trip tracker (real-time location sharing)

**Transport options** (see Section 7 for detailed protocol comparison):
- **MQTT** over WebSocket: purpose-built for real-time sensor data, tiny protocol, QoS levels.
  Best fit for location sharing. Broker: HiveMQ Cloud free tier, or self-hosted Mosquitto.
- **WebSocket** to a simple relay: each user connects, server broadcasts positions to room members.
  Simplest to implement, needs a server.
- **WebRTC DataChannel**: fully P2P, no relay needed once connected. NAT traversal via STUN.
  Harder to set up but no server cost.

**Message format** (compact, binary):
```
userId (4 bytes) | lat (8 bytes double) | lng (8 bytes double) | accuracy (4 bytes float) | timestamp (8 bytes long)
= 32 bytes per update
```
At 1 update/second per user, 10 users = 320 bytes/second. Tiny.

**Map rendering**: MapLibre with `GeoJsonSource` updated on each position update. Each user is a
feature in a `FeatureCollection`. Smooth interpolation between positions via `ValueAnimator`.

**Privacy**: location data should be E2E encrypted. Group shared key (derived from group DH or
pre-shared) encrypts each position update before sending. Server/broker sees only ciphertext.

### Real-time maps app in low-power greyscale mode

Architecture:
- MapLibre with greyscale style JSON
- `Protomaps .pmtiles` for offline vector tiles (download once per region)
- Location tracking via `FusedLocationProviderClient` as `Flow<Location>`
- Optional: contour lines from SRTM elevation data (available as OSM layer)

Greyscale mode is particularly good for e-paper-like scenarios (hiking, long battery life) and
for data density (greyscale reduces visual noise, lets POI labels stand out).

---

## 7. Protocols Beyond TCP

### WebSocket
TCP with HTTP upgrade, message framing, works through firewalls and corporate proxies. Text or
binary frames. OkHttp has a mature WebSocket client.

**Use when**: web compatibility matters, server is HTTP-based, firewall traversal needed.

### WebRTC
Peer-to-peer with NAT traversal via STUN/TURN. Three sub-protocols:
- `RTCDataChannel`: reliable or unreliable P2P data (like TCP or UDP semantics, your choice)
- `MediaStream`: audio/video tracks
- All traffic is DTLS-SRTP encrypted by default

Requires a **signaling server** (any WebSocket server to exchange SDP/ICE candidates — ~50 lines
of server code). STUN is free (Google's `stun.l.google.com:19302`). TURN (relay for symmetric NAT)
needs a server but only used as fallback.

Library: `io.getstream:webrtc-android` (~20MB AAR, easier than raw `libwebrtc`).

**Use when**: P2P video/audio, co-watching, collaborative editing, games. NAT traversal without
a relay server.

### QUIC
UDP-based transport protocol. TLS 1.3 built in. Multiplexed streams without head-of-line blocking.
0-RTT reconnection (important for mobile where network changes constantly: WiFi → LTE → WiFi).
HTTP/3 uses QUIC.

Android has **Cronet** (Chromium's network stack) which supports QUIC. Also `OkHttp` 5 will support
QUIC via `conscrypt`.

**Use when**: file transfers over mobile, high-latency connections, many parallel streams.
Not yet the default for custom protocols but growing.

### MQTT
Publish-subscribe. Broker-based. 2-byte fixed header. Three QoS levels:
- QoS 0: fire-and-forget (UDP-like, fastest)
- QoS 1: at-least-once delivery
- QoS 2: exactly-once (slowest, rarely needed)

Topic hierarchy: `trips/abc123/users/alice/location`, subscribe to `trips/abc123/#` to get all
users' positions. Broker: HiveMQ Cloud (free 100 connections), Eclipse Mosquitto (self-hosted, 1MB).

Library: `org.eclipse.paho:org.eclipse.paho.client.mqttv3`

**Use when**: location sharing, IoT-style sensor data, group state sync, anything pub-sub.

### Matrix Protocol
Federated, room-based, E2E encrypted (Olm = Double Ratchet per-user, Megolm = sender-key for rooms).
Can self-host a homeserver (Synapse, Conduit). Rooms are permanent and federated across servers.
SDK: `matrix-android-sdk2`.

**Use when**: group chat with federation, persistent rooms, decentralised community. Matrix solves
the server discovery problem for P2P chat by federating homeservers rather than going fully P2P.

### Protocol selection matrix

| Use case | Protocol |
|---|---|
| Location sharing, group state | MQTT |
| P2P chat (online, no server) | WebRTC DataChannel + Noise_XX |
| P2P chat (async, offline) | Matrix (Olm/Megolm) or Signal |
| Video call | WebRTC MediaStream |
| Co-watch sync events | WebSocket or WebRTC DataChannel |
| File transfer, mobile resilience | QUIC (Cronet) |
| REST API calls | HTTP/2 (OkHttp default) |
| Local network discovery | mDNS (NsdManager) |
| BLE peripherals | GATT (BLEHelper) |

---

## 8. Co-watching Video (Synchronized Playback)

Watch the same video with multiple users, chat on the side, stay in sync.

### Components

**Video player**: `androidx.media3:media3-exoplayer` (modern ExoPlayer). Supports HLS, DASH, MP4,
local files, HTTP streams.

**Sync channel**: WebSocket (if server exists) or WebRTC DataChannel (fully P2P).

**Chat**: same DataChannel or a separate reliable DataChannel.

### Sync protocol

Leader election: simplest = room creator is leader. Leader broadcasts:

```json
{ "type": "sync", "position": 142.3, "playing": true, "at": 1721234567890 }
```

Followers receive and apply:
```
targetPosition = sync.position + (localNow - sync.at) / 1000.0 * playbackRate
seek if |currentPosition - targetPosition| > DRIFT_THRESHOLD (0.5s)
```

This compensates for network latency. `sync.at` is the leader's wall clock when the event was
sent. Followers compute how far playback should have advanced since then.

**Clock sync**: wall clocks differ between devices. Run a brief NTP-style exchange at session start
(4 round-trips, compute offset and delay). Achievable accuracy: ±20ms on LAN, ±100ms over internet.

### Content sources

- **HLS/DASH stream**: everyone pulls from the same URL. Buffering is independent but position is
  synced. Best option for internet streams.
- **Local file**: all users must have the same file. Transfer via WebRTC DataChannel or IPFS CID.
- **YouTube/other**: DRM-protected content cannot be controlled via ExoPlayer. Use `WebView` with
  JS injection to control playback (fragile, works for YouTube embeds).

### Drift handling

Users on slow connections buffer differently. Options:
- Pause all: when any user buffers, leader pauses and waits. Simple, annoying.
- Speed adjustment: ExoPlayer supports `setPlaybackSpeed(1.02f)` to slowly catch up. Imperceptible.
- Tolerance window: only resync when drift > 2s. Ignore small differences.

### Architecture

```
P2PSession (WebRTC)
  ├── DataChannel "sync" (reliable, ordered) — play/pause/seek events
  ├── DataChannel "chat" (reliable, ordered) — chat messages
  └── optional: MediaStream (if screen sharing rather than content sync)

SyncController
  ├── ExoPlayer instance
  ├── ClockSync (NTP-style offset computation)
  └── DriftMonitor (periodic position comparison, emit resync when needed)

ChatOverlay
  └── LazyColumn of messages, input field
```

---

## 9. Android Share Target

Two directions:

### Receiving (appear in share sheet)

App appears in Android's "Share to" dialog when another app shares files/text.

Manifest:
```xml
<activity android:name=".MainActivity">
  <intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="image/*" />
  </intent-filter>
  <intent-filter>
    <action android:name="android.intent.action.SEND_MULTIPLE" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="image/*" />
  </intent-filter>
  <!-- Add more MIME types as needed: application/pdf, video/*, */* -->
</activity>
```

Skeleton provides `IntentHelper.kt` — reads `ACTION_SEND`/`ACTION_SEND_MULTIPLE` from intent, extracts
URIs, hands them to `UriHelper.copyToCache()`. Opencode uncomments the MIME types it needs.

### Direct Share (shortcuts in share sheet)

Show specific contacts/rooms as top-level targets in the share sheet.

`ShortcutManager.setDynamicShortcuts()` — Android 7.1+. Each shortcut is a `ShortcutInfo` with
a `category` matching `android.intent.category.BROWSABLE`. The system exposes these as Direct Share
targets. Update shortcuts when the user's contacts/rooms change.

### Sending (put content on the share sheet)

Already covered by `ShareHelper.shareFile()` in Tier 1. Uses `FileProvider` + `ShareCompat`.

---

## 10. Open Questions / Decisions

1. **Signal vs Noise vs Matrix** — which protocol profile to pre-build in `crypto/`?
   - Noise_XX: simplest, online-only P2P. No prekey server.
   - Signal: async P2P, needs minimal prekey server. Where does that server live?
   - Matrix: self-hosted Synapse. More infrastructure but federation is solved.

2. **Blockchain scope** — which tier?
   - Tier 2 (in skeleton network/): DID + IPFS client
   - Tier 5 (docs only): `web3j` for Ethereum, `bitcoinj` for Bitcoin
   - Probably Tier 5 — too app-specific for skeleton

3. **MapLibre in skeleton** — include `maps/` Tier 4 by default, or docs-only?
   - MapLibre AAR is ~10MB. Not every app needs it.
   - Probably Tier 5 with a ready-made `libs.versions.toml` entry and style JSON in `assets/`.

4. **Which prekey server for async Signal?**
   - Cloudflare Worker (free, stateless, ~20 lines of JS)
   - IPFS (no server, but eventual consistency — prekey may not be latest)
   - Decide per-app. Skeleton documents the interface, not the implementation.

5. **WebRTC library**: `io.getstream:webrtc-android` (~20MB) or roll with raw DTLS over UDP?
   - Stream's library is the pragmatic choice. Document as optional dep.

6. **MQTT broker for location sharing**: HiveMQ Cloud free tier vs self-hosted Mosquitto?
   - HiveMQ for quick starts. Mosquitto for production. Document both.

7. **Protomaps .pmtiles for offline**: in skeleton or per-app download?
   - Per-app — file size is region-dependent. Skeleton documents the download pattern.

---

## 11. CI / Release

### build.yml triggers
- `push` to `skeleton` → run tests only (no APK artifact — skeleton is not an installable app)
- `push` to `app/**` → test + `assembleRelease` + upload artifact

### release.yml (manual only)
```yaml
on:
  workflow_dispatch:
    inputs:
      branch:
        description: 'Branch to release (e.g. app/img-to-pdf)'
        required: true
      tag:
        description: 'Release tag (e.g. v1.0.0)'
        required: true
        default: 'v1.0.0'
```
Downloads the latest successful artifact from `branch`'s last build run, publishes GitHub Release.
No rebuild — releases the already-tested artifact.

### ABI targets
`arm64-v8a,armeabi-v7a` — covers ~99% of physical Android devices. Excludes `x86`/`x86_64` (emulator
only). For apps with FFmpeg or AI models: consider `arm64-v8a` only to halve APK size.

---

## 12. SKELETON.md — opencode contract

```markdown
## Pre-built — use these, do not regenerate
- AppSettings.getString/setString/getBoolean/setBoolean/getInt/setInt
- PermissionHelper(activity).request(permission, onGranted, onDenied)
- PermissionHelper(activity).requestMultiple(permissions, onAllGranted, onDenied)
- UriHelper.copyToCache(context, uri): File
- ShareHelper.shareFile(context, file, mimeType)
- BaseViewModel<S> — extend this, call emit(AppState.Success(data))
- AppTheme { } — wrap top-level Composable
- AppScaffold(title, actions) { content } — TopAppBar + LoadingOverlay
- NotificationHelper.createChannels(context) — already called from App.kt
- CrashLogger.init(context) — already called from App.kt
- BaseWorker — extend CoroutineWorker, report progress via StateFlow
- network/NsdHelper — advertise(serviceType, port) / discover(serviceType, onFound)
- network/TcpServer / TcpClient — framed ByteArray Flow
- network/SecureChannel — TOFU TLS, persistent RSA identity
- network/WebSocketTransport — OkHttp WebSocket as ITransport
- network/NetworkService — foreground service base, extend for long-running network ops
- ble/BLEHelper — scan/connect/notify lifecycle
- crypto/CryptoHelper — AES-256-GCM encrypt/decrypt, HKDF
- crypto/IdentityStore — persist/load key pairs

## You fill in
1. bash scripts/rename-package.sh com.forge.APPNAME AppName
2. res/values/strings.xml: app_name
3. AndroidManifest.xml: uncomment needed permissions and intent-filters
4. MainScreen.kt: your UI Composable
5. MainViewModel.kt: extend BaseViewModel<YourState>, implement business logic
6. libs.versions.toml + build.gradle: add app-specific deps (see docs/optional-deps.md)

## Do not touch
- All files in base/, permission/, file/, notification/, crash/, network/, ble/, crypto/
- FileProvider configuration (authority = com.forge.APPNAME.fileprovider)
- build.yml / release.yml
```
