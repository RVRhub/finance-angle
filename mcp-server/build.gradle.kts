import org.gradle.api.tasks.Sync
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("io.ktor:ktor-server-core:2.3.11")
    implementation("io.ktor:ktor-server-netty:2.3.11")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-jackson:2.3.11")
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

application {
    mainClass.set("com.financeangle.mcp.McpServerKt")
}

val httpStartScripts = tasks.register<CreateStartScripts>("httpStartScripts") {
    mainClass.set("com.financeangle.mcp.HttpMcpServerKt")
    applicationName = "http-mcp-server"
    classpath = sourceSets.main.get().runtimeClasspath
    outputDir = layout.buildDirectory.dir("httpScripts").get().asFile
}

tasks.named<Sync>("installDist") {
    dependsOn(httpStartScripts)
    from(httpStartScripts.map { it.outputDir }) {
        into("bin")
    }
}

distributions {
    named("main") {
        contents {
            from(httpStartScripts.map { it.outputDir }) {
                into("bin")
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
