[CmdletBinding()]
param(
    [string]$SourcePath,
    [string]$ExpectedSha256,
    [string]$Version,
    [switch]$NoLaunch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Repository = 'fiverocks-dev/ai-usage-monitor'
$InstallDirectory = Join-Path $env:LOCALAPPDATA 'Programs\AIUsage'
$TargetPath = Join-Path $InstallDirectory 'ai-usage.exe'
$InstalledUninstaller = Join-Path $InstallDirectory 'uninstall.ps1'
$ShortcutPath = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\AI Usage.lnk'
$DesktopShortcutPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'AI Usage.lnk'
$UninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\AIUsage'
$RunKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run'
$TempDirectory = Join-Path ([IO.Path]::GetTempPath()) ('ai-usage-install-' + [Guid]::NewGuid().ToString('N'))

# Legacy Codex Usage paths are kept only for migration.
$LegacyInstallDirectory = Join-Path $env:LOCALAPPDATA 'Programs\CodexUsage'
$LegacyTargetPath = Join-Path $LegacyInstallDirectory 'codex-usage.exe'
$LegacyUninstaller = Join-Path $LegacyInstallDirectory 'uninstall.ps1'
$LegacyShortcutPath = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Codex Usage.lnk'
$LegacyDesktopShortcutPath = Join-Path ([Environment]::GetFolderPath('Desktop')) 'Codex Usage.lnk'
$LegacyUninstallKey = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\CodexUsage'
$LegacyStartupKeys = @('CodexUsage', 'ClaudeCodeUsageMonitor')

$StartupWasEnabled = $false
$ExistingStartup = $null
$ExistingStartupKey = $null
if (Test-Path -LiteralPath $RunKey) {
    foreach ($StartupKey in @('AIUsage') + $LegacyStartupKeys) {
        $Candidate = try {
            Get-ItemPropertyValue -Path $RunKey -Name $StartupKey -ErrorAction Stop
        }
        catch {
            $null
        }
        if (-not [string]::IsNullOrWhiteSpace($Candidate)) {
            $StartupWasEnabled = $true
            $ExistingStartup = $Candidate
            $ExistingStartupKey = $StartupKey
            break
        }
    }
}

function Get-ReleaseAsset {
    param(
        [Parameter(Mandatory = $true)]$Release,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $asset = $Release.assets | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if (-not $asset) {
        throw "Release asset '$Name' was not found."
    }
    return $asset.browser_download_url
}

function Invoke-ReleaseDownload {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Invoke-WebRequest -UseBasicParsing -Headers @{ 'User-Agent' = 'AIUsage-Installer' } -Uri $Url -OutFile $Destination
}

New-Item -ItemType Directory -Force -Path $TempDirectory | Out-Null

try {
    $StagedExecutable = Join-Path $TempDirectory 'ai-usage.exe'
    $StagedUninstaller = Join-Path $TempDirectory 'uninstall.ps1'

    if ($SourcePath) {
        $ResolvedSource = (Resolve-Path -LiteralPath $SourcePath).Path
        Copy-Item -LiteralPath $ResolvedSource -Destination $StagedExecutable -Force

        $LocalUninstaller = Join-Path $PSScriptRoot 'uninstall.ps1'
        if (-not (Test-Path -LiteralPath $LocalUninstaller -PathType Leaf)) {
            throw "Local uninstall helper was not found at $LocalUninstaller"
        }
        Copy-Item -LiteralPath $LocalUninstaller -Destination $StagedUninstaller -Force
    }
    else {
        $ApiUrl = if ($Version) {
            "https://api.github.com/repos/$Repository/releases/tags/v$($Version.TrimStart('v'))"
        }
        else {
            "https://api.github.com/repos/$Repository/releases/latest"
        }

        $Release = Invoke-RestMethod -UseBasicParsing -Headers @{ 'User-Agent' = 'AIUsage-Installer' } -Uri $ApiUrl
        $ExecutableUrl = Get-ReleaseAsset -Release $Release -Name 'ai-usage.exe'
        $ChecksumUrl = Get-ReleaseAsset -Release $Release -Name 'ai-usage.exe.sha256'
        $UninstallerUrl = Get-ReleaseAsset -Release $Release -Name 'uninstall.ps1'
        $ChecksumPath = Join-Path $TempDirectory 'ai-usage.exe.sha256'

        Invoke-ReleaseDownload -Url $ExecutableUrl -Destination $StagedExecutable
        Invoke-ReleaseDownload -Url $ChecksumUrl -Destination $ChecksumPath
        Invoke-ReleaseDownload -Url $UninstallerUrl -Destination $StagedUninstaller

        $ChecksumText = Get-Content -Raw -LiteralPath $ChecksumPath
        if ($ChecksumText -notmatch '(?i)\b([0-9a-f]{64})\b') {
            throw 'The release checksum file is invalid.'
        }
        $ExpectedSha256 = $Matches[1]
    }

    $ActualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $StagedExecutable).Hash
    if ($ExpectedSha256 -and $ActualSha256 -ne $ExpectedSha256.Trim().ToUpperInvariant()) {
        throw "SHA256 mismatch. Expected $ExpectedSha256 but downloaded $ActualSha256."
    }

    New-Item -ItemType Directory -Force -Path $InstallDirectory | Out-Null

    Get-CimInstance Win32_Process -Filter "Name='ai-usage.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -eq $TargetPath } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

    Get-CimInstance Win32_Process -Filter "Name='codex-usage.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.ExecutablePath -eq $LegacyTargetPath } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }

    $NewPath = "$TargetPath.new"
    $BackupPath = "$TargetPath.old"
    Copy-Item -LiteralPath $StagedExecutable -Destination $NewPath -Force
    Remove-Item -LiteralPath $BackupPath -Force -ErrorAction SilentlyContinue

    $HadPreviousVersion = Test-Path -LiteralPath $TargetPath -PathType Leaf
    if ($HadPreviousVersion) {
        Move-Item -LiteralPath $TargetPath -Destination $BackupPath -Force
    }

    try {
        Move-Item -LiteralPath $NewPath -Destination $TargetPath -Force
    }
    catch {
        Remove-Item -LiteralPath $NewPath -Force -ErrorAction SilentlyContinue
        if ($HadPreviousVersion -and (Test-Path -LiteralPath $BackupPath -PathType Leaf)) {
            Move-Item -LiteralPath $BackupPath -Destination $TargetPath -Force
        }
        throw
    }

    try {
        Copy-Item -LiteralPath $StagedUninstaller -Destination $InstalledUninstaller -Force

        $InstalledVersion = (Get-Item -LiteralPath $TargetPath).VersionInfo.ProductVersion
        if (-not $InstalledVersion) {
            throw 'The installed executable does not contain a product version.'
        }

        New-Item -Path $UninstallKey -Force | Out-Null
        $UninstallCommand = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$InstalledUninstaller`""
        Set-ItemProperty -Path $UninstallKey -Name DisplayName -Value 'AI Usage'
        Set-ItemProperty -Path $UninstallKey -Name DisplayVersion -Value $InstalledVersion
        Set-ItemProperty -Path $UninstallKey -Name Publisher -Value 'fiverocks-dev'
        Set-ItemProperty -Path $UninstallKey -Name DisplayIcon -Value $TargetPath
        Set-ItemProperty -Path $UninstallKey -Name InstallLocation -Value $InstallDirectory
        Set-ItemProperty -Path $UninstallKey -Name URLInfoAbout -Value "https://github.com/$Repository"
        Set-ItemProperty -Path $UninstallKey -Name UninstallString -Value $UninstallCommand
        Set-ItemProperty -Path $UninstallKey -Name QuietUninstallString -Value "$UninstallCommand -Quiet"
        Set-ItemProperty -Path $UninstallKey -Name NoModify -Type DWord -Value 1
        Set-ItemProperty -Path $UninstallKey -Name NoRepair -Type DWord -Value 1

        if ($StartupWasEnabled) {
            Set-ItemProperty -Path $RunKey -Name 'AIUsage' -Value $TargetPath
        }
        foreach ($LegacyStartupKey in $LegacyStartupKeys) {
            Remove-ItemProperty -Path $RunKey -Name $LegacyStartupKey -ErrorAction SilentlyContinue
        }

        $Shell = New-Object -ComObject WScript.Shell
        foreach ($LinkPath in @($ShortcutPath, $DesktopShortcutPath)) {
            $ShortcutDirectory = Split-Path -Parent $LinkPath
            New-Item -ItemType Directory -Force -Path $ShortcutDirectory | Out-Null
            $Shortcut = $Shell.CreateShortcut($LinkPath)
            $Shortcut.TargetPath = $TargetPath
            $Shortcut.WorkingDirectory = $InstallDirectory
            $Shortcut.IconLocation = "$TargetPath,0"
            $Shortcut.Description = 'AI Usage'
            $Shortcut.Save()
        }

        $IconRefreshTool = Join-Path $env:SystemRoot 'System32\ie4uinit.exe'
        if (Test-Path -LiteralPath $IconRefreshTool -PathType Leaf) {
            Start-Process -FilePath $IconRefreshTool -ArgumentList '-show' -WindowStyle Hidden -Wait
        }
    }
    catch {
        if ($HadPreviousVersion -and (Test-Path -LiteralPath $BackupPath -PathType Leaf)) {
            Remove-Item -LiteralPath $TargetPath -Force -ErrorAction SilentlyContinue
            Move-Item -LiteralPath $BackupPath -Destination $TargetPath -Force
        }
        Remove-ItemProperty -Path $RunKey -Name 'AIUsage' -ErrorAction SilentlyContinue
        if ($StartupWasEnabled -and $ExistingStartup -and $ExistingStartupKey) {
            Set-ItemProperty -Path $RunKey -Name $ExistingStartupKey -Value $ExistingStartup
        }
        throw
    }

    Remove-Item -LiteralPath $BackupPath -Force -ErrorAction SilentlyContinue

    # Migration cleanup happens only after the new installation is committed.
    Remove-Item -LiteralPath $LegacyShortcutPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $LegacyDesktopShortcutPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $LegacyUninstallKey -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $LegacyTargetPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $LegacyUninstaller -Force -ErrorAction SilentlyContinue
    if (Test-Path -LiteralPath $LegacyInstallDirectory -PathType Container) {
        $LegacyRemaining = @(Get-ChildItem -LiteralPath $LegacyInstallDirectory -Force -ErrorAction SilentlyContinue)
        if ($LegacyRemaining.Count -eq 0) {
            Remove-Item -LiteralPath $LegacyInstallDirectory -Force -ErrorAction SilentlyContinue
        }
    }

    if (-not $NoLaunch) {
        Start-Process -FilePath $TargetPath -WorkingDirectory $InstallDirectory -WindowStyle Hidden
    }

    Write-Output "AI Usage $InstalledVersion installed to $InstallDirectory"
    Write-Output "SHA256: $ActualSha256"
}
finally {
    $ExpectedTempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
    $ResolvedTemp = [IO.Path]::GetFullPath($TempDirectory).TrimEnd('\')
    if ($ResolvedTemp.StartsWith($ExpectedTempParent + '\', [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $ResolvedTemp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
