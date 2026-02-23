plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "org.fahri.nexusone"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("NexusTags.jar")
    }
    build {
        dependsOn(shadowJar)
    }
}

tasks.processResources {
    // Memberitahu Gradle untuk mengambil file yang pertama ditemukan jika ada duplikat
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}