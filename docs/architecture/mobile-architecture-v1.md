# AI Usage Mobile Architecture v1 (design specification)

Date: 2026-10-16 | Status: **Design proposal, not implemented**  
Decision: [ADR-0001 staged monorepo](./ADR-0001-mobile-monorepo.md)  
Evidence: [Provider matrix](../research/provider-auth-matrix-2026-10-16.md), [Android source audit](../research/android-oss-source-audit-2026-10-15.md)

## Product constraints

1. Preserve four independent identities: `claude_code`, `codex`, `antigravity_gemini`, `antigravity_claude`; never equate Gemini CLI, Antigravity Gemini or Claude Code and Antigravity Claude.
2. Local-first, no proprietary backend in MVP; manual foreground refresh + OS-scheduled **best effort** refresh, no promise of strict 1/5/15-minute polling.
3. No production authentication or private quota endpoints until permitted as documented in provider-specific capability records.
4. Keep Windows build, installer, executable name `ai-usage.exe`, taskbar recovery and existing release paths behaviorally unchanged through extraction.
5. Zero token/cookie content in source control, logs, widgets, notification bodies, diagnostics, crash data or telemetry.

## Component diagram

```text
Android                           iOS (future)
Compose dashboard                 SwiftUI dashboard
Glance widget                     WidgetKit + App Group snapshot
WorkManager (best effort)         BGAppRefreshTask (best effort)
Android credential vault          iOS Keychain
       |                                |
       +---- Application orchestration -+
                          |
                ProviderCapabilityGate
                 (policy/feature flags)
                          |
             Approved provider adapters only
                [CredentialProvider]
                [HttpTransport]
                [QuotaParser]
                          |
                   ai-usage-core (Rust)
                 pure types/normalization
                 alerts/error classification
                          |
             Persisted non-secret snapshot
                  -> widget & dashboard
```

**Boundaries:** Rust core must not read local credential files, call Credential Manager, access Windows HWND or own mobile background tasks. Platform shell owns lifecycle, timers, storage, UI and secrets. Provider adapters translate documented responses into normalized core data, and are disabled unless the provider gate is approved.

## Domain contract v1

```text
ProviderId:
  claude_code | codex | antigravity_gemini | antigravity_claude

ProviderCapability:
  provider: ProviderId
  auth: unknown | permitted | denied
  quota_api: unknown | permitted | denied
  distribution: unknown | permitted | denied
  evidence_urls: URL[]
  verified_on: date?
  constraint_note: text?

QuotaSnapshot:
  schema_version: 1
  provider: ProviderId
  window_id: stable string                  # distinguish 5h/7d/model quota
  remaining_fraction: float?                # finite 0.0 .. 1.0, null unknown
  reset_at_utc: epoch_seconds?
  fetched_at_utc: epoch_seconds?
  state: fresh | stale | auth_required | rate_limited |
         offline | unavailable | unsupported | invalid_response

AlertKey:
  provider + window_id + reset_at_utc + threshold
```

Invariant: `remaining_fraction=0` means positively confirmed exhausted; no observation means `null`, not zero. Keep raw consumed-percentage field out of widget APIs. Reject NaN/infinity/out-of-range instead of silently presenting a plausible number. `fresh` means successful fetch and fresh-enough by policy, not guaranteed real-time. An unavailable provider must not overwrite a valid snapshot with synthetic zero.

Provider outcomes are independent. The app may show a freshly updated Codex value beside stale Claude Code data. Each bar includes its own last-success time and status. Timestamp contract is UTC; locale formatting occurs only in UI.

## Interface contracts (illustrative, not a commitment to binding method)

```text
CredentialProvider:
  auth_state(provider) -> signed_out | available | expiring | expired
  authorize(provider) -> result          # only for approved app-specific scheme
  access_token(provider) -> secret handle
  revoke(provider) -> result

QuotaProvider:
  capability(provider) -> ProviderCapability
  fetch(provider, credential_handle) -> Result<QuotaSnapshot[], ProviderError>

SnapshotStore:
  read(provider) -> cached QuotaSnapshot[]
  commit(provider, successful_snapshots) -> atomic operation
  mark_stale(provider, reason) -> no value destruction

SyncCoordinator:
  manual_sync(provider_set)
  scheduled_sync(provider_set)            # OS best effort
  enforce backoff/dedup/cancellation

WidgetProjection:
  from_cache(snapshots, now) -> UI-only value, no secrets
```

HTTP authorization headers are constructed inside a narrowly scoped transport. Do not return raw tokens to UI, widgets or telemetry. Credential sharing across providers is forbidden unless the provider expressly defines a shared identity (Antigravity response may supply distinct Gemini/Claude buckets but must be modeled separately).

## Android v1

