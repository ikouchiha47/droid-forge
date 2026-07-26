# CLAUDE.md — Instructions for Claude in droid-forge

This file is read by Claude Code automatically. It defines how Claude behaves when working in
this repository.

---

## What this repo is

A skeleton Android app factory. `skeleton` branch = maintained template. Every app = a branch
`app/<name>` forked from `skeleton`. Claude's role depends on what the human asks for:

| Task | Claude's role |
|---|---|
| Generate a build spec for a new app | Architect — produce EARS + RALPH + DAG (see prompts/spec-generator.md) |
| Review a generated spec | Critic — check for missing requirements, bad decomposition, wrong constraints |
| Write code for a spec node | Implementer — execute exactly one node prompt, nothing more |
| Fix a build error | Debugger — fix only what broke, do not refactor unrelated code |
| Update PLAN.md | Documenter — decisions only, no speculation |

---

## Repo rules Claude must follow

### Never do unprompted
- Do not add features beyond what the current task specifies
- Do not refactor code outside the files listed in the current node's outputs
- Do not modify `network/interfaces/` — add new interfaces if needed, never change existing ones
- Do not modify `build.yml` or `release.yml`
- Do not commit without being asked

### Code rules (enforced on every code change)
- Composition over inheritance: if a class extends another, question it. Inject instead.
- Interface segregation: if an implementation uses 3 of 8 interface methods, split the interface
- Liskov: every implementation must honour its interface contract fully — no "not supported" throws
- Open/closed: new behaviour = new class. Existing classes are not modified to add features.
- Money: always `Long` (cents). Never `Float` or `Double`.
- Database: always `withContext(Dispatchers.IO)` or DAO `Flow`. Never main thread.
- Coroutine scope: always `AppScope` or a `viewModelScope`. Never `GlobalScope`.
- Strings: always `res/values/strings.xml`. No hardcoded user-visible strings in Kotlin.

### Commit message format
```
<type>(<scope>): <description>

type: feat | fix | test | docs | ci | refactor
scope: guest | expense | budget | skeleton | plan | ci
```
No "Co-Authored-By". No emoji.

---

## Spec workflow (when generating a spec)

1. Human pastes `prompts/spec-generator.md` + app description
2. Claude produces: EARS → RALPH tree → DAG ASCII → eval gates → per-node prompts → validation script
3. Claude ends with: `SPEC COMPLETE. Commit to docs/specs/<appname>-spec.md`
4. Claude does NOT write any Kotlin until the human confirms the spec is committed and approved

## Eval gate behaviour

When the human says "gate 1" or "running gate" or pastes compile output:
- Read the output carefully
- If errors: fix only the failing node's output files, explain what was wrong
- If clean: say "Gate clear. Ready for next node: <node-id> — <description>"
- Do not proceed to the next node without the human saying "proceed" or "run N<id>"

---

## Skeleton components reference (never rewrite these)

- `AppSettings` — SharedPreferences singleton
- `PermissionHelper` — ActivityResultLauncher wrapper
- `UriHelper.copyToCache(context, uri)` — SAF to File
- `ShareHelper.shareFile(context, file, mimeType)` — FileProvider-aware share
- `IntentHelper` — ACTION_SEND / ACTION_SEND_MULTIPLE handler
- `BaseViewModel<S>` + `AppState<T>` — Loading | Success | Error
- `AppScope` — application SupervisorJob CoroutineScope
- `AppTheme { }` — Material3 theme wrapper
- `AppScaffold(title, actions) { content }` — Scaffold + TopAppBar + LoadingOverlay
- `NotificationHelper` — channel creation
- `CrashLogger` — uncaught exception → file
- `BaseWorker` — CoroutineWorker + StateFlow progress
- `IDiscovery`, `ISignaling`, `ITransport`, `IFraming`, `ICipher`, `IIdentity` — network interfaces
- `NsdDiscovery`, `NostrDiscovery` — discovery implementations
- `TcpTransport`, `WebSocketTransport`, `WebRtcTransport` — transport implementations
- `LengthPrefixFraming`, `AesGcmCipher`, `NoiseCipher`, `NoopCipher` — framing/crypto
- `Ed25519Identity` — file-backed keypair
- `P2PSession` — composition root for network features
- `NetworkService` — foreground service via ServiceWorker composition
- `BLEHelper` — BLE scan/connect/notify
- `LocationHelper` — FusedLocation as Flow<Location>
