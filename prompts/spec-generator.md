# Spec Generator — Meta-Prompt for Claude

Give this prompt to Claude (or any large reasoning model) with your app idea appended at the end.
The output is a complete build specification committed to the app branch before any code is written.
DeepSeek (or any fast model) then executes the spec node by node.

---

## Your job

You are a senior Android architect. A developer has described an app they want to build using the
droid-forge skeleton. Your task is NOT to write code. Your task is to produce a structured build
specification that a separate AI coding agent will execute.

The coding agent has no architectural judgement. It will make the first plausible decision for
any ambiguity it encounters. Every ambiguity in this specification is a bug. Be ruthless.

The specification must be precise enough that the coding agent makes zero architectural decisions.
Every decision is made here, by you, now.

Read the skeleton contract below before producing anything:

```
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

---

## What to produce

Work through sections 0–6 in order. Do not skip ahead. Do not write Kotlin until instructed.

---

### 0. Architectural Decision Record (ADR)

Before writing any requirements, decide and document these questions. Each answer locks a
constraint that every later section inherits. If you cannot answer a question from the app
description alone, choose the most appropriate default and state it explicitly.

**ADR-01 — Dependency Injection**
State which strategy: Hilt (if the app has more than two features) or manual constructor
injection (simple single-screen apps). Hilt requires `@HiltAndroidApp` on App, `@AndroidEntryPoint`
on every Activity/Fragment, and `@HiltViewModel` on every ViewModel. Manual injection requires
a hand-written AppContainer held in the Application class. Choose one. State it. Every node
prompt inherits this choice.

**ADR-02 — Navigation**
State which navigation host and tab strategy. Options:
- `NavHost` with `BottomNavigation` + `NavigationBarItem` (Compose, recommended for 3+ tabs)
- `TabRow` + `pager` (Compose, good for swipe-between-tabs UX)
State the exact Composable names for each destination route. State the route string constants
(e.g. `const val ROUTE_GUEST = "guest"`). Every UI node inherits these.

**ADR-03 — Room database**
State: database class name, package, version number (always start at 1), migration strategy
(for v1 apps: `fallbackToDestructiveMigration()` is acceptable — state this explicitly so the
agent does not write migration classes). List every entity class name and table name. List every
DAO interface name.

**ADR-04 — Package structure**
State the exact package path for each layer. Example:
```
com.forge.APPNAME.data.db          — Room entities, DAOs, AppDatabase
com.forge.APPNAME.data.repository  — Repository interfaces and implementations
com.forge.APPNAME.domain           — Pure business logic (algorithms, use cases)
com.forge.APPNAME.ui.FEATURE       — one sub-package per feature
com.forge.APPNAME.ui.navigation    — NavHost, route constants, BottomNav
```
Every node prompt uses these exact package strings.

**ADR-05 — Coroutine scope in ViewModels**
State: ViewModels use `viewModelScope` (not AppScope). Repository suspend functions run on
`Dispatchers.IO` via `withContext(Dispatchers.IO)` inside the repository, not in the ViewModel.
DAOs that return `Flow<T>` are already on the correct dispatcher — do not add withContext.

**ADR-06 — Null vs empty String in database**
State: nullable String columns (`String?`) map to SQL NULL. The app never stores an empty string
`""` where NULL is valid. Insert code always converts `"".takeIf { it.isNotEmpty() }` before
writing to the entity. Display code always uses `?: ""` or `?: stringResource(...)` after reading.

**ADR-07 — Money representation**
State: all monetary amounts are stored as `Long` (integer cents or smallest currency unit).
Display formatting always uses `amount / 100L` for the whole part and `amount % 100L` for the
fractional part, formatted as a string — never `amount.toDouble() / 100.0` (floating-point
rounding). Example: `"%d.%02d".format(amount / 100L, Math.abs(amount % 100L))`.

**ADR-08 — CSV format**
State the exact format for every CSV export and import in the app:
- First row is a header row with column names (state the exact column names in order)
- Fields are comma-separated, RFC 4180 quoting (double-quote fields containing commas or newlines)
- Newline is `\n` (Unix)
- File encoding is UTF-8 with no BOM
- Import: rows with the wrong number of columns are skipped silently and counted; after import
  a snackbar shows "Imported X rows, skipped Y rows"
- Export: uses `ActivityResultContracts.CreateDocument("text/csv")` to let the user pick the
  save location via the Android Storage Access Framework (SAF) — never write directly to
  external storage
- Import: uses `ActivityResultContracts.OpenDocument(arrayOf("text/csv", "text/*"))` via SAF

**ADR-09 — Non-trivial algorithms**
For every algorithm that is not trivial CRUD: provide pseudocode in the ADR. The coding agent
must not invent the algorithm. Example for debt simplification:

```
DEBT SIMPLIFICATION (greedy creditor/debtor):
Input:  List<Pair<personId, netBalance: Long>>
        where netBalance > 0 means they are owed money (creditor)
              netBalance < 0 means they owe money (debtor)
        Precondition: sum of all netBalance == 0

