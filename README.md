# Nexus Offline Sync Engine 🔄⚡

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.7.2-green.svg?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Jetpack Compose](https://img.shields.io/badge/Compose-BOM_2024.10.01-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material_3-795548.svg?logo=materialdesign)](https://m3.material.io)
[![Hilt](https://img.shields.io/badge/Hilt-2.52-orange.svg?logo=dagger)](https://dagger.dev/hilt/)
[![WorkManager](https://img.shields.io/badge/WorkManager-2.10.0-3DDC84.svg?logo=android)](https://developer.android.com/topic/libraries/architecture/workmanager)
[![Room](https://img.shields.io/badge/Room_DB-2.6.1_KSP-blue.svg?logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![Proto DataStore](https://img.shields.io/badge/Proto_DataStore-1.1.1-3367D6.svg?logo=google)](https://developer.android.com/topic/libraries/architecture/datastore)
[![Detekt](https://img.shields.io/badge/Detekt-1.23.7-17B890.svg)](https://detekt.dev)
[![JUnit 5](https://img.shields.io/badge/JUnit-5.11.3-25A162.svg?logo=junit5)](https://junit.org/junit5/)
[![MockK](https://img.shields.io/badge/MockK-1.13.13-E91E63.svg)](https://mockk.io)

An enterprise-grade, offline-first Android showcase application built in Kotlin demonstrating **bidirectional data synchronization**, **transactional outbox pattern with idempotency keys**, **multi-strategy conflict resolution**, **Proto DataStore**, and a **60 FPS Jetpack Compose** dashboard following strict Clean Architecture.

---

## 🏗 Multi-Module Architecture

The repository enforces strict separation of concerns across 4 decoupled modules:

```
nexus-offline-sync-engine/
├── app/                  # Application bootstrap, Hilt setup, WorkManager Configuration.Provider, Navigation host
├── feature/
│   └── inventory/        # Jetpack Compose Material 3 UI, MVI ViewModel, 60 FPS optimizations
├── data/                 # Room Database, Proto DataStore, OkHttp3, WorkManager Sync Worker, Repositories
└── domain/               # Pure Kotlin JVM module (Zero Android framework imports!), Use Cases, Entities
```

```
                   ┌──────────────┐
                   │     :app     │
                   └──────┬───────┘
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
  ┌───────────────────┐       ┌───────────────────┐
  │:feature:inventory │       │       :data       │
  └─────────┬─────────┘       └─────────▲─────────┘
            │                           │
            └─────────────┬─────────────┘
                          ▼
                   ┌──────────────┐
                   │   :domain    │ (Pure Kotlin JVM, No Android Framework)
                   └──────────────┘
```

| Module | Android Dependencies | Responsibilities |
| :--- | :--- | :--- |
| **`:domain`** | **None (Pure Kotlin JVM)** | Business models (`InventoryItem`, `SyncOperation`, `SyncConfig`), Use Cases (`PerformTwoWaySyncUseCase`, `ResolveConflictUseCase`, `SaveInventoryItemUseCase`), Repository interfaces. Zero Android SDK imports. |
| **`:data`** | Android SDK, Room, WorkManager, DataStore | Room DB (`InventoryDao`, `PendingSyncDao`), Proto DataStore (`sync_config.proto`), WorkManager (`SyncCoroutineWorker`), OkHttp3 remote data source. |
| **`:feature:inventory`** | Android SDK, Compose BOM | MVI `InventoryViewModel`, 60 FPS `LazyColumn` with stable keys, `derivedStateOf`, `ImmutableList`, Material 3 UI. |
| **`:app`** | Android SDK, Compose BOM, Hilt | `@HiltAndroidApp`, `Configuration.Provider` for Hilt WorkManager, Theme, and Navigation host. |

---

## 🔄 Two-Way Offline Sync State Machine

The sync engine uses the **Transactional Outbox Pattern** to decouple user actions from network availability. Local operations are committed immediately to Room DB and mirrored into an outbox queue (`PendingSyncEntity`) before background synchronization occurs.

```
       [User Creates / Edits / Deletes Item]
                         │
                         ▼
        ┌───────────────────────────────────┐
        │ Optimistic Local Write to Room DB │
        │    (syncStatus = PENDING_SYNC)    │
        └─────────────────┬─────────────────┘
                          │
                          ▼
        ┌───────────────────────────────────┐
        │ Enqueue in Outbox Queue with UUID │
        │         Idempotency Key           │
        └─────────────────┬─────────────────┘
                          │
                          ▼
        ┌───────────────────────────────────┐
        │ WorkManager / Manual Sync Trigger │
        │   (Constraints: Network CONNECTED)│
        └─────────────────┬─────────────────┘
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
    [Phase 1: PULL Delta]       [Phase 2: PUSH Outbox]
  Fetch remote changes since       Drain outbox FIFO
    last successful sync           with Idempotency-Key
            │                               │
            ▼                               ▼
    [Conflict Detected?]           [Server Confirms?]
       ├── YES: Run Strategy            ├── YES: Mark SYNCED in DB,
       │   (LWW vs. Server-Auth)        │        Evict from Outbox
       │   • Server Wins: Overwrite     │
       │     local & evict outbox       └── NO:  Increment retryCount,
       │   • Local Wins: Keep outbox             trigger exponential
       └── NO: Direct local mirror               backoff
```

### Sync State Transition Table

| Current State | Event | Next State | Local Action | Outbox Action |
| :--- | :--- | :--- | :--- | :--- |
| **`SYNCED`** | User Edit | `PENDING_SYNC` | Update local Room item, increment version | Enqueue `UPDATE` operation with new idempotency key |
| **`SYNCED`** | User Delete | `PENDING_SYNC` | Soft-delete (`isDeleted = true`) | Enqueue `DELETE` operation |
| **`PENDING_SYNC`** | Network Success | `SYNCED` | Update server version and status to `SYNCED` | Atomically delete operation from `pending_sync_queue` |
| **`PENDING_SYNC`** | Network Failure | `PENDING_SYNC` | Remain visible with "Pending Sync" pill | Increment `retryCount`, backoff via WorkManager |
| **`PENDING_SYNC`** | Server Conflict (Server Wins) | `SYNCED` | Overwrite local item with remote payload | Evict pending outbox operation for this entity |
| **`PENDING_SYNC`** | Server Conflict (Local Wins) | `PENDING_SYNC` | Increment local version | Preserve outbox operation for upstream push |

---

## ⚔️ Conflict Resolution Strategies

The engine supports dynamic, runtime-configurable conflict resolution strategies stored in **Proto DataStore**:

1. **Last-Write-Wins (LWW)**:
   * Compares the local `lastModifiedEpoch` timestamp against the remote server `lastModifiedEpoch`.
   * If local timestamp is newer, local data prevails; the version vector is incremented and kept in the outbox.
   * If remote timestamp is newer, server data overwrites local Room state and evicts any pending outbox items.
2. **Server-Authoritative**:
   * Enterprise-dominant strategy for strict centralized consistency.
   * Remote server data unconditionally supersedes uncommitted local changes during conflict resolution.
   * Conflicting local outbox operations are purged immediately to prevent stale overwrites.

---

## 📜 Proto DataStore Configuration (`sync_config.proto`)

Rather than relying on loosely typed, synchronous `SharedPreferences`, the sync configuration uses **Protocol Buffers** and AndroidX **Proto DataStore**:

```protobuf
syntax = "proto3";

option java_package = "com.nexus.sync.data.proto";
option java_multiple_files = true;

enum ConflictResolutionStrategyProto {
  CONFLICT_STRATEGY_UNSPECIFIED = 0;
  CONFLICT_STRATEGY_LAST_WRITE_WINS = 1;
  CONFLICT_STRATEGY_SERVER_AUTHORITATIVE = 2;
}

message SyncConfigProto {
  int32 sync_interval_minutes = 1;
  int32 batch_size = 2;
  ConflictResolutionStrategyProto conflict_strategy = 3;
  string auth_token = 4;
  int64 token_expiry_epoch = 5;
  int64 last_successful_sync_epoch = 6;
  bool auto_sync_enabled = 7;
}
```

* **Async & Thread-Safe**: Reads and writes run asynchronously off the main thread on `Dispatchers.IO` via Kotlin `Flow`.
* **Zero Corruptions**: Handled via `SyncConfigSerializer` with `CorruptionException` fallback to defaults.

---

## ⚡ 60 FPS Jetpack Compose Optimization

To achieve a butter-smooth 60 FPS on high-scale enterprise lists, the UI module incorporates four core architectural optimizations:

1. **Immutable Collections (`kotlinx.collections.immutable`)**:
   * Standard `java.util.List` is considered unstable by the Compose compiler, triggering recompositions of all cards whenever *any* item updates.
   * By using `ImmutableList<InventoryItemUiModel>`, the Compose compiler tags the parameter as `@Stable`, completely skipping unchanged item cards during recomposition cycles.
2. **`derivedStateOf` for Scroll State**:
   * Calculating whether to show the "Scroll to Top" Floating Action Button (`listState.firstVisibleItemIndex > 2`) inside standard composition would trigger recomposition on **every single pixel scrolled**.
   * Wrapping the calculation in `derivedStateOf { listState.firstVisibleItemIndex > 2 }` ensures recomposition occurs **only** when the resulting boolean changes state.
3. **Explicit Keys and Content Types in `LazyColumn`**:
   * Stable `key = { it.id }` prevents item recreation during list shifts or insertions.
   * `contentType = { "inventory_card" }` allows the Compose runtime to reuse node layout slots efficiently.
4. **Lifecycle-Aware Collection**:
   * Uses `collectAsStateWithLifecycle()` to pause StateFlow collection when the app is backgrounded, conserving CPU cycles and battery.

---

## 🛡️ ANR & Memory Leak Prevention

Enterprise apps syncing hundreds of database entries are vulnerable to Application Not Responding (ANR) dialogs and memory leaks if thread boundaries and lifecycles are neglected. Here is how this architecture eliminates them:

### 1. Eliminating ANRs (Main Thread Starvation)
* **Strict Dispatcher Isolation**: All Room DB transactions, Proto DataStore disk I/O, and OkHttp network calls are strictly bound to `Dispatchers.IO`. Use cases and repositories execute within suspending functions with non-blocking coroutines.
* **Batch Sizing & Chunked Draining**: Outbox operations are drained in user-configurable batches (`batchSize = 50`) rather than unbound single queries.
* **WorkManager Deferral**: Heavy background synchronization runs via `SyncCoroutineWorker` managed by the OS, preventing foreground thread contention.

### 2. Preventing Memory Leaks
* **WorkManager Lifecycle Decoupling**: Workers are decoupled from UI Activity contexts. `@HiltWorker` with custom `Configuration.Provider` allows Hilt to inject dependencies into Workers cleanly without storing static activity references.
* **Unidirectional StateFlow & Coroutine Scopes**: The ViewModel launches background flows inside `viewModelScope`, ensuring automatic cancellation when the user navigates away.
* **No Leaked Singletons**: Singletons (`@Singleton`) inject only `@ApplicationContext` rather than `Activity` contexts.

---

## ⚖️ Architectural Trade-offs

| Architectural Decision | Chosen Approach | Alternative Considered | Rationale |
| :--- | :--- | :--- | :--- |
| **Offline Persistence** | **Transactional Outbox (Room DB)** | Event Sourcing (CQRS) / Client-side CRDTs | Outbox pattern offers low mental complexity, zero schema bloat, and full ACID support with SQLite while remaining 100% reliable for inventory states. |
| **Sync Scheduling** | **WorkManager (`NetworkType.CONNECTED`)** | Foreground Service / AlarmManager | WorkManager adheres to Modern Android Doze mode and battery optimizations, guaranteeing background execution even if the app process is terminated. |
| **Background Backoff** | **WorkManager Exponential Backoff** | Custom `delay()` loops | WorkManager's native `BackoffPolicy.EXPONENTIAL` handles OS scheduling without keeping CPU wake-locks active. |
| **Configuration Storage** | **Proto DataStore** | `SharedPreferences` | Proto DataStore provides compile-time type safety, schema migration, async Flow emissions, and immunity to main-thread I/O stalls. |
| **UI Collections** | **`ImmutableList`** | `List<T>` | Guarantees Compose compiler stability, preventing frame drops during list scrolling. |

---

## 🧪 Testing Suite

The repository includes a comprehensive unit and UI test suite powered by **JUnit 5**, **MockK**, and CashApp **Turbine**:

* **`TwoWaySyncEngineTest`**: Verifies bidirectional synchronization, FIFO outbox queue draining, idempotency key propagation, LWW conflict resolution, and HTTP error retry handling.
* **`ConflictResolutionTest`**: Verifies deterministic outcome of Last-Write-Wins and Server-Authoritative strategies under varying local and remote timestamps.
* **`InventoryViewModelTest`**: Tests StateFlow emissions using Turbine, verifying optimistic item creation, filtering (low stock, pending sync), and search debouncing.
* **`InventoryScreenTest`**: Compose UI test verifying proper item card rendering, status badges, and accessibility.

### Running Tests & Quality Checks
```bash
# Run Detekt static analysis
./gradlew detekt

# Run all unit tests across all modules
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```

---

## 🚀 GitHub Actions CI Pipeline

The project includes [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) which executes:
1. `git checkout` & JDK 17 setup
2. `./gradlew detekt` (Static analysis and formatting)
3. `./gradlew testDebugUnitTest` (Unit tests with MockK & Turbine)
4. `./gradlew assembleDebug` (Debug APK build verification)
5. Test report artifact upload

---

## 📄 License

```
Copyright 2026 Nexus Sync Engine Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
