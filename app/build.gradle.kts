import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.flexy.stundenplan"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.flexy.schulplan"
        minSdk = 26
        targetSdk = 36
        // Auf GitHub Actions zählt die Build-Nummer hoch, damit Updates sich über die alte Version installieren lassen
        val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = ciBuild?.let { 100 + it } ?: 1
        versionName = ciBuild?.let { "1.0.$it" } ?: "1.0.0-dev"
    }

    // Signatur: Schlüssel liegt NICHT im Repo.
    //  - lokal:  signing/stundenplan.jks + signing/signing.properties (beides in .gitignore)
    //  - GitHub: Secrets SIGNING_KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD
    // Fehlt beides (z.B. in einem Fork), wird einfach mit dem Debug-Schlüssel signiert.
    val signingProps: Map<String, String> = rootProject.file("signing/signing.properties").let { f ->
        if (!f.exists()) emptyMap() else f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }
    fun signingValue(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() } ?: signingProps[prop]
    val keystore = rootProject.file("signing/stundenplan.jks")
    val hasOwnKey = keystore.exists() && signingValue("SIGNING_STORE_PASSWORD", "storePassword") != null

    signingConfigs {
        if (hasOwnKey) {
            create("own") {
                storeFile = keystore
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias") ?: "stundenplan"
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasOwnKey) signingConfig = signingConfigs.getByName("own")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (hasOwnKey) "own" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3:1.4.0-alpha15")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    testImplementation("junit:junit:4.13.2")
    // echte org.json-Implementierung für Unit-Tests (Android liefert nur Stubs)
    testImplementation("org.json:json:20240303")
}
