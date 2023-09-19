import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.6.9"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    kotlin("jvm") version "1.6.21"
    kotlin("plugin.spring") version "1.6.21"
}

group = "org.breizhcamp.camaaloth-uploader"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-websocket")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation ("org.apache.commons:commons-lang3")
    implementation("commons-io:commons-io:2.6")
    implementation("org.webjars:bootstrap:3.3.7-1")
    implementation("org.webjars:sockjs-client:1.1.2")
    implementation("org.webjars:stomp-websocket:2.3.3-1")
    implementation("org.webjars:angularjs:1.6.2")

    implementation("com.google.apis:google-api-services-youtube:v3-rev182-1.22.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("net.sourceforge.htmlunit:htmlunit")
    testImplementation("com.microsoft.playwright:playwright:1.36.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:3.6.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

