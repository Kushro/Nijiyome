# packaging/

Packaging of the desktop app (Compose Desktop) for Linux and Windows. Everything starts from the app image that Gradle builds with
jlink + jpackage:

    bash gradlew :desktopApp:createReleaseDistributable
    # -> desktopApp/build/compose/binaries/main-release/app/Nijiyome
    #    Linux:   bin/Nijiyome, lib/app/*.jar, lib/app/resources/, lib/runtime/ (lib/libjsig.so included)
    #    Windows: Nijiyome.exe, app\*.jar, app\resources\, runtime\

The version is `nijiyome.version` in `gradle.properties` (CI passes the tag as `NIJIYOME_VERSION_NAME`). The public client ids of
the desktop OAuth clients and of the Discord application come from `local.properties` or the environment at build time
(`NIJIYOME_ANILIST_CLIENT_ID_DESKTOP`, `NIJIYOME_MAL_CLIENT_ID_DESKTOP`, `NIJIYOME_DISCORD_APP_ID`); without one, that feature is
disabled in the build and says so. With `NIJIYOME_TEMPLE_LSFG=<built temple-lsfg>` the helper goes into the image's resources
(`System.getProperty("compose.application.resources.dir")/temple-lsfg`, `.exe` on Windows). With `NIJIYOME_DESKTOP_UPSCALE=<built
library>` (or a library left in `build/desktop-upscale/`) the GPU image upscaler goes there too, as `libnijiyome_upscale.so` /
`nijiyome_upscale.dll`, with `upscale-models/` (the three `realesr-animevideov3-x{2,3,4}.param` files and a single `-x4.bin`; the
engine uses it for every scale) and `upscale-licenses/` (ncnn, glslang, Real-ESRGAN, realesrgan-ncnn-vulkan).

| What | How | Artifact |
|---|---|---|
| temple-lsfg (Link's LSFG helper, C++/Vulkan) | `linux/build-temple-lsfg.sh` (CMake); on Windows `windows/build-windows.ps1` runs CMake | `build/temple-lsfg/temple-lsfg[.exe]` |
| desktop-upscale (GPU image upscaling, Real-ESRGAN on ncnn + Vulkan, JNI) | `linux/build-desktop-upscale.sh [--jobs <n>]` or `windows/build-desktop-upscale.ps1` (CMake; fetches the pinned ncnn submodule and its glslang on first use; that first build is slow); `build-appimage.sh` and `build-windows.ps1` run them unless `--no-upscale` / `-NoUpscale` | `build/desktop-upscale/libnijiyome_upscale.so`, `nijiyome_upscale.dll` |
| AppImage | `linux/build-appimage.sh [--image <app image>] [--out <dir>] [--tools <dir>]`: `install-tree.sh` layout + `AppRun` + appimagetool and the type2 runtime, downloaded and checked against `appimage-tools.lock.json` | `Nijiyome-v<version>-linux-x86_64.AppImage` |
| Arch / CachyOS package | `cd linux && makepkg` (never `-i` from a script): Gradle + CMake from this checkout, `install-tree.sh` into `/usr/lib/nijiyome` | `nijiyome-<version>-1-x86_64.pkg.tar.zst` |
| Windows installer | `windows/build-windows.ps1` (from a Developer PowerShell): image, then `makensis windows.nsi` | `Nijiyome-v<version>-windows-x64-setup.exe` |
| Windows portable | `windows/build-windows.ps1`: the image folder zipped (data still in `%APPDATA%\Nijiyome`) | `Nijiyome-v<version>-windows-x64-portable.zip` |

Link handlers: the Linux desktop entry declares `x-scheme-handler/nijiyome`, `x-scheme-handler/magnet` and
`application/x-bittorrent` (a desktop applies them when the package is installed; nothing registers itself at run time). The Windows
installer registers `nijiyome:` and offers `magnet:` / `.torrent` under "Default apps" (never taking them from another client); the
uninstaller removes those keys and the app's autostart value, never `%APPDATA%\Nijiyome`.

Natives inside the package: the Java runtime (jlink), libmpv (MediaMP's runtime jar, unpacked under the cache folder the first time),
libtorrent4j (its jar, glibc 2.38 or newer on Linux), the tray (ComposeNativeTray's jar), temple-lsfg, desktop-framegen and
desktop-upscale (resources). On Linux the app starts itself once more with the runtime's `lib/libjsig.so` preloaded (desktopApp
`SignalChaining.kt`, needed by libtorrent4j).

The `*.lock.json` files pin third-party downloads by SHA-256: `appimage-tools.lock.json` (used by `build-appimage.sh`) and, from the
former Rust desktop app, libmpv / FFmpeg / Anime4K / FSR / fonts (`fetch-deps.ps1`, `linux/fetch-deps.sh`), not used by the Compose
packaging today.
