plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.spring") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.jpa") version "1.9.24" apply false
    id("org.springframework.boot") version "3.3.0" apply false
    id("io.spring.dependency-management") version "1.1.5" apply false
}

allprojects {
    group = "com.financeangle"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
