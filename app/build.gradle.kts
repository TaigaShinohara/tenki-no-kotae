import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Persistent signing key in the project's .local/ (never the default debug
// keystore, which is regenerated when the build machine is replaced).
val signingPropsFile = rootProject.file(".local/signing.properties")
val signingProps = Properties().apply {
    if (signingPropsFile.exists()) signingPropsFile.inputStream().use { load(it) }
}
val hasPersistentSigning = signingPropsFile.exists() &&
    rootProject.file(".local/tenkinokotae.keystore").exists()

// Fail fast when the persistent key is missing (BUG_REVIEW #4): quietly
// falling back to the default debug keystore signs the build with a key
// that is regenerated when the build machine is replaced — updates then
// fail to install and a reinstall wipes the app's data (the accident
// this rule exists to prevent). No key, no build.
if (!hasPersistentSigning) {
    throw GradleException(
        "Refusing to build: .local/signing.properties and/or " +
            ".local/tenkinokotae.keystore not found. Building without the " +
            "project's persistent signing key is forbidden (the default " +
            "debug key is regenerated per machine and breaks updates)."
    )
}

android {
    namespace = "com.taigashinohara.tenkinokotae"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.taigashinohara.tenkinokotae"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "0.1.24"
    }

    signingConfigs {
        if (hasPersistentSigning) {
            create("persistent") {
                storeFile = rootProject.file(".local/tenkinokotae.keystore")
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasPersistentSigning) signingConfig = signingConfigs.getByName("persistent")
        }
        release {
            isMinifyEnabled = false
            if (hasPersistentSigning) signingConfig = signingConfigs.getByName("persistent")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Real org.json for local unit tests (android.jar's copy is a stub whose
    // methods throw); lets the MET adapter parse tests run on the JVM.
    testImplementation("org.json:json:20240303")
}
