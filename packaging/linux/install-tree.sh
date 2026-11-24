#!/usr/bin/env bash
# Installs the desktop app image (Compose Desktop / jpackage, `:desktopApp:createReleaseDistributable`) into a staging root.
# Shared by the PKGBUILD (root = $pkgdir) and build-appimage.sh (root = the AppDir):
#
#   <prefix>/lib/nijiyome/bin/Nijiyome                     the jpackage launcher
#   <prefix>/lib/nijiyome/lib/app/*.jar                    the app (libmpv / MediaMP and libtorrent4j natives inside their jars)
#   <prefix>/lib/nijiyome/lib/app/resources/temple-lsfg    Link's LSFG helper (when the image was built with NIJIYOME_TEMPLE_LSFG)
#   <prefix>/lib/nijiyome/lib/runtime/                     the jlink runtime (lib/libjsig.so included: signal chaining)
#   <prefix>/bin/nijiyome -> ../lib/nijiyome/bin/Nijiyome
#   <prefix>/share/{applications,metainfo,icons/hicolor}   desktop entry (nijiyome:, magnet:, .torrent), AppStream, icons
#   <prefix>/share/licenses/nijiyome/                      AGPL text, third-party notices, temple-lsfg licences
#
# Usage: install-tree.sh --image <app image dir (…/app/Nijiyome)> --root <staging root> [--prefix /usr]
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(cd "$here/../.." && pwd)"

image=""
root=""
prefix="/usr"
while [ $# -gt 0 ]; do
    case "$1" in
        --image) image="$2"; shift 2 ;;
        --root) root="$2"; shift 2 ;;
        --prefix) prefix="$2"; shift 2 ;;
        -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done
[ -n "$image" ] && [ -f "$image/bin/Nijiyome" ] || { echo "--image <app image with bin/Nijiyome> is required" >&2; exit 2; }
[ -n "$root" ] || { echo "--root <staging root> is required" >&2; exit 2; }
[ -f "$image/lib/runtime/lib/libjsig.so" ] || { echo "the image's runtime has no lib/libjsig.so (signal chaining)" >&2; exit 1; }

id="app.nijiyome"
base="$root$prefix"
lib="$base/lib/nijiyome"

install -d "$lib"
cp -a "$image/." "$lib/"
# Executables of the image (a build on a disk without exec bits loses them): the launcher, the runtime's tools and jspawnhelper,
# which the JDK uses to start every child process on Linux (mpv, ffmpeg, the signal-chaining relaunch).
chmod 0755 "$lib/bin/Nijiyome"
find "$lib/lib/runtime/bin" -type f -exec chmod 0755 {} + 2>/dev/null || true
for f in "$lib/lib/runtime/lib/jspawnhelper" "$lib/lib/runtime/lib/jexec"; do
    if [ -f "$f" ]; then chmod 0755 "$f"; fi
done
if [ -f "$lib/lib/app/resources/temple-lsfg" ]; then chmod 0755 "$lib/lib/app/resources/temple-lsfg"; fi
install -d "$base/bin"
ln -sfn ../lib/nijiyome/bin/Nijiyome "$base/bin/nijiyome"

install -Dm0644 "$here/$id.desktop" "$base/share/applications/$id.desktop"
install -Dm0644 "$here/$id.metainfo.xml" "$base/share/metainfo/$id.metainfo.xml"
for size in 16 32 48 64 128 256 512; do
    install -Dm0644 "$repo/packaging/icons/nijiyome-$size.png" "$base/share/icons/hicolor/${size}x${size}/apps/$id.png"
done
install -Dm0644 "$repo/packaging/icons/nijiyome.svg" "$base/share/icons/hicolor/scalable/apps/$id.svg"

lic="$base/share/licenses/nijiyome"
install -Dm0644 "$repo/LICENSE" "$lic/LICENSE"
install -m0644 "$repo/THIRD_PARTY_NOTICES.md" "$lic/THIRD_PARTY_NOTICES.md"
if [ -f "$lib/lib/app/resources/temple-lsfg" ]; then
    # GPL-3.0 combined work: its corresponding source is native/temple-lsfg of this repository.
    tl="$repo/native/temple-lsfg"
    install -Dm0644 "$tl/README.md" "$lic/temple-lsfg/README.md"
    install -m0644 "$tl/LICENSE" "$tl/LICENSE-GPL-3.0.txt" "$lic/temple-lsfg/"
    install -m0644 "$tl/third_party/README.md" "$lic/temple-lsfg/third_party-README.md"
    install -m0644 "$tl/third_party/lsfg-vk/LICENSE.md" "$lic/temple-lsfg/lsfg-vk-LICENSE.md"
    install -m0644 "$tl/third_party/volk/LICENSE.md" "$lic/temple-lsfg/volk-LICENSE.md"
    install -m0644 "$tl/third_party/pe-parse/LICENSE" "$lic/temple-lsfg/pe-parse-LICENSE"
    install -m0644 "$tl/third_party/vulkan-headers/LICENSE-Apache-2.0.txt" "$lic/temple-lsfg/vulkan-headers-LICENSE-Apache-2.0.txt"
fi
echo "Installed Nijiyome into $root (prefix $prefix)"