- Kotlin + Jetpack Compose, Glance app widgets, WorkManager, repository/data layer and Android Keystore-backed vault.
- Initial app lives in `apps/android/` and uses mock data; `src/`, root `Cargo.toml`, `build.rs`, current `ci.yml`/`release.yml` untouched.
- Initial UI: four provider cards, per-window remaining %, reset time, provider timestamp, stale/auth/unsupported state, refresh action, accessible labels and 11-language content review as needed.
- First background version: unique periodic work at 15–30 minutes with network constraints and exponential backoff, no foreground service by default. Doze, OEM and force-stop may delay or prevent runs. Widget updates after persisted successful results. Manual refresh coalesces with scheduled refresh.
- Notifications: local threshold (10/20/30 remaining) per provider/window with alert dedup persisted across restarts. No exact-time promise. Ask notification permission as contextually needed.
- Separate vault from non-secret widget cache; protect backups and process logs. Investigate modern Keystore encryption libraries instead of blindly copying deprecated `EncryptedSharedPreferences`.

## iOS future port

- SwiftUI dashboard; WidgetKit timeline reads non-secret App Group snapshot; secure tokens only in Keychain; BGAppRefreshTask is opportunistic.
- Consider lock-screen widget only with privacy controls; no assumed one-minute polls or Live Activity as a general network-polling channel.
- iOS CI requires authorized macOS/Xcode runner or hosted macOS plan. Existing Windows self-hosted runner cannot build/sign iOS apps.

## Refresh flow and error handling

```text
OS scheduler/user action
 -> SyncCoordinator checks provider capability + authorization + dedup budget
 -> provider fetch with bounded timeout and per-provider rate limit
 -> validate body/schema and normalize in core
 -> atomic per-provider snapshot commit and fresh timestamp
 -> widget projection + notification threshold evaluator
 -> if failure: classify (auth, rate-limit, offline, server, malformed)
 -> retain previous snapshot + mark stale + next eligible retry
```

Retry policy: honor documented `Retry-After` seconds or HTTP-date, cap attempts, backoff with jitter, cancellation aware; do not brute-force 401/403 or provider policy refusals. Avoid logging raw response body or error strings that may contain sensitive identifiers.

## Windows compatibility / extraction plan

- Step A: add core crate with pure functions + golden fixture tests; wire no callers initially.
- Step B: Windows adapter invokes core normalization for one provider behind tests; compare existing UI display using fixtures.
- Step C: migrate all four logical display identities; confirm unchanged alert thresholds, timestamps, language labels and no changed credential source selection.
- Step D: only after PR #10 main integration + independent successful Windows release-equivalence test, consider moving Windows source tree into `apps/windows`.
- Maintain windows-only `poller.rs` credential loaders and Win32 APIs outside core; do not copy token parsing shortcuts to Android.

## CI and release boundaries

- Keep existing Windows workflows unchanged during mock scaffold. New `android-ci.yml` with `paths` filters for `apps/android/**`, `crates/ai-usage-core/**`, shared relevant workflow/config; avoid unexpected release triggering.
- Tests: `cargo test` pure core on eligible Windows/portable targets; Gradle unit/lint/test; device tests when authorized; build provenance exact SHA.
- Android release signing keys are supplied through approved CI secret management, never committed. Production Play distribution and iOS signing are separate explicit approvals. Refer to [artifact-release policy](https://github.com/fiverocksgames/devops-standards/blob/main/ci/ARTIFACT_RELEASE_POLICY.md) rather than copying it.
- Windows regression gate: unchanged `ai-usage.exe` name/product metadata, install/uninstall/update behavior, WSL/Win32 credential discovery and taskbar recovery. PR #10 review/release is its own change.

## Verification checklist and stop gates

| Phase | Deliverable | Gate |
|---|---|---|
| 0 | ADR + architecture docs | review and merge documentation |
| 1 | Android mock dashboard/widget | screenshots, manual state/error UX, local unit tests; **no production credentials** |
| 2 | Rust domain core | fixture tests + Windows non-regression |
| 3 | Provider auth P0-P4 | documented approved registration/quota contract; otherwise mock only |
| 4 | Background/alerts | Doze, device idle, reboot, offline and battery tests; stale/alert dedup |
| 5 | External distribution | store rules, privacy notice, signed builds, accessible UX, permissions |

Acceptance scenarios: no data (— not 0%), 10% remaining, zero remaining, reset boundary, malformed 120%, provider 401/429, mixed provider success, date/locale shifts, DST, force-stop, logout while widget exists and installation upgrade. Keep CI unit tests deterministic; run timed OS scheduler tests on real devices instead of assuming interval settings imply execution.

## Explicit uncertainties and follow-up decisions

- Official third-party quota APIs and token issuance are **unverified** for all four bars. See provider matrix.
- Rust ↔ Kotlin/Swift FFI is deferred until measured shared core value; domain layer can initially expose serialization fixtures instead of premature UniFFI/JNI.
- Actual min Android SDK, supported OS distribution, app identifier, iOS min deployment target and signing decisions require product release planning.
- Public repository vs private mobile development: re-evaluate if permissions, secrets or license obligations differ.
- SDK/OS/API restrictions can evolve. Reconfirm official docs before implementation and store submission.
