plugins {
    id("java-library")
    id("maven-publish")
}

val snapshot = true

group = "dev.snowz"
version = "1.0.0" + if (snapshot) "-SNAPSHOT" else ""

repositories {
    mavenCentral()
}

dependencies {
    api(libs.ch.qos.logback.logback.classic)
    api(libs.org.slf4j.slf4j.api)
    api(libs.com.j256.ormlite.ormlite.core)
    api(libs.com.j256.ormlite.ormlite.jdbc)
    api(libs.junit.junit)
    api(libs.org.xerial.sqlite.jdbc)
    api(libs.org.postgresql.postgresql)
    api(libs.org.mariadb.jdbc.mariadb.java.client)
    api(libs.com.h2database.h2)
    testImplementation(libs.org.slf4j.slf4j.log4j12)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }

    withSourcesJar()
    withJavadocJar()
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

publishing {
    publications {
        create<MavenPublication>("jitpack") {
            from(components["java"])
        }
    }
}
