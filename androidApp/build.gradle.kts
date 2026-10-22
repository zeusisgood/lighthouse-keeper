plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.github.zeusisgood.lighthousekeeper"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.zeusisgood.lighthousekeeper"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.activity.compose)
}
