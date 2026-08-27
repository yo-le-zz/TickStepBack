plugins {
    java
}

group = "dev.yolezz"
version = "1.0.1"

description = "Debug temporel (undo borne des ticks) pour Paper/Purpur 1.21.10, pense pour le debug de machines Redstone."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper API only - provided by the server at runtime, never shaded into
    // our jar. We have no other runtime dependency, so no shadow/shading
    // plugin is needed at all: the plain `jar` task produced by the `java`
    // plugin is the whole build.
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")

    // Pure-Java unit tests (RingBuffer) - no Bukkit server required to run these.
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    // Substitutes ${version} in plugin.yml with the project version above.
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("TickStepBack")
    // archiveVersion defaults to project.version ("1.0.1"), producing
    // build/libs/TickStepBack-1.0.1.jar - exactly what plugins/ expects.
}

// `assemble` (and therefore `build`) already depends on `jar` by default via
// the java plugin; no extra wiring is required.
