plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.galmiza.android.pdfflattener"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.galmiza.android.pdfflattener"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"
    }

    // Stable release key, kept outside the repo: set these properties in ~/.gradle/gradle.properties
    // or pass them with -P (the release workflow does this from GitHub secrets).
    // Without them, release builds are signed with the debug key so the APK still installs.
    val keystore = providers.gradleProperty("pdfFlattenerKeystore").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("pdfFlattenerKeystorePassword").get()
                keyAlias = providers.gradleProperty("pdfFlattenerKeyAlias").get()
                keyPassword = providers.gradleProperty("pdfFlattenerKeyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        // Lists the translated languages for Android 13+'s per-app language setting
        generateLocaleConfig = true
    }
    packaging {
        // Post-quantum parameter tables of BouncyCastle (pulled in by PDFBox for certificate
        // encryption): ~8 MB of Java resources that PDF security never uses
        resources.excludes += "org/bouncycastle/**"
    }
    lint {
        warningsAsErrors = true
        lintConfig = file("lint.xml")
    }
    testOptions {
        // PDFBox-Android touches a few android.* classes (logging); stubs returning defaults are enough
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.pdfbox.android)
    testImplementation(libs.junit)
}
