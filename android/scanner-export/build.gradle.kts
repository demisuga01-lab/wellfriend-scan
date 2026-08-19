plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":scanner-core"))
    testImplementation(kotlin("test"))
}
