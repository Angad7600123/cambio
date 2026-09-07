import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Signing material, which is never committed.
 *
 * It is read from `local.properties` for a local build, or from the environment
 * for CI, and if neither supplies it the release build simply stays unsigned --
 * so a fresh clone with no keystore still builds and still passes CI. Nothing
 * here has a default: a wrong default would silently produce an APK signed with
 * the wrong key, which is worse than one that is not signed at all.
 *
 * See RELEASING.md for how to create the keystore and where to put the values.
 */
val signingProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun signingSecret(
    property: String,
    environment: String,
): String? = (signingProperties.getProperty(property) ?: System.getenv(environment))?.takeIf { it.isNotBlank() }

val keystorePath = signingSecret("cambio.keystore.file", "CAMBIO_KEYSTORE_FILE")
val keystorePassword = signingSecret("cambio.keystore.password", "CAMBIO_KEYSTORE_PASSWORD")
val keystoreAlias = signingSecret("cambio.key.alias", "CAMBIO_KEY_ALIAS")
val keystoreKeyPassword = signingSecret("cambio.key.password", "CAMBIO_KEY_PASSWORD")
val hasSigningMaterial =
    keystorePath != null && keystorePassword != null &&
        keystoreAlias != null && keystoreKeyPassword != null

android {
    namespace = "io.github.angad7600123.cambio"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.angad7600123.cambio"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasSigningMaterial) {
            create("release") {
                // Every one of these values is named differently from the property
                // it is assigned to, on purpose. Inside this block the receiver's
                // own names win, so a variable called `keyPassword` here would
                // resolve to the property being assigned and quietly set it to
                // itself. That fails only on the signed path -- an unsigned build
                // never touches it -- so it would have got all the way to the first
                // real release before anyone found out.
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed only when a keystore has actually been supplied. No keystore,
            // no signature -- the build still succeeds, and the APK it produces is
            // plainly uninstallable rather than quietly signed with something else.
            signingConfig = signingConfigs.findByName("release")
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

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Required so Robolectric can inflate real resources and render Compose
            // UI tests on the JVM.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/LICENSE.md",
                    "/META-INF/LICENSE-notice.md",
                )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
