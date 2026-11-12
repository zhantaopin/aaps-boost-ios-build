import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("ConstPropertyName")
object Versions {

    // Both workflows read this for their artifact names: ios-client.yml for the .ipa files,
    // android-release.yml for the APKs. Local builds (assembleFullRelease / assembleAapsclientRelease)
    // use it in their file names as well.
    const val appVersion = "4.0.0"
    const val versionCode = 1500

    const val compileSdk = 37
    const val minSdk = 27
    const val targetSdk = 35
    const val wearMinSdk = 27
    const val wearTargetSdk = 30

    // Boost version, uploaded to Nightscout as apsConfiguration.boostVersion with the branch and
    // short hash appended at build time.
    const val boostVersion = "6.3.0"

    val javaVersion = JavaVersion.VERSION_21
    val jvmTarget = JvmTarget.JVM_21
    const val jacoco = "0.8.11"
}
