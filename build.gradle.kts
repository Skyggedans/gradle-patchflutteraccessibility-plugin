plugins {
    java
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("com.gradle.plugin-publish") version "0.12.0"
}

version = "1.0"
group = "com.rockwellits.patchflutteraccessibility"

gradlePlugin {
    plugins {
        create("patchFlutterAccessibilityPlugin") {
            id = "com.rockwellits.patchflutteraccessibility"
            displayName = "Flutter Accessibility patch plugin for RealWear devices"
            description = "Overrides Flutter's AccessibilityBridge class to be compatible with WearHF Android service by exposing Semantics node value as content-desc attribute of a resulting View. Works with Flutter 1 and 2"
            implementationClass = "com.rockwellits.PatchFlutterAccessibilityPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/Skyggedans/gradle-patchflutteraccessibility-plugin"
    vcsUrl = "https://github.com/Skyggedans/gradle-patchflutteraccessibility-plugin"
    tags = listOf("flutter", "accessibility", "realwear", "hmt-1")
}

repositories {
    mavenCentral()
    google()
    jcenter()

    maven (
        url = "https://plugins.gradle.org/m2/"
    )
}

dependencies {
    implementation(gradleApi())
    implementation("com.github.jengelman.gradle.plugins:shadow:6.0.0")
    implementation("org.javassist:javassist:3.27.0-GA")
    implementation("com.android.tools.build:gradle:4.0.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72")
}

tasks.shadowJar {
    isZip64 = true
}
