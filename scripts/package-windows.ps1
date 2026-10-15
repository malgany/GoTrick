[CmdletBinding()]
param([string]$OutputDirectory, [switch]$SkipBuild, [switch]$Portable)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = (Get-Content -LiteralPath (Join-Path $root 'VERSION') -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid VERSION.' }
if (-not $OutputDirectory) { $OutputDirectory = Join-Path (Split-Path $root) "GoTrick-Releases/$version" }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
if (Test-Path -LiteralPath (Join-Path $OutputDirectory "GoTrick-$version-windows-x64-setup.exe")) { throw 'Release artifact already exists. Use a fresh output directory; never overwrite a published release.' }
if (-not $SkipBuild) { & (Join-Path $root 'build.ps1') }
$distribution = & (Join-Path $PSScriptRoot 'prepare-branding.ps1')
$stage = Join-Path $root ('.runtime/packages/' + [guid]::NewGuid().ToString('N') + '/GoTrick')
New-Item -ItemType Directory -Path $stage -Force | Out-Null
function Copy-Tree($source, $destination) {
    $prefix = (Resolve-Path -LiteralPath $source).Path.TrimEnd('\') + '\'
    foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File) {
        if ($file.Name -like '.gotrick-*') { continue }
        $target = Join-Path $destination $file.FullName.Substring($prefix.Length)
        New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
        # Staging is immutable. All packaging operations read these files only.
        try { New-Item -ItemType HardLink -Path $target -Target $file.FullName -ErrorAction Stop | Out-Null }
        catch { Copy-Item -LiteralPath $file.FullName -Destination $target }
    }
}
Copy-Tree $distribution.Home (Join-Path $stage 'ide')
$pluginZip = Join-Path $root "plugin/build/distributions/plugin-$version.zip"
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($pluginZip, (Join-Path $stage 'plugins'))
Copy-Tree (Join-Path $root '.tools/lsp4ij') (Join-Path $stage 'plugins/lsp4ij')
New-Item -ItemType Directory -Path "$stage/tools/gotools", "$stage/licenses" -Force | Out-Null
foreach ($name in @('gopls', 'dlv')) {
    Copy-Item -LiteralPath "$root/.tools/gotools/$name.exe" -Destination "$stage/tools/gotools/"
    Copy-Item -LiteralPath "$root/.tools/gotools/$name.json" -Destination "$stage/tools/gotools/"
    & "$root/.tools/go/bin/go.exe" version -m "$stage/tools/gotools/$name.exe" | Set-Content "$stage/licenses/$name-modules.txt" -Encoding UTF8
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inventory Go helper modules.' }
}
# Preserve license texts from the verified module cache, including nested notices.
$moduleRoot = (Resolve-Path "$root/.tools/go-work/pkg/mod").Path
foreach ($file in Get-ChildItem -LiteralPath $moduleRoot -Recurse -File | Where-Object { $_.Name -match '^(LICENSE|COPYING|NOTICE|AUTHORS|PATENTS)(\..*)?$' -and $_.FullName -notlike '*\cache\*' }) {
    $target = Join-Path "$stage/licenses/go-modules" $file.FullName.Substring($moduleRoot.Length + 1)
    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
Copy-Item -LiteralPath "$root/.tools/go/LICENSE" -Destination "$stage/licenses/Go-LICENSE"
Copy-Item -LiteralPath "$root/packaging/windows/launch.ps1", "$root/packaging/windows/GoTrick.cmd", "$root/LICENSE", "$root/NOTICE", "$root/VERSION" -Destination $stage
Copy-Item -LiteralPath "$root/docs/THIRD-PARTY.md" -Destination "$stage/THIRD-PARTY-NOTICES.md"
Copy-Item -LiteralPath "$root/README.md" -Destination $stage
foreach ($file in Get-ChildItem -LiteralPath "$root/licenses" -File) { Copy-Item -LiteralPath $file.FullName -Destination "$stage/licenses/" }
$forbidden = @(Get-ChildItem -LiteralPath $stage -Recurse -File | Where-Object { $_.Name -in @('go.exe', 'gradle.bat', 'gotrick-sdk.xml', 'idea.log') -or $_.Extension -in @('.pfx', '.pem', '.key') })
if ($forbidden.Count) { throw "Unexpected release content: $($forbidden.FullName -join ', ')" }
$manifest = [ordered]@{name='GoTrick';version=$version;platform='windows-x64';channel='preview';goSdkBundled=$false;base='IC-252.28539.97';gopls='v0.24.0';delve='v1.27.2';lsp4ij='0.21.0';signed=$false;brandingRevision=$distribution.Revision;pluginSha256=(Get-FileHash $pluginZip -Algorithm SHA256).Hash.ToLowerInvariant()}
$manifest | ConvertTo-Json | Set-Content "$stage/release-manifest.json" -Encoding UTF8
Copy-Item -LiteralPath "$stage/release-manifest.json" -Destination $OutputDirectory
$compiler = & (Join-Path $PSScriptRoot 'setup-installer.ps1')
& $compiler "/DAppVersion=$version" "/DStageDir=$stage" "/DOutputDir=$OutputDirectory" "$root/packaging/windows/GoTrick.iss"
if ($LASTEXITCODE -ne 0) { throw 'Installer compilation failed.' }
if ($Portable) { [IO.Compression.ZipFile]::CreateFromDirectory($stage, (Join-Path $OutputDirectory "GoTrick-$version-windows-x64.zip"), [IO.Compression.CompressionLevel]::Optimal, $true) }
Copy-Item -LiteralPath "$root/docs/releases/$version.md" -Destination "$OutputDirectory/RELEASE-NOTES.md"
Get-ChildItem -LiteralPath $OutputDirectory -File | Where-Object Name -ne 'SHA256SUMS.txt' | Sort-Object Name | ForEach-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.Name } | Set-Content "$OutputDirectory/SHA256SUMS.txt" -Encoding ASCII
Write-Host "Release prepared: $OutputDirectory"
Write-Host "Staging (immutable): $stage"
