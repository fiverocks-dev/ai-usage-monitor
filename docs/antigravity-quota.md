# Antigravity quota collection and validation

This document records the implementation and evidence merged through [PR #11](https://github.com/fiverocks-dev/ai-usage-monitor/pull/11), with taskbar recovery from [PR #10](https://github.com/fiverocks-dev/ai-usage-monitor/pull/10). Both PRs were merged into `main` on November 1, 2026.

## User-visible behavior

| Taskbar label | Antigravity group | Quota windows | Default |
| --- | --- | --- | --- |
| AG-G | Gemini Models (Gemini Flash, Gemini Pro) | 5h and weekly | Shown when Antigravity is enabled |
| AG-C | Claude and GPT models | 5h and weekly | Shown when Antigravity is enabled |
| AG-A | Gemini 4 Argon | Weekly only | Hidden; opt in via **Monitored services > Argon (7d)** |

The Argon option is saved in `%APPDATA%\AIUsage\settings.json` as `show_argon` and defaults to `false` for existing settings. AG-A requires both Antigravity and the weekly display row to be enabled. The display order defaults to `5h / 7d` and can be switched to `7d / 5h`; order preference is persistent.

The UI represents **remaining quota**; the internal `UsageSection.percentage` represents **used quota**, computed as `(1 - clamp(remaining_fraction, 0, 1)) * 100`. When a five-hour bucket carries `disabled: true`, its remaining fraction is not an actionable limit and the UI shows `--`. In particular, Claude/GPT may disable 5h after exhaustion of its weekly quota.

## Collection order and fallback

1. Resolve `agy.exe` under `%LOCALAPPDATA%\agy\bin\agy.exe` or from `PATH`.
2. Invoke `agy -p "/usage" --output-format json`, with no interactive input, a 10-second command timeout, and a bounded output size.
3. Read the structured JSON at `command.data.groups` and map known groups and windows independently. Do not merge Argon into the ordinary Gemini shared quota.
4. If the CLI is missing, fails, or has unusable/incomplete required groups, fall back to the pre-existing Google Cloud Code / Antigravity API using credentials already supported by the application.

The fallback may expose only the Claude/GPT group or model-level 5h information. **Do not label a model-level fallback as a Gemini weekly quota**, and do not synthesize Argon weekly quota from unrelated buckets. When no Argon data is available, treat its value as unavailable.

The app does not install or reauthenticate `agy`, does not write its credentials, and does not log access tokens or the full CLI JSON response. `agy` may perform authenticated network requests internally.

## Investigation and rejected assumptions

The legacy remote `retrieveUserQuotaSummary` endpoint returned only one Claude/GPT group in the observed environment. A local Antigravity language-server request to `RetrieveUserQuotaSummary` returned three groups but **different remaining percentages** from the visible client UI: Gemini 100/100 versus ~99/99 and Argon 0 versus ~19. Local reset timestamps were similar, but timestamps alone did not validate remaining percentages. The local language-server endpoint therefore was **not** adopted as the app's source of truth.

The `agy` CLI's `/usage` command returned group-level structured data including `remaining_fraction`, `reset_time`, and `disabled`, consistent with the visible usage screen. Example independently supplied diagnostic values:

| Bucket | Remaining quota from CLI |
| --- | --- |
| gemini-weekly | 99.5086% |
| gemini-5h | 99.8906% |
| 3p-weekly | 0% |
| 3p-5h | disabled (a numeric fraction was still present) |
| gemini-argon-wtu-weekly | 18.7304% |

A later Windows diagnostic confirmed `Antigravity CLI quota groups parsed successfully`, detected Claude/GPT's disabled 5h bucket, and read Argon weekly remaining quota at 16.30%. This establishes successful live parsing, not a guarantee of future CLI-version compatibility.

## Validation and status

- [CI #148](https://github.com/fiverocks-dev/ai-usage-monitor/actions/runs/41075222660): Windows formatting, Clippy, tests, release build and dependency audit passed on the merged feature head.
- Windows interactive test: user confirmed that Argon opt-in taskbar display worked.
- PR #11 was merged into `main` as `f32b5b54e78aa3421373c66f01c399fa4b9daab9`; PR #10 was then marked merged and its taskbar-recovery changes are already included.
- Longer-duration automatic refresh and taskbar-position resilience remain operational monitoring concerns; **do not state these were exhaustively proven**.

## Diagnostics

Start with `ai-usage.exe --diagnose`, then inspect `%TEMP%\ai-usage.log`. Expected successful path:

```text
Antigravity CLI quota groups parsed successfully
```

The log may also note a disabled quota window or Argon weekly remaining percentage. For direct comparison in PowerShell:

```powershell
agy -p "/usage" --output-format json |
    ConvertFrom-Json |
    Select-Object -ExpandProperty command |
    Select-Object -ExpandProperty data |
    ConvertTo-Json -Depth 15
```

CLI output may include account information if the entire envelope is shared; share only relevant quota data, not tokens, credentials or full session payloads.

If the CLI is unavailable, verify `Get-Command agy` and `agy --version`, check that it is signed in, and inspect the fallback diagnostics. The implementation was validated with `agy 1.4.10` but does not guarantee every earlier or future CLI release.

## Follow-up policy

New regressions after merge belong in a **new GitHub issue**, with symptoms, reproducible steps, app commit/version, `agy --version`, affected model/window, sanitised diagnostic lines and whether the app was on an interactive Windows taskbar. Use a separate fix PR and normal CI plus interactive Windows validation. Do not retroactively reopen the merged PRs simply to manage a newly discovered defect.

This is repository-specific documentation; overarching work-documentation guidance remains in [DevOps Standards](https://github.com/fiverocksgames/devops-standards/blob/main/common/WORK_DOCUMENTATION_HANDOFF_POLICY.md).
