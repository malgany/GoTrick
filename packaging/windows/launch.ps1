[CmdletBinding()]
param([string]$Project, [switch]$PrepareOnly, [string]$DataRoot)
$ErrorActionPreference = 'Stop'
try {
    $appRoot = $PSScriptRoot
    $configRoot = Join-Path ([Environment]::GetFolderPath('ApplicationData')) 'GoTrick'
    $localRoot = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'GoTrick'
    if ($DataRoot) { $configRoot = $localRoot = [IO.Path]::GetFullPath($DataRoot) }
    foreach ($path in @("$configRoot/config", "$configRoot/plugins", "$localRoot/system", "$localRoot/log")) {
        New-Item -ItemType Directory -Path $path -Force | Out-Null
    }
    $properties = Join-Path $localRoot 'gotrick.properties'
    $lines = @(
        'ide.no.platform.update=GoTrick'
        "idea.config.path=$configRoot/config"
        "idea.plugins.path=$configRoot/plugins"
        "idea.system.path=$localRoot/system"
        "idea.log.path=$localRoot/log"
        "gotrick.tools=$appRoot/tools"
        ('plugin.path=' + ((Get-ChildItem -LiteralPath "$appRoot/plugins" -Directory | ForEach-Object { $_.FullName }) -join ';'))
    ) | ForEach-Object { $_.Replace('\', '/') }
    # Java properties support non-ASCII paths through Unicode escapes.
    $content = ($lines -join "`n") + "`n"
    $content = [regex]::Replace($content, '[^\x00-\x7F]', { param($m) '\u' + ([int][char]$m.Value).ToString('x4') })
    [IO.File]::WriteAllText($properties, $content, [Text.Encoding]::ASCII)
    if ($PrepareOnly) { return }
    $env:IDEA_PROPERTIES = $properties
    $env:GOTRICK_TOOLS = Join-Path $appRoot 'tools'
    $start = @{ FilePath = "$appRoot/ide/bin/gotrick64.exe"; WorkingDirectory = $appRoot; PassThru = $true }
    if ($Project) {
        if (-not (Test-Path -LiteralPath $Project -PathType Container)) { throw 'Project folder not found.' }
        $start.ArgumentList = '"' + (Resolve-Path -LiteralPath $Project).Path + '"'
    }
    Start-Process @start | Out-Null
} catch {
    Add-Type -AssemblyName System.Windows.Forms
    [Windows.Forms.MessageBox]::Show($_.Exception.Message, 'GoTrick - startup error') | Out-Null
    exit 1
}
