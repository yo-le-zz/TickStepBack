# mod-fabric/ - Squelette Fabric (etat initial, PAS de parite fonctionnelle)

## Ce qui existe ici

- Un projet Gradle Fabric Loom minimal, isole du build multi-module Paper
  (`settings.gradle` separe : `cd mod-fabric && ./gradlew build`, ne pas
  lancer `./gradlew` depuis la racine du depot pour ce module).
- Un point d'entree de mod (`TickStepBackFabric`) qui lit l'etat vanilla
  de `/tick freeze`/`/tick step` a chaque tick et le journalise.
- Une commande `/tickstepback <ticks>` enregistree mais qui ne fait
  actuellement **rien** d'autre que repondre "pas encore implemente".

## Ce qui n'existe PAS ici (volontairement, pas par oubli)

Le vrai travail du plugin Paper - `BlockChangeTracker`/
`EntityChangeTracker` (capture avant/apres via les evenements Bukkit) et
`RollbackManager` (restauration + reactivation physique) - **n'est pas
porte sur Fabric**. Cote Fabric il n'y a pas d'evenements Bukkit a
ecouter : l'equivalent demanderait des **Mixins** dans le code de pose de
bloc du jeu lui-meme (`World`/`ServerWorld`), puis reutiliser la meme
architecture ring-buffer/delta deja ecrite dans `common/src/main/java`
mais portee sur les types vanilla (`net.minecraft.util.math.BlockPos`,
`net.minecraft.block.BlockState`) au lieu des types `org.bukkit.*`. C'est
un chantier separe et substantiel, pas un "port rapide" - je ne l'ai pas
fait ici pour ne pas livrer une fausse promesse de compatibilite
fonctionnelle.

## Ce que je n'ai pas pu verifier depuis cet environnement

- **Aucun acces reseau a `maven.fabricmc.net`** ici, donc ce module n'a
  jamais ete resolu ni compile dans cet environnement. Les versions dans
  `gradle.properties` (Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric
  Loader `0.16.10`, Fabric API `0.114.4+1.21.4`) sont plausibles pour
  cette cible mais a reverifier contre les releases reelles au moment ou
  vous buildez.
- **Les noms de methode exacts** sur `TickRateManager`
  (`isFrozen()`/`isSteppingForward()`/etc. dans le code ci-dessus) sont
  bases sur ma comprehension du mapping Yarn au moment de la redaction,
  pas sur une compilation reelle verifiee ici. A confirmer via un
  navigateur de mappings (ex. Linkie, ou le viewer Yarn officiel) pour
  votre `minecraft_version` exacte avant de compiler.
- Le code n'a donc **jamais tourne**, meme localement. Considerez ce
  module comme un point de depart documente, pas comme un jar utilisable
  en l'etat.

## Forge / NeoForge

Non commences. Ce seraient, comme Fabric, des projets a architecture
totalement differente (systeme de Mixins/evenements propre a chaque mod
loader, outillage Gradle different - ForgeGradle/NeoGradle au lieu de
Fabric Loom). Dites-le si vous voulez que je les demarre selon le meme
principe (squelette de detection de tick + commande, sans pretendre a une
parite complete non verifiee).
