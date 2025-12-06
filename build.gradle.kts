plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "9.2.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "pl.syntaxdevteam"
version = "1.1.4-DEV"
description = ""

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
    maven("https://repo.codemc.org/repository/maven-public")
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/") {
        name = "syntaxdevteam-releases"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("org.eclipse.aether:aether-api:1.1.0")
    compileOnly("org.yaml:snakeyaml:2.4")
    compileOnly("com.google.code.gson:gson:2.13.1")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.23.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.23.0")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.23.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.23.0")
    compileOnly("net.kyori:adventure-text-serializer-ansi:4.23.0")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.5.4")
    compileOnly("org.xerial:sqlite-jdbc:3.50.2.0")
    compileOnly("org.postgresql:postgresql:42.7.7")
    compileOnly("com.h2database:h2:2.3.232")
    compileOnly("com.zaxxer:HikariCP:6.3.0")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("io.github.miniplaceholders:miniplaceholders-kotlin-ext:2.3.0")
    compileOnly("com.github.milkbowl:VaultAPI:1.7.1")
    compileOnly("net.milkbowl.vault:VaultUnlockedAPI:2.13")
    compileOnly("pl.syntaxdevteam:cleanerx:1.5.1-DEV")
    compileOnly("pl.syntaxdevteam.punisher:PunisherX:1.4.1")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks {
    runServer {
        minecraftVersion("1.21.10")
        runDirectory(file("run/latest"))
    }

    build {
        dependsOn("shadowJar")
    }
}

tasks.processResources {
    val props = mapOf("version" to version, "description" to description)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
