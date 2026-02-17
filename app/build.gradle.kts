plugins {
    `java-library`
    id("io.github.goooler.shadow") version "8.1.8"
}

group = "pl.puffmc"
version = "1.7.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    
    // MySQL connector
    implementation("mysql:mysql-connector-java:8.0.33")
    
    // HikariCP for database connection pooling
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Caffeine for cache management (prevent memory leaks) - smaller than Guava
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
}

tasks {
    shadowJar {
        archiveFileName.set("changelogbook.jar")
        
        // Relocate dependencies to avoid conflicts
        relocate("com.mysql", "pl.puffmc.changelog.libs.mysql")
        relocate("com.zaxxer.hikari", "pl.puffmc.changelog.libs.hikari")
        relocate("com.github.benmanes.caffeine", "pl.puffmc.changelog.libs.caffeine")
        
        // Minimize - remove unused classes, but keep MySQL driver and Caffeine intact
        // Caffeine uses reflection to load internal cache implementations (SSMSA, etc.)
        minimize {
            exclude(dependency("mysql:mysql-connector-java:.*"))
            exclude(dependency("com.github.ben-manes.caffeine:.*"))
        }
    }
    
    // Use shadowJar as default build output
    build {
        dependsOn(shadowJar)
    }
    
    jar {
        // Disable regular jar task - we only want shadowJar
        enabled = false
    }
    
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }
    
    processResources {
        filteringCharset = Charsets.UTF_8.name()
        val props = mapOf(
            "name" to rootProject.name,
            "version" to project.version
        )
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
