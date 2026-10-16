# ADR-0001: Staged mobile monorepo for AI Usage

- Date: 2026-10-16
- Status: **Accepted as proposed architecture; implementation not started**
- Repository: `fiverocks-dev/ai-usage-monitor`
- Decision owners: project maintainers
- Research: [OSS landscape](../research/mobile-oss-landscape-2026-10-15.md), [Android source audit](../research/android-oss-source-audit-2026-10-15.md), [provider access matrix](../research/provider-auth-matrix-2026-10-16.md)

## Context

The current Windows Rust binary `ai-usage.exe` embeds a native child window in the taskbar. Four display identities are required: Claude Code, Codex, Antigravity Gemini, Antigravity Claude. Desktop credential discovery depends on local CLI files, WSL and Windows Credential Manager; none is directly portable to mobile sandboxes. The app currently has one root `Cargo.toml`, Windows-only `src/`, `build.rs`, `ci.yml`, and `release.yml`.

PR [#10](https://github.com/fiverocks-dev/ai-usage-monitor/pull/10), taskbar recovery, was open on 2026-10-16. Large code moves must not be combined with it.

## Decision

**Keep one product monorepo now.** Deliver an Android-first native app (Kotlin/Compose + Glance) with mock quota data and manual refresh UX first; add iOS (SwiftUI + WidgetKit) only after platform/product gates. Extract pure Rust normalization/model/alert logic to `crates/ai-usage-core` **incrementally** after evidence of useful sharing. Retain platform-specific credentials, polling orchestration, widgets, and UX in their apps.

Use **local-first/no owned backend by default**. All real third-party quota adapters are approval-gated; current API discoveries do not constitute a grant to redistribute clients.

Do not migrate the Windows package structure as part of the initial Android scaffolding. Only move the Windows sources to `apps/windows` after a separate build-equivalence migration with exact executable identity, installer, CI and releases verified.

## Intended logical layout (future; not created by this ADR)

```text
ai-usage-monitor/
  apps/
    windows/                    # later migration from src/, build.rs and root Cargo
    android/                    # Kotlin/Compose, Glance, WorkManager
    ios/                        # future SwiftUI, WidgetKit, BGTask
  crates/
    ai-usage-core/              # pure domain, no OS APIs or secrets
    ai-usage-providers/         # approved protocol parsing/adapters ONLY
  docs/
    architecture/
    research/
  .github/workflows/
    ci.yml                      # existing Windows CI remains
    release.yml                 # existing Windows release remains
    android-ci.yml              # later; path-scoped
    ios-ci.yml                  # later; macOS runner required
```

## Alternatives rejected for now

1. **Separate `fiverocks-dev/ai-usage-mobile` now** — gives independent permissions/releases but makes common domain work, issue/ADR traceability and API-version compatibility harder before the shared boundary is known.
2. **Flutter Android+iOS simultaneously** — common UI, but widgets, keychain/keystore and OS background work still need native adapters; adds plugin/FFI coordination.
3. **Kotlin Multiplatform full rewrite** — adds a second shared runtime alongside working Rust and premature migration cost.
4. **Desktop companion/relay as default** — may avoid mobile token import but requires a running PC, pairing/security surface and possible connectivity operations. Preserve as gated fallback, not baseline.
5. **Immediate wholesale Rust workspace migration** — high Windows regression and installer/release risk while PR #10 remains separate.

## Non-goals

No real provider login, token/cookie import, undocumented quota calls, server deployment, Windows taskbar rewrite, near-real-time background guarantees or public mobile release in the first phase.

## Consequences

- Pros: one domain/UX roadmap, shareable documentation/tests, minimum migration risk, zero hosted service baseline.
- Cons: platform-dependent CI/release ownership in one repo, need per-path protections, eventual multi-language build complexity, potential future repository split.
- Current repository is **public**; no credentials, private SDK packages, signing keys, Apple profiles or sensitive support evidence can be committed. A dedicated private repo becomes preferable if access/publishing/security boundaries genuinely diverge. Public repo visibility is not a substitute for mobile app secret safety.
- Platform-specific CI must preserve exact-SHA artifact provenance and existing Windows release identity per [DevOps artifact policy](https://github.com/fiverocksgames/devops-standards/blob/main/ci/ARTIFACT_RELEASE_POLICY.md).

## Reconsider separation when

Independent product name/ownership or approval path emerges; Windows/mobile release cadences or access permissions materially conflict; provider/client implementations share little tested domain code; public/private boundaries differ; or CI becomes operationally coupled despite path filtering. Document a new ADR instead of silently changing ownership.

## Release gates

- G0: PR #10 resolution + baseline Windows build/CI evidence.
- G1: explicit provider authorization and subscription quota API evidence per provider; missing approval = disabled provider.
- G2: Android mock dashboard, widget, stale/error states and accessible localization.
- G3: portable Rust core with unit tests; Windows behavior/build regression gate.
- G4: platform background/energy/credential security and private device tests.
- G5: store/privacy/third-party policy review before distribution.

## Source of truth and standards

Project-specific ADR lives here. Reusable governance is linked, not copied: [devops-standards](https://github.com/fiverocksgames/devops-standards). Use [spec-underflow](https://github.com/fiverocks-dev/spec-underflow) for unexpected blockers and [shared tools](https://github.com/fiverocks-dev/tools) for reusable skills. This ADR does not authorize implementation or bypass provider terms.
