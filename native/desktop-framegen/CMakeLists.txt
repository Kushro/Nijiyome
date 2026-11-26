# SPDX-License-Identifier: GPL-3.0-only
#
# nijiyome_fg_desktop: JNI native library for embedded desktop Frame Generation (LSFG 3.1 / 3.1P).
# Supports zero-copy GL <-> Vulkan (Linux via OPAQUE_FD + GL_EXT_memory_object_fd / GL_EXT_semaphore_fd),
# zero-copy D3D12 <-> Vulkan (Windows via WIN32 shared handles + D3D12 fences), and universal staging fallback.
cmake_minimum_required(VERSION 3.20)

project(nijiyome_fg_desktop LANGUAGES C CXX)

if(NOT WIN32 AND NOT CMAKE_SYSTEM_NAME STREQUAL "Linux")
    message(FATAL_ERROR "nijiyome_fg_desktop supports Windows (MSVC) and Linux only")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if(MSVC)
    set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

if(NOT CMAKE_BUILD_TYPE AND NOT CMAKE_CONFIGURATION_TYPES)
    set(CMAKE_BUILD_TYPE Release)
endif()

find_package(JNI REQUIRED)

set(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../..")
set(TEMPLE "${REPO_ROOT}/native/temple-lsfg")
set(TP "${TEMPLE}/third_party")
set(LSFG "${TP}/lsfg-vk")
set(CORE_FG "${REPO_ROOT}/core/framegen/src/main/cpp/fg")

if(MSVC)
    add_compile_definitions(NOMINMAX _CRT_SECURE_NO_WARNINGS WIN32_LEAN_AND_MEAN)
    add_compile_options(/utf-8 /EHsc /permissive-)
else()
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
endif()

set(FG_THREADS "")
if(NOT WIN32)
    set(THREADS_PREFER_PTHREAD_FLAG ON)
    find_package(Threads REQUIRED)
    set(FG_THREADS Threads::Threads)
endif()

# volk: Vulkan meta-loader (MIT). Loaded dynamically via dlopen/LoadLibrary at runtime.
add_library(fg_volk STATIC "${TP}/volk/volk.c")
target_include_directories(fg_volk PUBLIC "${TP}/volk" "${TP}/vulkan-headers/include")
target_compile_definitions(fg_volk PUBLIC VK_NO_PROTOTYPES)
if(WIN32)
    target_compile_definitions(fg_volk PUBLIC VK_USE_PLATFORM_WIN32_KHR)
else()
    target_link_libraries(fg_volk PUBLIC ${CMAKE_DL_LIBS})
endif()

# pe-parse (MIT): extracts SPIR-V shader resources from Lossless.dll in memory.
if(WIN32)
    set(FG_UNICODE_SRC "${TEMPLE}/src/unicode_win.cpp")
else()
    set(FG_UNICODE_SRC "${TEMPLE}/src/unicode_posix.cpp")
endif()
add_library(fg_peparse STATIC
    "${TP}/pe-parse/src/buffer.cpp"
    "${TP}/pe-parse/src/parse.cpp"
    "${FG_UNICODE_SRC}")
target_include_directories(fg_peparse PUBLIC "${TP}/pe-parse/include")

# lsfg-vk framegen core (MIT), 3.1 and 3.1P variants with optional external memory/semaphore extensions.
file(GLOB LSFG_SOURCES CONFIGURE_DEPENDS
    "${LSFG}/src/common/*.cpp"
    "${LSFG}/src/core/*.cpp"
    "${LSFG}/src/pool/*.cpp"
    "${LSFG}/v3.1_src/*.cpp"
    "${LSFG}/v3.1_src/shaders/*.cpp"
    "${LSFG}/v3.1p_src/*.cpp"
    "${LSFG}/v3.1p_src/shaders/*.cpp")
add_library(fg_lsfg_core STATIC ${LSFG_SOURCES})
target_include_directories(fg_lsfg_core
    PUBLIC "${LSFG}/include" "${LSFG}/public"
    PRIVATE "${LSFG}/v3.1_include" "${LSFG}/v3.1p_include")
target_compile_definitions(fg_lsfg_core PUBLIC TEMPLE_LSFG_LOCAL)
target_link_libraries(fg_lsfg_core PUBLIC fg_volk ${FG_THREADS})

set(PLATFORM_SOURCES "")
if(WIN32)
    list(APPEND PLATFORM_SOURCES src/transport_d3d12_vk.cpp)
else()
    list(APPEND PLATFORM_SOURCES src/transport_gl_vk.cpp)
endif()

add_library(nijiyome_fg_desktop SHARED
    src/fg_desktop_engine.cpp
    src/transport_staging.cpp
    src/fg_desktop_jni.cpp
    ${PLATFORM_SOURCES}
    "${TEMPLE}/src/shader_loader.cpp"
    "${CORE_FG}/fg_pacing.cpp")

target_include_directories(nijiyome_fg_desktop PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
    "${TEMPLE}/src"
    "${CORE_FG}"
    ${JNI_INCLUDE_DIRS})

target_link_libraries(nijiyome_fg_desktop PRIVATE
    fg_lsfg_core
    fg_peparse
    fg_volk
    ${FG_THREADS})

if(WIN32)
    target_link_libraries(nijiyome_fg_desktop PRIVATE dxgi d3d12)
else()
    target_link_libraries(nijiyome_fg_desktop PRIVATE ${CMAKE_DL_LIBS})
endif()

# Keep clean name: libnijiyome_fg_desktop.so on Linux, nijiyome_fg_desktop.dll on Windows.
set_target_properties(nijiyome_fg_desktop PROPERTIES
    OUTPUT_NAME "nijiyome_fg_desktop"
    CXX_VISIBILITY_PRESET hidden)
