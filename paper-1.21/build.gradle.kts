plugins {
    java
}

description = "TickStepBack pour Paper/Purpur 1.21.x (1.21.0 a 1.21.11), Java 21."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
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
    // Compiled against the EARLIEST 1.21.x Paper API build confirmed to
    // expose everything this plugin needs (ServerTickManager#isSprinting(),
    // confirmed present since 1.21.4), NOT the latest one - see root
    // README "Compatibilite" for why that maximizes the range of 1.21.x
    // patch versions this single jar keeps loading on.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

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
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("TickStepBack")
    archiveClassifier.set("paper-1.21")
    // -> build/libs/TickStepBack-1.0.1-paper-1.21.jar
}
