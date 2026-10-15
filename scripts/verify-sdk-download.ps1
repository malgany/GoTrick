[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $root 'validation/sdk-probe-classes'
$destination = Join-Path $root ('.runtime/sdk-validation/' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$classpath = "$root/.tools/idea/lib/*;$root/plugin/build/classes/kotlin/main"
& "$root/.tools/jdk/bin/javac.exe" -cp $classpath -d $classes "$PSScriptRoot/SdkDownloadProbe.java"
if ($LASTEXITCODE -ne 0) { throw 'SDK probe compilation failed.' }
& "$root/.tools/idea/jbr/bin/java.exe" -cp "$classpath;$classes" SdkDownloadProbe $destination
if ($LASTEXITCODE -ne 0) { throw 'SDK download validation failed.' }
