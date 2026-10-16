# Provider Authentication & Quota API Capability Matrix

Date: 2026-10-16 (KST)
Status: **research / unapproved**. Official documentation checked; no live authentication, security audit, legal approval, or store submission.
Previous: [mobile landscape](./mobile-oss-landscape-2026-10-15.md), [Android source audit](./android-oss-source-audit-2026-10-15.md).

## Executive decision

**Do not implement or distribute direct mobile credential capture or third-party subscription-quota requests until the applicable provider has an explicitly supported/authorized authentication AND quota endpoint path.** Technical feasibility in open-source apps is not authorization. No provider is classified as approved for the four required personal-plan quota bars as of this review.

Distinguish:
1. user authentication/login support;
2. authorization to obtain quota fields for the same consumer/CLI plan;
3. documented endpoint/schema and refresh/revocation;
4. rights to publish a third-party Android/iOS client using those interfaces.

A positive answer to #1 does not imply #2–4.

## Capability matrix

| AI Usage target | Existing Windows implementation evidence | Verified official adjacent capability | Direct third-party mobile quota authorization | Decision |
|---|---|---|---|---|
| **Claude Code** | Local Claude CLI credentials; `api.anthropic.com/api/oauth/usage` | Claude Help Center describes subscription authentication and says the preferred approach for third-party tools is API-key auth via Console or supported cloud provider | **Not established** for reading Claude Code subscription limits through its CLI token/usage endpoint | **UNKNOWN / GATED** |
| **Codex** | Local Codex auth; `chatgpt.com/backend-api/wham/usage` | OpenAI documents **Sign in with ChatGPT** for open-source apps and eligible ChatGPT-plan **AI requests** via approved routing. OpenAI Platform provides separate API organization usage endpoints. | **Not established** that Sign in with ChatGPT OAuth tokens can be used at `wham/usage` or that the 5h/weekly Codex quota is exposed via a supported public API. | **UNKNOWN / GATED** |
| **Antigravity Gemini** | Windows Credential Manager `gemini:antigravity`; Cloud Code endpoints | General Google authentication and Gemini API information are distinct products/contracts | **Not established** for Antigravity IDE subscription quota and third-party mobile sign-in | **UNKNOWN / GATED** |
| **Antigravity Claude** | Same Antigravity credential and quota responses; separate displayed model quota | A Claude-named model inside Antigravity does **not** imply Anthropic Claude Code usage API entitlement | **Not established**; requires Antigravity-specific contract and model-bucket identity | **UNKNOWN / GATED** |

**Not approved ≠ prohibited.** It means required affirmative evidence was not located in this bounded review. Do not infer a categorical provider ban.

## Primary official evidence

