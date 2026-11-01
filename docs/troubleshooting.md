# Troubleshooting AI Usage

## Taskbar error labels

AI Usage keeps authentication failures separate from transient service failures:

| Simplified Chinese | Other languages | Meaning | Recommended action |
|---|---|---|---|
| `!` | `!` | Enabled service credentials are missing or expired | Sign in with the relevant CLI/app, then refresh AI Usage. Claude Desktop login does not count as Claude Code CLI login. |
| `网络` | `NET` | Network or TLS connection failed | Check connectivity, VPN, proxy, and firewall settings |
| `限流` | `429` | Provider rate limit | Wait for the provider retry window; AI Usage retries with backoff |
| `服务` | `5XX` | Provider service failure | Wait and retry; check provider status if it persists |
| `错误` | `ERR` | Invalid or unsupported response | Enable diagnostics and inspect the log |

Authentication failures pause provider polling until the credential source changes, preventing repeated login notifications. Transient failures use exponential backoff up to the configured refresh interval.

## Diagnostic log

Run:

```powershell
ai-usage.exe --diagnose
```

The log is written to `%TEMP%\ai-usage.log`. It includes:

- application version and executable path
- direct or WinGet install channel
- provider failure category and retry delay
- window creation, taskbar placement, and relaunch events

The log does not include access tokens, refresh tokens, credential file contents, or API response bodies.

## Antigravity quota or Argon display issues

The preferred quota source is the locally installed, signed-in `agy` CLI. The monitor invokes `agy -p "/usage" --output-format json` and falls back to the authenticated Google API if the CLI is absent or its result cannot be used. The fallback may omit Gemini weekly or Argon quotas.

- Enable **Antigravity** under **Monitored services** to show AG-G (Gemini) and AG-C (Claude/GPT).
- Enable **Monitored services > Argon (7d)** to opt in to AG-A; this is off by default. The **7d** row must also be enabled. Argon has no separate 5h quota.
- Claude/GPT 5h may display `--` when its weekly quota has been exhausted. This reflects the upstream `disabled: true` state, not a missing credential.
- Check CLI installation and version with `Get-Command agy` and `agy --version`. The verified installation used `agy 1.4.10`.
- Compare quota with `agy -p "/usage" --output-format json`. Avoid sharing the unfiltered response envelope if it contains account details.
- Start the app with `--diagnose` and look for `Antigravity CLI quota groups parsed successfully`, a disabled-window message, or a CLI failure/fallback message.

If a regression occurs after the merged PRs #10/#11, open a **new issue** rather than reopening the merged PR. Include sanitized logs, Windows/app/CLI versions, affected model/window, and a screenshot of the visible behavior when helpful. See [Antigravity quota collection](antigravity-quota.md) for design, source limitations, and validation evidence.

## Update failures

Direct installations and portable copies download only the exact `ai-usage.exe` asset and verify it against `ai-usage.exe.sha256` from the same GitHub Release. The updater keeps the previous EXE until the downloaded version has been installed and restarted successfully. If restart fails, the old EXE is restored.

WinGet-managed installations delegate upgrades to WinGet:

```powershell
winget upgrade --id Ray.CodexUsage --exact
```

## Reset local position without deleting settings

Right-click the taskbar component and choose **Settings > Reset Position**. Settings are stored at `%APPDATA%\AIUsage\settings.json`.

## Reinstall while preserving settings

Normal uninstall keeps the settings file. Reinstalling restores the saved language, refresh interval, provider selection, widget visibility, and taskbar position. Use `uninstall.ps1 -RemoveSettings` only when a full reset is intended.
