#!/usr/bin/env bash
# Builds Nijiyome-v<version>-linux-x86_64.AppImage from the Compose Desktop app image (jpackage + jlink runtime).
#
# The AppDir is install-tree.sh's layout (usr/lib/nijiyome = the app image, usr/share = desktop entry, metainfo, icons, licences)
# plus AppRun, the desktop entry and the icon at its root. Nothing from the build host is deployed: the Java runtime comes from
# jlink, libmpv from MediaMP's runtime jar, libtorrent4j's native from its jar (glibc 2.38 or newer), temple-lsfg is built here.
# appimagetool and the type2 runtime are downloaded once into the tools folder and checked against packaging/appimage-tools.lock.json.
#
# Build host: CI uses Ubuntu 24.04. Needs bash, curl, sha256sum, python3 (reads the lock), a JDK 21 for Gradle (unless --image),
# and cmake + a C++20 compiler for temple-lsfg (unless --lsfg / --no-lsfg).
#
# Usage: build-appimage.sh [--image <app image dir>] [--lsfg <built temple-lsfg> | --no-lsfg] [--out <dir>] [--tools <dir>]
#                          [--version <x.y.z>]
#   --image   use an image built already (…/desktopApp/build/compose/binaries/main-release/app/Nijiyome); otherwise Gradle builds it
#   --tools   where appimagetool and the runtime are kept (default: ${XDG_CACHE_HOME:-~/.cache}/nijiyome-packaging); it must be on a
#             disk that can run programs
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
id="app.nijiyome"

image=""
lsfg=""
no_lsfg=0
out="$repo/build/appimage"
tools="${XDG_CACHE_HOME:-$HOME/.cache}/nijiyome-packaging"
version=""
while [ $# -gt 0 ]; do
    case "$1" in
        --image) image="$2"; shift 2 ;;
        --lsfg) lsfg="$2"; shift 2 ;;
        --no-lsfg) no_lsfg=1; shift ;;
        --out) out="$2"; shift 2 ;;
        --tools) tools="$2"; shift 2 ;;
        --version) version="$2"; shift 2 ;;
        -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done
for tool in curl sha256sum python3; do
    command -v "$tool" >/dev/null || { echo "$tool is required" >&2; exit 1; }
done

if [ -z "$version" ]; then
    version="${NIJIYOME_VERSION_NAME:-$(sed -n 's/^nijiyome\.version=\(.*\)$/\1/p' "$repo/gradle.properties" | head -n1)}"
    version="${version#v}"
fi
[ -n "$version" ] || { echo "cannot read nijiyome.version from gradle.properties" >&2; exit 1; }

if [ -z "$image" ]; then
    if [ -z "$lsfg" ] && [ "$no_lsfg" -eq 0 ]; then
        lsfg="$(bash "$here/build-temple-lsfg.sh" | tail -n1)"
    fi
    fg_lib="${NIJIYOME_DESKTOP_FRAMEGEN:-}"
    if [ -z "$fg_lib" ] && [ "$no_lsfg" -eq 0 ] && [ -d "$repo/native/desktop-framegen" ]; then
        fg_lib="$(bash "$here/build-desktop-framegen.sh" | tail -n1)"
    fi
    (cd "$repo" && NIJIYOME_TEMPLE_LSFG="$lsfg" NIJIYOME_DESKTOP_FRAMEGEN="$fg_lib" NIJIYOME_VERSION_NAME="$version" \
        bash gradlew :desktopApp:createReleaseDistributable --no-daemon --console=plain)
    image="$repo/desktopApp/build/compose/binaries/main-release/app/Nijiyome"
fi
[ -f "$image/bin/Nijiyome" ] || { echo "app image not found: $image" >&2; exit 1; }

# --- tools (pinned) ----------------------------------------------------------------------------------------------------------
mkdir -p "$tools"
fetch() {
    local name="$1" line url file sum
    line="$(python3 -I -c 'import json,sys; t=[x for x in json.load(open(sys.argv[1]))["tools"] if x["name"]==sys.argv[2]][0]; print(t["url"], t["file"], t["sha256"])' \
        "$repo/packaging/appimage-tools.lock.json" "$name")"
    read -r url file sum <<<"$line"
    if [ ! -f "$tools/$file" ] || ! echo "$sum  $tools/$file" | sha256sum -c --quiet - 2>/dev/null; then
        curl -fsSL --retry 3 -o "$tools/$file.partial" "$url"
        echo "$sum  $tools/$file.partial" | sha256sum -c --quiet - || { echo "$file: hash mismatch (left as $tools/$file.partial)" >&2; exit 1; }
        mv -f -- "$tools/$file.partial" "$tools/$file"
    fi
    chmod +x "$tools/$file"
    printf '%s\n' "$tools/$file"
}
appimagetool="$(fetch appimagetool)"
runtime="$(fetch type2-runtime)"

# --- AppDir ------------------------------------------------------------------------------------------------------------------
mkdir -p "$out"
work="$(mktemp -d "$out/build.XXXXXX")"
appdir="$work/AppDir"
mkdir -p "$appdir"
bash "$here/install-tree.sh" --image "$image" --root "$appdir" --prefix /usr
install -m0755 "$here/AppRun" "$appdir/AppRun"
install -m0644 "$here/$id.desktop" "$appdir/$id.desktop"
install -m0644 "$repo/packaging/icons/nijiyome-256.png" "$appdir/$id.png"
ln -sfn "$id.png" "$appdir/.DirIcon"
# What the AppImage carries, checked here so a broken image never ships.
[ -f "$appdir/usr/lib/nijiyome/lib/runtime/lib/libjsig.so" ] || { echo "libjsig.so missing from the runtime" >&2; exit 1; }
ls "$appdir"/usr/lib/nijiyome/lib/app/libtorrent4j-linux-*.jar >/dev/null 2>&1 || echo "note: no libtorrent4j Linux native jar in the image" >&2
ls "$appdir"/usr/lib/nijiyome/lib/app/mediamp-mpv-*linux*.jar "$appdir"/usr/lib/nijiyome/lib/app/*mpv*runtime*linux*.jar >/dev/null 2>&1 \
    || echo "note: no MediaMP Linux runtime jar matched (check lib/app)" >&2
[ -f "$appdir/usr/lib/nijiyome/lib/app/resources/temple-lsfg" ] || echo "note: shipping without temple-lsfg (Link LSFG profiles unavailable)" >&2

# --- pack --------------------------------------------------------------------------------------------------------------------
target="$out/Nijiyome-v$version-linux-x86_64.AppImage"
# No FUSE on CI runners (and inside containers): appimagetool runs extracted.
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 VERSION="$version" \
    "$appimagetool" --no-appstream --runtime-file "$runtime" "$appdir" "$target"
echo "AppImage: $target"
