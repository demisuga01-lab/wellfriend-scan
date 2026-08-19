plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
    sourceSets.named("test") {
        kotlin.srcDir("../../integration-tests/android-host/src/test/kotlin")
    }
}

dependencies {
    implementation(project(":scanner-core"))
    implementation(project(":scanner-perception"))
    implementation(project(":scanner-export"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}
