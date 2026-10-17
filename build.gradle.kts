plugins {
    java
}

group = "me.limbo"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

// Paper assumes plugins.yml-based plugins are Spigot-mapped and remaps them at startup. TickRatePackets uses
// reflection with Mojang names, so mark the jar as Mojang-mapped (see docs.papermc.io -> userdev -> Mojang mappings).
tasks.jar {
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }
}
