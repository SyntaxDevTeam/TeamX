plugins {
    kotlin("jvm") version "2.4.20"
    id("com.gradleup.shadow") version "9.6.1"
    `maven-publish`
    id("io.papermc.hangar-publish-plugin") version "0.1.3"
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("pl.syntaxdevteam.plugindeployer") version "1.0.2"
}

group = "pl.syntaxdevteam"
version = "1.0.0-SNAPSHOT"
description = ""

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

repositories {
    maven("https://nexus.syntaxdevteam.pl/repository/maven-snapshots/") //SyntaxDevTeam
    maven("https://nexus.syntaxdevteam.pl/repository/maven-releases/") //SyntaxDevTeam
    gradlePluginPortal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.menthamc.org/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
    maven("https://repo.codemc.org/repository/maven-public/") // VaultUnlockedAPI
    maven("https://jitpack.io") // VaultAPI
    maven("https://repo.essentialsx.net/releases/") // EssentialsX
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


tasks {
    build {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion("1.21.10")
        runDirectory(file("run/paper"))
    }
    runPaper.folia.registerTask()
}

tasks.processResources {
    val props = mapOf("version" to version, "description" to description)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("TeamX")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    mergeServiceFiles()
}

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/1.21.11/plugins" } //ostatnia wersja dla Paper
    folia { dir = "/home/debian/poligon/Folia/1.21.8/plugins" } //ostatnia wersja dla Folia
}