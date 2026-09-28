import java.util.Properties

plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
val localVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}.getProperty("versionCode").toInt()
android {
    namespace = "dev.mangelov.tune"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "dev.mangelov.tune"
        minSdk = 29
        targetSdk = 36
        versionCode = providers.environmentVariable("TUNE_VERSION_CODE").orNull?.toInt() ?: localVersion
        versionName = providers.environmentVariable("TUNE_VERSION_NAME").orNull ?: "1.0.${localVersion - 1}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core-dsp")); implementation(project(":audio"))
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.foundation)
    implementation(libs.compose.material3); implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    implementation(libs.activity.compose); implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose); implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences); implementation(libs.coroutines.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