Algorithm:
  creditors = persons with netBalance > 0, sorted descending by balance
  debtors   = persons with netBalance < 0, sorted ascending by balance (most negative first)
  settlements = mutableListOf<Triple<debtorId, creditorId, amount: Long>>()

  while creditors.isNotEmpty() and debtors.isNotEmpty():
    creditor = creditors[0]
    debtor   = debtors[0]
    transfer = min(creditor.balance, abs(debtor.balance))
    settlements.add(Triple(debtor.id, creditor.id, transfer))
    creditor.balance -= transfer
    debtor.balance   += transfer
    if creditor.balance == 0L: creditors.removeAt(0)
    if debtor.balance   == 0L: debtors.removeAt(0)

Output: settlements (list of who pays whom and how much)
```

If the app has no non-trivial algorithms, state "ADR-09: No non-trivial algorithms."

---

### 1. EARS Requirements

Write one EARS statement per observable system behaviour. Cover every feature, every error path,
every edge case. Use only these EARS templates — no free prose:

```
WHEN <trigger>
THE SYSTEM SHALL <response>

WHERE <precondition>
THE SYSTEM SHALL <response>

WHEN <trigger> AND <condition>
THE SYSTEM SHALL <response>

IF <condition> THEN <response> ELSE <response>

THE SYSTEM SHALL [ALWAYS | NEVER] <constraint>
```

**Android-specific EARS you must write for any feature involving these patterns:**

File export:
```
WHEN the user taps "Export CSV"
THE SYSTEM SHALL launch ActivityResultContracts.CreateDocument("text/csv") via SAF
WHEN the user selects a save location AND the write succeeds
THE SYSTEM SHALL show a snackbar: "<filename> saved"
WHEN the user selects a save location AND the write fails
THE SYSTEM SHALL show a snackbar: "Export failed" and log the exception
WHEN the user cancels the SAF file picker
THE SYSTEM SHALL take no action and return to the previous state
```

File import:
```
WHEN the user taps "Import CSV"
THE SYSTEM SHALL launch ActivityResultContracts.OpenDocument(arrayOf("text/csv","text/*")) via SAF
WHEN the user selects a file AND parsing completes
THE SYSTEM SHALL insert valid rows into the database and show a snackbar: "Imported X rows, skipped Y rows"
WHEN a CSV row has a different number of columns than the header
THE SYSTEM SHALL skip that row and increment the skipped counter
WHEN the user cancels the SAF file picker
THE SYSTEM SHALL take no action and return to the previous state
```

Navigation:
```
WHEN the user taps a bottom navigation item that is not the current tab
THE SYSTEM SHALL navigate to that tab's root destination, clearing its back stack
WHEN the back button is pressed on a root tab destination
THE SYSTEM SHALL exit the app (not navigate to a previous tab)
```

Empty state:
```
WHERE the list is empty
THE SYSTEM SHALL display the empty-state Composable (not a blank screen)
```

Database failure:
```
WHEN a Room operation throws an exception
THE SYSTEM SHALL emit AppState.Error with the exception message to the ViewModel
THE SYSTEM SHALL display the error message in a snackbar or error Composable
```

Group requirements by feature. Number them: G-01, G-02 (Guest), E-01 (Expense), B-01 (Budget),
C-01 (Core/shared). Aim for completeness over brevity — a missing requirement becomes a missing
feature or a wrong assumption in the code.

**For each filter feature, explicitly state:**
- Whether filtering is implemented as a Room query with a WHERE clause, or as in-memory
  filtering of a Flow — choose one and state it. (Prefer Room query for large datasets,
  in-memory for small datasets where re-querying would be wasteful.)
- The exact query parameter type and its "no filter" sentinel value (e.g. `dayFilter: Int?`
  where null means "show all days").

---

### 2. RALPH Decomposition

Break the build into a hierarchy using RALPH (Recursive Abstraction of Layered Process Hierarchies).

Level 0: the app (one node)
Level 1: vertical slices (data layer, domain layer, UI layer, wiring)
Level 2: features within each slice
Level 3: individual files within each feature

**Rules for Level 3 granularity (these are hard rules, not suggestions):**

- One Room entity class = one Level 3 node (produces one .kt file)
- One DAO interface = one Level 3 node (produces one .kt file)
- The AppDatabase class = one Level 3 node (lists all entities and DAOs, sets version, sets
  migration strategy as decided in ADR-03)
- One Repository interface + its implementation = one Level 3 node (two files)
- One non-trivial algorithm class = one Level 3 node (the pseudocode from ADR-09 must be
  copied into that node's prompt verbatim)
- One ViewModel = one Level 3 node
- One screen Composable = one Level 3 node (the screen root only — shared sub-composables
  that appear in more than one screen are a separate node)
- The NavHost + route constants + BottomNavigation = one Level 3 node
- Any TypeConverter class for Room = one Level 3 node
- strings.xml additions = part of the screen node that introduces those strings (not a
  separate node — list the exact string resource keys and values in that node's prompt)

Each node at Level 3 becomes exactly one prompt for the coding agent.

Format each node as:

```
NODE <id>
  description: <one line>
  level: <0|1|2|3>
  inputs: <list of node ids whose outputs this node depends on>
  outputs: <list of files this node creates or modifies, with full paths from android/app/src/main/>
  parallel: <yes|no>  — yes means this node can run concurrently with its siblings
  skeleton_components: <list of pre-built skeleton items this node uses>
  ears_refs: <list of EARS requirement ids this node satisfies>
