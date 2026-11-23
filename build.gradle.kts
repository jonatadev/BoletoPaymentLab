plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.2.0"
    id("io.spring.dependency-management") version "1.1.7"
}

jacoco {
    toolVersion = "0.8.14"
}

group = "org.example"
version = "0.0.1-SNAPSHOT"
description = "BoletoPaymentLab"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.1")
    implementation("org.springframework.boot:spring-boot-h2console")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.hibernate.orm:hibernate-envers")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-kafka:2.0.5")
    testImplementation("org.awaitility:awaitility:4.3.0")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
}

val integrationTest by sourceSets.creating {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    val published = layout.projectDirectory.file("docs-site/static/openapi/boleto-payment-lab-v1.yaml")
    val regenerate = providers.gradleProperty("regenerateOpenApi").map { it.toBoolean() }.orElse(false)
    systemProperty("bpl.openapi.published", published.asFile.absolutePath)
    systemProperty("bpl.openapi.regenerate", regenerate.get())
    inputs.files(published).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.upToDateWhen { !regenerate.get() }
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs tests that require Spring, PostgreSQL, Kafka, or controlled network infrastructure."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}

tasks.check {
    dependsOn(integrationTestTask)
    dependsOn(":boleto-analytics-mcp:check")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTestTask)
    executionData(fileTree(layout.buildDirectory) {
        include("jacoco/test.exec", "jacoco/integrationTest.exec")
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
