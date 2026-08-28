plugins {
    java
}

description = "EXPERIMENTAL, NON TESTE - TickStepBack pour Paper/Purpur 26.x (26.1+), Java 25. Voir root README \"Compatibilite\"."

// Minecraft 26.1+ requires Java 25 (first version to do so - confirmed via
// the Minecraft Wiki's Java-version tutorial). This module is compiled
// separately, on its own Java 25 toolchain, deliberately never merged into
// the paper-1.21 jar: mixing bytecode targets in one artifact is not how
// this is normally done, and more importantly I have NOT been able to
// verify on a real 26.x server that ServerTickManager / ServerTickStartEvent
// / ServerTickEndEvent kept the exact same shape across the 1.21.x -> 26.x
// transition (some Paper Registry API changes are documented for 26.x,
// though this plugin does not touch the Registry API itself). Treat this
// jar as a starting point to validate on a real 26.x server, not as a
// confirmed-working release.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

sourceSets {
    main {
        java.srcDirs("../common/src/main/java")
    }
    test {
        java.srcDirs("../common/src/test/java")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Paper changed its artifact version format starting with the 26.x
    // line: no more "-R0.1-SNAPSHOT" suffix. The official PaperMC project
    // setup docs give "26.2.build.+" for dynamic latest-build resolution,
    // or a pinned build like "26.2.build.112-stable" for reproducible
    // builds. Using the dynamic form here; pin to a specific build number
    // once you've picked one for a real release.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("TickStepBack")
    archiveClassifier.set("paper-26-EXPERIMENTAL")
    // -> build/libs/TickStepBack-1.0.1-paper-26-EXPERIMENTAL.jar
}
