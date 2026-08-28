// Root build script. No java plugin applied here on purpose: this project
// has no single-jar "build" anymore, it produces THREE separate jars, one
// per supported Paper API generation - see README "Compatibilite" for why.
// Build everything with:
//     ./gradlew clean build
// which builds paper-1.20, paper-1.21 and paper-26 as their own jars under
// each module's own build/libs/, or target one specifically with e.g.
//     ./gradlew :paper-1.21:build

allprojects {
    group = "dev.yolezz"
    version = "1.0.1"
}
