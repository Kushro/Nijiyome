# SPDX-License-Identifier: AGPL-3.0-or-later
#
# nijiyome_upscale: JNI library for GPU image upscaling (Real-ESRGAN realesr-animevideov3 x2/x3/x4 on ncnn + Vulkan).
# docs/plan-upscaling.md §4.1. One project for every platform:
#   - Linux   -> libnijiyome_upscale.so   (packaging/linux/build-desktop-upscale.sh)
#   - Windows -> nijiyome_upscale.dll     (packaging/windows/build-desktop-upscale.ps1, MSVC)
#   - Android -> libnijiyome_upscale.so   (:core:upscale-native externalNativeBuild, arm64-v8a and x86_64 only; on the
#                32-bit ABIs nothing is built and System.loadLibrary fails, so the feature reports no GPU)
#
# ncnn is the git submodule third_party/ncnn pinned to tag 20261020, built static with Vulkan (ncnn's in-house simplevk
# loader, so no Vulkan SDK is needed) and its bundled glslang (nested submodule third_party/ncnn/glslang). Only the
# layers realesr-animevideov3 and ncnn's Vulkan runtime need are compiled. There is no CPU inference path: the CPU only
# uploads pixels and reads results back.
cmake_minimum_required(VERSION 3.20)

project(nijiyome_upscale LANGUAGES C CXX)