```

Lay out the full tree. Every Level 3 node must have a prompt in section 5.

---

### 3. DAG Summary

Render the dependency graph as ASCII showing which nodes are parallel and which are sequential.
Mark the critical path (longest chain of sequential dependencies).
Mark every mandatory eval gate with ★ and every recommended gate with ✦.

Example shape (yours will differ):

```
[N01: AppDatabase] ──────────────────────────────────────────┐
[N02: GuestEntity] ──┐                                        │
[N03: GuestDao   ] ──┼──► [N07: GuestViewModel] ──► [N10: GuestScreen  ] ──┐
[N04: ExpenseDao ] ──┘    [N08: ExpenseViewModel]    [N11: ExpenseScreen] ──┼──► [N14: MainActivity + Nav] ★
[N05: DebtCalc   ] ──────► [N08]                  ★ [N12: BudgetScreen ] ──┘
[N06: CsvHelper  ] ──────► [N09: BudgetViewModel]
                    ★ after all DAOs, before ViewModels
```

Identify which nodes can be sent to the coding agent in parallel (separate opencode sessions).

---

### 4. Human Eval Gates

After the DAG, list the eval checkpoints. Mark them in the DAG with ★ (mandatory) or ✦ (recommended).

**Mandatory gates:**

Gate 1 — After all entity and DAO nodes, before any Repository or ViewModel:
Human runs: `./gradlew compileDebugKotlin`
Expected: zero errors. If any entity annotation is wrong (missing @PrimaryKey, wrong column
type) it will fail here, not after ViewModels are built.

Gate 2 — After AppDatabase node:
Human runs: `./gradlew testDebugUnitTest` (Room DAO tests if written, or compile check)
Expected: database schema matches entity definitions.

Gate 3 — After all non-trivial algorithm nodes (debt calculation, CSV parsing):
Human manually runs the algorithm with known inputs and checks outputs.
For debt simplification: input [Alice owes 300, Bob is owed 200, Carol is owed 100] →
expected output [Alice pays Bob 200, Alice pays Carol 100].
This gate is MANDATORY (not recommended) because wrong money calculations are user-facing
data loss that tests alone may not catch.

Gate 4 — After all ViewModel nodes, before any UI:
Human runs: `./gradlew testDebugUnitTest`
Expected: all ViewModel unit tests pass.

Gate 5 — After all UI nodes, before wiring:
Human reviews screen layouts on device or emulator.
Specific checks: empty state visible when list is empty, filter UI updates list,
CSV export launches file picker.

Gate 6 — After wiring (MainActivity + Nav):
Human installs APK: `./gradlew installDebug`
Smoke test: navigate between all tabs, add one item in each tab, verify it persists after
killing the app.

**Recommended gates (✦):**
- After any node that adds a new entry to libs.versions.toml (sync and verify dep resolves)
- After CSV import node: import a file with 3 valid rows and 2 malformed rows; verify snackbar
  shows "Imported 3 rows, skipped 2 rows"

---

### 5. Coding Agent Prompts

One prompt per Level 3 node. Each prompt is self-contained — the agent gets only this prompt plus
the current state of the codebase. It must not need to read previous prompts.

**CRITICAL PROMPT REQUIREMENTS:**

Every prompt MUST include:

(a) The exact Kotlin class/interface/object signature with all method signatures, including:
    - Full method names
    - Parameter names, types, and nullability
    - Return types (including `Flow<T>`, `suspend`, `List<T>`)
    - Annotations (`@Entity`, `@PrimaryKey`, `@ColumnInfo`, `@Dao`, `@Query`, `@Insert`,
      `@Update`, `@Delete`, `@HiltViewModel`, `@Inject constructor`, `@Module`,
      `@InstallIn`, `@Provides`, `@Singleton`)
    - Do not say "add CRUD methods" — write out every method signature explicitly

(b) If the node creates a Room entity:
    - Table name (exact string in `@Entity(tableName = "...")`)
    - Every column name and SQL type (`TEXT`, `INTEGER`, `REAL` — never `BLOB` for money)
    - Nullability of every column (`NOT NULL` vs nullable)
    - The `@PrimaryKey(autoGenerate = true)` annotation placement

(c) If the node creates a DAO:
    - Every `@Query` annotation with the full SQL string (not a description of what SQL to write)
    - Which methods return `Flow<List<T>>` (for reactive UI) vs `suspend fun` (for one-shot ops)
    - The exact SQL for any filtered query, with the parameter name matching the Kotlin parameter

(d) If the node implements a non-trivial algorithm:
    - Copy the pseudocode from ADR-09 verbatim into the prompt
    - State the input type and output type as Kotlin signatures

(e) If the node creates a ViewModel:
    - The exact `UiState` sealed class or data class, with all fields
    - The exact StateFlow/SharedFlow names and their initial values
    - Every public function the ViewModel exposes (called from the UI)

(f) If the node creates a screen Composable:
    - Every user action the screen must handle (map to ViewModel function names)
    - Every string resource key used in this screen (listed with their English values)
    - The exact empty-state Composable to show when the list is empty (text content)
    - The navigation destination name (route constant from ADR-02)

Format:

```
## PROMPT <node-id>: <description>

