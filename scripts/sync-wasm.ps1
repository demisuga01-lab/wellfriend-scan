[CmdletBinding()]
param([string]$SourceRoot = (Join-Path $PSScriptRoot "..\..\wellfriend-perception\target\wellfriend-wasm"))
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$source = (Resolve-Path $SourceRoot).Path
$manifest = Get-Content -Raw (Join-Path $source "manifest.json") | ConvertFrom-Json
$checksums = Get-Content -Raw (Join-Path $source "checksums.json") | ConvertFrom-Json
if ($manifest.schema_version -ne 1 -or $manifest.artifact_kind -ne "wellfriend-wasm-package" -or $manifest.source_sha -notmatch '^[0-9a-f]{40}$') { throw "Invalid WASM runtime artifact manifest" }
$expected = @("wellfriend_perception_bg.wasm", "wellfriend_perception.js", "wellfriend_perception.d.ts", "package.json")
$destRoot = Join-Path $repo "web\public\wasm"; New-Item -ItemType Directory -Force -Path $destRoot | Out-Null
foreach ($relative in $expected) {
    $record = $manifest.files | Where-Object { $_.path -eq $relative } | Select-Object -First 1
    $checksum = $checksums.files | Where-Object { $_.path -eq $relative } | Select-Object -First 1
    $path = Join-Path $source $relative
    if (-not $record -or -not $checksum -or -not (Test-Path $path)) { throw "Missing expected WASM runtime artifact: $relative" }
    $actual = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $record.sha256 -or $actual -ne $checksum.sha256) { throw "Checksum mismatch: $relative" }
    Copy-Item -Force $path (Join-Path $destRoot $relative)
}
Copy-Item -Force (Join-Path $source "manifest.json") (Join-Path $destRoot "manifest.json")
Copy-Item -Force (Join-Path $source "checksums.json") (Join-Path $destRoot "checksums.json")
Write-Host "Synced verified browser WASM artifact from $($manifest.source_sha)"
