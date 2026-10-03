[CmdletBinding()]
param(
    [switch]$RemoveSettings,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$InstallDirectory = Join-Path $env:LOCALAPPDATA 'Programs\AIUsage'
$ExpectedInstallDirectory = [IO.Path]::GetFullPath($InstallDirectory).TrimEnd('\')
$TargetPath = Join-Path $ExpectedInstallDirectory 'ai-usage.exe'
$ShortcutPath = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\AI Usage.lnk'
$DesktopShortcutPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'AI Usage.lnk'
$UninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\AIUsage'
$RunKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$SettingsDirectory = Join-Path $env:APPDATA 'AIUsage'

$LegacyShortcutPath = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Codex Usage.lnk'
$LegacyDesktopShortcutPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Codex Usage.lnk'
$LegacyUninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\CodexUsage'
$LegacySettingsDirectories = @(
    (Join-Path $env:APPDATA 'CodexUsage'),
    (Join-Path $env:APPDATA 'ClaudeCodeUsageMonitor')
)

$AllowedRoot = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Programs')).TrimEnd('\')
if (-not $ExpectedInstallDirectory.StartsWith($AllowedRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to remove unexpected install directory: $ExpectedInstallDirectory"
}

Get-CimInstance Win32_Process -Filter "Name='ai-usage.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.ExecutablePath -eq $TargetPath } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

if (Test-Path -LiteralPath $RunKey) {
    foreach ($StartupKey in @('AIUsage', 'CodexUsage', 'ClaudeCodeUsageMonitor')) {
        Remove-ItemProperty -Path $RunKey -Name $StartupKey -ErrorAction SilentlyContinue
    }
}

foreach ($LinkPath in @($ShortcutPath, $DesktopShortcutPath, $LegacyShortcutPath, $LegacyDesktopShortcutPath)) {
    Remove-Item -LiteralPath $LinkPath -Force -ErrorAction SilentlyContinue
}
Remove-Item -LiteralPath $UninstallKey -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $LegacyUninstallKey -Recurse -Force -ErrorAction SilentlyContinue

if ($RemoveSettings) {
    $ExpectedSettingsDirectory = [IO.Path]::GetFullPath((Join-Path $env:APPDATA 'AIUsage')).TrimEnd('\')
    $ResolvedSettingsDirectory = [IO.Path]::GetFullPath($SettingsDirectory).TrimEnd('\')
    if ($ResolvedSettingsDirectory -eq $ExpectedSettingsDirectory) {
        Remove-Item -LiteralPath $ResolvedSettingsDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
    foreach ($LegacySettingsDirectory in $LegacySettingsDirectories) {
        $ResolvedLegacySettings = [IO.Path]::GetFullPath($LegacySettingsDirectory).TrimEnd('\')
        if ($ResolvedLegacySettings.StartsWith([IO.Path]::GetFullPath($env:APPDATA).TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $ResolvedLegacySettings -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

if (Test-Path -LiteralPath $ExpectedInstallDirectory -PathType Container) {
    Remove-Item -LiteralPath $ExpectedInstallDirectory -Recurse -Force
}

if (-not $Quiet) {
    Write-Output 'AI Usage was uninstalled.'
    if (-not $RemoveSettings) {
        Write-Output "Settings were preserved at $SettingsDirectory"
    }
}
