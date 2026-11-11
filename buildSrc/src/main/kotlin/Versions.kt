import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("ConstPropertyName")
object Versions {

    // ios-client.yml reads this for the ipa file names. The Android APKs are built locally
    // (assembleFullRelease / assembleAapsclientRelease), so they use it only in their own names.
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
