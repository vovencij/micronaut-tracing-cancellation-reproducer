plugins {
    java
}

group = "example"
version = "1.0.0"

repositories {
    mavenCentral()
}

val micronautPlatformVersion = providers.gradleProperty("micronautPlatformVersion").get()
val micronautPlatformCoordinates =
    "io.micronaut.platform:micronaut-platform:$micronautPlatformVersion"

dependencies {
    annotationProcessor(platform(micronautPlatformCoordinates))
    annotationProcessor("io.micronaut:micronaut-inject-java")

    implementation(platform(micronautPlatformCoordinates))
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.tracing:micronaut-tracing-opentelemetry-http")
    implementation("io.projectreactor:reactor-core")

    runtimeOnly("ch.qos.logback:logback-classic")

    testAnnotationProcessor(platform(micronautPlatformCoordinates))
    testAnnotationProcessor("io.micronaut:micronaut-inject-java")

    testImplementation(platform(micronautPlatformCoordinates))
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.assertj:assertj-core:3.27.7")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
