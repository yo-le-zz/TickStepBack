# TickStepBack

Plugin Paper/Purpur 1.21.10 (Java 21) qui ajoute un "undo" borne au systeme
vanilla `/tick freeze` + `/tick step`, pense pour le debug de machines
Redstone.

Ce n'est **pas** une machine a remonter le temps infinie : le plugin ne
conserve qu'une fenetre glissante des N derniers ticks reellement executes
(par defaut 200), sous forme de deltas (changements), pas de copies
completes du monde.

## Changelog recent

- **Version 1.0.1.**
- **Numerotation des ticks alignee sur vanilla.** Le plugin utilise
  desormais directement `ServerTickStartEvent#getTickNumber()` (le vrai
  compteur de tick du serveur, celui que `/tick query` affiche) au lieu
  d'un compteur interne. Avant ce changement, un compteur prive pouvait
  driver du compteur reel du serveur ; "N ticks en arriere" correspond
  maintenant toujours exactement a N vrais ticks Minecraft, sans facteur
  de conversion.
- **Le monde ne reste plus fige apres un rollback (tentative 2).** La
  restauration des blocs se fait toujours en deux passes (voir "Details
  du rollback"), mais la seconde passe (reactivation physique) couvre
  maintenant aussi les 6 voisins directs de chaque bloc restaure, pas
  seulement les blocs restaures eux-memes - necessaire pour que les
  comparators/repeaters adjacents a un bloc restaure se re-evaluent
  correctement.
- **Nouveau : `rollback-physics-mode` dans config.yml** (`settle` par
  defaut, ou `immediate`). Si le blocage persiste malgre tout sur votre
  installation apres ces deux correctifs - ce que je n'ai pas pu verifier
  moi-meme faute d'acces a un serveur reel dans cet environnement -
  essayez `immediate` et/ou activez `debug-logging: true` : la console
  affichera l'etat de `ServerTickManager` avant/apres chaque rollback et
  chaque restauration/reactivation de bloc, ce qui est le seul moyen
  fiable de localiser precisement ce qui bloque sur votre version/
  configuration specifique.
- **Commande courte `/tsb`** enregistree comme commande racine a part
  entiere (pas seulement un alias) pour eviter tout probleme de
  resolution.

## Sommaire

- [Installation](#installation)
- [Compatibilite](#compatibilite)
- [Commandes](#commandes)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Fonctionnement](#fonctionnement)
- [Ce qui est restaurable / ce qui ne l'est pas](#ce-qui-est-restaurable--ce-qui-ne-lest-pas)
- [Performances](#performances)
- [Details du rollback](#details-du-rollback)
- [Tests](#tests)
- [Limitations connues et pistes NMS](#limitations-connues-et-pistes-nms)

## Installation

1. `./gradlew build` (necessite un acces reseau a Maven Central et au repo
   PaperMC : `https://repo.papermc.io/repository/maven-public/`).
2. Copier `build/libs/TickStepBack-<version>.jar` dans `plugins/` de votre
   serveur **Paper 1.21.10** ou **Purpur 1.21.10** (Java 21).
3. Demarrer le serveur, editer `plugins/TickStepBack/config.yml` si besoin,
   puis `/reload confirm` ou redemarrer.

> Ce depot n'a pas pu etre compile dans l'environnement qui a genere ce
> code (pas d'acces reseau a `repo.papermc.io` / Maven Central depuis ce
> bac a sable). Le code a ete relu attentivement et verifie contre la
> Javadoc Paper 1.21.x pour chaque API utilisee (voir commentaires dans le
> code), mais vous devez compiler et tester sur un vrai serveur avant
> production. `RingBuffer`, le coeur du ring buffer d'historique, est
> independant de Bukkit et ses tests unitaires (`./gradlew test`) ont ete
> executes avec succes dans cet environnement.

## Compatibilite

Version courante : **1.0.1**.

### Icone

`icon-placeholder.png` (512x512 px, PNG, fond transparent) est un
espace reserve a la racine du depot — a remplacer par votre propre
icone une fois prete, meme nom de fichier. 512x512 est la resolution
carree communement utilisee par les plateformes de plugins (Hangar,
Modrinth, SpigotMC) ; gardez le sujet principal dans la zone centrale
au cas ou l'image serait recadree/arrondie a l'affichage. Ce fichier
n'a aucun effet sur le fonctionnement du plugin lui-meme (Paper ne lit
pas d'icone depuis `plugin.yml` ou le jar) : il ne sert qu'a l'usage
externe (page de publication, README, etc.).

### Ce qui est reellement livre dans ce depot

- Cible officielle : **Paper 1.21.10** et **Purpur 1.21.10**, Java 21.
- N'utilise que l'API publique Paper/Bukkit (`org.bukkit.*`,
  `com.destroystokyo.paper.*`, `io.papermc.paper.*`). Aucun acces NMS,
  aucune modification du jar Paper/Purpur.

### La demande "supporte toutes les versions 1.20.1 -> derniere, et tous
### ces systemes : Paper, Purpur, Fabric, Forge, NeoForge, Mohist, Bukkit,
### Spigot, Magma, Arclight, Banner"

Plutot que de livrer une compatibilite "en apparence" qui ne marcherait
pas reellement sur la moitie de cette liste (ce que le cahier des charges
initial du projet interdit explicitement), voici l'etat des lieux honnete,
plateforme par plateforme, avec la raison technique a chaque fois :

| Systeme | `/tick freeze` + `/tick step` existe ? | Ce plugin peut y tourner ? |
|---|---|---|
| **Bukkit** (reference) | Non | **Impossible.** Bukkit ne definit que l'API commune ; `/tick freeze`/`step` et `ServerTickManager` sont des ajouts **Paper**, absents de Bukkit lui-meme. |
| **Spigot** | Non | **Impossible**, meme raison : Spigot herite de Bukkit et n'implemente pas le systeme de tick freeze/step de Paper. Un plugin qui appelle `Bukkit.getServerTickManager()` plante au chargement sur Spigot (classe absente). |
| **Paper** | Oui, depuis la 1.20.3 (voir plus bas) | **Oui** - c'est la plateforme cible de ce plugin. |
| **Purpur** | Oui (fork de Paper, herite de son API) | **Oui** - deja dans le perimetre officiel. |
| **Fabric** | Le jeu vanilla a bien `/tick freeze`/`step` depuis la 1.20.3, mais Fabric est un *mod loader*, pas un serveur de plugins Bukkit | **Non avec ce jar.** Un plugin Bukkit ne se charge pas sur un serveur Fabric : il n'y a pas de `JavaPlugin`, pas de `plugin.yml`, pas d'API `org.bukkit.*` du tout. Il faudrait un **mod Fabric independant** (Fabric API + Mixins dans la boucle de tick), c'est-a-dire un second projet avec une architecture totalement differente, pas une "version" du meme code. |
| **Forge** | Idem vanilla 1.20.3+ | **Non avec ce jar**, meme raison que Fabric : Forge est un mod loader, pas un serveur Bukkit. Necessiterait un mod Forge (evenements Forge + Mixins/ASM), projet a part. |
| **NeoForge** | Idem | **Non avec ce jar**, meme raison (fork de Forge, toujours pas Bukkit). |
| **Mohist** | Hybride Forge+Bukkit non officiel | **Incertain, non teste.** Mohist rejoue une partie de l'API Bukkit par-dessus Forge ; si sa reimplementation expose fidelement `ServerTickManager`/`ServerTickStartEvent`/`ServerTickEndEvent` (ce qui n'est pas garanti - ce sont des ajouts Paper, pas Bukkit standard, et les hybrides ciblent surtout la compatibilite Bukkit/Spigot), le plugin pourrait charger, mais rien ne garantit que le comportement (detection de tick, evenements de bloc) soit fidele. Non maintenu officiellement par Mohist a ma connaissance a chaque version ; a tester avec `debug-logging: true` avant toute confiance. |
| **Magma** | Hybride Forge+Bukkit non officiel (successeur spirituel de Mohist) | **Incertain, non teste**, meme reserve que Mohist. |
| **Arclight** | Hybride Forge/Fabric/NeoForge+Bukkit non officiel | **Incertain, non teste**, meme reserve. Arclight vise plus large (plusieurs loaders) mais la question reste la meme : implemente-t-il specifiquement les classes Paper `ServerTickManager` et les evenements de tick Paper, ou seulement l'API Bukkit/Spigot de base ? Je n'ai pas pu le verifier ici. |
| **Banner** | Hybride Fabric+Bukkit non officiel | **Incertain, non teste**, meme reserve. |

**Pourquoi je n'ai pas fabrique 8 jars "compatibles" pour cette reponse :**
ce depot n'a pas d'acces reseau aux depots Fabric/Forge/NeoForge/
Mohist/Magma/Arclight/Banner ni a un serveur reel pour verifier quoi que
ce soit, et surtout, Fabric/Forge/NeoForge ne sont **pas des variantes**
de ce projet - ce sont des architectures totalement differentes (mods
avec Mixins dans le moteur du jeu, pas des plugins Bukkit) qui exigeraient
un code source distinct du debut a la fin, pas une recompilation. Fournir
un jar qui prétend "supporter Fabric" sans que ça marche irait
directement a l'encontre de la consigne initiale de ce projet ("pas de
fausse implementation qui pretend marcher"). Pour les hybrides (Mohist,
Magma, Arclight, Banner), la seule reponse honnete sans acces a un vrai
serveur de ce type est "essayez le jar Paper existant, activez
`debug-logging: true`, et verifiez au `/tsb status` si `ServerTickManager`
repond correctement" - je ne peux pas certifier un resultat que je n'ai
pas pu observer.

### Pourquoi la 1.20.1 precisement est hors de portee, quel que soit
### le plugin

`/tick freeze`, `/tick step` et l'interface `ServerTickManager` ont ete
ajoutes en **Minecraft 1.20.3** (pas avant). La 1.20.1 ne possede tout
simplement pas cette fonctionnalite cote vanilla/serveur : il n'y a rien
a "step back" puisqu'il n'existe pas de mecanisme de freeze/step a cette
version, sur aucune implementation (Paper y compris). Ce n'est pas une
limite de ce plugin en particulier, c'est une fonctionnalite absente du
jeu lui-meme avant la 1.20.3.

### Multi-version Paper/Purpur (1.20.3 -> derniere version)

Sur le perimetre ou c'est reellement possible (Paper/Purpur et leurs forks
qui suivent fidelement l'API Paper), la strategie recommandee pour une
compatibilite large est de **compiler contre la premiere version d'API
Paper qui expose `ServerTickManager`** (ligne 1.20.4, `paper-api:
1.20.4-R0.1-SNAPSHOT` - a verifier/ajuster selon la disponibilite exacte
de l'artefact au moment de la publication) plutot que contre la toute
derniere. Paper maintient une compatibilite source/binaire descendante
tres large sur l'API heritee de Bukkit et n'a pas de raison de casser une
interface aussi utilisee que `ServerTickManager`, donc un jar compile
ainsi devrait rester chargeable sur les versions plus recentes. Ce depot
cible pour l'instant specifiquement 1.21.10 (voir `build.gradle.kts`) ;
abaisser cette cible est une tache separee et **necessite un vrai test sur
chaque version visee**, que je ne peux pas faire depuis cet environnement
(pas d'acces reseau a `repo.papermc.io`, pas de serveur Minecraft
disponible ici). Si vous voulez ce changement fait maintenant, dites-le
et je modifie `build.gradle.kts`/`plugin.yml` en consequence - mais je ne
pourrai que vous fournir le code, pas une confirmation testee que chaque
version cible charge correctement.

## Commandes

Le plugin expose `/tickstepback` (alias `/stepback`) et, en commande
racine a part entiere pour aller plus vite, **`/tsb`** :

| Commande                        | Effet                                                             |
|----------------------------------|--------------------------------------------------------------------|
| `/tickstepback <ticks>`         | Revient en arriere de `<ticks>` ticks reellement executes.        |
| `/tickstepback status`          | Affiche l'etat de l'historique (voir plus bas).                   |
| `/tickstepback checkpoint`      | Force l'ecriture d'un checkpoint de securite (admin).              |
| `/tickstepback clear`           | Vide l'historique (admin).                                        |

`/tickstepback status` affiche :
- le nombre de ticks actuellement disponibles pour un rollback,
- l'id de tick interne le plus recent,
- le nombre total de changements enregistres dans la fenetre,
- la memoire approximative utilisee par l'historique,
- l'etat freeze/stepping courant du serveur.

### Pourquoi pas litteralement `/tick stepback <n>` ?

`/tick` est une commande Brigadier vanilla, reconstruite par le serveur a
chaque (re)chargement ; l'API plugin publique ne permet pas d'y greffer un
sous-noeud sans passer par des internals Brigadier/NMS fragiles d'une
version a l'autre (voir `nms/NmsCompatibility.java` pour le detail
technique). Plutot que de bricoler quelque chose de fragile pour gagner un
mot dans la commande, TickStepBack expose sa propre commande racine
`/tickstepback` (commande racine a part entiere, pas seulement un alias,
pour eviter tout probleme de resolution d'alias : `/tsb`). Le comportement demande (revenir en
arriere sur les ticks executes par le dernier `/tick step`) est identique.

## Permissions

- `tickstepback.use` (defaut : op) - `/tickstepback <ticks>` et `status`.
- `tickstepback.admin` (defaut : op) - `checkpoint` et `clear`.

## Configuration

Voir `config.yml` (genere automatiquement au premier demarrage) :

```yaml
history-ticks: 200          # profondeur max de la fenetre d'historique
tracking:
  blocks: true
  block-entities: true      # inventaires de coffres/fours/tonneaux/etc.
  entities: true
  redstone-power-only: false
auto-checkpoint: true
max-checkpoints: 10
max-block-changes-per-tick: 20000
debug-logging: false
rollback-physics-mode: settle   # settle (par defaut) ou immediate
```

## Fonctionnement

### Detection des ticks (le coeur du probleme)

Paper expose `ServerTickManager` (`Bukkit.getServerTickManager()`) avec
`isFrozen()` / `isStepping()` / `stepGameIfFrozen(int)` : c'est exactement
ce que `/tick freeze` et `/tick step` pilotent en interne, il n'y a pas
d'etat "plus vrai" a lire ailleurs.

Paper declenche aussi `ServerTickStartEvent` / `ServerTickEndEvent` a
chaque iteration de la boucle de tick principale - boucle qui continue de
tourner meme gele (reseau, commandes, chat...), mais qui ne fait avancer le
*monde* (redstone, pistons, entites...) que si le serveur n'est pas gele,
ou s'il est gele et qu'un step est en cours.

TickStepBack considere donc un tick comme "digne d'historique" (le monde
va reellement avancer) exactement quand, au moment de `ServerTickStartEvent` :

```
!tickManager.isFrozen()  OU  tickManager.isStepping()
```

C'est-a-dire : fonctionnement normal, ou freeze + step en cours. Aucun
historique n'est fabrique pendant un freeze inactif (pas de step en
cours), conformement a la demande.

### Capture des changements

Plutot que de faire confiance aux champs "avant/apres" propres a chaque
evenement Bukkit (incoherents d'un evenement a l'autre), TickStepBack
utilise une strategie unifiee de **positions "sales"** :

1. Un large ensemble d'evenements Bukkit/Paper (placement, casse, piston,
   redstone, dispenser/dropper, hopper, four, explosion, changement de bloc
   par une entite, etc. - voir `BlockChangeTracker`) marque une position de
   bloc comme "touchee ce tick" et capture un `BlockState` "avant" (premier
   contact seulement).
2. En fin de tick (`ServerTickEndEvent`), chaque position touchee est
   re-capturee ("apres") et la paire avant/apres est stockee dans le
   `TickDelta` du tick courant - sauf si elles sont identiques (no-op net
   sur le tick).

`BlockState` (et non seulement `Material`/`BlockData`) est capture : cela
inclut nativement les sous-etats pertinents pour la redstone (delai/verrou
d'un repeater, mode d'un comparator, extension/direction d'un piston, etat
d'un observer, forme d'un rail, ouverture d'une porte...) ainsi que le NBT
des block entities (contenu d'un coffre/four/tonneau/dispenser/dropper/
hopper), puisque Bukkit modelise deja tout cela dans `BlockState`/
`TileState`/`Container`.

Les entites (hors joueurs) sont suivies separement (spawn/despawn/
position/rotation/velocite/vie), voir `EntityChangeTracker`.

### Ring buffer et memoire

Chaque tick "digne d'historique" ouvre un `TickDelta`. S'il ne contient
aucun changement, il n'est pas stocke (rien a annuler de toute facon) -
seul son identifiant de tick continue d'incrementer, ce qui permet de
garder une correspondance exacte entre "N ticks en arriere" demande par
l'utilisateur et les vrais ticks ecoules, meme quand des ticks vides ne
sont pas stockes. S'il contient des changements, il est pousse dans un
ring buffer borne a `history-ticks` entrees ; au-dela, les entrees les plus
anciennes sont evincees automatiquement (voir `util/RingBuffer.java`,
teste unitairement).

## Ce qui est restaurable / ce qui ne l'est pas

**Restaurable (best-effort solide) :**
- Type de bloc, orientation, et toutes les sous-proprietes de `BlockData`
  (delai de repeater, mode de comparator, extension de piston, puissance
  d'observer, forme de rail, portes/trapdoors, etc.).
- Contenu des block entities suivies : coffres, doubles-coffres, fours,
  tonneaux, dispensers, droppers, hoppers, chaudrons, tout ce qui passe par
  les evenements ecoutes dans `BlockChangeTracker`.
- Spawn/despawn d'entites non-joueur, leur position/rotation/velocite/vie.
- Explosions (blocs detruits par `EntityExplodeEvent`/`BlockExplodeEvent`).

**Non restaurable, documente honnêtement plutôt que simule :**
- **Suppression d'entite (mort, despawn naturel...)** : Bukkit/Paper
  n'offre pas de moyen public de re-faire apparaitre une entite avec son
  UUID exact et son etat interne complet (equipement, IA, passagers...).
  TickStepBack ne tente pas un faux respawn approximatif qui serait
  silencieusement faux : ce cas est compte et signale ("suppressions
  d'entite non restaurables") dans le resultat de la commande, jamais
  restaure.
- **Etat interne IA/comportemental des entites** (cible de pathfinding,
  memoire du "brain", reputation de villageois, etat de raid...) : aucune
  API publique generique n'expose ceci (contrairement aux blocs via
  `BlockState`). Voir `nms/NmsCompatibility.java` pour ce qu'impliquerait
  une solution NMS.
- **Inventaire, position, XP et progression du joueur** : volontairement
  jamais touches par le rollback (le joueur doit rester present pendant le
  debug, voir cahier des charges). Seul l'etat du monde est restaure.
- **Tout changement de bloc qui ne declenche aucun evenement Bukkit**
  (rares, dependants de la version : par ex. certaines mises a jour de
  bookkeeping interne de sculk sensor). Non capture donc non restaure.
- **Mise a jour programmee de bloc (scheduled block tick) en cours** :
  restaurer un bloc restaure son etat visible, mais ne remet pas
  necessairement en file une mise a jour planifiee (ex: temps restant
  avant qu'un piston lent ne se retracte) - l'API Bukkit n'expose pas la
  file de scheduled ticks. En pratique, la plupart des mecanismes redstone
  re-evaluent leur etat au tick suivant, donc l'impact est generalement
  invisible, mais ce n'est pas garanti a 100% et c'est signale ici plutot
  que passe sous silence.

## Performances

Concu pour de grosses machines redstone en fonctionnement normal (hors
rollback) :

- **Aucun scan complet du monde**, jamais. Seules les positions
  effectivement touchees par un evenement ecoute sont capturees.
- **Aucun clone de chunk**, aucune sauvegarde complete du monde a chaque
  tick.
- Les ticks sans le moindre changement ne consomment (quasiment) aucune
  memoire (pas d'entree stockee dans le ring buffer).
- `max-block-changes-per-tick` protege contre un tick anormalement massif
  (des dizaines de milliers de blocs modifies d'un coup) : au-dela, le
  surplus est ignore avec un avertissement en log plutot que de degrader
  le TPS ou d'exploser la memoire.
- Le rollback lui-meme s'execute sur le thread principal (obligatoire pour
  modifier le monde en toute securite avec l'API Bukkit) ; pour un
  `/tickstepback` portant sur beaucoup de changements, prevoyez un court
  gel visible du serveur pendant l'operation - c'est attendu et sans
  danger puisque le serveur est de toute facon suppose etre `/tick freeze`
  pendant une session de debug.

## Details du rollback

1. Verifie qu'aucun rollback n'est deja en cours (sinon refus propre).
2. Si `auto-checkpoint: true` et qu'aucun checkpoint n'a encore ete pris
   pour la session de debug courante (delimitee par le dernier front
   montant de `/tick freeze`), ecrit un fichier de securite dans
   `plugins/TickStepBack/checkpoints/` (voir `CheckpointManager` - **ce
   n'est pas une sauvegarde complete du monde**, seulement le plus ancien
   etat connu de chaque position deja suivie par l'historique, au format
   `/setblock`, pour un filet de securite manuel).
3. Passe `rollbackInProgress = true` : les trackers cessent d'enregistrer
   (et donc d'observer) tout changement le temps du rollback, pour ne pas
   polluer son propre historique.
4. Depile les ticks du plus recent au plus ancien tant que l'id de tick
   cible n'est pas atteint ou que l'historique n'est pas epuise. Pour
   chaque bloc touche, applique `beforeState.update(force=true,
   applyPhysics=false)` :
   - `force=true` : applique l'etat quel que soit le bloc actuellement
     present (evite les refus de coherence de Bukkit).
   - `applyPhysics=false` : n'entraine **pas** de mise a jour de voisinage
     a ce stade - si on notifiait a chaque bloc restaure individuellement,
     un bloc deja restaure pourrait declencher une cascade en reagissant
     a des voisins encore dans leur etat pre-rollback (pas encore
     restaures), ce qui produirait un resultat incoherent selon l'ordre
     de traitement.
5. **Passe de reactivation (settle pass)** : une fois TOUTES les positions
   de TOUS les ticks annules deja dans leur etat final correct, une
   seconde passe re-applique chaque position touchee **et ses 6 voisins
   directs** avec cette fois `applyPhysics=true` (sans changer la donnee
   des positions touchees, qui est deja la bonne - les voisins, eux,
   reçoivent simplement une notification). C'est cette passe qui notifie
   les voisins et raccroche le redstone/les pistons/les observers au
   systeme de mise a jour vanilla, pour que la propagation reprenne
   normalement aux ticks suivants. Les voisins non modifies sont inclus
   expres : un comparator ou un repeater a cote d'un bloc restaure decide
   de sa propre sortie en lisant ses voisins, donc s'il n'est pas
   lui-meme notifie, il peut continuer a afficher l'etat d'avant le
   rollback indefiniment. **Sans cette passe (ou en ne notifiant que les
   blocs eux-memes sans leurs voisins), le monde reste visuellement
   correct mais partiellement deconnecte du systeme de tick/notification :
   plus rien ne se propage correctement, meme apres un nouveau
   `/tick step` ou un `/tick unfreeze`**, jusqu'a ce qu'un evenement
   externe (casser/poser un bloc a cote, etc.) force une mise a jour de
   voisinage - c'est le bug corrige ici.
6. Pour les entites : un spawn est annule en supprimant l'entite (si
   toujours presente) ; un deplacement est annule en teleportant l'entite
   a sa position anterieure ; une suppression n'est pas annulable (voir
   plus haut).
7. Remet `rollbackInProgress = false` et renvoie un resultat detaille
   (ticks demandes vs obtenus, nombre de blocs/entites traites, messages
   d'avertissement) affiche au demandeur.

Si l'historique disponible est plus court que la demande, le rollback fait
de son mieux (restaure tout ce qui est disponible) et le signale
explicitement comme partiel plutot que de pretendre avoir tout restaure.

## Tests

- `util/RingBuffer.java` (le ring buffer d'historique, independant de
  Bukkit) est couvert par des tests JUnit 5 dans
  `src/test/java/.../util/RingBufferTest.java`, executables hors serveur
  via `./gradlew test`. Ces tests couvrent : eviction FIFO au-dela de la
  capacite, retrait LIFO (`popNewest`), ordre d'iteration du plus recent au
  plus ancien, retrecissement de capacite a chaud, et une simulation
  numerique de l'exemple du cahier des charges (`/tick step 50` puis
  `/tickstepback 20` doit retrouver l'etat apres 30 ticks).
- Le reste de la logique (trackers, rollback) depend de l'API Bukkit et
  necessite un serveur reel pour un test d'integration - **cet
  environnement de generation de code n'a pas d'acces reseau a un
  jar Paper/Purpur ni la possibilite de lancer un serveur Minecraft**, donc
  aucun test d'integration automatise n'a pu etre execute ici. Plan de
  test manuel recommande sur un vrai serveur 1.21.10, a executer avant
  toute utilisation en production :

  1. **Bloc simple** : `/tick freeze` -> poser stone -> `/tick step 1` ->
     casser et poser un bloc de redstone -> `/tick step 1` ->
     `/tickstepback 1` -> verifier retour a stone.
  2. **Piston** : etendre un piston via redstone -> `/tickstepback` ->
     verifier piston et blocs deplaces restaures.
  3. **Repeater/comparator** : changer l'etat (delai, verrouillage, mode)
     -> `/tickstepback` -> verifier l'etat exact restaure.
  4. **Coffre** : deposer/retirer des objets -> `/tickstepback` ->
     verifier le contenu.
  5. **Dispenser/dropper** : le faire tirer -> `/tickstepback` -> verifier
     contenu et etat.
  6. **Hopper** : transfert entre deux inventaires -> `/tickstepback` ->
     verifier les deux cotes.
  7. **Plusieurs changements dans le meme tick** : machine qui modifie
     plusieurs blocs en un seul `/tick step 1` -> verifier que tous sont
     restaures ensemble.
  8. **Plusieurs `/tick step` successifs** : reproduire l'exemple du
     cahier des charges (`step 10`, `step 20`, `stepback 15`) et verifier
     numeriquement (via `status` et observation) que l'etat correspond aux
     15 premiers ticks de la sequence.
  9. **`/tickstepback status`** apres chaque etape pour verifier la
     coherence des compteurs.
  10. **Depassement de l'historique** : reduire `history-ticks` a une
      petite valeur, demander un stepback plus grand, verifier que le
      resultat est signale `PARTIAL` avec le bon compte.

## Limitations connues et pistes NMS

Voir `src/main/java/dev/yolezz/tickstepback/nms/NmsCompatibility.java`,
qui documente precisement (1) pourquoi `/tick stepback` litteral comme
sous-commande vanilla n'a pas ete implemente sans NMS, et (2) ce
qu'impliquerait une restauration complete de l'etat interne des entites
via NMS. Aucun des deux n'est implemente dans cette version : le plugin
prefere ne rien pretendre au-dela de ce que l'API publique permet de
garantir.