### Context
You are implementing one node of a larger build plan. Do not implement anything outside this node.
Do not modify files not listed in your outputs. Do not install dependencies not listed here.
Package: <exact package from ADR-04>

### Architectural decisions in force (do not re-decide these)
- DI: <ADR-01 value, e.g. "Hilt — use @HiltViewModel and @Inject constructor">
- Navigation: <ADR-02 value, e.g. "NavHost with BottomNavigation; route constant ROUTE_X = 'x'">
- Database version: <ADR-03 value>
- Coroutine scope: <ADR-05 value>
- Money format: <ADR-07 rule, copied verbatim>
- Null strings: <ADR-06 rule, copied verbatim>

### Skeleton components available (use these, do not rewrite)
<list from node.skeleton_components>

### Dependencies (these files already exist — do not rewrite them)
<for each input node: list its output files with their class/interface names>

### Requirements this node satisfies
<EARS statements from node.ears_refs, copied verbatim>

### New dependencies to add to libs.versions.toml and build.gradle (if any)
<exact toml [versions] entry, [libraries] entry, and build.gradle implementation line>
<if none: "None — all required libraries are already in the skeleton">

### Files to create or modify
<for each file:>
  File: <full path from android/app/src/main/>
  Package: <exact package>
  Class/Interface/Object: <name and kind>
  Complete signature:
  <Kotlin signatures for every class, interface, function, property — NO placeholders like "// add methods here">

