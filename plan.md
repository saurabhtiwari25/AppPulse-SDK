# AppPulse — Android Analytics SDK Development Plan

> **Goal:** Build a production-quality, reusable Android Analytics SDK in Kotlin, with a separate demo app, designed to showcase skills for an Application SDK Developer role.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Phase 1 — Project Setup & SDK Initialization](#phase-1--project-setup--sdk-initialization)
- [Phase 2 — Event Model & Local Persistence](#phase-2--event-model--local-persistence)
- [Phase 3 — Network Layer & REST API](#phase-3--network-layer--rest-api)
- [Phase 4 — Retry, WorkManager & Offline Sync](#phase-4--retry-workmanager--offline-sync)
- [Phase 5 — Testing, Debugging & Error Handling](#phase-5--testing-debugging--error-handling)
- [Phase 6 — Performance, Docs, AAR & Release](#phase-6--performance-docs-aar--release)
- [Final Acceptance Checklist](#final-acceptance-checklist)

---

## Architecture Overview

```
Demo App
  ↓
AppPulse Public API  (AppPulse.kt, AppPulseConfig.kt)
  ↓
AppPulse Manager     (internal orchestrator)
  ↓
Repository
  ├── Local Data Source  →  Room (AnalyticsEventEntity)
  └── Remote Data Source →  Retrofit / OkHttp  →  REST API
  ↓
Background Sync      (WorkManager + Coroutines)
```

**Design Principles:**
- Host app interacts only with the public API surface (`AppPulse`, `AppPulseConfig`, `EventCallback`, `AppPulseError`)
- All internals (Room, Retrofit, OkHttp, WorkManager, repositories, entities) are `internal`
- SDK never crashes the host app — all failures are gracefully handled
- No main-thread blocking — all I/O via Coroutines

---

## Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Language | Kotlin | Primary language |
| Build | Gradle (Kotlin DSL) | Multi-module build |
| Local Storage | Room | Event queue persistence |
| Networking | Retrofit + OkHttp | REST API communication |
| Serialization | Gson / Moshi | JSON encoding/decoding |
| Async | Kotlin Coroutines | Non-blocking I/O |
| Background | WorkManager | Offline sync scheduling |
| Testing | JUnit 5 + MockK | Unit & integration tests |
| VCS | Git / GitHub | Version control + releases |

---

## Project Structure

```
apppulse/
│
├── sdk/                          # Android Library module
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── kotlin/com/apppulse/sdk/
│   │       ├── AppPulse.kt                    # Public singleton entry point
│   │       ├── AppPulseConfig.kt               # Public configuration data class
│   │       ├── EventCallback.kt                # Public callback interface
│   │       ├── AppPulseError.kt                # Public sealed error hierarchy
│   │       ├── internal/
│   │       │   ├── manager/
│   │       │   │   └── AppPulseManager.kt      # Internal orchestrator
│   │       │   ├── model/
│   │       │   │   └── AnalyticsEvent.kt       # Internal event model
│   │       │   ├── db/
│   │       │   │   ├── AppPulseDatabase.kt     # Room database
│   │       │   │   ├── EventDao.kt             # Room DAO
│   │       │   │   └── AnalyticsEventEntity.kt # Room entity
│   │       │   ├── network/
│   │       │   │   ├── ApiService.kt           # Retrofit interface
│   │       │   │   ├── NetworkClient.kt        # OkHttp setup
│   │       │   │   ├── EventRequest.kt         # Request DTO
│   │       │   │   └── EventResponse.kt        # Response DTO
│   │       │   ├── repository/
│   │       │   │   ├── EventRepository.kt      # Repository interface
│   │       │   │   └── EventRepositoryImpl.kt  # Repository implementation
│   │       │   ├── sync/
│   │       │   │   ├── SyncWorker.kt           # WorkManager worker
│   │       │   │   └── SyncScheduler.kt        # WorkManager scheduling
│   │       │   ├── retry/
│   │       │   │   └── RetryPolicy.kt          # Exponential backoff logic
│   │       │   ├── validation/
│   │       │   │   └── EventValidator.kt       # Input validation
│   │       │   └── logging/
│   │       │       └── SdkLogger.kt            # Internal configurable logger
│   │       └── util/
│   │           └── DeviceInfo.kt               # Device metadata helper
│   ├── src/test/                               # Unit tests
│   └── build.gradle.kts
│
├── demo-app/                     # Android Application module
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   └── kotlin/com/apppulse/demo/
│   │       ├── DemoApplication.kt
│   │       ├── MainActivity.kt
│   │       └── ui/
│   ├── src/test/
│   └── build.gradle.kts
│
├── mock-server/                  # Simple mock backend (Ktor or Python)
│   └── ...
│
├── docs/
│   ├── architecture.md
│   ├── integration.md
│   ├── api-reference.md
│   └── troubleshooting.md
│
├── build.gradle.kts              # Root build file
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── CHANGELOG.md
├── LICENSE
└── .gitignore
```

---

## Phase 1 — Project Setup & SDK Initialization

> **Objective:** Bootable multi-module project with a working SDK initialization flow.

### 1.1 — Create Multi-Module Gradle Project

| Task | Details |
|---|---|
| Create root `settings.gradle.kts` | Include `:sdk` and `:demo-app` modules |
| Create root `build.gradle.kts` | Common plugin versions, Kotlin version, Android Gradle Plugin |
| Create `sdk/build.gradle.kts` | Apply `com.android.library` + `kotlin-android`; set `minSdk`, `compileSdk`, `targetSdk` |
| Create `demo-app/build.gradle.kts` | Apply `com.android.application` + `kotlin-android`; add `implementation(project(":sdk"))` |
| Configure `.gitignore` | Ignore build dirs, `.idea`, `*.jks`, `local.properties`, etc. |
| Add `gradle.properties` | JVM args, AndroidX opt-ins |

**Key decisions:**
- `minSdk = 24` (Android 7.0) — covers ~97% of devices
- `compileSdk = 35` / `targetSdk = 35`
- Kotlin 2.0.x, AGP 8.5.x

**Verification:** `./gradlew :sdk:assemble :demo-app:assembleDebug` succeeds.

**Git commit:** `Initial multi-module project setup`

---

### 1.2 — Define Public API Surface

| File | Purpose |
|---|---|
| `AppPulseConfig.kt` | Data class holding `apiKey`, `endpoint`, `debugLogging`, `batchSize`, `retryLimit` |
| `AppPulse.kt` | Public singleton object — `initialize()`, `setUserId()`, `clearUser()`, `trackEvent()` |
| `AppPulseError.kt` | Sealed class hierarchy: `InitializationError`, `ConfigurationError`, `ValidationError`, `NetworkError`, `TimeoutError`, `ApiError`, `StorageError` |
| `EventCallback.kt` | Interface with `onSuccess()` / `onFailure(error: AppPulseError)` |

**Design rules:**
- `AppPulse` is the only public entry point
- Config validation happens at `initialize()` time
- `initialize()` is idempotent — second call logs a warning, does nothing
- All public methods are non-blocking and thread-safe

**Implementation notes:**
```kotlin
// AppPulseConfig.kt
data class AppPulseConfig(
    val apiKey: String,
    val endpoint: String,
    val debugLogging: Boolean = false,
    val batchSize: Int = 20,
    val retryLimit: Int = 3
)

// AppPulse.kt
object AppPulse {
    @Volatile private var manager: AppPulseManager? = null

    fun initialize(context: Context, config: AppPulseConfig) { ... }
    fun setUserId(userId: String) { ... }
    fun clearUser() { ... }
    fun trackEvent(name: String, properties: Map<String, Any>? = null, callback: EventCallback? = null) { ... }
}
```

**Verification:** Demo app calls `AppPulse.initialize()` in `Application.onCreate()` and logs "SDK initialized."

**Git commit:** `Add SDK public API and initialization`

---

### 1.3 — Internal Manager & Logger

| File | Purpose |
|---|---|
| `AppPulseManager.kt` | Internal orchestrator — holds config, userId, references to repo/sync |
| `SdkLogger.kt` | Tag-based logger that respects `debugLogging` flag; never logs API keys |

**Verification:** Debug logs appear in Logcat when `debugLogging = true`, absent when `false`.

**Git commit:** `Add internal manager and configurable SDK logger`

---

### 1.4 — Demo App Skeleton

| File | Purpose |
|---|---|
| `DemoApplication.kt` | Custom `Application` subclass — calls `AppPulse.initialize()` |
| `MainActivity.kt` | Basic UI with buttons to trigger SDK calls |

**Verification:** App launches, shows status "Initialized", buttons exist but event tracking is stubbed.

**Git commit:** `Add demo app skeleton with SDK integration`

---

### Phase 1 Exit Criteria

- [ ] Both modules compile and build
- [ ] Demo app launches on emulator/device
- [ ] `AppPulse.initialize()` works with valid config
- [ ] Invalid config is rejected with clear error
- [ ] Double-init is handled gracefully
- [ ] Logger works and respects config
- [ ] No crashes

---

## Phase 2 — Event Model & Local Persistence

> **Objective:** Track events and persist them locally with Room.

### 2.1 — Event Model & Validation

| File | Purpose |
|---|---|
| `AnalyticsEvent.kt` | Internal data class: `id` (UUID), `name`, `userId`, `properties`, `timestamp`, `sdkVersion`, `appVersion`, `deviceInfo` |
| `EventValidator.kt` | Validates event name (non-empty, max length), properties (supported types, max size), payload size |
| `DeviceInfo.kt` | Collects Android version, device model, locale — privacy-safe only |

**Validation rules:**
- Event name: non-blank, max 256 chars, alphanumeric + underscores
- Properties: max 50 keys, values must be `String`, `Number`, `Boolean`; max 1 KB per value
- Total payload: max 64 KB
- Invalid events → return `ValidationError`, never crash

**Git commit:** `Add event model, validation, and device info`

---

### 2.2 — Room Database

| File | Purpose |
|---|---|
| `AnalyticsEventEntity.kt` | Room `@Entity` — `id`, `eventName`, `userId`, `propertiesJson`, `timestamp`, `retryCount`, `status` (PENDING / SENDING / SENT / FAILED) |
| `EventDao.kt` | `@Dao` — `insert`, `getPending(limit)`, `updateStatus`, `incrementRetry`, `deleteSent`, `deleteById`, `countByStatus` |
| `AppPulseDatabase.kt` | Room `@Database` — singleton, version 1, export schema = false |

**Status flow:**
```
PENDING → SENDING → SENT (delete)
                  → FAILED (retry or abandon)
```

**Threading:** All DAO methods are `suspend` functions — called from Coroutines only.

**Git commit:** `Add Room database for local event persistence`

---

### 2.3 — Repository (Local-only for now)

| File | Purpose |
|---|---|
| `EventRepository.kt` | Internal interface: `saveEvent()`, `getPendingEvents()`, `markSent()`, `markFailed()`, `deleteEvent()`, `getEventCounts()` |
| `EventRepositoryImpl.kt` | Implementation using `EventDao`; maps between domain model ↔ entity |

**Git commit:** `Add event repository with local persistence`

---

### 2.4 — Wire trackEvent → Room

Update `AppPulseManager` to:
1. Validate event via `EventValidator`
2. Create `AnalyticsEvent` with metadata
3. Save to Room via `EventRepository`
4. Invoke callback with success/failure
5. Log the action

**Verification:**
- Call `trackEvent("login")` from demo app
- Query Room (via App Inspection or debug code) → event exists with status `PENDING`
- Invalid event name → `ValidationError` callback, no crash

**Git commit:** `Wire event tracking to local Room storage`

---

### 2.5 — Demo App: Event Counters

Update `MainActivity` to display:
- Pending events count
- Uploaded events count (0 for now)
- Failed events count

Use a simple polling or LiveData/Flow mechanism.

**Git commit:** `Add event counters to demo app UI`

---

### Phase 2 Exit Criteria

- [ ] Events are persisted in Room
- [ ] Event validation catches bad inputs
- [ ] Callbacks fire correctly (success/failure)
- [ ] Demo app shows event counts
- [ ] No main-thread database access
- [ ] No crashes on invalid input

---

## Phase 3 — Network Layer & REST API

> **Objective:** Send events to a REST API using Retrofit/OkHttp; create a mock backend.

### 3.1 — Mock Server

| Option | Details |
|---|---|
| **Option A: Ktor** | Lightweight Kotlin server; single file; runs locally on port 8080 |
| **Option B: Python Flask** | Minimal Python script; easier for non-JVM reviewers |
| **Option C: MockWebServer** | OkHttp's test server; great for tests but not a standalone demo |

**Recommendation:** Use **Ktor** for the standalone mock server (Kotlin consistency) + **MockWebServer** for automated tests.

Mock server endpoints:
```
POST /events        → Accept single event, return 200/201
POST /events/batch  → Accept array of events, return 200/201
GET  /health        → Return 200 OK
```

The mock server should simulate:
- Normal success (200)
- Random failures (500) for retry testing
- Slow responses for timeout testing
- 401 for invalid API key testing

**Git commit:** `Add Ktor mock server for analytics backend`

---

### 3.2 — Network Client

| File | Purpose |
|---|---|
| `NetworkClient.kt` | Internal OkHttp client factory — timeouts (connect: 10s, read: 30s, write: 30s), API key header interceptor, logging interceptor (debug only) |
| `ApiService.kt` | Retrofit interface — `suspend fun postEvent(event: EventRequest): Response<EventResponse>`, `suspend fun postEventsBatch(events: List<EventRequest>): Response<EventResponse>` |
| `EventRequest.kt` | DTO for outgoing JSON |
| `EventResponse.kt` | DTO for incoming JSON |

**Security:** API key sent via `X-API-Key` header, never in URL or logs.

**Git commit:** `Add Retrofit/OkHttp network client`

---

### 3.3 — Remote Data Source & Error Mapping

| File | Purpose |
|---|---|
| `RemoteDataSource.kt` | Wraps `ApiService`; maps HTTP responses → `Result<Unit>` / `AppPulseError` |

**Error mapping:**
| HTTP Status | AppPulseError | Retryable? |
|---|---|---|
| 200-201 | ✅ Success | — |
| 400 | `ApiError("Bad request")` | ❌ No |
| 401 | `ApiError("Invalid API key")` | ❌ No |
| 429 | `ApiError("Rate limited")` | ✅ Yes |
| 500-503 | `ApiError("Server error")` | ✅ Yes |
| Timeout | `TimeoutError` | ✅ Yes |
| No network | `NetworkError` | ✅ Yes |

**Git commit:** `Add remote data source with error mapping`

---

### 3.4 — Update Repository

Extend `EventRepositoryImpl` to:
1. Save event locally (Room)
2. Attempt immediate upload via `RemoteDataSource`
3. On success → mark as SENT / delete
4. On retryable failure → keep as PENDING
5. On non-retryable failure → mark as FAILED

**Git commit:** `Integrate remote data source into repository`

---

### 3.5 — Demo App: Network Testing

- Add a toggle in the demo app to switch between real endpoint / bad endpoint
- Show upload results in real-time
- Display success/failure toasts

**Git commit:** `Add network testing controls to demo app`

---

### Phase 3 Exit Criteria

- [ ] Mock server runs and accepts events
- [ ] SDK sends events via Retrofit
- [ ] JSON serialization/deserialization works
- [ ] HTTP errors are mapped correctly
- [ ] Timeout/network errors are handled
- [ ] API key is sent securely
- [ ] No Retrofit/OkHttp types leak into public API
- [ ] Demo app shows upload results

---

## Phase 4 — Retry, WorkManager & Offline Sync

> **Objective:** Reliable background synchronization with retry and batching.

### 4.1 — Retry Policy

| File | Purpose |
|---|---|
| `RetryPolicy.kt` | Exponential backoff calculator: `delay = baseDelay * 2^retryCount` (capped at max delay); determines if error is retryable |

**Configuration:**
- Base delay: 1 second
- Max delay: 5 minutes
- Max retries: `config.retryLimit` (default 3)

**Logic:**
```
if retryCount >= retryLimit → abandon event (mark FAILED)
if error is non-retryable   → abandon event immediately
else                         → increment retryCount, schedule retry
```

**Git commit:** `Add retry policy with exponential backoff`

---

### 4.2 — Batch Sender

| File | Purpose |
|---|---|
| `BatchSender.kt` | Reads up to `batchSize` pending events, sends as batch, processes results per-event |

**Flow:**
```
1. Query PENDING events (limit = batchSize)
2. Mark them as SENDING
3. POST /events/batch
4. On success → delete sent events
5. On failure → increment retryCount, revert to PENDING or mark FAILED
6. Repeat until no more PENDING events
```

**Git commit:** `Add batch event sender`

---

### 4.3 — WorkManager Sync

| File | Purpose |
|---|---|
| `SyncWorker.kt` | `CoroutineWorker` — runs `BatchSender` logic; returns `Result.success()` / `Result.retry()` |
| `SyncScheduler.kt` | Schedules periodic sync (e.g., every 15 min) with network constraint; provides `requestImmediateSync()` |

**WorkManager constraints:**
- `NetworkType.CONNECTED` required
- Backoff policy: exponential, 30 seconds initial

**Scheduling:**
- Periodic: every 15 minutes (minimum allowed)
- One-time: triggered on each `trackEvent()` call (debounced)
- Manual: `AppPulse.forceSync()` for demo/testing

**Git commit:** `Add WorkManager background synchronization`

---

### 4.4 — Offline Behavior

Verify:
1. Turn off network → track events → events stay in Room as PENDING
2. Turn on network → WorkManager fires → events uploaded
3. Events are never lost

**Git commit:** `Verify and polish offline sync behavior`

---

### 4.5 — Demo App: Sync Controls

- Add "Force Sync" button → calls `AppPulse.forceSync()`
- Add "Clear Local Events" button
- Show sync status / last sync time
- Test airplane mode flow

**Git commit:** `Add sync controls to demo app`

---

### Phase 4 Exit Criteria

- [ ] Retry with exponential backoff works
- [ ] Non-retryable errors are not retried
- [ ] Retry limit is respected
- [ ] Batching works with configurable batch size
- [ ] WorkManager runs in background
- [ ] Offline → online flow works end-to-end
- [ ] Events are never silently lost
- [ ] `forceSync()` works from demo app

---

## Phase 5 — Testing, Debugging & Error Handling

> **Objective:** Comprehensive automated tests and polished error handling.

### 5.1 — Unit Tests

| Test Class | What It Tests |
|---|---|
| `EventValidatorTest` | Empty names, long names, invalid chars, valid events, property type checks, payload size |
| `AppPulseConfigTest` | Blank API key, blank endpoint, invalid URL, negative batch size, valid config |
| `AnalyticsEventTest` | Event creation, UUID generation, timestamp, metadata population |
| `RetryPolicyTest` | Backoff calculation, retry limit, retryable vs non-retryable errors |
| `ErrorMappingTest` | HTTP status → `AppPulseError` mapping for all cases |
| `EventEntityMappingTest` | Domain model ↔ Room entity conversion, JSON properties serialization |

**Testing dependencies:**
- JUnit 5
- MockK
- Kotlin Coroutines Test
- Truth or AssertJ for assertions

**Git commit:** `Add unit tests for validation, config, retry, and error mapping`

---

### 5.2 — Integration Tests

| Test Class | What It Tests |
|---|---|
| `EventRepositoryIntegrationTest` | `trackEvent → Room → read back` flow using in-memory Room DB |
| `SyncFlowIntegrationTest` | `trackEvent → Room → API upload → deletion` with MockWebServer |
| `RetryFlowIntegrationTest` | `trackEvent → API failure → retry → success` with MockWebServer |
| `OfflineFlowIntegrationTest` | `trackEvent → no network → events persist → network returns → sync` |
| `BatchingIntegrationTest` | Correct batch splitting, partial batch failure handling |

**Test infrastructure:**
- MockWebServer (OkHttp) for API simulation
- In-memory Room DB for fast tests
- Coroutines `TestDispatcher` for deterministic async

**Git commit:** `Add integration tests for sync, retry, and offline flows`

---

### 5.3 — Polish Error Handling

Review all error paths:
- [ ] `initialize()` with null context → meaningful error
- [ ] `trackEvent()` before `initialize()` → `InitializationError`
- [ ] Room failure (disk full, corruption) → `StorageError`, event not lost silently
- [ ] API returns unexpected JSON → handled, not crash
- [ ] OkHttp SSL error → `NetworkError`

Ensure: **SDK never throws an uncaught exception that crashes the host app.**

**Git commit:** `Polish error handling across all SDK paths`

---

### 5.4 — Polish Debug Logging

Verify log output covers:
```
AppPulse: SDK initialized (endpoint=https://..., debug=true)
AppPulse: User set: user-***  (masked)
AppPulse: Event queued: purchase (id=abc123)
AppPulse: Sync started — 5 pending events
AppPulse: Batch sent: 5 events → 200 OK
AppPulse: Sync complete — 0 pending
AppPulse: Request failed: timeout (retry 1/3)
AppPulse: Event abandoned: abc123 (max retries exceeded)
```

- No API keys logged
- No raw user IDs (mask or truncate)
- No property values in production logs

**Git commit:** `Polish SDK debug logging`

---

### Phase 5 Exit Criteria

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Test coverage is meaningful (not vanity metrics)
- [ ] No SDK operation can crash the host app
- [ ] Error types are specific and actionable
- [ ] Debug logging is comprehensive but safe
- [ ] Tests run in CI-compatible way (`./gradlew test`)

---

## Phase 6 — Performance, Docs, AAR & Release

> **Objective:** Production polish, documentation, and release preparation.

### 6.1 — Performance Review

| Area | Check |
|---|---|
| Startup | `initialize()` does no heavy work synchronously; Room/Retrofit created lazily |
| Memory | No context leaks; use `applicationContext` only; no static Activity references |
| Threading | Verify no main-thread I/O via StrictMode |
| Batching | Large queues don't cause OOM; process in chunks |
| Network | No redundant requests; respect backoff; cancel on SDK reset |
| Database | Indexes on `status` column for efficient queries; prune old SENT events |
| Logging | Logging overhead is negligible when disabled |

Document findings in `docs/architecture.md` under "Performance Considerations."

**Git commit:** `Performance review and optimizations`

---

### 6.2 — Documentation

| Document | Contents |
|---|---|
| **README.md** | Overview, features, architecture diagram, quick start, full API reference, build/run instructions, screenshots of demo app |
| **docs/architecture.md** | Detailed architecture, module diagram, data flow, threading model, performance notes |
| **docs/integration.md** | Step-by-step guide: add dependency → initialize → track events → handle errors |
| **docs/api-reference.md** | Every public class/method with parameters, return types, examples |
| **docs/troubleshooting.md** | Common issues: "Events not uploading", "SDK not initialized", ProGuard rules, etc. |
| **CHANGELOG.md** | `v1.0.0` — initial release with full feature list |
| **LICENSE** | Apache 2.0 or MIT |

**Git commit:** `Add comprehensive documentation`

---

### 6.3 — AAR Packaging

```bash
./gradlew :sdk:assembleRelease
```

Output: `sdk/build/outputs/aar/sdk-release.aar`

Rename to `apppulse-sdk-1.0.0.aar` in release.

**Document integration via local AAR:**
```kotlin
// In consuming app's build.gradle.kts
implementation(files("libs/apppulse-sdk-1.0.0.aar"))
```

**Document transitive dependencies** the consuming app must include (Retrofit, Room, WorkManager).

**Consider:** publishing to Maven Local for cleaner integration demo.

**Git commit:** `Configure AAR packaging and document integration`

---

### 6.4 — ProGuard / R8

Add `consumer-rules.pro` in the SDK module:
```proguard
# Keep public API
-keep class com.apppulse.sdk.AppPulse { *; }
-keep class com.apppulse.sdk.AppPulseConfig { *; }
-keep class com.apppulse.sdk.AppPulseError { *; }
-keep class com.apppulse.sdk.EventCallback { *; }

# Keep Retrofit models (internal but needed for serialization)
-keep class com.apppulse.sdk.internal.network.** { *; }
```

**Git commit:** `Add ProGuard/R8 consumer rules`

---

### 6.5 — Demo APK & Release

1. Build signed release APK for demo app (signing config via `local.properties`, NOT committed)
2. Run full test suite: `./gradlew test`
3. Create git tag: `v1.0.0`
4. Create GitHub Release `v1.0.0` with:
   - Release notes
   - `apppulse-sdk-1.0.0.aar` attached
   - `AppPulseDemo-v1.0.0.apk` attached

**Git commit:** `Prepare v1.0.0 release`

---

### Phase 6 Exit Criteria

- [ ] No performance regressions
- [ ] All documentation complete and accurate
- [ ] AAR builds successfully
- [ ] ProGuard rules work (demo app with minification ON)
- [ ] README is recruiter/engineer-friendly
- [ ] GitHub release is ready
- [ ] Repository is clean (no secrets, no build artifacts)

---

## Git Strategy

### Branching

```
main          ← stable, tagged releases
├── develop   ← integration branch
│   ├── feature/phase-1-setup
│   ├── feature/phase-2-events
│   ├── feature/phase-3-network
│   ├── feature/phase-4-sync
│   ├── feature/phase-5-testing
│   └── feature/phase-6-release
```

### Commit Convention

```
<type>: <short description>

Types: feat, fix, test, docs, refactor, chore
```

Examples:
```
feat: add SDK public API and initialization
feat: add Room event persistence
feat: add Retrofit network layer
feat: add WorkManager background sync
test: add unit tests for event validation
docs: add README and architecture docs
chore: configure AAR packaging
```

---

## Final Acceptance Checklist

- [ ] SDK builds successfully (`./gradlew :sdk:assembleRelease`)
- [ ] Demo app builds successfully (`./gradlew :demo-app:assembleDebug`)
- [ ] Demo app integrates SDK through Gradle module dependency
- [ ] `AppPulse.initialize()` works with valid config
- [ ] Invalid config is rejected with `ConfigurationError`
- [ ] `setUserId()` / `clearUser()` work
- [ ] `trackEvent()` with and without properties works
- [ ] Events are stored in Room when tracked
- [ ] REST API communication works (mock server)
- [ ] JSON request/response serialization works
- [ ] Offline mode: events queue locally, sync when online
- [ ] Retry: exponential backoff, respects retry limit
- [ ] Non-retryable errors (400, 401) are not retried
- [ ] WorkManager runs periodic sync
- [ ] `forceSync()` triggers immediate sync
- [ ] Batching: events sent in configurable batch sizes
- [ ] Error handling: SDK never crashes host app
- [ ] Debug logging: comprehensive, safe, configurable
- [ ] Callbacks: `onSuccess` / `onFailure` fire correctly
- [ ] Unit tests pass (`./gradlew :sdk:test`)
- [ ] Integration tests pass
- [ ] AAR can be generated
- [ ] ProGuard/R8 rules work
- [ ] Documentation is complete (README, docs/, CHANGELOG)
- [ ] No secrets committed (API keys, keystores)
- [ ] Demo APK can be generated
- [ ] GitHub release `v1.0.0` is ready

---

## Interview Talking Points per Phase

| Phase | Key Topics to Discuss |
|---|---|
| **1 — Setup** | Multi-module Gradle, Android Library vs Application, singleton pattern, idempotent initialization, context leak prevention |
| **2 — Events** | Data modeling, Room architecture, DAO patterns, input validation, defensive programming |
| **3 — Network** | Retrofit/OkHttp architecture, interceptors, error mapping, DTO vs domain model, HTTP status handling |
| **4 — Sync** | WorkManager vs AlarmManager vs JobScheduler, exponential backoff, offline-first design, batching strategies |
| **5 — Testing** | Unit vs integration vs E2E, MockWebServer, test doubles, coroutine testing, test isolation |
| **6 — Release** | AAR packaging, ProGuard consumer rules, semantic versioning, API stability, backward compatibility |

---

> **Start with Phase 1. Do not skip ahead. Verify each phase builds and runs before proceeding.**
