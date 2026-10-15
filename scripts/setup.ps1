[CmdletBinding()]
param(
    [switch]$SkipIde,
    [switch]$SkipGoTools,
    [switch]$Offline,
    [switch]$RuntimeOnly
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $projectRoot '.tools'
$downloadsRoot = Join-Path $toolsRoot 'downloads'
New-Item -ItemType Directory -Path $downloadsRoot -Force | Out-Null

# Versions and SHA-256 values come from the publishers' release metadata.
# Updating a dependency requires updating both its version and pinned digest.
$packages = @(
    @{
        Name = 'jdk'; Version = '21.0.12.1+1'; Executable = 'bin/java.exe'
        Url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip'
        Sha256 = 'f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e'
        Source = 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows'
    },
    @{
        Name = 'gradle'; Version = '8.14.3'; Executable = 'bin/gradle.bat'
        Url = 'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip'
        Sha256 = 'bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531'
        Source = 'https://services.gradle.org/distributions/gradle-8.14.3-bin.zip.sha256'
    },
    @{
        Name = 'go'; Version = '1.27.2'; Executable = 'bin/go.exe'
        Url = 'https://go.dev/dl/go1.27.2.windows-amd64.zip'
        Sha256 = '1314008898bd40df77af4b014f777f08873dbdfbcd3d92308728ee03304fe04f'
        Source = 'https://go.dev/dl/?mode=json&include=all'
    },
    @{
        Name = 'lsp4ij'; Version = '0.21.0'; Executable = 'lib/lsp4ij-0.21.0.jar'
        Url = 'https://github.com/redhat-developer/lsp4ij/releases/download/0.21.0/lsp4ij-0.21.0.zip'
        Sha256 = 'dd3d0024a3f160e6bb28164314f5a5af80700fa2d491d853c64d309bd42b8d43'
        Source = 'https://api.github.com/repos/redhat-developer/lsp4ij/releases/tags/0.21.0'
    }
)
if (-not $SkipIde) {
    $packages += @{
        Name = 'idea'; Version = '2025.2.6.3'; Executable = 'bin/idea64.exe'
        Url = 'https://download.jetbrains.com/idea/ideaIC-2025.2.6.3.win.zip'
        Sha256 = 'ee3bb2bf315298da0758f5d72ee97a9c5e017f22d129c12db80b69a392f4673e'
        Source = 'https://download.jetbrains.com/idea/ideaIC-2025.2.6.3.win.zip.sha256'
    }
}

function Install-PinnedArchive($package) {
    $destination = Join-Path $toolsRoot $package.Name
    $markerPath = Join-Path $destination '.gotrick-package.json'
    if ((Test-Path -LiteralPath $markerPath) -and (Test-Path -LiteralPath (Join-Path $destination $package.Executable))) {
        $installed = Get-Content -LiteralPath $markerPath -Raw | ConvertFrom-Json
        if ($installed.Sha256 -eq $package.Sha256) {
            Write-Host "$($package.Name) $($package.Version) ready."
            return
        }
        throw "A different $($package.Name) is present at $destination. Move it aside before changing the pinned toolchain."
    }
    if (Test-Path -LiteralPath $destination) {
        throw "Incomplete or unmanaged tool directory: $destination. Move it aside and retry."
    }
    $archive = Join-Path $downloadsRoot "$($package.Name)-$($package.Version.Replace('+', '_')).zip"
    if (-not (Test-Path -LiteralPath $archive)) {
        if ($Offline) { throw "Offline setup requires cached archive $archive" }
        Write-Host "Downloading $($package.Name) $($package.Version)..."
        $partial = "$archive.partial"
        & curl.exe --fail --location --retry 3 --silent --show-error --output $partial $package.Url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($package.Url)" }
        $actual = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $package.Sha256) { throw "SHA-256 mismatch for $partial. Expected $($package.Sha256); received $actual. The archive was not extracted." }
        Move-Item -LiteralPath $partial -Destination $archive
    }
    $actual = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $package.Sha256) { throw "SHA-256 mismatch for cached archive $archive. The archive was not extracted." }
    Write-Host "Extracting verified $($package.Name)..."
    $stage = Join-Path $toolsRoot ('.extract-' + $package.Name + '-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stage | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::ExtractToDirectory($archive, $stage)
    # Validate both directory-move targets before moving an extracted tree.
    $toolsPrefix = [System.IO.Path]::GetFullPath($toolsRoot).TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
    foreach ($movePath in @($stage, $destination)) {
        $resolvedMovePath = [System.IO.Path]::GetFullPath($movePath)
        if (-not $resolvedMovePath.StartsWith($toolsPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to move a tool directory outside $toolsRoot : $resolvedMovePath"
        }
    }
    if (Test-Path -LiteralPath (Join-Path $stage $package.Executable)) {
        Move-Item -LiteralPath $stage -Destination $destination
    } else {
        $candidates = @(Get-ChildItem -LiteralPath $stage -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName $package.Executable) })
        if ($candidates.Count -ne 1) { throw "Expected executable $($package.Executable) not found in verified $archive; extracted files remain at $stage." }
        Move-Item -LiteralPath $candidates[0].FullName -Destination $destination
        # Non-recursive: unexpected archive contents are retained for inspection.
        if (@(Get-ChildItem -LiteralPath $stage -Force).Count -eq 0) { Remove-Item -LiteralPath $stage }
    }
    $package | ConvertTo-Json | Set-Content -LiteralPath $markerPath -Encoding UTF8
}

