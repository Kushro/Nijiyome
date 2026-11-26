#!/usr/bin/env bash
# Builds the libnijiyome_fg_desktop.so JNI library (native/desktop-framegen, C++/Vulkan/GL interop)
# used by the embedded desktop player for zero-copy in-app LSFG 3.1 / 3.1P frame generation.
# Needs cmake (3.20+), a C++20 compiler (GCC 11+ / Clang 14+), and JAVA_HOME (JNI headers).
# Prints the path of the built shared library on the last line of stdout.
#
# Usage: build-desktop-framegen.sh [--out <build dir>]   (default: <repo>/build/desktop-framegen)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app="$(cd "$here/../.." && pwd)"
src="$app/native/desktop-framegen"
out="$app/build/desktop-framegen"
while [ $# -gt 0 ]; do
    case "$1" in
        --out) out="$2"; shift 2 ;;
        -h|--help) sed -n '2,7p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done
command -v cmake >/dev/null || { echo "cmake is required to build desktop-framegen" >&2; exit 1; }

generator=()
if command -v ninja >/dev/null; then generator=(-G Ninja); fi
cmake -S "$src" -B "$out" "${generator[@]}" -DCMAKE_BUILD_TYPE=Release >&2
cmake --build "$out" --parallel >&2
[ -f "$out/libnijiyome_fg_desktop.so" ] || { echo "libnijiyome_fg_desktop.so was not built in $out" >&2; exit 1; }
printf '%s\n' "$out/libnijiyome_fg_desktop.so"
