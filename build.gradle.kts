import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
    application
}

group = "kernel"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }

    withSourcesJar()
}

sourceSets {
    named("main") {
        resources {
            srcDir("src/main/kotlin")
            include("**/*.stub")
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("kernel.command.KernelCli")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "kernel.command.KernelCli"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("kernel")
                description.set("Private lightweight Kotlin/JVM application kernel.")
            }
        }
    }
}
