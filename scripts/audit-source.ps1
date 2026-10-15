[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $paths = @(git -c core.quotepath=false diff --cached --name-only --diff-filter=ACM)
    if ($LASTEXITCODE -ne 0 -or -not $paths.Count) { throw 'No staged source files to audit.' }
    $findings = [Collections.Generic.List[string]]::new()
    $patterns = @(
        '-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
        '\bgh[pousr]_[A-Za-z0-9]{30,}\b',
        '\bgithub_pat_[A-Za-z0-9_]{40,}\b',
        '\bAKIA[0-9A-Z]{16}\b',
        '\bsk-[A-Za-z0-9_-]{24,}\b',
        '(?i)(?:password|api[_-]?key|access[_-]?token|client[_-]?secret)\s*[:=]\s*["''][^"''\s]{12,}["'']',
        '(?i)[A-Z]:[\\/]Users[\\/](?!Public[\\/]|<)[^\\/\s]+'
    )
    foreach ($path in $paths) {
        if ($path -match '(^|/)(\.tools|\.runtime|\.gradle|\.idea|validation|research|analysis|build|dist)/|(^|/)\.env($|\.)|\.(exe|zip|pfx|pem|key|log)$') { $findings.Add("Forbidden staged path: $path"); continue }
        $item = Get-Item -LiteralPath $path
        if ($item.Length -gt 10MB) { $findings.Add("Large staged file: $path") }
        if ($path -match '\.(png|ico)$') { continue }
        # Audit the staged blob, not just the working tree.
        $content = (git show ":$path") -join "`n"
        foreach ($pattern in $patterns) {
            if ($content -match $pattern) { $findings.Add("Review potential sensitive content: $path (pattern $($patterns.IndexOf($pattern) + 1))") }
        }
    }
    git diff --cached --check
    if ($LASTEXITCODE -ne 0) { $findings.Add('Staged whitespace errors.') }
    if ($findings.Count) { $findings | ForEach-Object { Write-Host $_ }; throw 'Source audit requires review. No secret values were printed.' }
    Write-Host "Staged source audit passed ($($paths.Count) files). Heuristic scan; manual review is still required."
} finally { Pop-Location }
