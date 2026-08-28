plugins {
    id("com.android.application")
}

val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val ciDebugKeystore = providers.gradleProperty("pocketForgeDebugKeystore").orNull

android {
    namespace = "com.moneyclaritytech.pocketforge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moneyclaritytech.pocketforge"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 100
        versionName = "0.1.0"
    }

    signingConfigs {
        if (!ciDebugKeystore.isNullOrBlank()) {
            create("persistentDebug") {
                storeFile = file(ciDebugKeystore)
                storePassword = "pocketforge-debug-2026"
                keyAlias = "pocketforge-debug"
                keyPassword = "pocketforge-debug-2026"
            }
        }
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
            if (!ciDebugKeystore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("persistentDebug")
            }
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

    // Android-compatible Eclipse Compiler for Java. ECJ 3.20+ references
    // javax.lang.model.SourceVersion from the desktop java.compiler module,
    // which Android does not ship. Eclipse 4.12 / ECJ 3.18.0 is the last
    // PocketForge-tested Android-compatible line and still supports Java 11/12 syntax.
    implementation("org.eclipse.jdt:ecj:3.18.0")

    // D8 converts ECJ-generated JVM .class files into Android DEX at runtime.
    implementation("com.android.tools:r8:8.13.19")
}
