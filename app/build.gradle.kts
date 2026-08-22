plugins {
    id("com.android.application")
}

val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val ciDebugKeystore = providers.gradleProperty("pocketJavaDebugKeystore").orNull

android {
    namespace = "com.moneyclaritytech.javapocketlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moneyclaritytech.javapocketlab"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 3
        versionName = "0.3.0"
    }

    signingConfigs {
        if (!ciDebugKeystore.isNullOrBlank()) {
            create("persistentDebug") {
                storeFile = file(ciDebugKeystore)
                storePassword = "pocketjava-debug-2026"
                keyAlias = "pocketjava-debug"
                keyPassword = "pocketjava-debug-2026"
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

    // Full Eclipse Compiler for Java. Unlike Janino, ECJ compiles modern Java syntax,
    // including lambdas, method references, streams-facing source and Java 11 code.
    implementation("org.eclipse.jdt:ecj:3.46.0")

    // D8 converts ECJ-generated JVM .class files into Android DEX at runtime.
    implementation("com.android.tools:r8:8.13.19")
}
