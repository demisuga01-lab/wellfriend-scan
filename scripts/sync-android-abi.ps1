[CmdletBinding()]
param([string]$SourceRoot = (Join-Path $PSScriptRoot "..\..\wellfriend-perception\target\wellfriend-android"))
$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$source = (Resolve-Path $SourceRoot).Path
$manifest = Get-Content -Raw (Join-Path $source "manifest.json") | ConvertFrom-Json
$checksums = Get-Content -Raw (Join-Path $source "checksums.json") | ConvertFrom-Json
if ($manifest.schema_version -ne 1 -or $manifest.artifact_kind -ne "wellfriend-android-abi" -or $manifest.source_sha -notmatch '^[0-9a-f]{40}$' -or $manifest.page_size_alignment_bytes -ne 16384) { throw "Invalid Android runtime artifact manifest or 16 KiB page-size declaration" }
$expected = @("arm64-v8a/libwellfriend_perception.so", "arm64-v8a/libwellfriend_perception_jni.so", "x86_64/libwellfriend_perception.so", "x86_64/libwellfriend_perception_jni.so")
foreach ($relative in $expected) {
    $record = $manifest.libraries | Where-Object { $_.file -eq $relative } | Select-Object -First 1
    $checksum = $checksums.files | Where-Object { $_.path -eq $relative } | Select-Object -First 1
    $path = Join-Path $source $relative
    if (-not $record -or -not $checksum -or -not (Test-Path $path)) { throw "Missing expected Android runtime artifact: $relative" }
    $actual = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $record.sha256 -or $actual -ne $checksum.sha256) { throw "Checksum mismatch: $relative" }
    $dest = Join-Path $repo "android\app\src\main\jniLibs\$relative"
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    Copy-Item -Force $path $dest
}
$asset = Join-Path $repo "android\app\src\main\assets\wellfriend-runtime\android"
New-Item -ItemType Directory -Force -Path $asset | Out-Null
Copy-Item -Force (Join-Path $source "manifest.json") (Join-Path $asset "manifest.json")
Copy-Item -Force (Join-Path $source "checksums.json") (Join-Path $asset "checksums.json")
Write-Host "Synced verified Android ABI artifact from $($manifest.source_sha)"
