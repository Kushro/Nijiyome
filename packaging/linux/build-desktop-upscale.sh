#!/usr/bin/env bash
# Builds the libnijiyome_upscale.so JNI library (native/upscale: Real-ESRGAN realesr-animevideov3 on ncnn + Vulkan)
# used by :core:upscale for GPU image upscaling. Needs cmake (3.20+), a C++20 compiler (GCC 11+ / Clang 14+), git (to
# fetch the pinned ncnn submodule on first use) and JAVA_HOME (JNI headers). The first build compiles ncnn and glslang
# and is the slow part; later builds reuse the build dir.
# Prints the path of the built shared library on the last line of stdout.
#
# Usage: build-desktop-upscale.sh [--out <build dir>] [--jobs <n>]   (default: <repo>/build/desktop-upscale, all cores)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app="$(cd "$here/../.." && pwd)"
src="$app/native/upscale"
ncnn="$src/third_party/ncnn"
out="$app/build/desktop-upscale"
jobs=""
while [ $# -gt 0 ]; do
    case "$1" in
        --out) out="$2"; shift 2 ;;
        --jobs) jobs="$2"; shift 2 ;;
        -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done
command -v cmake >/dev/null || { echo "cmake is required to build desktop-upscale" >&2; exit 1; }

# ncnn (tag 20261020) and only its glslang submodule; pybind11 is not needed.
if [ ! -f "$ncnn/CMakeLists.txt" ]; then
    git -C "$app" submodule update --init --depth 1 native/upscale/third_party/ncnn >&2
fi
if [ ! -f "$ncnn/glslang/CMakeLists.txt" ]; then
    git -C "$ncnn" submodule update --init --depth 1 glslang >&2
fi

generator=()
if command -v ninja >/dev/null; then generator=(-G Ninja); fi
cmake -S "$src" -B "$out" "${generator[@]}" -DCMAKE_BUILD_TYPE=Release >&2
if [ -n "$jobs" ]; then
    cmake --build "$out" --target nijiyome_upscale --parallel "$jobs" >&2
else
    cmake --build "$out" --target nijiyome_upscale --parallel >&2
fi
[ -f "$out/libnijiyome_upscale.so" ] || { echo "libnijiyome_upscale.so was not built in $out" >&2; exit 1; }
printf '%s\n' "$out/libnijiyome_upscale.so"
