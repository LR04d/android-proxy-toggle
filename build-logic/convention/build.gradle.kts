plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

group = "com.kinandcarta.create.proxytoggle.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("application") {
            id = "proxytoggle.application"
            implementationClass = "plugins.ApplicationPlugin"
        }
        register("applicationCompose") {
            id = "proxytoggle.application.compose"
            implementationClass = "plugins.ApplicationComposePlugin"
        }
        register("library") {
            id = "proxytoggle.library"
            implementationClass = "plugins.LibraryPlugin"
        }
        register("libraryCompose") {
            id = "proxytoggle.library.compose"
            implementationClass = "plugins.LibraryComposePlugin"
        }
        register("hilt") {
            id = "proxytoggle.hilt"
            implementationClass = "plugins.HiltPlugin"
        }
        register("test") {
            id = "proxytoggle.test"
            implementationClass = "plugins.TestPlugin"
        }
        register("detekt") {
            id = "proxytoggle.detekt"
            implementationClass = "plugins.DetektPlugin"
        }
        register("applicationJacoco") {
            id = "proxytoggle.application.jacoco"
            implementationClass = "plugins.ApplicationJacocoPlugin"
        }
        register("libraryJacoco") {
            id = "proxytoggle.library.jacoco"
            implementationClass = "plugins.LibraryJacocoPlugin"
        }
    }
}