set(NJUP_BUILD ON)
if(ANDROID)
    if(NOT (ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64"))
        message(STATUS "nijiyome_upscale: ABI ${ANDROID_ABI} not supported, nothing to build")
        set(NJUP_BUILD OFF)
    endif()
elseif(NOT WIN32 AND NOT CMAKE_SYSTEM_NAME STREQUAL "Linux")
    message(FATAL_ERROR "nijiyome_upscale supports Linux, Windows (MSVC) and Android only")
endif()

if(NJUP_BUILD)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../..")
set(NCNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn")
if(NOT EXISTS "${NCNN_DIR}/CMakeLists.txt" OR NOT EXISTS "${NCNN_DIR}/glslang/CMakeLists.txt")
    message(FATAL_ERROR
        "ncnn sources missing. Run:\n"
        "  git submodule update --init --depth 1 native/upscale/third_party/ncnn\n"
        "  git -C native/upscale/third_party/ncnn submodule update --init --depth 1 glslang")
endif()

# --- ncnn options -------------------------------------------------------------------------------------------------------------
macro(njup_force name value)
    set(${name} ${value} CACHE BOOL "" FORCE)
endmacro()
njup_force(NCNN_VULKAN ON)
njup_force(NCNN_SIMPLEVK ON)
njup_force(NCNN_SYSTEM_GLSLANG OFF)
njup_force(NCNN_SHARED_LIB OFF)
njup_force(NCNN_INSTALL_SDK OFF)
njup_force(NCNN_BUILD_TOOLS OFF)
njup_force(NCNN_BUILD_EXAMPLES OFF)
njup_force(NCNN_BUILD_BENCHMARK OFF)
njup_force(NCNN_BUILD_TESTS OFF)
njup_force(NCNN_PYTHON OFF)
njup_force(NCNN_C_API OFF)
njup_force(NCNN_PLATFORM_API OFF)   # no Android asset/bitmap helpers: models load from files, pixels come from Kotlin
njup_force(NCNN_OPENMP OFF)         # inference is on the GPU; no libomp/vcomp to ship
njup_force(NCNN_PIXEL ON)           # from_pixels/to_pixels for the fp32 fallback path
njup_force(NCNN_PIXEL_ROTATE OFF)
njup_force(NCNN_PIXEL_AFFINE OFF)
njup_force(NCNN_PIXEL_DRAWING OFF)
njup_force(NCNN_STDIO ON)
njup_force(NCNN_STRING ON)          # blobs are looked up by name ("data", "output")
if(MSVC)
    njup_force(NCNN_BUILD_WITH_STATIC_CRT ON)
endif()

# Layers: everything off except what the three .param files use (Input, Split, Convolution, PReLU, Interp, BinaryOp,
# PixelShuffle) plus the helpers ncnn's Vulkan runtime and those layers create internally (Packing, Padding, Cast, Crop,
# Flatten, Reshape, Quantize/Dequantize/Requantize, Bias, Scale, InnerProduct). The list of layers is read from ncnn
# itself so a submodule bump cannot silently turn new layers on.
set(NJUP_NCNN_LAYERS_ON
    input split convolution prelu interp binaryop pixelshuffle
    packing padding cast crop flatten reshape quantize dequantize requantize bias scale innerproduct)
file(STRINGS "${NCNN_DIR}/src/CMakeLists.txt" _njup_layer_lines REGEX "^ncnn_add_layer\\(")
foreach(_line IN LISTS _njup_layer_lines)
    string(REGEX REPLACE "^ncnn_add_layer\\(([A-Za-z0-9_]+).*$" "\\1" _layer "${_line}")
    string(TOLOWER "${_layer}" _layer)
    if(_layer IN_LIST NJUP_NCNN_LAYERS_ON)
        set(WITH_LAYER_${_layer} ON CACHE BOOL "" FORCE)
    else()
        set(WITH_LAYER_${_layer} OFF CACHE BOOL "" FORCE)
    endif()
endforeach()

add_subdirectory("${NCNN_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/ncnn" EXCLUDE_FROM_ALL)

# --- the JNI library ----------------------------------------------------------------------------------------------------------
add_library(nijiyome_upscale SHARED
    src/upscale_engine.cpp
    src/upscale_jni.cpp)

target_include_directories(nijiyome_upscale PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
    "${REPO_ROOT}/native/common")

target_link_libraries(nijiyome_upscale PRIVATE ncnn)

if(ANDROID)
    # jni.h comes from the NDK sysroot.
    target_link_libraries(nijiyome_upscale PRIVATE log)
    target_compile_options(nijiyome_upscale PRIVATE
        "$<$<NOT:$<CONFIG:Debug>>:-ffunction-sections>"
        "$<$<NOT:$<CONFIG:Debug>>:-fdata-sections>")
    # Same release link options as core/framegen: 16 KB pages, no static libc++/ncnn symbols exported, and stripped here
    # because AGP builds release as RelWithDebInfo.
    target_link_options(nijiyome_upscale PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,--exclude-libs,ALL"
        "$<$<NOT:$<CONFIG:Debug>>:-Wl,-O1>"
        "$<$<NOT:$<CONFIG:Debug>>:-Wl,--gc-sections>"
        "$<$<NOT:$<CONFIG:Debug>>:-Wl,--icf=safe>"
        "$<$<NOT:$<CONFIG:Debug>>:-Wl,--strip-all>")
else()
    find_package(JNI REQUIRED)
    target_include_directories(nijiyome_upscale PRIVATE ${JNI_INCLUDE_DIRS})
    if(MSVC)
        target_compile_definitions(nijiyome_upscale PRIVATE NOMINMAX _CRT_SECURE_NO_WARNINGS WIN32_LEAN_AND_MEAN)
        target_compile_options(nijiyome_upscale PRIVATE /utf-8 /EHsc /permissive-)
    else()
        target_link_libraries(nijiyome_upscale PRIVATE ${CMAKE_DL_LIBS})
        # Keep ncnn/glslang symbols out of the dynamic table: only the Java_* entry points are exported.
        target_link_options(nijiyome_upscale PRIVATE "-Wl,--exclude-libs,ALL" "$<$<NOT:$<CONFIG:Debug>>:-Wl,--gc-sections>")
    endif()
endif()

# Keep clean name: libnijiyome_upscale.so on Linux/Android, nijiyome_upscale.dll on Windows.
set_target_properties(nijiyome_upscale PROPERTIES
    OUTPUT_NAME "nijiyome_upscale"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON)

endif()  # NJUP_BUILD
