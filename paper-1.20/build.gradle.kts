plugins {
    java
}

description = "TickStepBack pour Paper/Purpur 1.20.4 (premiere ligne 1.20.x avec /tick freeze+step), Java 17."

// Minecraft 1.18 -> 1.20.4 requires Java 17 (Java 21 only became the
// minimum starting 1.20.5). /tick freeze + /tick step + ServerTickManager
// were added in 1.20.3, which Paper folded into its 1.20.4 API line (no
// separate 1.20.3 paper-api artifact was published, as 1.20.3 was a very
// short-lived release quickly superseded by 1.20.4) - so 1.20.4 is the
// earliest version this module can realistically target. If you need
// 1.20.3 specifically, verify whether PaperMC ever published a
// `1.20.3-R0.1-SNAPSHOT` artifact; I could not confirm this from here.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
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
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
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
    archiveClassifier.set("paper-1.20")
    // -> build/libs/TickStepBack-1.0.1-paper-1.20.jar
}