if ($RuntimeOnly) { $packages = @($packages | Where-Object { $_.Name -notin @('go', 'jdk', 'gradle') }) }
foreach ($package in $packages) { Install-PinnedArchive $package }

if ($RuntimeOnly -and -not $SkipGoTools) {
    foreach ($tool in @('gopls', 'dlv')) {
        if (-not (Test-Path -LiteralPath (Join-Path $toolsRoot "gotools/$tool.exe"))) {
            throw "Bundled helper $tool is missing. For a source checkout, run scripts/setup.ps1 to build developer tools; end-user distributions must include the helper binaries."
        }
    }
}
if (-not $SkipGoTools -and -not $RuntimeOnly) {
    $goExe = Join-Path $toolsRoot 'go/bin/go.exe'
    $goBin = Join-Path $toolsRoot 'gotools'
    New-Item -ItemType Directory -Path $goBin -Force | Out-Null
    $savedEnvironment = @{}
    foreach ($name in @('GOBIN', 'GOPATH', 'GOMODCACHE', 'GOCACHE', 'GOROOT', 'GOTOOLCHAIN', 'GOSUMDB', 'GOPROXY', 'GOENV', 'GOWORK', 'GOFLAGS', 'GOOS', 'GOARCH', 'CGO_ENABLED')) {
        $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    try {
        $env:GOBIN = $goBin
        $env:GOPATH = Join-Path $toolsRoot 'go-work'
        $env:GOMODCACHE = Join-Path $toolsRoot 'go-work/pkg/mod'
        $env:GOCACHE = Join-Path $toolsRoot 'go-cache'
        $env:GOROOT = Join-Path $toolsRoot 'go'
        $env:GOTOOLCHAIN = 'local'
        $env:GOENV = 'off'
        $env:GOWORK = 'off'
        $env:GOFLAGS = '-trimpath -buildvcs=false'
        $env:GOOS = 'windows'
        $env:GOARCH = 'amd64'
        $env:CGO_ENABLED = '0'
        $env:GOSUMDB = 'sum.golang.org'
        $env:GOPROXY = if ($Offline) { 'off' } else { 'https://proxy.golang.org' }
        foreach ($tool in @(
            @{Name = 'gopls'; Module = 'golang.org/x/tools/gopls'; Version = 'v0.24.0'},
            @{Name = 'dlv'; Module = 'github.com/go-delve/delve/cmd/dlv'; Version = 'v1.27.2'}
        )) {
            $target = Join-Path $goBin ($tool.Name + '.exe')
            $marker = Join-Path $goBin ($tool.Name + '.json')
            if ((Test-Path -LiteralPath $target) -and (Test-Path -LiteralPath $marker)) {
                $previous = Get-Content -LiteralPath $marker -Raw | ConvertFrom-Json
                if (($previous.Version -eq $tool.Version) -and ($previous.BuildFlags -eq $env:GOFLAGS) -and ($previous.Sha256 -eq (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash)) {
                    Write-Host "$($tool.Name) $($tool.Version) ready."
                    continue
                }
            }
            Write-Host "Building $($tool.Module)@$($tool.Version) (Go checksum database verification enabled)..."
            & $goExe install ($tool.Module + '@' + $tool.Version)
            if ($LASTEXITCODE -ne 0) { throw "go install failed for $($tool.Name)" }
            $tool.Sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
            $tool.GoVersion = '1.27.2'
            $tool.BuildFlags = $env:GOFLAGS
            $tool | ConvertTo-Json | Set-Content -LiteralPath $marker -Encoding UTF8
        }
    } finally {
        foreach ($name in $savedEnvironment.Keys) {
            [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
        }
    }
}

Write-Host "GoTrick dependencies are ready in $toolsRoot. The global PATH was not modified."
