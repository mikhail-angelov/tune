plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.mangelov.tune.audio"; compileSdk = 36; buildToolsVersion = "35.0.0"; defaultConfig { minSdk = 29 } }
kotlin { jvmToolchain(21) }
dependencies { implementation(project(":core-dsp")) }
