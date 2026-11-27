#Requires -Version 7.0
# SPDX-License-Identifier: AGPL-3.0-or-later
<#
.SYNOPSIS
  Builds the Windows installer and the portable zip of Nijiyome from the Compose Desktop app image.

.DESCRIPTION
  1. temple-lsfg.exe (native/temple-lsfg, C++/Vulkan) and nijiyome_fg_desktop.dll with CMake, unless -NoLsfg or -Lsfg <built exe>;
     nijiyome_upscale.dll (native/upscale, ncnn + Vulkan) with build-desktop-upscale.ps1, unless -NoUpscale or
     $env:NIJIYOME_DESKTOP_UPSCALE points at a built DLL.
  2. The app image: Gradle :desktopApp:createReleaseDistributable (jlink runtime, jars; the natives, the upscale models and their
     licences into app\resources).
  3. Nijiyome-v<version>-windows-x64-portable.zip: the image folder as is (data still in %APPDATA%\Nijiyome).
  4. Nijiyome-v<version>-windows-x64-setup.exe: packaging/windows.nsi over the same folder (makensis on PATH or the default
     NSIS folder).
  Run from a Developer PowerShell (MSVC) on Windows x64, with JAVA_HOME pointing at a JDK 21. Writes into build/windows.

.PARAMETER Version
  Version of the artifacts (default: nijiyome.version of gradle.properties, or $env:NIJIYOME_VERSION_NAME).
#>
[CmdletBinding()]
param(
    [string] $Version,
    [string] $Lsfg,
    [switch] $NoLsfg,
    [switch] $NoUpscale,
    [string] $Out
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $Out) { $Out = Join-Path $repo 'build\windows' }
New-Item -ItemType Directory -Force -Path $Out | Out-Null

if (-not $Version) { $Version = $env:NIJIYOME_VERSION_NAME }
if (-not $Version) {
    $Version = (Select-String -Path (Join-Path $repo 'gradle.properties') -Pattern '^nijiyome\.version=(.+)$').Matches[0].Groups[1].Value
}
$Version = $Version.Trim().TrimStart('v')
if ($Version -notmatch '^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$') { throw "not semver: $Version" }

# --- temple-lsfg ----------------------------------------------------------------------------------------------------------------
if (-not $NoLsfg -and -not $Lsfg) {
    $build = Join-Path $repo 'build\temple-lsfg'
    if (Get-Command ninja -ErrorAction SilentlyContinue) {
        cmake -S (Join-Path $repo 'native\temple-lsfg') -B $build -G Ninja -DCMAKE_BUILD_TYPE=Release
    } else {
        cmake -S (Join-Path $repo 'native\temple-lsfg') -B $build -G 'Visual Studio 17 2022' -A x64
    }
    if ($LASTEXITCODE -ne 0) { throw "cmake configure failed ($LASTEXITCODE)" }
    cmake --build $build --config Release --parallel
    if ($LASTEXITCODE -ne 0) { throw "cmake build failed ($LASTEXITCODE)" }
    $exe = Get-ChildItem $build -Recurse -Filter temple-lsfg.exe | Select-Object -First 1
    if (-not $exe) { throw 'temple-lsfg.exe was not built' }
    $Lsfg = $exe.FullName
}

# --- desktop-framegen (embedded zero-copy D3D12 <-> Vulkan LSFG JNI library) ----------------------------------------------------
$FgLib = $env:NIJIYOME_DESKTOP_FRAMEGEN
if (-not $NoLsfg -and -not $FgLib -and (Test-Path (Join-Path $repo 'native\desktop-framegen'))) {
    $fgBuild = Join-Path $repo 'build\desktop-framegen'
    if (Get-Command ninja -ErrorAction SilentlyContinue) {
        cmake -S (Join-Path $repo 'native\desktop-framegen') -B $fgBuild -G Ninja -DCMAKE_BUILD_TYPE=Release
    } else {
        cmake -S (Join-Path $repo 'native\desktop-framegen') -B $fgBuild -G 'Visual Studio 17 2022' -A x64
    }
    if ($LASTEXITCODE -ne 0) { throw "desktop-framegen cmake configure failed ($LASTEXITCODE)" }
    cmake --build $fgBuild --config Release --parallel
    if ($LASTEXITCODE -ne 0) { throw "desktop-framegen cmake build failed ($LASTEXITCODE)" }
    $dll = Get-ChildItem $fgBuild -Recurse -Filter nijiyome_fg_desktop.dll | Select-Object -First 1
    if (-not $dll) { throw 'nijiyome_fg_desktop.dll was not built' }
    $FgLib = $dll.FullName
}

