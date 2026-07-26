# spec-generator.md — Changelog

## What changed and why

### Added Section 0: Architectural Decision Record (ADR)

**Problem**: The original prompt left nine architectural choices open. A dumb model executing
node prompts will make the first plausible choice for each one, producing inconsistent code
across nodes. Specific gaps found by simulating EventTools:

- DI strategy: Hilt vs manual injection — if N03 (GuestDao) and N07 (GuestViewModel) are
  written by different agent sessions, they may disagree on how injection works.
- Navigation: `BottomNavigation` vs `TabRow + pager` — affects back-stack behaviour entirely.
  The back-button-on-root-tab requirement (should exit app, not navigate to previous tab)
  requires `saveState`/`restoreState` in the NavController, which is only needed for
  BottomNavigation. An agent that chooses TabRow will implement it differently.
- Room migration strategy: an agent that writes migration classes for v1 is wasting effort
  and introducing untested code; an agent that calls `fallbackToDestructiveMigration()` is
  correct for v1 but only if explicitly told to.
- Package structure: without a mandate, agents put files in `com.forge.app`, `com.forge.app.ui`,
  `com.forge.app.feature.guest.ui`, etc. across sessions, creating a mess.
- Coroutine scope: `viewModelScope` vs `AppScope` in ViewModels — AppScope survives ViewModel
  death, which is wrong for UI-driven operations.
- Null vs empty String: SQLite distinguishes NULL from ''. Without a rule, insert code sometimes
  writes "" and query code checks for NULL, producing invisible data.
- Money formatting: `amount.toDouble() / 100.0` introduces floating-point rounding errors
  (e.g. 1/3 of 100 cents = 33.333... rendered as 33.33 or 33.34 depending on rounding).
- CSV format: column order, header presence, quoting, SAF vs direct file write, error handling
  on import — all left open.
- Non-trivial algorithms (debt simplification): a greedy creditor/debtor algorithm is not
  self-evident. Different implementations produce different settlement counts for the same
  inputs. The ADR mandates pseudocode for any such algorithm so the agent cannot invent one.

**Fix**: Section 0 (ADR) locks all nine decisions before requirements are written. Every later
section references the ADR. Node prompts copy the relevant ADR decisions verbatim under
"Architectural decisions in force."

---

### Replaced `[PASTE CONTENTS OF SKELETON.md HERE]` with actual content

**Problem**: The placeholder was literal — a model receiving the prompt would see a blank fence.
The skeleton contract (what is pre-built, what the agent must not touch) is the most critical
context for the coding agent. It was missing from every generated spec.

**Fix**: Embedded the SKELETON.md content from PLAN.md section 16 directly into the prompt.

---

### Added Android-specific EARS templates

**Problem**: The original EARS templates were generic. EventTools exposes concrete failure modes
that generic templates do not cover: SAF file picker cancellation (no action, but original prompt
had no template for this), DB write failure (must surface to UI), empty state (blank screen vs
empty-state composable), navigation back-button on root tab (exit app vs back to previous tab).

Without these templates, an agent writing the GuestScreen might handle file picker cancellation
by crashing (no result returned to ActivityResultCallback) or by navigating back (wrong).

**Fix**: Added six Android-specific EARS blocks (file export, file import, navigation,
empty state, database failure) with exact Android API names. Added a rule requiring filter
features to state whether filtering is a Room query or in-memory operation.

---

### Tightened RALPH Level 3 granularity rules

**Problem**: "Each node at Level 3 becomes exactly one prompt" is ambiguous. For EventTools,
a model might put GuestEntity + GuestDao in one node (two files), or put the entire data layer
in one node (six files). Either choice defeats the purpose of node-level parallelism and makes
prompts too large for a fast model.

**Fix**: Added explicit rules: one entity = one node, one DAO = one node, AppDatabase = one
node, one Repository interface + impl = one node, one algorithm = one node, one ViewModel = one
node, one screen = one node, NavHost + routes + BottomNav = one node. TypeConverters = own node.
strings.xml entries are part of the screen node that introduces them (not a separate node).

---

### Strengthened node prompt template with mandatory Kotlin signatures

**Problem**: The original template said "complete interface or data structure" in the Files
section, but did not require Kotlin syntax. A model generating the spec could write:

  "Create GuestDao with methods to insert, update, delete, and query guests filtered by day."

A coding agent reading this invents the method signatures. For EventTools, the DAO needs
`fun getGuestsByDay(day: Int?): Flow<List<Guest>>` where `null` means "all days". An agent
might write `fun getGuestsByDay(day: Int): Flow<List<Guest>>` (no null sentinel), requiring
a second DAO method for the "all days" case, breaking the ViewModel that calls it.

**Fix**: Required every prompt to include full Kotlin method signatures, all annotations,
all @Query SQL strings (not descriptions), nullability of every parameter and return type.

Added explicit checklists for Room entity annotations, DAO method signatures, ViewModel UiState
structure, and screen Composable user-action/string-resource lists.

---

### Elevated debt algorithm gate from RECOMMENDED to MANDATORY (Gate 3)

**Problem**: The original prompt marked the debt algorithm gate as "OPTIONAL (RECOMMENDED)".
The debt simplification algorithm processes money. A wrong implementation (e.g. one that does
not correctly handle the case where netBalances do not sum to zero due to rounding) produces
wrong settlement amounts displayed to real users. This is a data-correctness issue, not a
style issue.

**Fix**: Gate 3 is now mandatory. It includes a specific known-input/expected-output test case
(Alice owes 300, Bob owed 200, Carol owed 100 → Alice pays Bob 200, Alice pays Carol 100) that
the human must verify manually. A `DebtCalculatorTest` unit test node is mandated in the DAG.

---

### Added CSV data integrity as a Recommended Gate (Gate 5 ✦)

**Problem**: CSV import silently skipping malformed rows is correct behaviour, but the agent
might implement the skip counter wrong (e.g. counting skipped rows as 0 always) or not show
the snackbar. This is a user-facing data loss scenario: the user imports 100 rows thinking
all imported, but 20 were silently dropped.

**Fix**: Added a recommended gate with a specific manual test: import a file with 3 valid rows
and 2 malformed rows, verify snackbar shows "Imported 3 rows, skipped 2 rows".

---

### Added "Architectural decisions in force" block to every node prompt

**Problem**: Each node prompt is sent to the coding agent in isolation. Without repeating the
ADR decisions in each prompt, a node written in session N+3 has no way to know whether the
project uses Hilt or manual injection.

**Fix**: Every prompt template now includes a mandatory "Architectural decisions in force"
section that copies the relevant ADR values verbatim. The spec generator is instructed to
populate this section for each node.

---

### Changed validation script to set -euo pipefail and use relative paths correctly

**Problem**: The original script used `cd android` as a bare relative path. If run from a
different working directory it would fail silently or produce wrong output.

**Fix**: Script now uses `cd "$(dirname "$0")/../android"` which is robust regardless of
invocation directory. Added `set -euo pipefail`. Added a separate `verify-debt-*.sh` script
that runs only the algorithm unit tests, making Gate 3 executable without running the full
test suite.
