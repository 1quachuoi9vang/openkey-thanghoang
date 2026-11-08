plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
