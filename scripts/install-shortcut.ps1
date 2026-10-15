[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$programs = [Environment]::GetFolderPath('Programs')
$shortcutPath = Join-Path $programs 'GoTrick.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
$shortcut.Arguments = '-NoLogo -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + (Join-Path $PSScriptRoot 'start-menu.ps1') + '"'
$shortcut.WorkingDirectory = $projectRoot
$shortcut.IconLocation = (Join-Path $projectRoot 'branding/assets/GoTrick.ico') + ',0'
$shortcut.Description = 'GoTrick - ambiente de desenvolvimento Go'
$shortcut.Save()
Write-Host "Start menu shortcut installed: $shortcutPath"
