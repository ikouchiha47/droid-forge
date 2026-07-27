# Optional Dependencies — Copy-Paste Blocks

Add these to `libs.versions.toml` and `app/build.gradle` as needed. Every block is self-contained.

---

## WebRTC — P2P video/data

`io.getstream:webrtc-android` (~20MB AAR). Required for `WebRtcTransport`.

```toml
# libs.versions.toml
[versions]
webrtc = "1.1.2"
[libraries]
webrtc = { group = "io.getstream", name = "stream-webrtc-android", version.ref = "webrtc" }
```

```groovy
// app/build.gradle
implementation libs.webrtc
```

```pro
# proguard-rules.pro
-keep class org.webrtc.** { *; }
-keep class io.getstream.webrtc.** { *; }
```

---

## FFmpeg — video/audio processing

`ffmpeg-kit-android-min` (~15MB per ABI). Enables transcoding, thumbnail extraction, stream mixing.

```toml
[versions]
ffmpeg = "6.0-2"
[libraries]
ffmpeg = { group = "com.arthenica", name = "ffmpeg-kit-android-min", version.ref = "ffmpeg" }
```

```groovy
implementation libs.ffmpeg
```

Usage:
```kotlin
FFmpegKit.executeAsync("-i input.mp4 -vf scale=320:-1 thumb.jpg") { session ->
    if (ReturnCode.isSuccess(session.returnCode)) { /* done */ }
}
```

---

## TensorFlow Lite — on-device vision inference

~1MB + model file. Works well for image classification, object detection.

```toml
[versions]
tflite = "2.14.0"
[libraries]
tflite = { group = "org.tensorflow", name = "tensorflow-lite", version.ref = "tflite" }
tflite-support = { group = "org.tensorflow", name = "tensorflow-lite-support", version.ref = "tflite" }
tflite-gpu = { group = "org.tensorflow", name = "tensorflow-lite-gpu", version.ref = "tflite" }
```

```groovy
implementation libs.tflite
implementation libs.tflite.support
implementation libs.tflite.gpu  // optional, GPU delegate
```

See `docs/ai-inference.md` for the inference pattern.

---

## ONNX Runtime — general on-device inference

~5MB. Works with models exported from PyTorch/HuggingFace.

```toml
[versions]
onnx = "1.18.0"
[libraries]
onnx = { group = "com.microsoft.onnxruntime", name = "onnxruntime-android", version.ref = "onnx" }
```

```groovy
implementation libs.onnx
```

---

## Room — SQLite ORM

~500KB. Use for any persistent structured data.

```toml
[versions]
room = "2.6.1"
[libraries]
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

```groovy
implementation libs.room.runtime
implementation libs.room.ktx
kapt libs.room.compiler  // add 'kotlin-kapt' plugin at top
```

Rules: always `withContext(Dispatchers.IO)` for `suspend` DAO calls, or use DAO `Flow` directly.
Never call DAO on the main thread.

```pro
# proguard-rules.pro
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
```

### WAL mode + tuning (always add this)

Based on BigBinary's SQLite tuning recommendations. Apply in your `RoomDatabase` builder:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA journal_mode = WAL")
            // fsync only at WAL checkpoint, not every write — safe on crash
            db.execSQL("PRAGMA synchronous = NORMAL")
            // 128MB memory-mapped I/O — reads bypass kernel copy
            db.execSQL("PRAGMA mmap_size = 134217728")
            // cap WAL file at 64MB before checkpointing
            db.execSQL("PRAGMA journal_size_limit = 67108864")
            // 20MB page cache (negative = KB)
            db.execSQL("PRAGMA cache_size = -20000")
            // wait up to 5s on a locked db instead of failing immediately
            db.execSQL("PRAGMA busy_timeout = 5000")
            // temp tables in memory, not disk
            db.execSQL("PRAGMA temp_store = MEMORY")
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    })
    .build()
```

Put this in a `db/DatabaseModule.kt` — not inline in Application or ViewModel.

---

## MapLibre — vector maps

