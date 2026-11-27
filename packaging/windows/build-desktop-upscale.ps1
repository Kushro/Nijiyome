#Requires -Version 7.0
# SPDX-License-Identifier: AGPL-3.0-or-later
<#
.SYNOPSIS
  Builds nijiyome_upscale.dll (native/upscale: Real-ESRGAN realesr-animevideov3 on ncnn + Vulkan, JNI for :core:upscale).

.DESCRIPTION
  Windows twin of packaging/linux/build-desktop-upscale.sh, written like the desktop-framegen block of build-windows.ps1
  (Ninja when present, otherwise Visual Studio 17 2022 x64; static MSVC runtime). Fetches the pinned ncnn submodule and
  its glslang submodule on first use. The first build compiles ncnn and glslang and is the slow part.
  Run from a Developer PowerShell (MSVC) on Windows x64, with JAVA_HOME pointing at a JDK 21 (JNI headers).
  Writes the full path of the built DLL as the last line of output.

  build-windows.ps1 and CI call it like this (step 5 of docs/plan-upscaling.md wires it in):
    $UpLib = $env:NIJIYOME_DESKTOP_UPSCALE
    if (-not $UpLib) { $UpLib = (& (Join-Path $PSScriptRoot 'build-desktop-upscale.ps1') | Select-Object -Last 1) }
    $env:NIJIYOME_DESKTOP_UPSCALE = $UpLib

.PARAMETER Out
  Build directory (default: <repo>\build\desktop-upscale).
#>
[CmdletBinding()]
param(
    [string] $Out
)

# --- desktop-upscale (GPU image upscaling JNI library) -------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$src = Join-Path $repo 'native\upscale'
$ncnn = Join-Path $src 'third_party\ncnn'
if (-not $Out) { $Out = Join-Path $repo 'build\desktop-upscale' }

if (-not (Test-Path (Join-Path $ncnn 'CMakeLists.txt'))) {
    git -C $repo submodule update --init --depth 1 native/upscale/third_party/ncnn
    if ($LASTEXITCODE -ne 0) { throw "ncnn submodule fetch failed ($LASTEXITCODE)" }
}
if (-not (Test-Path (Join-Path $ncnn 'glslang\CMakeLists.txt'))) {
    git -C $ncnn submodule update --init --depth 1 glslang
    if ($LASTEXITCODE -ne 0) { throw "glslang submodule fetch failed ($LASTEXITCODE)" }
}

if (Get-Command ninja -ErrorAction SilentlyContinue) {
    cmake -S $src -B $Out -G Ninja -DCMAKE_BUILD_TYPE=Release | Write-Host
} else {
    cmake -S $src -B $Out -G 'Visual Studio 17 2022' -A x64 | Write-Host
}
if ($LASTEXITCODE -ne 0) { throw "desktop-upscale cmake configure failed ($LASTEXITCODE)" }
cmake --build $Out --config Release --target nijiyome_upscale --parallel | Write-Host
if ($LASTEXITCODE -ne 0) { throw "desktop-upscale cmake build failed ($LASTEXITCODE)" }
$dll = Get-ChildItem $Out -Recurse -Filter nijiyome_upscale.dll | Select-Object -First 1
if (-not $dll) { throw 'nijiyome_upscale.dll was not built' }
Write-Output $dll.FullName
# --- end desktop-upscale -------------------------------------------------------------------------------------------------------
