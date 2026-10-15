[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compiler = Join-Path $root '.tools/inno/ISCC.exe'
if (Test-Path -LiteralPath $compiler) { return $compiler }
$archive = Join-Path $root '.tools/downloads/innosetup-6.7.3.exe'
New-Item -ItemType Directory -Path (Split-Path $archive) -Force | Out-Null
if (-not (Test-Path -LiteralPath $archive)) {
    & curl.exe --fail --location --retry 3 --output $archive 'https://github.com/jrsoftware/issrc/releases/download/is-6_7_3/innosetup-6.7.3.exe'
    if ($LASTEXITCODE -ne 0) { throw 'Inno Setup download failed.' }
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne '9c73c3bae7ed48d44112a0f48e66742c00090bdb5bef71d9d3c056c66e97b732') { throw 'Inno Setup SHA-256 mismatch.' }
$process = Start-Process -FilePath $archive -ArgumentList ('/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /CURRENTUSER /NOICONS /DIR="' + (Join-Path $root '.tools/inno') + '"') -WindowStyle Hidden -PassThru -Wait
if (-not (Test-Path -LiteralPath $compiler)) { throw 'Inno Setup installation failed.' }
return $compiler
