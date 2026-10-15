[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $distribution = & (Join-Path $PSScriptRoot 'prepare-branding.ps1')
    $version = (Get-Content VERSION -Raw).Trim()
    $classes = Join-Path $projectRoot 'validation/branding-classes'
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    & './.tools/jdk/bin/javac.exe' -cp '.tools/idea/lib/*' -d $classes scripts/BrandingProbe.java
    if ($LASTEXITCODE -ne 0) { throw 'Branding probe compilation failed.' }
    $product = Get-Content -LiteralPath (Join-Path $distribution.Home 'product-info.json') -Raw | ConvertFrom-Json
    $updatePolicy = @($product.launch[0].additionalJvmArguments | Where-Object { $_ -eq '-Dide.no.platform.update=GoTrick' })
    if ($updatePolicy.Count -ne 1) { throw 'Native launcher is missing the platform update policy.' }
    $probe = & './.tools/idea/jbr/bin/java.exe' '-Djava.awt.headless=true' '-Didea.platform.prefix=Idea' '-Dintellij.platform.load.app.info.from.resources=true' $updatePolicy "-Didea.home.path=$($distribution.Home)" -cp "$($distribution.Home)/lib/gotrick-branding.jar;.tools/idea/lib/*;$classes" BrandingProbe
    if ($LASTEXITCODE -ne 0) { throw 'Branding probe failed.' }
    $nativeVersion = (Get-Item -LiteralPath $distribution.Executable).VersionInfo
    if ($nativeVersion.ProductName -ne 'GoTrick') { throw 'Native product name incorrect.' }
    $original = (Get-Item '.tools/idea/bin/idea64.exe').VersionInfo
    if ($original.ProductName -notlike '*IntelliJ*') { throw 'Original runtime identity was changed.' }
    $linkPath = Join-Path ([Environment]::GetFolderPath('Programs')) 'GoTrick.lnk'
    if (Test-Path -LiteralPath $linkPath) {
        $link = (New-Object -ComObject WScript.Shell).CreateShortcut($linkPath)
        if ($link.Arguments -notlike '*start-menu.ps1*' -or $link.IconLocation -notlike '*GoTrick.ico*') { throw 'Shortcut configuration incorrect.' }
    }
    $out = Join-Path $projectRoot 'validation/branding-version.txt'
    $err = Join-Path $projectRoot 'validation/branding-version-error.txt'
    $process = Start-Process -FilePath $distribution.Executable -ArgumentList '--version' -WindowStyle Hidden -PassThru -RedirectStandardOutput $out -RedirectStandardError $err
    if (-not $process.WaitForExit(30000)) { throw 'Native version probe did not exit in time.' }
    $nativeOutput = Get-Content -LiteralPath $out -Raw
    if ($process.ExitCode -ne 0 -or $nativeOutput -notlike "*GoTrick $version*") { throw "Native launcher check failed: $nativeOutput" }
    [ordered]@{passed=$true;timestamp=(Get-Date -Format o);distribution=$distribution.Home;resourceProbe="$probe";nativeVersion=$nativeOutput.Trim();originalProduct=$original.ProductName;shortcut=$linkPath;fullGuiVisuallyVerified=$false} | ConvertTo-Json | Set-Content validation/branding.json -Encoding UTF8
    Write-Host 'Branding resources, rendered icon and native launcher passed; existing developer shortcut checked when present.'
} finally { Pop-Location }
