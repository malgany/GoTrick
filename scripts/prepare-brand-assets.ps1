[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$brandingRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'branding'
$assetRoot = Join-Path $brandingRoot 'assets'
New-Item -ItemType Directory -Path $assetRoot -Force | Out-Null
Add-Type -AssemblyName System.Drawing

# Packaging conversions only: the original generated artwork is kept untouched.
function Save-ScaledPng([Drawing.Image]$Source, [int]$Width, [int]$Height, [string]$Path) {
    $bitmap = [Drawing.Bitmap]::new($Width, $Height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.Clear([Drawing.Color]::Transparent)
        $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($Source, [Drawing.Rectangle]::new(0, 0, $Width, $Height))
        $bitmap.Save($Path, [Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}

$logo = [Drawing.Image]::FromFile((Join-Path $brandingRoot 'logo-source.png'))
try {
    foreach ($size in @(16,20,24,32,40,48,64,128,256,512)) {
        Save-ScaledPng $logo $size $size (Join-Path $assetRoot "icon-$size.png")
    }
} finally { $logo.Dispose() }
$splash = [Drawing.Image]::FromFile((Join-Path $brandingRoot 'splash-source.png'))
try {
    Save-ScaledPng $splash 720 480 (Join-Path $assetRoot 'splash.png')
    Save-ScaledPng $splash 1440 960 (Join-Path $assetRoot 'splash@2x.png')
} finally { $splash.Dispose() }

# SVG containers retain the actual generated raster artwork; no substitute mark.
foreach ($variant in @(@('icon.svg',128,256), @('icon-small.svg',16,32))) {
    $png = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $assetRoot "icon-$($variant[2]).png")))
    $side = $variant[1]
    $svg = '<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="' + $side + '" height="' + $side + '" viewBox="0 0 ' + $side + ' ' + $side + '"><image width="' + $side + '" height="' + $side + '" xlink:href="data:image/png;base64,' + $png + '"/></svg>'
    [IO.File]::WriteAllText((Join-Path $assetRoot $variant[0]), $svg, [Text.UTF8Encoding]::new($false))
}

$sizes = @(16,20,24,32,40,48,64,128,256)
$ico = [IO.File]::Create((Join-Path $assetRoot 'GoTrick.ico'))
$writer = [IO.BinaryWriter]::new($ico)
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    $frames = foreach ($size in $sizes) { ,([IO.File]::ReadAllBytes((Join-Path $assetRoot "icon-$size.png"))) }
    for ($i=0; $i -lt $sizes.Count; $i++) {
        $dimension = if ($sizes[$i] -eq 256) { 0 } else { $sizes[$i] }
        $writer.Write([byte]$dimension); $writer.Write([byte]$dimension)
        $writer.Write([byte]0); $writer.Write([byte]0)
        $writer.Write([uint16]1); $writer.Write([uint16]32)
        $writer.Write([uint32]$frames[$i].Length); $writer.Write([uint32]$offset)
        $offset += $frames[$i].Length
    }
    foreach ($frame in $frames) { $writer.Write([byte[]]$frame) }
} finally { $writer.Dispose(); $ico.Dispose() }
Write-Host "GoTrick image assets prepared: $assetRoot"
