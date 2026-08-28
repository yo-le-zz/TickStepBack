pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // paper-1.20 needs Java 17 and paper-26 needs Java 25 (see their
    // build.gradle.kts) while paper-1.21 needs Java 21 - most machines
    // only have ONE JDK installed locally, so without this plugin Gradle
    // refuses to build any module whose toolchain isn't already present
    // ("Cannot find a Java installation... toolchain download repositories
    // have not been configured"). This plugin lets Gradle auto-download
    // whichever JDK each module's toolchain{} block asks for, from the
    // Foojay Disco API, instead of requiring you to install 17/21/25
    // manually side by side.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "TickStepBack"

// "common/" is NOT included here on purpose: it holds shared Java source
// only (see common/README.md), no build.gradle.kts of its own. Each
// paper-* module below compiles that same source fresh against its own
// Paper API generation - see the top-level README "Compatibilite" for why
// a single compiled jar cannot span every era.
include("paper-1.20")
include("paper-1.21")
include("paper-26")

