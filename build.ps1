[CmdletBinding()]
param([switch]$SkipTests, [switch]$Setup)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
if ($Setup) { & (Join-Path $projectRoot 'scripts/setup.ps1') }
$jdkPath = Join-Path $projectRoot '.tools/jdk'
$gradleExe = Join-Path $projectRoot '.tools/gradle/bin/gradle.bat'
if (!(Test-Path -LiteralPath $gradleExe) -or !(Test-Path -LiteralPath (Join-Path $jdkPath 'bin/java.exe'))) {
    throw 'Execute .\scripts\setup.ps1 primeiro para preparar as ferramentas locais.'
}
$oldJavaHome = $env:JAVA_HOME
$oldGradleHome = $env:GRADLE_USER_HOME
Push-Location -LiteralPath $projectRoot
try {
    $env:JAVA_HOME = $jdkPath
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-cache'
    $tasks = @(':plugin:buildPlugin')
    if (!$SkipTests) { $tasks += ':plugin:test' }
    $tasks += @(':plugin:verifyPluginStructure', ':plugin:verifyPluginProjectConfiguration')
    & $gradleExe @tasks --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Build falhou: $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:GRADLE_USER_HOME = $oldGradleHome
    Pop-Location
}
