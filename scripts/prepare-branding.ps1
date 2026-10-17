[CmdletBinding()]
param([switch]$DeveloperRuntime)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceHome = Join-Path $projectRoot '.tools/idea'
$brandingRoot = Join-Path $projectRoot 'branding'
$version = (Get-Content -LiteralPath (Join-Path $projectRoot 'VERSION') -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'VERSION must contain major.minor.patch.' }
if (-not (Test-Path -LiteralPath (Join-Path $brandingRoot 'assets/GoTrick.ico'))) { & (Join-Path $PSScriptRoot 'prepare-brand-assets.ps1') }
$hashInputs = @(Get-ChildItem -LiteralPath $brandingRoot -Recurse -File) + @(Get-Item -LiteralPath $PSCommandPath,(Join-Path $PSScriptRoot 'NativeBranding.cs'),(Join-Path $projectRoot 'VERSION'))
[string[]]$hashPaths = @($hashInputs | ForEach-Object { $_.FullName })
[Array]::Sort($hashPaths, [StringComparer]::Ordinal)
$hashText = ($hashPaths | ForEach-Object { (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }) -join ''
if ($DeveloperRuntime) { $hashText += '|developer-runtime' }
$digest = [Security.Cryptography.SHA256]::Create()
try { $revision = ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($hashText)))).Replace('-','').ToLowerInvariant().Substring(0,16) } finally { $digest.Dispose() }
$distributionHome = Join-Path $projectRoot ('.runtime/distributions/GoTrick-' + $revision)
$ready = Join-Path $distributionHome '.gotrick-ready.json'
$executable = Join-Path $distributionHome 'bin/gotrick64.exe'
if (Test-Path -LiteralPath $ready) { return [pscustomobject]@{Home=$distributionHome;Executable=$executable;Revision=$revision} }
if (Test-Path -LiteralPath $distributionHome) { throw "Incomplete distribution at $distributionHome. Preserve it for diagnosis and rebuild after correcting the preparation script." }
New-Item -ItemType Directory -Path $distributionHome -Force | Out-Null

# Shared runtime files are never edited. All modified files are fresh copies.
# Hard links avoid duplicating the multi-gigabyte SDK on the same volume.
$sourcePrefix = (Resolve-Path -LiteralPath $sourceHome).Path.TrimEnd('\') + '\'
$excluded = @('product-info.json','bin\idea64.exe','bin\idea64.exe.vmoptions','bin\idea.svg','.gotrick-package.json')
foreach ($file in Get-ChildItem -LiteralPath $sourceHome -Recurse -File) {
    $relative = $file.FullName.Substring($sourcePrefix.Length)
    if ($relative -in $excluded) { continue }
    $target = Join-Path $distributionHome $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
    try { New-Item -ItemType HardLink -Path $target -Target $file.FullName -ErrorAction Stop | Out-Null }
    catch { Copy-Item -LiteralPath $file.FullName -Destination $target }
}
Copy-Item -LiteralPath (Join-Path $sourceHome 'bin/idea64.exe') -Destination $executable
$vmOptions = Join-Path $distributionHome 'bin/gotrick64.exe.vmoptions'
Copy-Item -LiteralPath (Join-Path $sourceHome 'bin/idea64.exe.vmoptions') -Destination $vmOptions
if ($DeveloperRuntime) {
    # Taskbar pins launch the native executable directly. Point it at the same
    # isolated settings file that scripts/launch.ps1 prepares for this checkout.
    $runtimeProperties = (Join-Path $projectRoot '.runtime/gotrick.properties').Replace('\', '/')
    Add-Content -LiteralPath $vmOptions -Value ("-Didea.properties.file=$runtimeProperties") -Encoding ASCII
}
Copy-Item -LiteralPath (Join-Path $brandingRoot 'assets/icon.svg') -Destination (Join-Path $distributionHome 'bin/gotrick.svg')
Copy-Item -LiteralPath (Join-Path $brandingRoot 'assets/GoTrick.ico') -Destination (Join-Path $distributionHome 'bin/GoTrick.ico')
if (-not ('GoTrickNativeBranding' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'NativeBranding.cs') }
[GoTrickNativeBranding]::Apply($executable, (Join-Path $brandingRoot 'assets/GoTrick.ico'), $version)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$brandJar = Join-Path $distributionHome 'lib/gotrick-branding.jar'
$archive = [IO.Compression.ZipFile]::Open($brandJar, [IO.Compression.ZipArchiveMode]::Create)
try {
    [xml]$identity = Get-Content -LiteralPath (Join-Path $brandingRoot 'GoTrickApplicationInfo.xml') -Raw
    $numbers = $version.Split('.')
    $identity.component.version.major = $numbers[0]; $identity.component.version.minor = $numbers[1]; $identity.component.version.micro = $numbers[2]
    $entry = $archive.CreateEntry('idea/IdeaApplicationInfo.xml')
    $writer = [IO.StreamWriter]::new($entry.Open(),[Text.UTF8Encoding]::new($false))
    try { $writer.Write($identity.OuterXml) } finally { $writer.Dispose() }
    # The platform chooses consent metadata by vendor name. Keep the original
    # definitions under the GoTrick alias; missing metadata causes editor crashes.
    $baseJar = [IO.Compression.ZipFile]::OpenRead((Join-Path $sourceHome 'lib/app-client.jar'))
    try {
        $inputStream = $baseJar.GetEntry('consents.json').Open()
        $outputStream = $archive.CreateEntry('consents-GoTrick.json').Open()
        try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
    } finally { $baseJar.Dispose() }
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $brandingRoot 'assets') -File) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,$file.FullName,('gotrick/' + $file.Name)) | Out-Null
    }
} finally { $archive.Dispose() }
$product = Get-Content -LiteralPath (Join-Path $sourceHome 'product-info.json') -Raw | ConvertFrom-Json
$product.name = 'GoTrick'; $product.version = $version; $product.productVendor = 'GoTrick'
$product.dataDirectoryName = 'GoTrick'; $product.svgIconPath = 'bin/gotrick.svg'
foreach ($launch in $product.launch) {
    $launch.launcherPath = 'bin/gotrick64.exe'
    $launch.vmOptionsFilePath = 'bin/gotrick64.exe.vmoptions'
    $launch.bootClassPathJarNames = @('gotrick-branding.jar') + @($launch.bootClassPathJarNames)
    $launch.additionalJvmArguments = @($launch.additionalJvmArguments | ForEach-Object {
        $_ -replace '^-Didea.vendor.name=.*$', '-Didea.vendor.name=GoTrick' -replace '^-Didea.paths.selector=.*$', '-Didea.paths.selector=GoTrick'
    }) + @('-Dintellij.platform.load.app.info.from.resources=true', '-Dide.no.platform.update=GoTrick')
}
[IO.File]::WriteAllText((Join-Path $distributionHome 'product-info.json'),($product | ConvertTo-Json -Depth 100),[Text.UTF8Encoding]::new($false))
$manifest = [ordered]@{name='GoTrick';version=$version;revision=$revision;base='IntelliJ IDEA Community 2025.2.6.3';platformBuild='IC-252.28539.97';modifications=@('Application identity resources','Launcher icon and version resources','Product metadata','Consent resource alias');originalRuntimeUnmodified=$true}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $ready -Encoding UTF8
Write-Host "GoTrick branded distribution prepared: $distributionHome"
[pscustomobject]@{Home=$distributionHome;Executable=$executable;Revision=$revision}
