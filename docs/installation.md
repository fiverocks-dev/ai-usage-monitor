# AI Usage installation model

AI Usage remains a single native Windows executable. Installation only places the executable and an uninstall helper in a stable per-user directory; it does not add a runtime, service, driver, telemetry component, or machine-wide dependency.

## Direct installation

- Install directory: `%LOCALAPPDATA%\Programs\AIUsage`
- Executable: `%LOCALAPPDATA%\Programs\AIUsage\ai-usage.exe`
- Uninstall helper: `%LOCALAPPDATA%\Programs\AIUsage\uninstall.ps1`
- Start menu shortcut: `%APPDATA%\Microsoft\Windows\Start Menu\Programs\AI Usage.lnk`
- Add/Remove Programs key: `HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\AIUsage`

The installer is per-user and does not request elevation. It verifies the release SHA256 before replacing an existing executable. Replacement uses a temporary file and keeps the previous executable until the new file has been placed successfully.

## Portable mode

`ai-usage.exe` can be run from any user-writable directory without installation. Portable mode uses the same `%APPDATA%\AIUsage\settings.json` settings as a direct or WinGet installation.

## Settings and startup behavior

- Upgrades preserve `%APPDATA%\AIUsage\settings.json`.
- Normal uninstall preserves settings so a later reinstall restores preferences.
- `uninstall.ps1 -RemoveSettings` explicitly deletes the settings directory.
- The installer does not enable startup automatically. If startup was already enabled, installation preserves that choice and updates the registry value to the stable installed executable. Users otherwise control startup from the application's settings menu.
- Uninstall removes the `AIUsage` startup entry because its executable no longer exists.

## WinGet

The WinGet package uses the release EXE as a portable installer with package identifier `Ray.CodexUsage`. WinGet owns its installation directory and upgrade/uninstall lifecycle. The in-app updater detects WinGet-managed paths and delegates upgrades back to WinGet.

The PowerShell installer is not used as a WinGet installer because the public WinGet community repository does not accept script-based installers.