# --- desktop-upscale (GPU image upscaling JNI library; first build compiles ncnn and glslang) ------------------------------------
$UpLib = $env:NIJIYOME_DESKTOP_UPSCALE
if (-not $NoUpscale -and -not $UpLib -and (Test-Path (Join-Path $repo 'native\upscale'))) {
    $UpLib = (& (Join-Path $PSScriptRoot 'build-desktop-upscale.ps1') | Select-Object -Last 1)
}
if ($NoUpscale) { $UpLib = '' }

# --- app image ------------------------------------------------------------------------------------------------------------------
Push-Location $repo
try {
    $env:NIJIYOME_TEMPLE_LSFG = if ($Lsfg) { $Lsfg } else { '' }
    $env:NIJIYOME_DESKTOP_FRAMEGEN = if ($FgLib) { $FgLib } else { '' }
    $env:NIJIYOME_DESKTOP_UPSCALE = if ($UpLib) { $UpLib } else { '' }
    $env:NIJIYOME_VERSION_NAME = $Version
    & .\gradlew.bat :desktopApp:createReleaseDistributable --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "gradle failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}
$image = Join-Path $repo 'desktopApp\build\compose\binaries\main-release\app\Nijiyome'
foreach ($f in 'Nijiyome.exe', 'runtime\bin\server\jvm.dll', 'app\resources\LICENSE') {
    if (-not (Test-Path (Join-Path $image $f))) { throw "the app image is missing $f" }
}
if (Test-Path (Join-Path $image 'app\resources\nijiyome_upscale.dll')) {
    foreach ($m in 'x2.param', 'x3.param', 'x4.param', 'x4.bin') {
        if (-not (Test-Path (Join-Path $image "app\resources\upscale-models\realesr-animevideov3-$m"))) { throw "the app image is missing the upscale model realesr-animevideov3-$m" }
    }
} else {
    Write-Warning 'shipping without nijiyome_upscale.dll (GPU image upscaling unavailable)'
}
if (-not (Test-Path (Join-Path $image 'Nijiyome.ico'))) { Copy-Item (Join-Path $repo 'packaging\icons\nijiyome.ico') (Join-Path $image 'Nijiyome.ico') }

# --version prints before any window, data folder or lock exists.
$versionOut = Join-Path $Out 'version.txt'
$p = Start-Process -FilePath (Join-Path $image 'Nijiyome.exe') -ArgumentList '--version' -Wait -PassThru -NoNewWindow -RedirectStandardOutput $versionOut
$text = Get-Content $versionOut -Raw
Write-Host "exit=$($p.ExitCode) output=$text"
if ($p.ExitCode -ne 0 -or $text -notmatch "^nijiyome $([regex]::Escape($Version))") { Write-Warning '--version smoke check did not answer as expected (a GUI launcher may not print to the console)' }

# --- portable zip -----------------------------------------------------------------------------------------------------------------
$zip = Join-Path $Out "Nijiyome-v$Version-windows-x64-portable.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $image -DestinationPath $zip -CompressionLevel Optimal

# --- installer --------------------------------------------------------------------------------------------------------------------
$makensis = (Get-Command makensis -ErrorAction SilentlyContinue).Source
if (-not $makensis) { $makensis = 'C:\Program Files (x86)\NSIS\makensis.exe' }
if (-not (Test-Path $makensis)) { throw 'makensis not found (install NSIS 3)' }
$setup = Join-Path $Out "Nijiyome-v$Version-windows-x64-setup.exe"
$winver = ($Version -replace '-.*$', '') + '.0'
& $makensis /V2 "/DPAYLOAD=$image" "/DVERSION=$Version" "/DWINDOWS_VERSION=$winver" "/DOUTFILE=$setup" "/DLICENSE=$(Join-Path $repo 'LICENSE')" (Join-Path $repo 'packaging\windows.nsi')
if ($LASTEXITCODE -ne 0) { throw "makensis failed ($LASTEXITCODE)" }
Get-ChildItem $Out | Format-Table Name, Length
