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
    api("com.github.skjaere:kotlin-compression-utils:v0.3.1")

    // Testcontainer client dependencies
    api(libs.testcontainers)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    testImplementation(libs.ktor.client.serialization)
    testImplementation(libs.ktor.server.test.host)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.3")
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
        // The build time instead of jib's default epoch, so an image can be told apart from source
        creationTime = "USE_CURRENT_TIMESTAMP"
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
    // The yEnc encoder loads the librapidyenc.so its wrapper bundles. RAPIDYENC_LIB_PATH points the tests at a folder
    // holding another build of it.
    System.getenv("RAPIDYENC_LIB_PATH")?.let { systemProperty("jna.library.path", it) }
}

application {
    mainClass = "io.skjaere.AppKt"
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                licenses {
                    license {
                        name = "PolyForm Noncommercial License 1.0.0"
                        url = "https://polyformproject.org/licenses/noncommercial/1.0.0"
                    }
                }
            }
        }
    }
}

// The licence goes in with the resources, in a folder of its own under META-INF, so every packaging carries it: the
// jars, the distributions and the Docker image, which jib builds from the classes and resources rather than the jar.
// The folder keeps it from clashing with another library's in an app that packs several.
tasks.processResources {
    into("META-INF/${project.name}") {
        from("LICENSE.md")
    }
}
