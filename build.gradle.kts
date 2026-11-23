plugins {
    alias(libs.plugins.kotlin.jvm)
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
    maven("https://repo.extendedclip.com/releases/") // PlaceholderAPI
    maven("https://jitpack.io") // VaultAPI
}

val syntaxCoreBundle by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    syntaxCoreBundle(libs.syntaxcore)
    compileOnly(kotlin("stdlib"))
    compileOnly(libs.syntaxcore)
    compileOnly(libs.syntaxdb.core)
    compileOnly(libs.messagehandler.paper)
    compileOnly(libs.plotsx.api)
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("org.eclipse.aether:aether-api:1.1.0")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.milkbowl:VaultAPI:1.7.1")
    compileOnly("pl.syntaxdevteam:cleanerx:1.5.1-DEV")
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
    val props = mapOf(
        "version" to project.version,
        "description" to project.description,
        "kotlinVersion" to libs.versions.kotlin.get(),
        "syntaxDbVersion" to libs.versions.syntaxdb.get(),
        "messageHandlerVersion" to libs.versions.messagehandler.get()
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching(listOf("paper-plugin.yml", "paper-libraries.yml")) {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("TeamX")
    archiveClassifier.set("")
    archiveVersion.set(project.version.toString())
    from({ zipTree(syntaxCoreBundle.singleFile) }) {
        exclude("net/kyori/**", "META-INF/versions/**/net/kyori/**")
        exclude("kotlin/**", "META-INF/versions/**/kotlin/**", "META-INF/*.kotlin_module")
        exclude("META-INF/MANIFEST.MF", "META-INF/maven/**", "**/module-info.class")
    }
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    mergeServiceFiles()
}

tasks.jar {
    archiveClassifier.set("plain")
}

plugindeployer {
    paper { dir = "/home/debian/poligon/Paper/1.21.11/plugins" } //ostatnia wersja dla Paper
    folia { dir = "/home/debian/poligon/Folia/1.21.8/plugins" } //ostatnia wersja dla Folia
}
