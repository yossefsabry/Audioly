plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
                freeCompilerArgs += "-Xexpect-actual-classes"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Ktor (shared HTTP client)
                implementation("io.ktor:ktor-client-core:2.3.8")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)

                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

                // SQLDelight coroutines extensions
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }

        val androidMain by getting {
            dependencies {
                // Android-specific Ktor engine
                implementation("io.ktor:ktor-client-okhttp:2.3.8")

                // Media3 / ExoPlayer
                implementation("androidx.media3:media3-exoplayer:1.3.0")
                implementation("androidx.media3:media3-session:1.3.0")
                implementation("androidx.media3:media3-datasource:1.3.0")
                implementation("androidx.media3:media3-datasource-okhttp:1.3.0")

                // Android core
                implementation("androidx.core:core-ktx:1.12.0")

                // NewPipe Extractor (JVM-only, Android actual impl)
                implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")

                // Coil for image loading
                implementation("io.coil-kt:coil-compose:2.6.0")

                // SQLDelight Android driver
                implementation("app.cash.sqldelight:android-driver:2.0.1")
            }
        }

        // Create intermediate iosMain source set manually (Kotlin 1.9.x)
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                // iOS Ktor engine
                implementation("io.ktor:ktor-client-darwin:2.3.8")

                // SQLDelight native driver
                implementation("app.cash.sqldelight:native-driver:2.0.1")
            }
        }

        // iOS test source sets
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

android {
    namespace = "com.audioly.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("AudiolyDb") {
            packageName.set("com.audioly.shared.db")
        }
    }
}
