// SPDX-License-Identifier: AGPL-3.0-or-later
// v1.3.3 GPU image upscaling (docs/plan-upscaling.md §4.1): the Android side of libnijiyome_upscale.so (ncnn + Vulkan, CMake in
// native/upscale) and its Kotlin bridge. An Android library like :core:framegen because the Android-KMP plugin has no CMake.
// Only arm64-v8a and x86_64 build the library; the models ship as assets (three .param and the x4 .bin, which x2 / x3 reuse).
plugins {
    id("nijiyome.android.library")
}

android {
    // The only NDK installed on the build hosts (same as :core:framegen).
    ndkVersion = "28.2.13676358"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                abiFilters += listOf("arm64-v8a", "x86_64")
                targets += "nijiyome_upscale"
                // ncnn + glslang take long per ABI and variant: debug builds the optimised library too (nothing to step into there).
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_static")
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("../../native/upscale/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

// The models as APK assets under `upscale-models/`, copied from native/upscale/models (assets.srcDir cannot filter out the identical
// x2 / x3 .bin files, the README and the licence, which THIRD_PARTY_NOTICES.md already carries).
abstract class CopyUpscaleModels : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val models: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("upscale-models")
        target.deleteRecursively()
        target.mkdirs()
        models.files.forEach { it.copyTo(target.resolve(it.name), overwrite = true) }
    }
}

val copyUpscaleModels = tasks.register<CopyUpscaleModels>("copyUpscaleModels") {
    val dir = rootProject.layout.projectDirectory.dir("native/upscale/models")
    models.from(
        dir.file("realesr-animevideov3-x2.param"),
        dir.file("realesr-animevideov3-x3.param"),
        dir.file("realesr-animevideov3-x4.param"),
        dir.file("realesr-animevideov3-x4.bin"),
    )
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyUpscaleModels, CopyUpscaleModels::outputDir)
    }
}

dependencies {
    api(projects.core.upscale)
}