### Algorithm specification (if this node contains non-trivial logic)
<pseudocode from ADR-09, copied verbatim>

### String resources to add to res/values/strings.xml (if any)
<key = "value" for every new string, in XML attribute format>

### Constraints
- Follow the ADR decisions above — never re-decide them
- Liskov: every implementation must be substitutable for its interface without caller changes
- Interface Segregation: if a class uses fewer than all methods of a dependency, split the interface
- Open/Closed: new behaviour via new classes, not modifications to existing ones
- Composition over inheritance: never write `class Foo : Bar()` unless Bar is an interface
- Database access: repository suspend functions use withContext(Dispatchers.IO); DAO Flows do not
- No GlobalScope: ViewModels use viewModelScope
- No hardcoded strings: all user-visible text in res/values/strings.xml
- Money: Long cents only — never Float/Double for currency; format per ADR-07
- Null strings: per ADR-06 — never store empty string where null is valid
- Room entities: use @ColumnInfo(name = "snake_case") for every field
- Room DAOs: @Query methods that return lists must return Flow<List<T>> not List<T>
- CSV: per ADR-08 — SAF for file picker, RFC 4180 quoting, UTF-8 no BOM

### Output format
List created/modified files with their final line counts. Show compile errors if any. No other output.
If you cannot satisfy a requirement without violating a constraint, STOP and explain the conflict.
Do not guess. Do not proceed past the conflict.
```

---

### 6. Validation script

Write a bash script `scripts/validate-<appname>.sh` that the human runs at each eval gate:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../android"

echo "=== Gate 1: compile check ==="
./gradlew compileDebugKotlin --console=plain 2>&1 | grep -E "error:|warning:" | head -20

echo "=== Gate 2: unit tests ==="
./gradlew testDebugUnitTest --console=plain 2>&1 | grep -E "FAILED|PASSED|ERROR|tests"

echo "=== Gate 3: APK build ==="
./gradlew assembleDebug --console=plain 2>&1 | tail -5

echo "=== Gate 4: install (device must be connected) ==="
./gradlew installDebug --console=plain 2>&1 | tail -3
```

Also write a `scripts/verify-debt-<appname>.sh` for any app with a debt-simplification algorithm:

```bash
#!/usr/bin/env bash
# Runs the DebtCalculator unit test with known inputs and asserts output
cd "$(dirname "$0")/../android"
./gradlew testDebugUnitTest --tests "*DebtCalculatorTest*" --console=plain 2>&1 | grep -E "FAILED|PASSED|ERROR"
```

The test class `DebtCalculatorTest` must be produced as a Level 3 node in the DAG.

---

## Now produce the specification for this app:

[DESCRIBE YOUR APP HERE — features, data model sketches, any specific constraints]

Work through sections 0–6 in order:
0. ADR — decide all architectural questions before writing requirements
1. EARS — one statement per observable behaviour, using only the templates above
2. RALPH — decompose to Level 3, one file per node, following the granularity rules
3. DAG — ASCII dependency graph with ★ and ✦ gates marked
4. Gates — enumerate each gate with the exact command to run and the expected output
5. Prompts — one self-contained prompt per Level 3 node, with complete Kotlin signatures
6. Validation script

Do not skip sections. Do not write Kotlin in sections 0–4. Output the specification only.
When done, end with:
> SPEC COMPLETE. Commit this file to docs/specs/<appname>-spec.md on the app branch before
> running any coding agent prompt.
