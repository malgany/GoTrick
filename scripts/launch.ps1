[CmdletBinding()]
param(
    [string]$Project,
    [switch]$Build,
    [switch]$Wait,
    [Alias('NoLaunch')][switch]$PrepareOnly,
    [switch]$SkipSetup
)

$ErrorActionPreference = 'Stop'
# A shortcut may inherit PSModulePath from PowerShell 7. Windows PowerShell
# needs its own built-in modules, including the Get-FileHash script module.
if ($PSVersionTable.PSVersion.Major -le 5) {
    $windowsModules = Join-Path $PSHOME 'Modules'
    $env:PSModulePath = $windowsModules + ';' + $env:PSModulePath
    Import-Module (Join-Path $windowsModules 'Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
}
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.tools'
$runtimeRoot = Join-Path $projectRoot '.runtime'

if (-not $SkipSetup) { & (Join-Path $PSScriptRoot 'setup.ps1') -RuntimeOnly }
$ideaExe = Join-Path $toolsRoot 'idea/bin/idea64.exe'
foreach ($required in @($ideaExe, (Join-Path $toolsRoot 'lsp4ij/lib/lsp4ij-0.21.0.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing dependency $required. Run scripts/setup.ps1 first." }
}
$brandedDistribution = & (Join-Path $PSScriptRoot 'prepare-branding.ps1')
$ideaExe = $brandedDistribution.Executable

$savedEnvironment = @{}
foreach ($name in @('JAVA_HOME', 'GRADLE_USER_HOME', 'PATH', 'IDEA_PROPERTIES', 'GOTRICK_HOME', 'GOTRICK_TOOLS', 'GOROOT', 'GOPATH', 'GOCACHE', 'GOTOOLCHAIN')) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:JAVA_HOME = Join-Path $toolsRoot 'jdk'
    $env:GRADLE_USER_HOME = Join-Path $toolsRoot 'gradle-cache'
    $env:GOTRICK_HOME = $projectRoot
    $env:GOTRICK_TOOLS = $toolsRoot
    $env:PATH = "$(Join-Path $toolsRoot 'gotools');$env:PATH"
    $distributionPath = Join-Path $projectRoot 'plugin/build/distributions/*.zip'
    $pluginZip = Get-ChildItem -Path $distributionPath -ErrorAction SilentlyContinue | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($Build -or -not $pluginZip) {
        if (-not (Test-Path -LiteralPath (Join-Path $toolsRoot 'gradle/bin/gradle.bat'))) {
            throw 'This source checkout needs a plugin build. Run scripts/setup.ps1 and build.ps1; installed distributions include the compiled plugin.'
        }
        Push-Location $projectRoot
        try {
            & (Join-Path $toolsRoot 'gradle/bin/gradle.bat') --no-daemon :plugin:buildPlugin
            if ($LASTEXITCODE -ne 0) { throw 'GoTrick plugin compilation failed.' }
        } finally { Pop-Location }
        $pluginZip = Get-ChildItem -Path $distributionPath | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    }
    if (-not $pluginZip) { throw 'No plugin distribution was produced in plugin/build/distributions.' }

    # Each plugin revision gets its own immutable directory. Existing plugin
    # installations and runtime settings are never removed by the launcher.
    $pluginHash = (Get-FileHash -LiteralPath $pluginZip.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $pluginSet = Join-Path $runtimeRoot ('plugin-sets/' + $pluginHash)
    $pluginReady = Join-Path $pluginSet '.ready'
    if (-not (Test-Path -LiteralPath $pluginReady)) {
        if (Test-Path -LiteralPath $pluginSet) { throw "An incomplete plugin installation exists: $pluginSet. Move it aside and retry." }
        New-Item -ItemType Directory -Path $pluginSet -Force | Out-Null
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($pluginZip.FullName, $pluginSet)
        Copy-Item -LiteralPath (Join-Path $toolsRoot 'lsp4ij') -Destination (Join-Path $pluginSet 'lsp4ij') -Recurse
        Set-Content -LiteralPath $pluginReady -Value $pluginHash -Encoding ASCII
    }

    foreach ($folder in @('config', 'system', 'log', 'plugins')) {
        New-Item -ItemType Directory -Path (Join-Path $runtimeRoot $folder) -Force | Out-Null
    }
    $properties = Join-Path $runtimeRoot 'gotrick.properties'
    $bundledPluginPaths = @(Get-ChildItem -LiteralPath $pluginSet -Directory | ForEach-Object { $_.FullName.Replace('\', '/') })
    $propertyLines = @(
        '# GoTrick distribution on IntelliJ Platform; isolated settings and tools.'
        # Platform upgrades must be packaged and validated by GoTrick.
        # This platform switch leaves plugin updates available.
        'ide.no.platform.update=GoTrick'
        ('idea.config.path=' + (Join-Path $runtimeRoot 'config').Replace('\', '/'))
        ('idea.system.path=' + (Join-Path $runtimeRoot 'system').Replace('\', '/'))
        ('idea.log.path=' + (Join-Path $runtimeRoot 'log').Replace('\', '/'))
        ('idea.plugins.path=' + (Join-Path $runtimeRoot 'plugins').Replace('\', '/'))
        ('plugin.path=' + ($bundledPluginPaths -join ';'))
        ('gotrick.home=' + $projectRoot.Replace('\', '/'))
        ('gotrick.tools=' + $toolsRoot.Replace('\', '/'))
        ('gotrick.tools.path=' + $toolsRoot.Replace('\', '/'))
    )
    $propertyLines | Set-Content -LiteralPath $properties -Encoding ASCII
    $env:IDEA_PROPERTIES = $properties
    if ($PrepareOnly) {
        Write-Host "GoTrick runtime prepared: $properties"
        return
    }
    if (-not $Project) {
        $sample = Join-Path $projectRoot 'examples/hello-go'
        if (Test-Path -LiteralPath $sample -PathType Container) { $Project = $sample }
    }
    $startArguments = @{FilePath = $ideaExe; WorkingDirectory = $projectRoot; PassThru = $true}
    if ($Project) {
        if (-not (Test-Path -LiteralPath $Project -PathType Container)) { throw "Project directory does not exist: $Project" }
        $absoluteProject = (Resolve-Path -LiteralPath $Project).Path
        # Start-Process joins arguments; keep paths containing spaces as one arg.
        $startArguments.ArgumentList = '"' + $absoluteProject + '"'
    }
    if ($Wait) { $startArguments.Wait = $true }
    $process = Start-Process @startArguments
    if ($Wait -and $process.ExitCode -ne 0) { throw "GoTrick host exited with code $($process.ExitCode). See $runtimeRoot/log/idea.log" }
    Write-Host "GoTrick 0.1.0 started (IntelliJ Platform 252.28539.97, PID $($process.Id))."
    Write-Host "Settings and logs: $runtimeRoot"
} finally {
    foreach ($name in $savedEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
