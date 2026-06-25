plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
}

android {
    namespace = "com.bridgerwatch.ballistics"
    compileSdk = 34

    defaultConfig {
        minSdk = 26 // java.util.Base64 / java.util.zip used directly; Android 8.0+
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")

    // Unit tests run on the JVM; org.json is part of Android at runtime but must
    // be supplied for plain JVM tests.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.bridgerwatch"
            artifactId = "bridgerballistics"
            version = "1.0.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
