plugins {
    id("proxytoggle.application")
    id("proxytoggle.application.compose")
    id("proxytoggle.application.jacoco")
    id("proxytoggle.test")
    id("proxytoggle.hilt")
    id("com.google.protobuf")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.kinandcarta.create.proxytoggle"

    defaultConfig {
        applicationId = "com.kinandcarta.create.proxytoggle"

        versionName = "1.2.0"
        versionCode = common.versionCode(versionName)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("proguard-rules.pro")
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

hilt {
    enableAggregatingTask = false
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)

    // Android / Material
    implementation(libs.com.google.android.material.material)
    implementation(libs.appcompat)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.material3.window.size.clazz)
    implementation(libs.activity.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.constraintlayout.compose)
    implementation(libs.accompanist.systemuicontroller)

    // Showkase
    implementation(libs.showkase)
    ksp(libs.showkase.processor)

    // DataStore / Protobuf
    implementation(libs.protobuf.javalite)
    implementation(libs.datastore)
    implementation(libs.datastore.preferences)

    // Compose preview debug
    debugImplementation(libs.lifecycle.viewmodel.savedstate)
    debugImplementation(libs.customview.poolingcontainer)
    debugImplementation(libs.compose.ui.test.manifest)

    // Test
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.core.testing)
    testImplementation(libs.compose.ui.test)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.test.ext.junit)
    testImplementation(libs.test.parameter.injector)
    testImplementation(libs.hamcrest.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.android.compiler)
    kspTest(libs.showkase.processor)
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