~10MB. Required to render maps. Greyscale style is already in `assets/styles/greyscale.json`.

```toml
[versions]
maplibre = "11.5.2"
[libraries]
maplibre = { group = "org.maplibre.gl", name = "android-sdk", version.ref = "maplibre" }
```

```groovy
implementation libs.maplibre
```

See `docs/maps.md` for usage patterns and tile sources.

---

## Noise — Noise protocol library

~50KB. If you want to use a well-tested Noise implementation instead of the skeleton's `NoiseCipher`.

```toml
[versions]
noise = "2.0.0"
[libraries]
noise = { group = "com.southernstorm.noise", name = "noise-java", version.ref = "noise" }
```

Note: `noise-java` is not on Maven Central. Add the JAR manually or use JitPack:
```groovy
// in settings.gradle repositories:
maven { url 'https://jitpack.io' }
```

---

## libsignal — Signal X3DH + Double Ratchet

~5MB. Use when you need offline messaging with per-message forward secrecy.

```toml
[versions]
signal = "0.57.1"
[libraries]
signal = { group = "org.signal", name = "libsignal-android", version.ref = "signal" }
```

```groovy
implementation libs.signal
```

```pro
-keep class org.signal.libsignal.** { *; }
-keep class org.whispersystems.** { *; }
```

See `docs/crypto.md` for X3DH/Double Ratchet design and Nostr-based prekey distribution.

---

## web3j — Ethereum wallet and RPC

~3MB. Wallet keygen (BIP39), signing, and reading chain state via public RPC.

```toml
[versions]
web3j = "4.12.2"
[libraries]
web3j = { group = "org.web3j", name = "core", version.ref = "web3j" }
```

```groovy
implementation libs.web3j
```

```pro
-keep class org.web3j.** { *; }
-dontwarn org.web3j.**
```

See `docs/blockchain.md` for feasibility analysis.

---

## MQTT — pub-sub messaging

~200KB. Best for group location sharing or any pub-sub pattern over internet.

```toml
[versions]
mqtt = "1.2.5"
[libraries]
mqtt = { group = "org.eclipse.paho", name = "org.eclipse.paho.client.mqttv3", version.ref = "mqtt" }
mqtt-android = { group = "org.eclipse.paho", name = "org.eclipse.paho.android.service", version.ref = "mqtt" }
```

```groovy
implementation libs.mqtt
implementation libs.mqtt.android
```

Public MQTT brokers: `tcp://broker.emqx.io:1883`, `ssl://broker.emqx.io:8883`.

Pattern:
```kotlin
val client = MqttAndroidClient(context, "ssl://broker.emqx.io:8883", clientId)
client.connect(options).waitForCompletion()
client.subscribe("trips/$roomId/locations/#", 1)
client.setCallback(object : MqttCallbackExtended {
    override fun messageArrived(topic: String, message: MqttMessage) {
        val frame = cipher.decrypt(message.payload)
        process(frame)
    }
    // ...
})
```

---

## ExoPlayer / Media3 — video playback

Already on Google's Maven, no extra repository needed.

```toml
[versions]
media3 = "1.3.1"
[libraries]
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
```

```groovy
implementation libs.media3.exoplayer
implementation libs.media3.ui
implementation libs.media3.session  // for co-watch sync
```

---

## QUIC / Cronet — file transfer over mobile

Useful for file transfer where connection migration (WiFi → LTE) matters. Uses Android's built-in
Cronet engine.

```toml
[versions]
cronet = "113.5672.61"
[libraries]
cronet = { group = "org.chromium.net", name = "cronet-embedded", version.ref = "cronet" }
```

```groovy
implementation libs.cronet
```

---

## java-ipfs-http-client — IPFS content

```toml
[versions]
ipfs = "1.4.4"
[libraries]
ipfs = { group = "io.ipfs", name = "java-ipfs-http-client", version.ref = "ipfs" }
```

```groovy
implementation libs.ipfs
```

See `docs/blockchain.md` for IPFS + DID usage patterns.
