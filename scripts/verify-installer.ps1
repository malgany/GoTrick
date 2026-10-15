[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Installer)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = (Get-Content -LiteralPath "$root/VERSION" -Raw).Trim()
if (Test-Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\{3E71A5F8-EE21-44C5-843B-839FDCA4CC75}_is1') {
    throw 'An installed GoTrick already exists. Run this installation test in a clean Windows user or VM to preserve its uninstall record.'
}
$sandbox = Join-Path $root ('.runtime/installer-validation/' + [guid]::NewGuid().ToString('N'))
$installPath = Join-Path $sandbox 'Application with spaces'
$group = 'GoTrick Validation ' + (Split-Path $sandbox -Leaf)
New-Item -ItemType Directory -Path $sandbox -Force | Out-Null
$setup = Start-Process -FilePath (Resolve-Path -LiteralPath $Installer).Path -ArgumentList ('/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /TASKS="" /GROUP="' + $group + '" /DIR="' + $installPath + '" /LOG="' + $sandbox + '\install.log"') -WindowStyle Hidden -PassThru -Wait
if ($setup.ExitCode -ne 0) { throw "Installer failed: $($setup.ExitCode)" }
try {
    foreach ($required in @('ide/bin/gotrick64.exe','tools/gotools/gopls.exe','tools/gotools/dlv.exe','plugins/lsp4ij/lib/lsp4ij-0.21.0.jar','launch.ps1','licenses/LSP4IJ-LICENSE')) {
        if (-not (Test-Path -LiteralPath (Join-Path $installPath $required))) { throw "Missing packaged file: $required" }
    }
    if (Get-ChildItem -LiteralPath $installPath -Recurse -File -Filter go.exe) { throw 'Developer Go SDK leaked into installer.' }
    $native = Start-Process -FilePath "$installPath/ide/bin/gotrick64.exe" -ArgumentList '--version' -WindowStyle Hidden -PassThru -RedirectStandardOutput "$sandbox/version.txt" -RedirectStandardError "$sandbox/version-error.txt" -Wait
    $nativeVersion = Get-Content -LiteralPath "$sandbox/version.txt" -Raw
    if ($native.ExitCode -ne 0 -or $nativeVersion -notlike "*GoTrick $version*") { throw 'Installed launcher version check failed.' }
    $linkPath = Join-Path ([Environment]::GetFolderPath('Programs')) "$group/GoTrick.lnk"
    if (-not (Test-Path -LiteralPath $linkPath)) { throw 'Installer did not create the Start menu shortcut.' }
    $link = (New-Object -ComObject WScript.Shell).CreateShortcut($linkPath)
    if ($link.Arguments -notlike ('*' + $installPath + '\launch.ps1*') -or $link.IconLocation -notlike '*GoTrick.ico*') { throw 'Installed shortcut paths are incorrect.' }
    & "$installPath/tools/gotools/gopls.exe" version
    if ($LASTEXITCODE -ne 0) { throw 'Installed gopls failed.' }
    & "$installPath/tools/gotools/dlv.exe" version
    if ($LASTEXITCODE -ne 0) { throw 'Installed Delve failed.' }
    $powershell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    & $powershell -NoProfile -ExecutionPolicy Bypass -File "$installPath/launch.ps1" -PrepareOnly -DataRoot "$sandbox/User data ação"
    if ($LASTEXITCODE -ne 0) { throw 'Installed launcher configuration failed under Windows PowerShell.' }
    $properties = Get-Content -LiteralPath "$sandbox/User data ação/gotrick.properties" -Raw
    if ($properties -notmatch 'plugin.path=.*plugins' -or $properties -notmatch 'gotrick.tools=.*tools' -or $properties -notmatch '\\u00') { throw 'Installed paths or Unicode property encoding incorrect.' }
    [ordered]@{passed=$true;version=$version;nativeVersion=$nativeVersion.Trim();installerSha256=(Get-FileHash -LiteralPath $Installer -Algorithm SHA256).Hash;goSdkBundled=$false;guiVerified=$false} | ConvertTo-Json | Set-Content "$root/validation/installer.json" -Encoding UTF8
} finally {
    # The exact uninstaller was created by this isolated installation only.
    # Preserve logs and user data for inspection; the uninstaller removes app files.
    $uninstaller = Join-Path $installPath 'unins000.exe'
    if ((Test-Path -LiteralPath $uninstaller) -and [IO.Path]::GetFullPath($uninstaller).StartsWith([IO.Path]::GetFullPath($sandbox) + '\', [StringComparison]::OrdinalIgnoreCase)) {
        $process = Start-Process -FilePath $uninstaller -ArgumentList '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART' -WindowStyle Hidden -Wait -PassThru
        if ($process.ExitCode -ne 0) { throw 'Smoke-test uninstallation failed.' }
    }
}
Write-Host 'Installer, native launcher, helpers, Windows PowerShell configuration and uninstaller passed.'
