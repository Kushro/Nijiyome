// SPDX-License-Identifier: MIT
// Pre/post-processing compute shaders for the tiled Real-ESRGAN loop, adapted from realesrgan-ncnn-vulkan
// (src/realesrgan_preproc.comp and src/realesrgan_postproc.comp at 37026f4, v0.2.0).
// Copyright (c) 2021 Xintao Wang; Copyright (c) 2019 nihui (realsr-ncnn-vulkan). MIT License, full text in
// native/upscale/third_party/realesrgan-ncnn-vulkan/LICENSE.
//
// Changes from upstream:
// - Compiled at runtime with ncnn::compile_spirv_module against the network's own Option, so the NCNN_fp16_storage /
//   NCNN_int8_storage macros always match the blobs ncnn allocates (no glslangValidator step at build time). The
//   #extension lines are gone because ncnn injects them from the same Option.
// - Upstream's `sfp` macro is renamed `nj_sfp`: ncnn's preamble already defines `sfp`, and under bf16/fp16-packed it
//   means a packed uint, which would break the scalar indexing below. nj_sfp is float16_t only with real 16-bit storage.
// - RGBA only, no BGR specialization: the source/destination pixel stride is always 4 bytes (int8 path), while
//   `channels` (3 or 4) is how many planes are processed. With channels == 3 the postproc writes alpha 255.
// - No TTA variants.
#pragma once

namespace njup {

inline constexpr char kPreprocComp[] = R"GLSL(#version 450

#if NCNN_fp16_storage
#define nj_sfp float16_t
#else
#define nj_sfp float
#endif

#if NCNN_int8_storage
layout (binding = 0) readonly buffer bottom_blob { uint8_t bottom_blob_data[]; };
#else
layout (binding = 0) readonly buffer bottom_blob { float bottom_blob_data[]; };
#endif
layout (binding = 1) writeonly buffer top_blob { nj_sfp top_blob_data[]; };
layout (binding = 2) writeonly buffer alpha_blob { nj_sfp alpha_blob_data[]; };

layout (push_constant) uniform parameter
{
    int w;
    int h;
    int cstep;

    int outw;
    int outh;
    int outcstep;

    int pad_top;
    int pad_left;

    int crop_x;
    int crop_y;

    int channels;

    int alphaw;
    int alphah;
} p;

void main()
{
    int gx = int(gl_GlobalInvocationID.x);
    int gy = int(gl_GlobalInvocationID.y);
    int gz = int(gl_GlobalInvocationID.z);

    if (gx >= p.outw || gy >= p.outh || gz >= p.channels)
        return;

    int x = gx + p.crop_x - p.pad_left;
    int y = gy + p.crop_y - p.pad_top;

    // reflect padding at the image border
    x = abs(x);
    y = abs(y);
    x = (p.w - 1) - abs(x - (p.w - 1));
    y = (p.h - 1) - abs(y - (p.h - 1));

#if NCNN_int8_storage
    int v_offset = y * p.w + x;

    float v = float(uint(bottom_blob_data[v_offset * 4 + gz]));
#else
    int v_offset = gz * p.cstep + y * p.w + x;

    float v = bottom_blob_data[v_offset];
#endif

    if (gz == 3)
    {
        gx -= p.pad_left;
        gy -= p.pad_top;

        if (gx >= 0 && gx < p.alphaw && gy >= 0 && gy < p.alphah)
        {
            alpha_blob_data[gy * p.alphaw + gx] = nj_sfp(v);
        }
    }
    else
    {
        const float norm_val = 1 / 255.f;

        top_blob_data[gz * p.outcstep + gy * p.outw + gx] = nj_sfp(v * norm_val);
    }
}
)GLSL";

inline constexpr char kPostprocComp[] = R"GLSL(#version 450

#if NCNN_fp16_storage
#define nj_sfp float16_t
#else
#define nj_sfp float
#endif

layout (binding = 0) readonly buffer bottom_blob { nj_sfp bottom_blob_data[]; };
layout (binding = 1) readonly buffer alpha_blob { nj_sfp alpha_blob_data[]; };
#if NCNN_int8_storage
layout (binding = 2) writeonly buffer top_blob { uint8_t top_blob_data[]; };
#else
layout (binding = 2) writeonly buffer top_blob { float top_blob_data[]; };
#endif

layout (push_constant) uniform parameter
{
    int w;
    int h;
    int cstep;

    int outw;
    int outh;
    int outcstep;

    int offset_x;
    int gx_max;

    int crop_x;
    int crop_y;

    int channels;

    int alphaw;
    int alphah;
} p;

void main()
{
    int gx = int(gl_GlobalInvocationID.x);
    int gy = int(gl_GlobalInvocationID.y);
    int gz = int(gl_GlobalInvocationID.z);

#if NCNN_int8_storage
    // the int8 path always writes 4 bytes per pixel; plane 3 is opaque when only RGB was processed
    if (gx >= p.gx_max || gy >= p.outh || gz >= 4)
        return;
#else
    if (gx >= p.gx_max || gy >= p.outh || gz >= p.channels)
        return;
#endif

    float v;

    if (gz == 3)
    {
        if (p.channels == 4)
            v = float(alpha_blob_data[gy * p.alphaw + gx]);
        else
            v = 255.f;
    }
    else
    {
        v = float(bottom_blob_data[gz * p.cstep + (gy + p.crop_y) * p.w + gx + p.crop_x]);

        const float denorm_val = 255.f;

        v = v * denorm_val;
    }

    const float clip_eps = 0.5f;

    v = v + clip_eps;

#if NCNN_int8_storage
    int v_offset = gy * p.outw + gx + p.offset_x;

    uint v32 = uint(clamp(floor(v), 0.f, 255.f));

    top_blob_data[v_offset * 4 + gz] = uint8_t(v32);
#else
    int v_offset = gz * p.outcstep + gy * p.outw + gx + p.offset_x;

    top_blob_data[v_offset] = v;
#endif
}
)GLSL";

}  // namespace njup