1. [Anthropic Claude sign-in Help](https://support.claude.com/en/articles/13189465-log-in-to-your-claude-account): subscription use, and preference for Console API keys / supported cloud providers for third-party tools. This is stronger policy guidance than observing existing apps using session cookies.
2. [OpenAI Sign in with ChatGPT quickstart](https://developers.openai.com/siwc/quickstart) and [overview](https://developers.openai.com/siwc): an open-source OAuth/client-registration pathway exists for eligible AI requests; this is a noteworthy improvement over treating all third-party sign-in as undocumented.
3. [OpenAI SIWC preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations), [error recovery](https://developers.openai.com/siwc/token-sharing-open-source/errors-and-recovery): scope and routing are constrained; an app-specific usage limit does not necessarily mean the user's whole subscription quota is exhausted. No public Codex 5h/7d entitlement query was verified in those documents.
4. [OpenAI Platform Usage API](https://platform.openai.com/docs/api-reference/usage/moderations_object): documented organization API consumption metrics, **not** evidence of ChatGPT subscription/Codex reset-window quota.
5. [Apple App Review Guidelines 5.2.2](https://developer.apple.com/kr/app-store/review/guidelines/): ensure authorization for content/access to third-party services, which may need supporting authorization evidence.
6. [Apple review submission requirements](https://developer.apple.com/help/app-review/before-submitting-for-review/complete-review): third-party services and required demonstration/reviewer information should be declared.
7. [Current Windows implementation](https://github.com/fiverocks-dev/ai-usage-monitor/blob/main/src/poller.rs): proves how desktop app obtains data, **not** a provider's external authorization.

## Provider-specific findings and next evidence requests

### Claude Code

- The Windows CLI OAuth token and the `/api/oauth/usage` path are implementation details.
- OpenUsage's `claude.ai` sessionKey cookie is **a different authentication scheme**; cannot use its success to validate Claude Code CLI quota equivalence.
- API token-spend metrics and consumer/Claude Code 5h/7d quota are not interchangeable.
- Ask Anthropic (or find authoritative published integration terms) whether a read-only, mobile third-party quota-monitoring client is permitted; supported login/app registration, scope, usage endpoint, reset timestamps, rate limit, storage and refresh policies.
- Without affirmative evidence, production adapter stays disabled.

### Codex

- **New positive discovery:** Official Sign in with ChatGPT flow for open-source apps, including registration and tokens for eligible requests; do not borrow the static client ID found in OpenUsage.
- **Remaining gap:** permission and documented response contract for `chatgpt.com/backend-api/wham/usage`, including both current 5h and weekly windows. The Platform organization's usage endpoints measure API activity and require different credentials; they do not satisfy this product requirement.
- Obtain exact scope/audience and provider approval for a read-only quota app. Do not infer quota window values from responses to inference requests or app-specific 429 errors.

### Antigravity Gemini / Claude

- Google Gemini API limits, Gemini CLI quota, and Antigravity IDE quotas require separate evidence. The current Windows reader relies on Antigravity credentials and Cloud Code related endpoints.
- Need Google Antigravity documentation naming official app registration, scope, read-only quota API, model/bucket taxonomy and behavior for Gemini and Claude independently.
- Desktop bridge is a **potential** technical alternative, not a ToS override: desktop must obtain and disclose data in an authorized manner; authenticate/pair devices; send only normalized read-only quota snapshots and no raw tokens. Do not adopt remote-control/CDP/file-access features.

## Product/implementation gates

| Gate | Acceptance evidence | Failing disposition |
|---|---|---|
| P0 Third-party data authorization | Explicit terms or written permission for mobile third-party quota reader | provider marked `unsupported_pending_policy`; no live adapter |
| P1 OAuth / auth ownership | App-specific client registration or explicit allowed public client and grant; PKCE/device flow where applicable | no user login UI for provider |
| P2 Endpoint contract | Published/authorized quota endpoint and field semantics, reset-time and rate limits | do not show speculative % or reverse-engineer private API |
| P3 Token security | Secure storage, rotation/revoke, logout, backup exclusions, redacted diagnostics | no production credential persistence |
| P4 OS distribution | Apple 5.2.2/store privacy and Google Play data/background declarations reviewed | internal research/demo only |
| P5 Reliability | Mock and device tests for 401/403/429/offline, partial results, stale widget cache and background | hold public release |

### Preferred data contract

`ProviderCapability { provider_id, auth_status, quota_api_status, distribution_status, proof_urls, checked_at, limitations }`

`QuotaSnapshot { provider_id, window_id, remaining_fraction?, reset_at_utc?, fetched_at_utc?, status }`

Do not collapse provider capability with quota runtime state. `unknown` != `forbidden`; `stale` != `zero`; API usage != consumer subscription quota.

## Safe next engineering step

Only create mock-based `QuotaSnapshot`, alert-policy tests, and Android Compose/Glance demo while policy is unresolved. If adding real adapters, restrict to explicitly permitted endpoints and app-specific registration. Before any source changes, reconcile PR #10 and existing Windows tests and use a separate implementation branch.

## Limitations / verification notes

- This is **bounded documentation research**, not proof of absence across all possible programs/private partner docs. Do not claim a provider definitively lacks an official endpoint.
- No live access to user credentials, subscription accounts, provider support channels, developer consoles or App Store review records was performed.
- Public official documentation cited above establishes adjacent capabilities and review expectations, **not** explicit provider authorization for the four required bars. Every production integration remains pending.
