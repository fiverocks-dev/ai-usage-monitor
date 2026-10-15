# AI Usage Mobile — 공개 유사 프로젝트 기술 조사

- Date: 2026-10-15 (KST)
- Status: Research / decision support; no mobile implementation approved
- Product: `fiverocks-dev/ai-usage-monitor`
- Scope: Android/iOS, quota, credential, widgets, background, licensing, architectural reuse
- Evidence: Public repository READMEs and selected repository trees checked on 2026-10-15. **Not** an audit of every source file, build, credential flow, or store approval.
- Governing standards: [devops-standards](https://github.com/fiverocksgames/devops-standards); unresolved specifications: [spec-underflow](https://github.com/fiverocks-dev/spec-underflow). Consult shared tools policy before adding reusable automation.

## Executive recommendation

**First choice:** Treat [OpenUsage](https://github.com/maplenk/claude-usage) as the Android reference implementation; do **not** adopt it wholesale or assume its credential flows are permitted. Build an Android-first AI Usage app with Kotlin/Compose, platform-specific widgets and credential storage, and an extracted pure-Rust quota domain core only where this demonstrably reduces maintenance.

**Alternative:** A companion/bridge architecture inspired by [AntigravityMobile](https://github.com/AvenalJ/AntigravityMobile) if direct mobile provider authentication cannot be documented as permitted. This sacrifices standalone/mobile offline-PC use, needs trusted pairing and secure connectivity, and may cost zero in hosted infrastructure but not in always-on PC power/maintenance.

**Hard gate:** A GitHub implementation proves technical possibility, *not* OAuth-client authorization, public API stability, provider Terms of Service compliance, or App Store / Play approval. Do not ship real third-party sign-in or imported refresh tokens until each provider is assessed.

## Baseline: current AI Usage Windows code

At review, `main` is a single Rust package (`Cargo.toml` 1.9.1) with `src/models.rs`, `src/poller.rs`, `src/localization/`, `src/native_interop.rs`, `src/window.rs`, `src/tray_icon.rs`, and `src/updater.rs`. PR [#10](https://github.com/fiverocks-dev/ai-usage-monitor/pull/10) was **open/unmerged at earlier check on 2026-10-15**; recheck immediately before any refactor.

`poller.rs` combines provider HTTP calls/parsers with Windows-specific process execution, local CLI/WSL credential discovery, and Windows Credential Manager `CredReadW`. Relevant current endpoint examples: Anthropic `/api/oauth/usage`, Codex `chatgpt.com/backend-api/wham/usage`, and Cloud Code/Antigravity endpoints. These are observations of existing code, **not** an endorsement for third-party distribution.

`models.rs` contains `UsageSection { percentage, resets_at }` and `AppUsageData` with `claude_code`, `codex`, `antigravity`, `antigravity_claude`. Normalize semantics to *remaining* fraction; preserve provider/window identity, fetch time, stale/auth/rate-limit status, and reset time. Exclude Windows credential reading, HWND/taskbar, winget updater and installer from mobile core.

## Repository findings

| Project | Stack / delivery | Confirmed by repository documentation | Constraints & adoption |
|---|---|---|---|
| [maplenk/claude-usage (OpenUsage)](https://github.com/maplenk/claude-usage) | Android native; Compose / widget-first; `app/`, `ios/` present in repo tree (do not infer iOS parity) | Claude session+weekly, Codex weekly, Grok weekly; mobile-direct calls; home widgets; background WorkManager one-time chain + 15m periodic safety net; Codex device-code sign-in + refresh with Keystore-backed encrypted preferences; widget reads Glance cache | Claude requires **user-supplied claude.ai sessionKey**; Codex WHAM is explicitly described as non-public contract; short intervals not guaranteed by OS; cannot assume supported Antigravity or Codex 5h parity |
| [hyunnnchoi/CodexBar-android](https://github.com/hyunnnchoi/CodexBar-android) | Android Kotlin/Compose, Retrofit/OkHttp, WorkManager, Hilt | Claude/Codex/Gemini quota, Quick Settings tile, persistent notification, encrypted credentials, APK | README states Claude refresh token repeatedly fails; instructs manual extraction of CLI tokens and Gemini CLI OAuth client values; treat credential and client-secret copying as **high-risk reference, not production pattern** |
| [GuangjieYu1/ai-quota-dashboard](https://github.com/GuangjieYu1/ai-quota-dashboard) | Android, fork-derived UI | Multiservice dashboard concept described in public listing | Verify exact current source, provenance, license, authentication and maintenance before adoption; secondary reference only |
| [stanleyswiss/claude-widget-ios](https://github.com/stanleyswiss/claude-widget-ios) | SwiftUI + WidgetKit; `Core/`, `App/`, `Widget/`; MIT per repository | Claude & Codex widgets, home/lock screen; WKWebView Claude session with httpOnly cookies; App Group snapshot; best-effort BGAppRefreshTask; Keychain token store | Explicitly designed for **personal sideload**, not App Store. Google auth blocked inside embedded webview; user pastes Codex CLI auth content; cookie/Cloudflare and best-effort refresh limitations. Excellent widget/cache architecture, **not** evidence of distributable login |
| [AvenalJ/AntigravityMobile](https://github.com/AvenalJ/AntigravityMobile) | Android Kotlin/Compose client + Node desktop bridge + Rust desktop helper; MIT per repository | Antigravity model quota via Windows bridge/IDE; Tailscale/private pairing; desktop remote features | Quota monitoring Windows-only by author's statement; PC/IDE/bridge must be available. Attack surface much wider than quota-only app (CDP, files, screenshots, remote input): do **not** reuse bridge wholesale |
| [Hephaestus-DevKit/codex-bar](https://github.com/Hephaestus-DevKit/codex-bar) | Android-related variant | Background/OEM reliability candidate from earlier search | Secondary candidate; code, current availability and exact claims **not independently audited** |
| [steipete/CodexBar](https://github.com/steipete/CodexBar) | Desktop reference | Multi-provider quota patterns | Desktop/provider reference, not mobile credential authorization |
| [niflheim77/AICycle-Widget](https://github.com/niflheim77/AICycle-Widget) | Desktop widget reference | Antigravity comparison candidate | Verify implementation and license before reuse |
| [CodeZeno/Claude-Code-Usage-Monitor](https://github.com/CodeZeno/Claude-Code-Usage-Monitor) | Windows upstream | Existing AI Usage ancestry | Track attribution and original license |

### Important comparability caveats

- **Gemini CLI ≠ Antigravity Gemini**; both may be Google-related but quota sources/scopes differ.
- **Antigravity Claude ≠ Claude Code**; never collapse their quotas.
- Codex weekly-only in OpenUsage's documented presentation is not automatically feature parity with AI Usage's 5h + weekly display.
- A repository having an `ios/` directory does not establish a shipping iOS widget.
- 'Real time' in project marketing does not establish reliable minute-level background polling.

## Credential/auth pattern comparison

| Mechanism | Reference | Technical utility | Production risk / gate |
|---|---|---|---|
| User-pasted Claude session cookie | OpenUsage | Direct quota requests | Cookie exposure, expiration, Cloudflare, service policy, store review; **do not default** |
| Codex device code + refresh | OpenUsage | Mobile login without copying Windows auth file | Check **officially authorized client ID, scopes, redirect/device-flow eligibility** and quota endpoint's public support; never lift another application's OAuth client without permission |
| CLI access/refresh-token copy | CodexBar Android / iOS widget | Fast personal PoC | Refresh-token exfiltration and invalidation, ToS ambiguity; not recommended for distributed app |
| `WKWebView` first-party session reuse | iOS widget | Handles browser cookies for personal sideload | Embedded login rules, identity provider restrictions, Cloudflare expiry, app-store concerns |
| Desktop bridge over private network | AntigravityMobile | Avoids direct phone credential acquisition; can leverage logged-in desktop | Always-on PC, pairing, network security, replay/authorization, disclosure; prefer **quota-only read-only endpoint** if selected |
| Official OAuth PKCE + documented quota API | Preferred target | Maintainable and store-reviewable | **Unverified** per provider and account plan; must obtain documentation/permissions |

**Go/no-go evidence for EACH of Claude Code, Codex, Antigravity Gemini, Antigravity Claude:**

1. Official endpoint documentation and scope coverage for *subscription quota* (not billing/token spend), or explicit authorization to use the endpoint;
2. App registration/approved OAuth mechanism for a third-party mobile app (PKCE/device flow where supported);
3. Refresh/revocation and token binding semantics;
4. Rate-limit, retries/backoff and forbidden behavior;
5. Provider brand/IP and distribution permissions;
6. Actual account-class test with evidence scrubbed of secrets.
   
Track status as `approved` / `prototype-only` / `blocked` / `unknown`, default `unknown`; never interpret missing evidence as permission.

## UX and OS feasibility

**Android:** Compose dashboard + Glance home widget + manual refresh + clearly displayed 'last successful sync'; WorkManager periodic minimum **15 minutes** and still non-exact. OneTimeWork chains for faster updates are *best effort*, not a durable 1/5-minute guarantee; OEM/Doze may defer. A persistent notification or foreground service solely to poll quota has battery, foreground-service-type and Play review costs. Separate OS widget update schedule from network quota refresh.

**iOS:** SwiftUI + WidgetKit home/lock widget with App Group **non-secret snapshot**; BGAppRefreshTask and timeline budgets are system scheduled, not guaranteed 1/5/15-minute polling. Live Activity/Dynamic Island are not an unrestricted background fetch channel. Best-effort refresh and explicit stale data UI required.

**Cross-platform product states:** Fresh (timestamp), stale, unauthenticated, token expired, provider unsupported, provider error, quota exhausted, rate-limited; do not show unknown as `0%`. Keep thresholds (10/20/30% remaining) local and dedupe by provider + window + reset key; alerts may be late without push/backend.

**Security:** Android Keystore-protected storage; iOS Keychain with suitable accessibility; no credentials in widget preferences/App Group; no secrets in logs or crash traces; exclude secrets from backup unless deliberately documented; revocation, compromised-device policy, consent and clear privacy notice. Avoid arbitrary credential export/import and embedded OAuth webviews where disallowed.

## Reuse/implementation decision

| Component | Decision |
|---|---|
| Existing Rust usage models/parsing | **Extract/refactor** portable pure-domain logic; test remaining/used semantics and timestamp conversion |
| Windows CLI/WSL credentials, Win32 interop, HWND/tray/watchdog | **Windows-only** |
| Android dashboard/widget UI | **Develop AI Usage identity**; inspect OpenUsage as UX and Glance/WorkManager reference; do not copy code pending license and dependency audit |
| Android credential subsystem | **New platform adapter**, direct officially permitted authorization only |
| iOS WidgetKit/App Group/cache boundary | **Reference** stanleyswiss design; new app implementation and approved auth |
| Antigravity mobile access | **Validate direct authentication first**; otherwise optional **narrow, read-only local bridge** inspired by AntigravityMobile, not remote desktop clone |
| Mobile auto-updater | **Replace** with platform store distribution/update model |
| Localization strings | **Reuse content**; platform locale detection separate |
| Settings and notifications | **Reuse domain rules**, native storage/permissions/delivery separate |

Do **not** fork any of these projects as the default. Fork only after (1) confirming license and third-party dependencies, (2) validating provider auth/ToS, (3) diffing functionality against four independent AI Usage bars, (4) evaluating tests/activity, and (5) confirming upstream patch maintenance strategy.

## Suggested decision record and next actions

1. Record provider capability matrix under `docs/research/provider-auth-matrix.md` in a subsequent evidence pass; attach official provider docs, not just sample code.
2. Audit OpenUsage's credential module, network host allowlist, widget cache, WorkManager behavior, token redaction and tests at pinned SHA. Include cold-start, expired credential, offline and Doze tests.
3. Audit CodexBar Android specifically for broken Claude refresh, token handling and Gemini client-ID/secret acquisition.
4. Audit iOS widget Keychain/App Group boundaries and extension refresh on device; assess App Store acceptance separately from sideload.
5. Audit AntigravityMobile only as a **bridge pattern**; document minimal read-only API, pairing, no CDP/remote-control exposure, rate-limits, TLS/private-network assumptions.
6. Define provider-agnostic `QuotaSnapshot` with `provider`, `window`, `remaining_ratio`, `reset_at_utc`, `fetched_at_utc`, `status`; extract parser tests before changing Windows app.
7. Record architecture ADR: **primary Android-first native + Rust core**, fallback **read-only desktop companion**; no backend by default.
8. Complete a source-level implementation/license audit at pinned commit SHA before copying any code; this report is *research*, not a legal/security clearance.

## Sources checked

- https://github.com/maplenk/claude-usage (README, repository structure)
- https://github.com/hyunnnchoi/CodexBar-android (README, repository structure)
- https://github.com/stanleyswiss/claude-widget-ios (README, Core/App/Widget tree)
- https://github.com/AvenalJ/AntigravityMobile (README, android/rust-helper/bridge tree)
- https://github.com/GuangjieYu1/ai-quota-dashboard (candidate; shallow review)
- https://github.com/Hephaestus-DevKit/codex-bar (candidate; shallow review)
- https://github.com/steipete/CodexBar
- https://github.com/niflheim77/AICycle-Widget
- https://github.com/CodeZeno/Claude-Code-Usage-Monitor
- https://github.com/fiverocks-dev/ai-usage-monitor (Cargo.toml, README, src/models.rs, src/poller.rs, src/localization/mod.rs, src/updater.rs)
- https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work
- https://developer.android.com/develop/ui/views/appwidgets/advanced
- https://developer.apple.com/documentation/backgroundtasks/bgapprefreshtask
- https://developer.apple.com/documentation/widgetkit/keeping-a-widget-up-to-date
- https://developer.apple.com/app-store/review/guidelines/

### Evidence limitations

Public GitHub code may change without notice. This report relies principally on explicit README claims and sampled repository layout, not runtime validation. Exact latest commit SHA, star count, last commit timestamps, release provenance, license files and dependencies for **every** candidate were not exhaustively verified. They are omitted rather than invented. Recheck before implementation. No provider approval, security assurance, functionality parity or store submission success is claimed.
