# common/

Ce dossier n'est **pas** un module Gradle (il n'apparait pas dans
`settings.gradle.kts` et n'a pas de `build.gradle.kts`). Il contient le
code Java partage par tous les modules `paper-*/`, qui pointent chacun
leur `sourceSets.main.java.srcDirs` ici.

Pourquoi pas un module compile une fois et reutilise en dependance jar ?
Parce que ce code appelle des classes `org.bukkit.*`/`io.papermc.paper.*`
dont la version exacte differe entre 1.20.x, 1.21.x et 26.x (voir le
README a la racine). Il doit donc etre **recompile a chaque fois** contre
le `paper-api` propre a chaque module, pas compile une seule fois puis
partage en binaire. C'est la technique standard pour les plugins Paper
qui visent plusieurs generations d'API a partir d'une seule base de code.

`common/src/test/java` contient les tests unitaires purement Java
(`RingBufferTest`) qui ne dependent d'aucune API Bukkit - ils tournent
identiquement dans chacun des trois modules.
