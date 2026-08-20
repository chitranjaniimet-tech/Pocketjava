plugins {
    id("com.android.application")
}

android {
    namespace = "com.moneyclaritytech.javapocketlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moneyclaritytech.javapocketlab"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Mobile-optimized code editor (LGPL-2.1)
    implementation(platform("io.github.rosemoe:editor-bom:0.24.6"))
    implementation("io.github.rosemoe:editor")
    implementation("io.github.rosemoe:language-java")

    // Small embedded Java compiler (BSD-3-Clause)
    implementation("org.codehaus.janino:janino:3.1.12")
    implementation("org.codehaus.janino:commons-compiler:3.1.12")

    // D8 converts generated JVM .class files into Android DEX at runtime.
    // Pin to the stable R8 version paired with Android Gradle Plugin 8.13.2.
    implementation("com.android.tools:r8:8.13.19")
}
