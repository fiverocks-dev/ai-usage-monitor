# Android OSS Source Audit — OpenUsage vs CodexBar

Date: 2026-10-15 KST  
Status: focused source-level architecture audit (no builds, live auth, test-device runs or policy approval)  
Parent: [Mobile OSS landscape](./mobile-oss-landscape-2026-10-15.md)  
Target: `fiverocks-dev/ai-usage-monitor`

## Decision

**Adopt patterns, not upstream credential code.** Build the AI Usage Android client with Kotlin/Compose and Glance, keep existing Rust domain models/parsers only after extracting Windows-specific IO, and implement provider credentials through a separate approval-gated interface. Prefer 15+ minute best-effort periodic work for background synchronization, manual refresh when foregrounded, and a public, timestamped non-secret widget cache.

Do **not** treat OpenUsage's private Codex OAuth client identifier or Claude session cookie use, CodexBar's copied refresh tokens, or any undocumented `wham/usage` access as approved product interfaces.

## Evidence inventory: direct source inspection

These specific source files were fetched from GitHub (current default branch at read time; source links point to default branch and can move).

### OpenUsage — maplenk/claude-usage

- [SecureCredentialStore.kt](https://github.com/maplenk/claude-usage/blob/main/app/src/main/java/com/qbapps/claudeusage/data/local/SecureCredentialStore.kt) — AndroidX `EncryptedSharedPreferences` + `MasterKeys.AES256_GCM_SPEC`; holds Claude sessionKey and Codex access/refresh/ID tokens, plus Grok tokens. **Boundary:** encrypted-at-rest is useful, but a third-party session cookie still has authorization risk. AndroidX security-crypto maintenance/deprecation status must be evaluated before adopting this precise API.
- [CodexApiService.kt](https://github.com/maplenk/claude-usage/blob/main/app/src/main/java/com/qbapps/claudeusage/data/remote/CodexApiService.kt) — device-code request/poll, authorization-code/token exchange, refresh, and `wham/usage` usage request. A static OAuth client ID is embedded in `CodexApiContract`. **Boundary:** a public client ID in source is not proof that another product can reuse its registration or endpoint.
- [ClaudeApiService.kt](https://github.com/maplenk/claude-usage/blob/main/app/src/main/java/com/qbapps/claudeusage/data/remote/ClaudeApiService.kt) — sends `sessionKey` as a `Cookie` to `claude.ai/api/organizations` and organization usage. **Reject as default app-distribution login model** until provider and store policy evidence exists.
- [WorkManagerScheduler.kt](https://github.com/maplenk/claude-usage/blob/main/app/src/main/java/com/qbapps/claudeusage/worker/WorkManagerScheduler.kt) — one-time sync initial delay + unique-work `REPLACE`; parallel 15-minute periodic fallback `KEEP` under network constraint. Comments say fallback 'guarantees' refresh cadence; this is **not an OS guarantee**.
- [UsageSyncWorker.kt](https://github.com/maplenk/claude-usage/blob/main/app/src/main/java/com/qbapps/claudeusage/worker/UsageSyncWorker.kt) — quota sync, widget push, reset notification, self-scheduled next task; returns `Result.success()` after enqueueing even if provider failed. **Risk:** retry/backoff semantics are not explicitly delegated to WorkManager, transient and terminal failures need different handling; overlapping periodic and one-shot jobs demand dedupe.
- [WidgetDataPusher.kt](https://github.com/maplenk/claude-usage/blob/main/app/src/main/java/com/qbapps/claudeusage/widget/WidgetDataPusher.kt) — writes per-service quota metrics + fetched timestamp to Glance state, then updates widgets. **Keep:** explicit timestamp and cached display snapshot. **Improve:** retain status/error/staleness and all four AI Usage provider identities separately.

### CodexBar Android — hyunnnchoi/CodexBar-android

- [EncryptedPrefsManager.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/security/EncryptedPrefsManager.kt) — `EncryptedSharedPreferences` stores provider credentials; for Gemini also persists OAuth client ID and client secret. **Risk:** do not normalize moving a CLI client secret to a distributed APK; investigate provider approval.
- [WorkManagerInitializer.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/workmanager/WorkManagerInitializer.kt) — periodic quota and token workers with configurable interval clamped at 15 minutes, exponential backoff, unique periodic work. **Keep:** one scheduling owner, unique jobs, network constraint; **improve:** token-refresh strategy should follow each provider's documented expiry/rotation behavior, not a global blind cadence.
- [QuotaRefreshWorker.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/workmanager/QuotaRefreshWorker.kt) — concurrent provider fetch, cache successful provider data, widget/notification/tile refresh, WorkManager retry when all fail. **Keep:** provider failure isolation and cached UI. **Risk:** when a provider fails but others succeed, the failed one's previous state needs explicit stale/error marking rather than merely retaining an old percentage.
- [TokenRefreshWorker.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/workmanager/TokenRefreshWorker.kt) — Claude, Codex and Gemini refresh paths; Codex refresh is attempted proactively because no expiry field; some terminal Codex failures delete saved credentials. **Risk:** possible unnecessary refresh calls; deletion should be based on structured, authoritative terminal error classification and user-visible reauthentication status.
- [CodexRepositoryImpl.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/data/CodexRepositoryImpl.kt) — fetch, 401 -> refresh -> retry, 429 separate, provider result/error object. **Keep:** repository interface and per-provider error classification. **Improve:** no raw error payload or exception messages should reach sensitive telemetry/logs.
- [RetryInterceptor.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/network/RetryInterceptor.kt) — retries 429 (up to 3) using `Retry-After` numeric seconds or capped exponential wait, blocking `Thread.sleep`. **Do not copy unchanged:** needs HTTP-date `Retry-After`, cancellation-aware delays, jitter, per-provider rate-limit budgets, and bounded retries.
- [WidgetPrefsManager.kt](https://github.com/hyunnnchoi/CodexBar-android/blob/main/app/src/main/java/com/codexbar/android/core/widget/WidgetPrefsManager.kt) — separate non-encrypted widget preferences + quota cache and update timestamp. **Keep:** cache and tokens separated. **Improve:** `getCachedUtilization` defaults to 0, which is ambiguous versus true zero usage; use nullable/typed missing state.

## Comparative decision matrix

| Domain | OpenUsage source observation | CodexBar source observation | AI Usage rule |
|---|---|---|---|
| Credentials | Keystore-backed encrypted prefs; session cookies and OAuth tokens | Keystore-backed encrypted prefs; manual token/client values | Platform vault interface; no credential export, no embedded unapproved client; no secrets in widget |
| Codex login | Device-code flow tied to static client ID | CLI-derived credential flows | BLOCK deployment until official third-party OAuth and quota contract verified |
| Claude login | `sessionKey` cookie for `claude.ai` | OAuth access/refresh handling but reported refresh issue | Official provider-approved path only; no copy-pasted cookies in production |
| Sync scheduling | Sub-15m best-effort self-requeue plus 15m fallback | 15m+ periodic WorkManager | First release periodic 15–30m best-effort + manual refresh; no precise 1/5m promises |
| Sync errors | Worker schedules next then returns success | Retry if all provider fetches fail | Classify `auth`, `rate limit`, `offline`, `transient`, `schema`; preserve per-provider state |
| Widget | Glance state and per-provider fetched-at | Plain prefs cache + timestamp | No secrets in widget state, provider/window-specific timestamp and stale status |
| Notification | Reset logic in worker | Quota notification in worker | Threshold 10/20/30% remaining, dedup key `provider+window+reset_at+threshold`; observe platform-delivery uncertainty |
| Gemini | Not same as Antigravity | Gemini CLI credentials and quota | Treat Antigravity Gemini and Antigravity Claude as independent quota identities, shared connector only if response supports both |

## Proposed module boundaries (no code changes in this research PR)

```text
AI Usage Android (Compose / Glance)
  ui/dashboard + widget/view-only-cache + notifications
  application/sync-orchestrator (WorkManager)
  platform/credential-vault + auth-coordinator
  adapters/provider-{claude,codex,antigravity} [ONLY approved adapters]
             |
  ai-usage-core (Rust if extraction proves worthwhile)
    quota-normalization, models, status/error, alerts
             |
  AI Usage Windows-specific adapters untouched during spike
```

### Minimum domain contract

```text
QuotaSnapshot:
  provider_id: claude_code | codex | antigravity_gemini | antigravity_claude
  window_id: provider-defined stable identifier
  remaining_fraction: float? // 0..1, null = unknown, never default zero
  reset_at_utc: timestamp?
  fetched_at_utc: timestamp?
  status: fresh | stale | auth_required | rate_limited | unreachable | unsupported | invalid
```

Provider data may include both consumed/used percent and remaining fraction: normalize once in domain logic, with tests. An expired cached snapshot is not silently presented as current.

## Required tests before implementation decision

1. For each provider, mock `200/401/403/429/5xx`, malformed payload, absent window, midnight reset, clock skew and offline cache; confirm independent provider status.
2. Credentials: token never logged, redaction of URLs/headers/exceptions, invalidation/revocation, Android backup/restore, root threat model.
3. Scheduling: Doze + airplane mode + process termination + boot + manual refresh and network restoration; measure actual inter-poll gap, duplicates and battery.
4. Widgets: missing data ≠ 0%; stale timestamp; per-widget selected services; update after background fetch and logout.
5. Alerts: threshold crossing once per reset epoch, independently by provider/window/threshold; no spurious reset when response missing.
6. Static source/license/dependency check at **pinned SHA**, SBOM, security review, redistribution rights. No upstream code vendoring before review.

## Repository-state and process notes

- PR #10 in `fiverocks-dev/ai-usage-monitor` still open when checked on 2026-10-15; do not conflate this research branch with Windows taskbar recovery.
- Research-only branch `docs/mobile-oss-research-20261015`; no Windows/runtime changes and no OAuth/credential material introduced.
- DevOps policy: [AI Agent Investigation Gate](https://github.com/fiverocksgames/devops-standards/blob/main/common/AI_AGENT_INVESTIGATION_GATE.md). Provider policy uncertainty should be recorded as a decision/blocker, not worked around with borrowed tokens.
- No app builds, live OAuth or battery experiments have been run, and no claim of store approval is made.
