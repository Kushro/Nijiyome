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
(`System.getProperty("compose.application.resources.dir")/temple-lsfg`, `.exe` on Windows).

| What | How | Artifact |
|---|---|---|
| temple-lsfg (Link's LSFG helper, C++/Vulkan) | `linux/build-temple-lsfg.sh` (CMake); on Windows `windows/build-windows.ps1` runs CMake | `build/temple-lsfg/temple-lsfg[.exe]` |
| AppImage | `linux/build-appimage.sh [--image <app image>] [--out <dir>] [--tools <dir>]`: `install-tree.sh` layout + `AppRun` + appimagetool and the type2 runtime, downloaded and checked against `appimage-tools.lock.json` | `Nijiyome-v<version>-linux-x86_64.AppImage` |
| Arch / CachyOS package | `cd linux && makepkg` (never `-i` from a script): Gradle + CMake from this checkout, `install-tree.sh` into `/usr/lib/nijiyome` | `nijiyome-<version>-1-x86_64.pkg.tar.zst` |
| Windows installer | `windows/build-windows.ps1` (from a Developer PowerShell): image, then `makensis windows.nsi` | `Nijiyome-v<version>-windows-x64-setup.exe` |
| Windows portable | `windows/build-windows.ps1`: the image folder zipped (data still in `%APPDATA%\Nijiyome`) | `Nijiyome-v<version>-windows-x64-portable.zip` |

Link handlers: the Linux desktop entry declares `x-scheme-handler/nijiyome`, `x-scheme-handler/magnet` and
`application/x-bittorrent` (a desktop applies them when the package is installed; nothing registers itself at run time). The Windows
installer registers `nijiyome:` and offers `magnet:` / `.torrent` under "Default apps" (never taking them from another client); the
uninstaller removes those keys and the app's autostart value, never `%APPDATA%\Nijiyome`.

Natives inside the package: the Java runtime (jlink), libmpv (MediaMP's runtime jar, unpacked under the cache folder the first time),
libtorrent4j (its jar, glibc 2.38 or newer on Linux), the tray (ComposeNativeTray's jar), temple-lsfg (resources). On Linux the app
starts itself once more with the runtime's `lib/libjsig.so` preloaded (desktopApp `SignalChaining.kt`, needed by libtorrent4j).

The `*.lock.json` files pin third-party downloads by SHA-256: `appimage-tools.lock.json` (used by `build-appimage.sh`) and, from the
former Rust desktop app, libmpv / FFmpeg / Anime4K / FSR / fonts (`fetch-deps.ps1`, `linux/fetch-deps.sh`), not used by the Compose
packaging today.
