plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.autonomousapps.dependency-analysis") version "3.19.2"
    id("com.google.gms.google-services") version "4.5.0" apply false
}