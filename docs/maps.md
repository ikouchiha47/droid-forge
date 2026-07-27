# Maps and Location — Reference

---

## Map rendering

### MapLibre Android

Vector tile rendering. Open source. Offline capable. Not in skeleton `build.gradle` — add when needed.

```toml
# libs.versions.toml
[versions]
maplibre = "11.5.2"

[libraries]
maplibre = { group = "org.maplibre.gl", name = "android-sdk", version.ref = "maplibre" }
```

```groovy
// app/build.gradle
implementation libs.maplibre
```

Basic usage:
```kotlin
val mapView = MapView(context)
mapView.getMapAsync { map ->
    map.setStyle(Style.Builder().fromAsset("styles/greyscale.json")) { style ->
        // map is ready
    }
}
```

The greyscale style (`assets/styles/greyscale.json`) is included in the skeleton. It uses
OpenMapTiles schema tiles. For online tiles, point to a public tile server. For offline, use
Protomaps.

### Offline tiles — Protomaps

`.pmtiles` is a single-file archive of vector tiles. Download once, use offline.

```kotlin
// Point MapLibre at a local pmtiles file:
val tilesUrl = "pmtiles://${context.filesDir}/region.pmtiles"
map.setStyle(Style.Builder()
    .fromAsset("styles/greyscale.json")
    .withSource(VectorSource("tiles", tilesUrl))
)
```

Download `.pmtiles` files from: https://protomaps.com/downloads (city/country extracts)

---

## Data sources

### Geocoding

**Nominatim** (OpenStreetMap):
```kotlin
// GET https://nominatim.openstreetmap.org/search?q={query}&format=json&limit=5
// Response: [{"lat":"51.5074","lon":"-0.1278","display_name":"London, ..."}]
```

Rate limit: 1 req/sec. Add `User-Agent` header with your app name.

### Routing

**OSRM** (OpenStreetMap Routing Machine):
```kotlin
// GET https://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=full&geometries=geojson
```

Rate-limited on public instance. Self-hostable with Docker for production use.

Alternative: **Valhalla** (also self-hostable, more routing profiles including cycling, walking).

### POI search

**Overpass API** (full OSM query language):
```kotlin
// POST https://overpass-api.de/api/interpreter
// Body: [out:json];node["amenity"="cafe"](around:500,{lat},{lon});out body;
```

Returns OSM nodes/ways matching the query. Powerful but rate-limited.

---

## Live data sources

### Flight positions

**OpenSky Network** — free ADS-B:
```kotlin
// GET https://opensky-network.org/api/states/all?lamin={lat1}&lomin={lon1}&lamax={lat2}&lomax={lon2}
// No API key required. Returns aircraft states (icao24, callsign, lat, lon, altitude, velocity)
```

Rate limit: 10 requests/10 sec unauthenticated, 100/10 sec with free account.

### Flight search / pricing

**Amadeus API** (free sandbox: 2000 req/month):
```
POST https://test.api.amadeus.com/v1/security/oauth2/token  // get bearer token
GET  https://test.api.amadeus.com/v2/shopping/flight-offers?originLocationCode=LHR&...
```

### Transit schedules — GTFS

Every major transit agency publishes GTFS feeds (static schedules). Format: ZIP of CSV files.

```kotlin
// Download feed, unzip, parse stops.txt / stop_times.txt / trips.txt
// No API key. Direct download from agency website.
```

Example feeds: NYC MTA, London TfL, BART, Berlin BVG — all free.

### Transit real-time — GTFS-RT

Protobuf format. Vehicle positions, trip updates (delays), service alerts.

```groovy
// Add to build.gradle:
implementation "com.google.protobuf:protobuf-javalite:3.25.3"
```

```kotlin
val feed = GtfsRealtime.FeedMessage.parseFrom(responseBody.bytes())
feed.entityList.forEach { entity ->
    entity.vehicle.position.latitude  // current lat
    entity.vehicle.position.longitude // current lon
}
```

### Weather

**Open-Meteo** — completely free, no API key:
```kotlin
// GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&hourly=temperature_2m,precipitation
```

---

## Group location sharing

### Wire format (32 bytes/update, E2E encrypted)

```
userId    (4 bytes, uint32)
latitude  (8 bytes, double)
longitude (8 bytes, double)
accuracy  (4 bytes, float, metres)
timestamp (8 bytes, int64, epoch ms)
```

Pack with `java.nio.ByteBuffer`:
```kotlin
val buf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
buf.putInt(userId)
buf.putDouble(latitude)
buf.putDouble(longitude)
buf.putFloat(accuracy)
buf.putLong(timestamp)
val bytes = buf.array()
// Encrypt with AesGcmCipher before sending
```

### Transport choice

| Scenario | Transport |
|---|---|
| LAN (same WiFi) | `NsdDiscovery` + `TcpTransport` + `LengthPrefixFraming` |
| Internet, small group (<10) | `NostrDiscovery` + `WebRtcTransport` mesh |
| Internet, any size | MQTT broker (see `docs/optional-deps.md`) — subscribe to `trips/{roomId}/locations/#` |

### Location update flow

```kotlin
LocationHelper(context).locations
    .sample(5000)  // at most once per 5s
    .map { loc -> buildLocationFrame(userId, loc) }
    .map { frame -> cipher.encrypt(frame) }
    .map { encrypted -> framing.encode(encrypted) }
    .collect { framed -> transport.send(framed) }
```

---

## Offline map regions

Pattern for downloading and storing map regions:

```kotlin
// 1. User selects a bounding box on the map
// 2. Build the pmtiles download URL for that bbox
// 3. Download via OkHttp with progress reporting
// 4. Save to context.filesDir/maps/{regionName}.pmtiles
// 5. Switch MapLibre style source to the local file

val outputFile = File(context.filesDir, "maps/$regionName.pmtiles")
val request = Request.Builder().url(downloadUrl).build()
okHttpClient.newCall(request).execute().use { response ->
    response.body?.byteStream()?.use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}
```

Tile file sizes: ~50MB for a city, ~500MB for a country.
