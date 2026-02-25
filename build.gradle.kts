plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.kover)
    `java-library`
    `maven-publish`
    application
    id("io.ktor.plugin") version "3.4.0"
    id("com.google.cloud.tools.jib") version "3.4.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation(libs.guava)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.rapidyenc.kotlin.wrapper)
    implementation(libs.jna)

    // Archive generation for test helpers
    api("io.skjaere:kotlin-compression-utils:0.3.1")

    // Testcontainer client dependencies
    api(libs.testcontainers)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.ktor.client.serialization)
    testImplementation(libs.ktor.server.test.host)
}

jib {
    from {
        image = "eclipse-temurin:25-jre"
    }
    to {
        image = "ghcr.io/skjaere/mock-nntp-server:latest"
        auth {
            username = System.getenv("DOCKER_USERNAME") ?: ""
            password = System.getenv("DOCKER_PASSWORD") ?: ""
        }
    }
    container {
        mainClass = "io.skjaere.AppKt"
        ports = listOf("8081/tcp", "1119/tcp")
        jvmFlags = listOf("-Xms512m", "-Xmx1024m")
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.10.3")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<Test> {
    val libPath = System.getenv("RAPIDYENC_LIB_PATH")
        ?: "/home/william/IdeaProjects/rapidyenc/build"
    systemProperty("jna.library.path", libPath)
}

application {
    mainClass = "io.skjaere.AppKt"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
