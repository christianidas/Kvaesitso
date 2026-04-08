import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.serialization)
}

val localProperties = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use {
    localProperties.load(it)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val sdmProjectId = localProperties.getProperty("google.sdmProjectId", "")
        buildConfigField("String", "SDM_PROJECT_ID", "\"$sdmProjectId\"")
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    namespace = "de.mm20.launcher2.homeautomation"
}

dependencies {
    implementation(libs.bundles.kotlin)
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)

    implementation(libs.koin.android)
    implementation(libs.bundles.ktor)
    implementation(libs.play.services.auth)

    implementation(project(":core:ktx"))
    implementation(project(":core:base"))
    implementation(project(":libs:google"))
}
