import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/** Shared constants. Keep in sync with apps/README.md. */
object NijiyomeAndroid {
    const val COMPILE_SDK = 37
    /** The SDK ships API 37 as `platforms;android-37.0` (hash `android-37.0`); without the minor AGP looks for `android-37`. */
    const val COMPILE_SDK_MINOR = 0
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    val JAVA = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

val Project.nijiyomeCatalog: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()
fun VersionCatalog.bundle(alias: String) = findBundle(alias).get()

/**
 * Android namespace derived from the Gradle path:
 *   :core:model        -> app.nijiyome.model
 *   :core:extensions-quickjs -> app.nijiyome.extensions_quickjs
 *   :feature:home      -> app.nijiyome.feature.home
 *   :app               -> app.nijiyome   (set explicitly in :app)
 */
fun Project.nijiyomeNamespace(): String {
    val segments = path.trim(':').split(':').map { it.replace('-', '_') }
    return when (segments.first()) {
        "core" -> "app.nijiyome." + segments.drop(1).joinToString(".")
        else -> "app.nijiyome." + segments.joinToString(".")
    }
}

fun Project.configureAndroidCommon(ext: CommonExtension) {
    ext.apply {
        compileSdk = NijiyomeAndroid.COMPILE_SDK
        compileSdkMinor = NijiyomeAndroid.COMPILE_SDK_MINOR
        defaultConfig.minSdk = NijiyomeAndroid.MIN_SDK
        compileOptions.sourceCompatibility = NijiyomeAndroid.JAVA
        compileOptions.targetCompatibility = NijiyomeAndroid.JAVA
        testOptions.unitTests.isReturnDefaultValues = true
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(NijiyomeAndroid.JVM_TARGET)
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
        }
    }
}

fun Project.configureJvmCommon() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(NijiyomeAndroid.JVM_TARGET)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(17)
    }
}
