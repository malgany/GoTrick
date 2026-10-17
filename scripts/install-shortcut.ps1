[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$programs = [Environment]::GetFolderPath('Programs')
$shortcutPath = Join-Path $programs 'GoTrick.lnk'
$launcher = Join-Path $PSScriptRoot 'start-menu.ps1'
$launcherArguments = '-NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $launcher + '"'
$powershell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$icon = (Join-Path $projectRoot 'branding/assets/GoTrick.ico') + ',0'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $powershell
$shortcut.Arguments = $launcherArguments
$shortcut.WorkingDirectory = $projectRoot
$shortcut.IconLocation = $icon
$shortcut.Description = 'GoTrick - ambiente de desenvolvimento Go'
$shortcut.Save()
Write-Host "Start menu shortcut installed: $shortcutPath"

# Prepare the same isolated settings for direct native launches from the taskbar.
& (Join-Path $PSScriptRoot 'launch.ps1') -PrepareOnly -SkipSetup
$distribution = & (Join-Path $PSScriptRoot 'prepare-branding.ps1') -DeveloperRuntime

# Repair only this checkout's pin. Keeping the native executable as the target
# makes Windows group the pinned icon with the running IDE window.
$taskbar = Join-Path ([Environment]::GetFolderPath('ApplicationData')) 'Microsoft/Internet Explorer/Quick Launch/User Pinned/TaskBar/GoTrick.lnk'
if (Test-Path -LiteralPath $taskbar) {
    $pinned = $shell.CreateShortcut($taskbar)
    $distributionRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.runtime/distributions')).TrimEnd('\') + '\'
    $pinnedTarget = $pinned.TargetPath
    $fromThisCheckout = $pinnedTarget -and (
        ([IO.Path]::GetFileName($pinnedTarget).Equals('gotrick64.exe', [StringComparison]::OrdinalIgnoreCase) -and
         [IO.Path]::GetFullPath($pinnedTarget).StartsWith($distributionRoot, [StringComparison]::OrdinalIgnoreCase)) -or
        ($pinnedTarget.Equals($powershell, [StringComparison]::OrdinalIgnoreCase) -and
         $pinned.Arguments.Contains($launcher))
    )
    if ($fromThisCheckout -and $pinnedTarget -ne $distribution.Executable) {
        $backupDirectory = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'GoTrick/shortcut-backups'
        New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
        $backup = Join-Path $backupDirectory ('GoTrick-taskbar-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.lnk')
        Copy-Item -LiteralPath $taskbar -Destination $backup
        $pinned.TargetPath = $distribution.Executable
        $pinned.Arguments = ''
        $pinned.WorkingDirectory = Split-Path -Parent $distribution.Executable
        $pinned.IconLocation = $icon
        $pinned.Description = 'GoTrick - ambiente de desenvolvimento Go'
        $pinned.Save()
        Write-Host "Taskbar shortcut repaired: $taskbar"
    }
}
