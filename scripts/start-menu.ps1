[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $projectRoot '.runtime/log'
New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
$logFile = Join-Path $logDirectory ('launcher-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '.txt')
$transcribing = $false
try {
    Start-Transcript -Path $logFile | Out-Null
    $transcribing = $true
    & (Join-Path $PSScriptRoot 'launch.ps1')
} catch {
    $failure = $_ | Out-String
    Write-Host $failure
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show("GoTrick could not start.`r`n`r`n$failure`r`nLog: $logFile", 'GoTrick - startup error') | Out-Null
    exit 1
} finally {
    if ($transcribing) { Stop-Transcript | Out-Null }
}
