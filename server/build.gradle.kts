plugins {
	kotlin("jvm") version "2.2.21"
	kotlin("plugin.spring") version "2.2.21"
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.2.21"
	id("org.openapi.generator") version "7.12.0"
	id("com.diffplug.spotless") version "7.0.2"
}

group = "com.microchirp"
version = "0.0.1-SNAPSHOT"
description = "Event Sourcing and CQRS SNS application"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

sourceSets {
	create("integrationTest") {
		compileClasspath += sourceSets.main.get().output
		runtimeClasspath += sourceSets.main.get().output
	}
}

val integrationTestImplementation by configurations.getting {
	extendsFrom(configurations.implementation.get())
}

val integrationTestRuntimeOnly by configurations.getting {
	extendsFrom(configurations.runtimeOnly.get())
}

dependencies {
	// Spring Boot Starters
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// Kotlin
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")

	// OpenAPI
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

	// OpenTelemetry
	implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter:2.11.0-alpha")
	implementation("io.opentelemetry.instrumentation:opentelemetry-annotations:2.11.0-alpha")

	// Database
	runtimeOnly("org.postgresql:postgresql")

	// Unit Test
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// Integration Test
	integrationTestImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	integrationTestImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	integrationTestImplementation("org.springframework.boot:spring-boot-testcontainers")
	integrationTestImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	integrationTestImplementation("org.testcontainers:testcontainers-junit-jupiter")
	integrationTestImplementation("org.testcontainers:testcontainers-postgresql")
	integrationTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showExceptions = true
		showCauses = true
		showStackTraces = true
		showStandardStreams = true
	}
}

val integrationTest = task<Test>("integrationTest") {
	description = "Runs integration tests."
	group = "verification"

	testClassesDirs = sourceSets["integrationTest"].output.classesDirs
	classpath = sourceSets["integrationTest"].runtimeClasspath
	shouldRunAfter("test")

	useJUnitPlatform()
	testLogging {
		events("passed", "skipped", "failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showExceptions = true
		showCauses = true
		showStackTraces = true
		showStandardStreams = true
	}
}

tasks.named("check") {
	dependsOn(integrationTest)
}

// OpenAPI Generator Configuration
openApiGenerate {
	generatorName.set("kotlin-spring")
	inputSpec.set("$rootDir/spec/openapi.yaml")
	outputDir.set("$buildDir/generated")
	apiPackage.set("com.microchirp.api")
	modelPackage.set("com.microchirp.model")
	configOptions.set(mapOf(
		"delegatePattern" to "true",
		"interfaceOnly" to "true",
		"useTags" to "true"
	))
}

tasks.named("compileKotlin") {
	dependsOn("openApiGenerate")
}

// Spotless Configuration
spotless {
	kotlin {
		target("src/**/*.kt")
		targetExclude("build/**")
		ktlint()
	}
	kotlinGradle {
		target("*.gradle.kts")
		ktlint()
	}
}
