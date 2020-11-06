import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.itransition"
version = "1.0"

plugins {
    kotlin("jvm") version "1.4.10"
    application
}

application {
    mainClassName = "TrimmerKt"
    applicationDefaultJvmArgs = listOf("-Dlog4j.configuration=non_existent_file")
}

repositories {
    jcenter()
    maven("https://packages.atlassian.com/maven/repository/public")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.shyiko.dotenv", "dotenv", "0.1.1")
    implementation("com.atlassian.jira", "jira-tests", "8.13.1")
    implementation("com.atlassian.jira", "jira-core", "8.13.1") {
        exclude("jta", "jta")
        exclude("jndi", "jndi")
        exclude("com.octo.captcha", "jcaptcha")
        exclude("com.octo.captcha", "jcaptcha-api")
    }
    implementation("com.atlassian.jira", "jira-rest-java-client-core", "5.2.2")
}
