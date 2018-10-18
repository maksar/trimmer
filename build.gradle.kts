import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.itransition"
version = "1.0"

plugins {
    kotlin("jvm") version "1.2.71"
    application
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

application {
    mainClassName = "TrimmerKt"
}

repositories {
    jcenter()
    maven("https://packages.atlassian.com/maven/repository/public")
    maven("https://jitpack.io")
}

dependencies {
    implementation("net.rcarz", "jira-client", "0.5")
    implementation("io.github.cdimascio", "java-dotenv", "3.1.2")
    implementation("com.atlassian.jira", "jira-tests", "7.4.0")
    implementation("com.atlassian.jira", "jira-core", "7.4.0").
            exclude("jta", "jta").
            exclude("jndi", "jndi")

    compile("com.github.rcarz", "jira-client", "master")
}