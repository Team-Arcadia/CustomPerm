# CustomPerm

> Système de permissions granulaires pour Minecraft NeoForge — accordez des commandes vanilla individuelles à des joueurs non-op, avec ou sans LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-MIT-blue.svg)]()
[![Version](https://img.shields.io/badge/version-0.1.0-brightgreen.svg)]()

---

## Pourquoi ce mod

Minecraft vanilla a un système binaire : un joueur est **op** (toutes les commandes) ou **non-op** (aucune commande de gestion). Pas d'entre-deux.

CustomPerm permet de donner **précisément** les commandes que vous voulez à des joueurs non-op, sans leur accorder l'op complet. Concrètement :

- Vous voulez qu'un joueur puisse `/gamemode spectator` mais pas `/op` ? Possible.
- Donner `/give` à un grade VIP sans qu'ils puissent `/ban` ? Possible.
- Créer des macros (alias) qui chaînent plusieurs commandes en une seule ? Possible.

Le mod s'intègre nativement à **LuckPerms** s'il est installé, sinon il fournit son propre système de grades stocké en JSON.

---

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Installation](#installation)
- [Démarrage rapide](#démarrage-rapide)
- [Commandes](#commandes)
- [Nodes de permission](#nodes-de-permission)
- [Fichiers de configuration](#fichiers-de-configuration)
- [Cas d'usage courants](#cas-dusage-courants)
- [Aliases et macros](#aliases-et-macros)
- [Considérations de sécurité](#considérations-de-sécurité)
- [Diagnostic et dépannage](#diagnostic-et-dépannage)
- [Compilation depuis les sources](#compilation-depuis-les-sources)
- [Tests](#tests)
- [Comment ça marche (technique)](#comment-ça-marche-technique)
- [Compatibilité avec d'autres mods](#compatibilité-avec-dautres-mods)
- [Limitations connues](#limitations-connues)
- [Licence](#licence)

---

## Fonctionnalités

- **Permissions granulaires par commande** : exposez n'importe quelle commande racine vanilla ou de mod tiers avec `/customperm command add <name>`.
- **Modèle deny-by-default** : aucune commande n'est exposée par défaut ; une commande non exposée garde son comportement vanilla ou moddé.
- **Backend JSON interne** : gestion des grades, assignations joueurs et nodes de permission sans plugin externe.
- **Backend LuckPerms** : utilisation automatique de LuckPerms lorsqu'une version compatible est installée.
- **Contrôle de version LuckPerms** : LuckPerms `5.4.150+` requis ; les versions trop anciennes ou prerelease sont refusées par sécurité.
- **Fallback LP configurable** : si LuckPerms devient indisponible au runtime, `settings.json` décide si CustomPerm refuse les permissions (`deny`, défaut) ou bascule sur le backend interne (`internal`).
- **Visibilité du backend** : logs de boot, `/customperm status`, `/customperm debug` et `/customperm test` indiquent Internal, LuckPerms, Internal fallback from LuckPerms ou le mode deny.
- **RBAC multi-grades** : un joueur peut avoir plusieurs grades internes ; les permissions sont résolues par union des grades assignés.
- **DENY explicite** : les grades internes supportent `deniedPermissions`, et tout DENY correspondant l'emporte sur les ALLOW.
- **Wildcards de permissions** : `*`, `customperm.command.*` et `customperm.alias.*` sont supportés.
- **Aliases et macros** : créez des commandes racine personnalisées (`/fly`, `/heal`, `/starter`) qui exécutent une ou plusieurs commandes configurées.
- **Edition des steps d'alias** : ajout, suppression et inspection de steps individuels avec indices 0-based.
- **Elévation des aliases** : les steps d'alias s'exécutent avec op level 4 pour permettre aux macros signées par l'admin d'appeler des commandes op-only.
- **Garde-fous sur les aliases** : `/customperm` est réservé, les steps vides sont ignorés, les aliases sans step sont refusés, et le shadow d'une commande existante émet un warning.
- **Enregistrement runtime des aliases** : ajout, remplacement ou retrait d'alias sur le dispatcher vivant sans redémarrage serveur.
- **Wrapping du command tree** : les commandes racine Brigadier sont clonées et wrappées pour appliquer les permissions CustomPerm sur les commandes exposées.
- **Préservation des ops** : les sources réellement op level 2+ gardent toujours l'accès ; le mod ne retire pas les droits opérateur.
- **Re-sync du command tree client** : après changement interne ou event LuckPerms, les joueurs concernés reçoivent un arbre de commandes à jour.
- **Hot-reload atomique** : `/customperm reload` charge `grades.json`, `aliases.json`, `commands.json` et `settings.json` en transaction ; un JSON invalide conserve le snapshot précédent.
- **Création et normalisation automatique des configs** : fichiers manquants, `{}`, champs inconnus et collections explicitement `null` sont normalisés vers des structures vides sûres.
- **Backups automatiques** : les reloads réussis écrivent des backups horodatés et conservent les trois dernières sauvegardes par fichier.
- **Lectures concurrentes sûres** : le snapshot actif est stocké dans un `AtomicReference`, sans lock sur le hot path de permission.
- **Diagnostics** : `/customperm status`, `/customperm scan`, `/customperm debug` et `/customperm test` couvrent l'inspection runtime et le dépannage.
- **Procédure de test manuel** : `docs/manual-test-procedure.html` fournit une checklist thème sombre avec export JSON/Markdown pour validation release.
- **Baseline de performance** : les benchmarks JMH documentent la résolution de permissions et la lecture concurrente du snapshot dans `docs/performance-baseline.md`.
- **Checks CI release** : GitHub Actions lance les GameTests, construit le jar distribuable et vérifie les métadonnées requises du jar.
- **Côté serveur uniquement** : aucun mod n'est requis côté client.

---

## Installation

### Prérequis

- **Minecraft 1.21.1**
- **NeoForge 21.1.221** ou supérieur
- **Java 21**
- (Optionnel mais recommandé) **LuckPerms 5.4.x ou 5.5.x** pour NeoForge

### Étapes

1. Téléchargez le dernier artefact de release CustomPerm depuis la [page Releases](../../releases).
2. Déposez le jar dans le dossier `mods/` de votre serveur.
3. (Optionnel) Déposez aussi le jar de [LuckPerms](https://luckperms.net/download) (build NeoForge 1.21.1).
4. Démarrez le serveur.

Au démarrage, vous verrez dans les logs **une seule** des deux lignes suivantes selon votre configuration :

```
[CustomPerm] LuckPerms detected — using LuckPerms backend.
[CustomPerm] LuckPerms not present — using internal JSON grade backend.
```

Suivie de la ligne de santé :

```
[CustomPerm] Ready — backend=LuckPerms dispatcherCommands=89 exposed=0 aliases=0 grades=0
```

Si aucune des deux lignes n'apparaît, le mod n'a pas chargé — vérifiez vos logs pour des stacktraces.

---

## Démarrage rapide

### Avec LuckPerms

```
# Console serveur
customperm command add gamemode
lp creategroup vip
lp group vip permission set customperm.command.gamemode true
lp user Steve parent add vip
```

`Steve` peut maintenant faire `/gamemode creative` même sans être op.

### Sans LuckPerms (système interne)

```
# Console serveur
customperm command add gamemode
customperm grade create vip
customperm grade addperm vip customperm.command.gamemode
customperm grade assign Steve vip
```

Même résultat : `Steve` peut utiliser `/gamemode`.

---

## Commandes

Toutes les commandes admin sont sous `/customperm` et **requièrent op level 2**.

### Exposition des commandes

Définit quelles commandes sont éligibles au système de permissions. Une commande non-exposée garde son comportement vanilla (op-only).

| Commande | Effet |
|---|---|
| `/customperm command add <name>` | Expose la commande `<name>` au système. |
| `/customperm command remove <name>` | Retire la commande, retour au comportement vanilla. |
| `/customperm command list` | Liste les commandes exposées. |

### Aliases (macros)

Crée des commandes personnalisées qui exécutent une ou plusieurs commandes. Les steps s'exécutent avec **op level 4** — voir [Considérations de sécurité](#considérations-de-sécurité).

| Commande | Effet |
|---|---|
| `/customperm alias add <name> <cmd1; cmd2; ...>` | Crée un alias. Les commandes sont séparées par `;`. |
| `/customperm alias addstep <name> <cmd>` | Ajoute un step à un alias existant (ou en crée un). |
| `/customperm alias removestep <name> <index>` | Retire le step d'index donné (0-based). |
| `/customperm alias steps <name>` | Affiche tous les steps d'un alias. |
| `/customperm alias remove <name>` | Supprime entièrement un alias. |
| `/customperm alias list` | Liste tous les aliases définis. |

### Grades (système interne, sans LuckPerms)

Ces commandes sont **bloquées si LuckPerms est actif** — utilisez `/lp` à la place.
Elles gèrent les nodes ALLOW. Les nodes DENY internes sont stockés dans `grades.json` via `deniedPermissions`.

| Commande | Effet |
|---|---|
| `/customperm grade create <name>` | Crée un grade vide. |
| `/customperm grade delete <name>` | Supprime un grade et le désassigne de tous les joueurs. |
| `/customperm grade addperm <grade> <node>` | Ajoute une perm au grade. |
| `/customperm grade removeperm <grade> <node>` | Retire une perm du grade. |
| `/customperm grade assign <player> <grade>` | Assigne le grade à un joueur. |
| `/customperm grade unassign <player> <grade>` | Désassigne. |
| `/customperm grade list` | Liste les grades définis. |

### Diagnostic et utilitaires

| Commande | Effet |
|---|---|
| `/customperm test <player> <node>` | Vérifie si un joueur a un node de permission donné. Retourne `GRANTED` ou `DENIED`. |
| `/customperm debug <player> <command>` | Rapport détaillé : commande dans le dispatcher ? exposée ? l'op-level passe ? la perm est granted ? le wrapper renvoie quoi ? |
| `/customperm status` | Snapshot global : backend, nb de commandes wrappées, exposées, aliases, grades. |
| `/customperm scan [pattern]` | Liste toutes les commandes du dispatcher avec leur état (exposée, alias, mod-interne). Filtre optionnel. |
| `/customperm reload` | Recharge les fichiers de config depuis le disque. |

---

## Nodes de permission

CustomPerm utilise un schéma de nodes hiérarchique compatible LuckPerms (et son système interne).

| Node | Effet |
|---|---|
| `*` | Wildcard global du backend interne. À utiliser avec précaution. |
| `customperm.command.<name>` | Autorise la commande `<name>` (si elle a été exposée). Ex: `customperm.command.gamemode` |
| `customperm.command.*` | Wildcard : couvre toutes les commandes exposées. |
| `customperm.alias.<name>` | Autorise l'alias `<name>`. Ex: `customperm.alias.fly` |
| `customperm.alias.*` | Wildcard alias. |

> ⚠️ **Important** : `customperm.command.<name>` ne donne accès à `<name>` que si elle a été préalablement exposée par `/customperm command add <name>`. Sinon, la commande reste op-only quelles que soient les perms.

---

## Fichiers de configuration

Stockés dans `config/arcadia/customperm/`. Auto-créés au premier lancement, modifiables à chaud (utilisez `/customperm reload` pour appliquer). Si un ancien dossier `config/customperm/` existe et que le nouveau dossier n'existe pas encore, CustomPerm copie les fichiers de configuration connus vers le nouvel emplacement sans supprimer les anciens fichiers.

### `commands.json`

Liste des commandes exposées au système.

```json
{
  "grantedCommands": ["gamemode", "time", "adminpanel"],
  "preserveOriginalRequires": {
    "gamemode": false,
    "time": false,
    "adminpanel": true
  }
}
```

`preserveOriginalRequires` est optionnel par commande. Une entrée absente vaut `false` pour conserver le comportement historique de CustomPerm. Mettez `true` pour les commandes sensibles, surtout modded, dont le prédicat Brigadier `requires` original doit rester obligatoire en plus de la permission CustomPerm.

### `settings.json`

Réglages de sécurité runtime.

```json
{
  "luckPermsFallbackMode": "deny"
}
```

`luckPermsFallbackMode` accepte :

- `deny` : défaut recommandé pour serveur public. Si LuckPerms est chargé mais indisponible, les checks CustomPerm retournent false.
- `internal` : mode compatibilité. Si LuckPerms est chargé mais indisponible, CustomPerm utilise `grades.json`.

### `aliases.json`

Aliases avec leurs steps.

```json
{
  "aliases": {
    "fly": ["gamemode spectator"],
    "heal": [
      "effect give @s minecraft:instant_health 10 100",
      "effect give @s minecraft:saturation 1 100",
      "say bien soigné !"
    ]
  }
}
```

### `grades.json` (mode Internal uniquement)

Grades et assignations utilisateurs.

```json
{
  "grades": {
    "vip": {
      "name": "vip",
      "permissions": ["customperm.command.gamemode", "customperm.alias.fly"],
      "deniedPermissions": []
    },
    "staff": {
      "name": "staff",
      "permissions": ["customperm.command.*", "customperm.alias.*"],
      "deniedPermissions": ["customperm.command.op"]
    }
  },
  "userGrades": {
    "550e8400-e29b-41d4-a716-446655440000": ["vip"],
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": ["staff", "vip"]
  }
}
```

Avec LuckPerms actif, ce fichier est ignoré (les perms passent par LP).

`deniedPermissions` est utilisé uniquement par le backend interne. Un DENY correspondant l'emporte sur n'importe quel ALLOW provenant des grades assignés.

---

## Cas d'usage courants

### Donner `/gamemode` à un grade VIP

**Avec LuckPerms** :
```
customperm command add gamemode
lp creategroup vip
lp group vip permission set customperm.command.gamemode true
lp user <pseudo> parent add vip
```

**Sans LuckPerms** :
```
customperm command add gamemode
customperm grade create vip
customperm grade addperm vip customperm.command.gamemode
customperm grade assign <pseudo> vip
```

### Créer un raccourci `/fly` qui passe en spectator

```
customperm alias add fly gamemode spectator
lp group vip permission set customperm.alias.fly true       # ou via grade
```

### Macro de soin avec plusieurs effets

```
customperm alias add heal effect give @s minecraft:instant_health 10 100; effect give @s minecraft:saturation 1 100; effect give @s minecraft:regeneration 30 2
lp group vip permission set customperm.alias.heal true
```

### Donner accès à plusieurs commandes d'un coup (wildcard)

```
customperm command add gamemode
customperm command add give
customperm command add tp
customperm command add effect
lp group staff permission set customperm.command.* true       # OU
customperm grade addperm staff customperm.command.*
```

Le wildcard ne couvre que les commandes **exposées**. Les autres commandes vanilla restent op-only.

### Permettre uniquement `/gamemode spectator` sans creative

L'API actuelle expose une commande au niveau racine — elle ne distingue pas les sous-modes. Pour ce cas spécifique, **utilisez les aliases** :

```
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
# n'expose PAS /gamemode lui-même
```

Les joueurs utilisent `/spec` au lieu de `/gamemode spectator`. La vraie `/gamemode` reste op-only, donc pas d'accès à `/gamemode creative`.

---

## Aliases et macros

Les aliases sont au cœur du mod et méritent une explication détaillée.

### Format

Un alias = une **liste ordonnée de commandes**. Quand un joueur autorisé exécute l'alias, chaque step est exécuté **séquentiellement** avec l'**autorité d'op level 4**.

### Création multi-step

```
customperm alias add starter give @s diamond_sword; give @s shield; effect give @s minecraft:resistance 60 1; tp @s 0 100 0
```

Sépare les commandes avec `;` (et un espace après pour la lisibilité, optionnel).

### Édition incrémentale

Pour ajouter/retirer des steps après création :

```
customperm alias steps heal           # affiche les steps avec leur index
customperm alias addstep heal say "Tu es soigné !"
customperm alias removestep heal 0    # retire le premier step
```

### Sélecteurs Minecraft

Les sélecteurs (`@s`, `@p`, `@a`, etc.) fonctionnent normalement. La source pendant l'exécution est le joueur qui a invoqué l'alias.

### Comportement en cas d'erreur

Si un step échoue, les steps suivants **continuent quand même** (comportement type command-block, prédictible). Les erreurs sont loggées avec le nom de l'alias et le step fautif.

### Pourquoi op level 4 pendant l'exécution

Sans cette élévation, l'alias `gamemode spectator` échouerait : la commande interne `/gamemode` re-vérifie `requires(2)` et le joueur n'est pas op. L'alias est conçu comme une **macro signée par l'admin** — c'est l'admin qui décide ce que l'alias contient, et le joueur reçoit juste une délégation pour ce contenu précis.

---

## Considérations de sécurité

### ⚠ Élévation des aliases

**Tout ce qu'un alias contient s'exécute avec autorité op-4.** Si vous donnez à un joueur `customperm.alias.X`, vous lui donnez le droit d'exécuter X **avec privilèges admin**.

**Conséquence** : ne JAMAIS mettre dans un alias des commandes que vous ne donneriez pas à ce joueur en op direct, par exemple :
- `op @s` → le joueur devient op pour de bon
- `whitelist remove ...`, `ban ...` → outils de modération
- `gamerule keepInventory false` → modifie l'état du serveur
- `data modify ...` → modifie n'importe quelle entité ou block
- `function <namespace>:<malicious>` → exécute des fonctions arbitraires

**Bonne pratique** : auditez périodiquement vos aliases avec `customperm alias list` puis `customperm alias steps <name>`.

### Collision d'alias avec une commande native

Si vous créez `/customperm alias add gamemode ...`, l'alias **shadow** la commande native. Le mod affiche un warning explicite à la création. Les joueurs auront besoin de `customperm.alias.gamemode` (pas `customperm.command.gamemode`) pour utiliser cette version.

### Wildcards à manier avec précaution

`customperm.command.*` couvre **toutes** les commandes exposées. Si vous exposez `/op` (déconseillé) ou `/whitelist`, le wildcard les couvre aussi. **Préférez** des nodes explicites pour les commandes sensibles.

### Audit régulier

Inspectez les fichiers `commands.json`, `aliases.json`, et (en mode interne) `grades.json` régulièrement, ou utilisez `/customperm status` et `/customperm scan` en jeu.

---

## Diagnostic et dépannage

### Le mod ne charge pas

- Vérifiez le log de boot — la ligne `[CustomPerm] Ready —` doit apparaître.
- Si LP est présent mais l'init échoue, le mod fallback sur Internal avec un log d'erreur. Vérifiez que votre version de LP est compatible.

### Une commande exposée ne marche pas pour un joueur autorisé

```
/customperm debug <pseudo> <commande>
```

Cette commande affiche un rapport ligne par ligne :
- Présence dans le dispatcher
- Présence dans la liste exposée
- Op level du joueur
- Résultat du check de permission
- Décision logique attendue
- **Décision réelle du wrapper**

Si la décision réelle ≠ décision logique → mismatch, ouvrez une issue.

### Vérifier qu'une perm est bien donnée

```
/customperm test <pseudo> <node>
```

Retourne `GRANTED` (vert) ou `DENIED` (rouge) avec le backend en clair.

### Le joueur ne voit pas la commande dans l'autocomplétion

Le tree de commandes est mis en cache côté client. Le mod re-synchronise automatiquement quand les perms changent (via l'event `UserDataRecalculateEvent` de LuckPerms ou les commandes `/customperm grade`). Si ça ne suffit pas :
- Le joueur peut se déconnecter/reconnecter pour forcer le rafraîchissement.
- L'admin peut faire `/customperm reload` puis `/reload`.

### Vérifier qu'un mod tiers est bien détecté

```
/customperm scan <nom_partiel>
```

Liste les commandes du dispatcher qui contiennent ce mot. Les commandes de mods tiers apparaissent si le mod a registré ses commandes via le `RegisterCommandsEvent` standard (cas le plus courant).

---

## Compilation depuis les sources

### Prérequis

- JDK 21
- Git

### Build

```bash
git clone https://github.com/<user>/CustomPerm.git
cd CustomPerm
./gradlew build               # Linux/Mac
.\gradlew.bat build           # Windows
```

Gradle génère l'artefact distribuable du mod pendant `build`. Les artefacts générés ne sont pas commités dans Git ; publiez l'artefact via GitHub Releases.

### Tests en environnement de dev

```bash
./gradlew runServer           # serveur de dev avec hot-reload
./gradlew runClient           # client de dev
```

### Versions ajustables

Dans `gradle.properties` :

```properties
minecraft_version=1.21.1
neo_version=21.1.221
luckperms_api_version=5.4
```

---

## Tests

Le mod est livré avec trois niveaux de validation : tests JUnit purs, GameTests NeoForge et checklist manuelle de release.

### Lancer la suite en local

```bash
./gradlew runGameTestServer
```

Cette tâche démarre un serveur Minecraft de test dédié, exécute les GameTests enregistrés, et sort avec un code égal au nombre de tests échoués (zéro = tout passe). Adapté aux pipelines CI.

Les tests Java purs se lancent avec :

```bash
./gradlew test
```

Les benchmarks de performance se lancent avec :

```bash
./gradlew jmh
```

### Couverture

| Zone | Valide |
|---|---|
| Résolution de permissions | Deny par défaut, ALLOW direct, wildcard ALLOW, wildcard global, DENY explicite, DENY-over-ALLOW entre plusieurs grades. |
| Grades internes | Création/listage/suppression de grades, assignation/désassignation joueurs, prévention des doublons, cascade lors de la suppression d'un grade. |
| Exposition de commandes | Ajout/retrait/listage de commandes exposées, changements idempotents, commandes non exposées refusées par CustomPerm. |
| Config aliases | Création, overwrite, suppression, listage, ordre des aliases, parsing par `;`, steps vides ignorés. |
| Exécution aliases | Forme du node de permission, exécution op level 4, ordre des steps, continuation après erreur, filtrage des steps vides. |
| Config manager | Lectures atomiques du snapshot, rejet de reload concurrent, rollback après JSON invalide, création et rotation des backups. |
| Compatibilité config | Fichiers manquants, fichiers `{}`, collections explicitement `null`, champs futurs inconnus, configs partielles. |
| Sélection LuckPerms | Backend interne sans LP, parsing de versions, version minimale, sélection stable du backend. |
| GameTests | Dispatcher live, gates d'exposition, ajout/retrait live d'alias, hot reload, rollback JSON corrompu, repush du command tree. |
| Performance | `PermissionResolver.resolve()` et lecture concurrente du snapshot config via JMH. |

### Intégration continue

Chaque push sur `main` et chaque pull request déclenche `.github/workflows/gametest.yml`, qui :

1. Configure JDK 21 sur Ubuntu.
2. Cache les dépendances Gradle pour accélérer les runs suivants.
3. Lance `gradlew runGameTestServer`.
4. Construit le jar distribuable avec `gradlew build`.
5. Vérifie que le jar contient `META-INF/neoforge.mods.toml` et `META-INF/MANIFEST.MF`.
6. Fait échouer le build si un test ou contrôle jar échoue.
7. Upload les logs de run en artifact en cas d'échec, pour inspection.

### Validation manuelle en dev

Les GameTests couvrent la logique des composants ; pour un test end-to-end complet LP + Internal, lancez un serveur et un client de dev dans deux terminaux :

```bash
./gradlew runServer    # terminal 1
./gradlew runClient    # terminal 2 — se connecter sur 127.0.0.1
```

Puis exécutez les recettes des [Cas d'usage courants](#cas-dusage-courants). Les commandes in-game `/customperm debug`, `/customperm test`, `/customperm status` et `/customperm scan` sont conçues pour la vérification en direct.

Pour les validations de release, ouvrez `docs/manual-test-procedure.html` dans un navigateur. La page fournit une checklist thème sombre séparée entre scénarios Internal et LuckPerms, avec export JSON ou Markdown.

---

## Comment ça marche (technique)

### Wrapping du dispatcher

Au `RegisterCommandsEvent`, le mod parcourt toutes les commandes de Brigadier et **clone** chaque node racine en un nouveau `LiteralCommandNode` dont le `requires` enchaîne :

```
1. Si la commande n'est pas exposée, conserver le requirement vanilla/moddé original
2. Si la commande est exposée et que la source est op level 2+, autoriser
3. Sinon, demander au PermissionService si la source a customperm.command.<root>
```

Les nodes clonés sont insérés dans les `Map` internes (`children`/`literals`/`arguments`) du root via reflection. Cette approche évite les pièges du JIT inlining sur les champs `final`.

### Backend pluggable

`PermissionService` est une interface avec deux implémentations :

- `LuckPermsService` : interroge LP via son API publique (`LuckPermsProvider.get()`).
- `InternalPermService` : lit les grades dans `grades.json`.

La sélection se fait au boot via `ModList.get().isLoaded("luckperms")` avec contrôle de version minimale (`5.4.150+`). Si LuckPerms est absent, CustomPerm utilise le backend interne. Si LuckPerms est présent mais incompatible, échoue à l'initialisation, ou lève plus tard pendant un check de permission, `settings.json` décide le fallback : `deny` refuse les permissions, `internal` utilise `grades.json`.

Le resolver interne applique cet ordre :

```
1. Joueur ou node null => false
2. Aucun grade assigné => false
3. Un node deniedPermissions correspondant => false
4. Un node permissions correspondant => true
5. Sinon => false
```

### Re-synchronisation

Quand une perm change via LP, l'event `UserDataRecalculateEvent` est captée et `Commands.sendCommands(player)` est appelé pour le joueur affecté. Le tree client est mis à jour sans déconnexion.

Pour les changements via `/customperm` (mode interne), `sendCommands` est appelé directement après la modification.

### Aliases

Enregistrés comme des `Commands.literal(name).requires(...).executes(...)`. Le `executes` itère les steps et appelle `server.getCommands().performPrefixedCommand(elevatedSource, step)` pour chaque, avec une `CommandSourceStack` ayant `permissionLevel = 4`.

---

## Compatibilité avec d'autres mods

### Mods qui ajoutent des commandes

**Compatible automatiquement.** Les commandes sont enregistrées au `RegisterCommandsEvent` standard ; notre handler tourne après tous les autres et wrappe tout l'arbre. Aucune intégration nécessaire.

Pour exposer une commande de mod tiers : `customperm command add <addon_command>`. Pour vérifier qu'elle est bien vue : `customperm scan <pattern>`.

### Mods qui modifient le dispatcher dynamiquement

Cas rare. Si un mod ajoute des commandes **après** le `RegisterCommandsEvent`, elles ne sont pas wrappées et gardent leur `requires` original (souvent op-only). Pour forcer un re-wrapping : `/reload` (côté serveur).

### LuckPerms

Cible privilégiée. Toute la machinerie LP standard fonctionne :
- Groupes (`/lp creategroup`)
- Hiérarchie (`/lp group <name> parent add <parent>`)
- Contextes (servers, worlds — non testé extensivement, mais l'API est respectée)
- Web editor
- Stockage SQL/MySQL/MongoDB

---

## Limitations connues

- **Pas de granularité par sous-commande** : `customperm.command.gamemode` couvre tous les sous-modes (creative, spectator, etc.). Pour scinder, utilisez les aliases.
- **Pas de paramètres dans les aliases** : un alias est une commande sans argument. Pour faire `/heal <player>`, écrivez `/heal_target` avec `effect give @p` etc., ou créez plusieurs aliases.
- **Contextes LP partiellement testés** : les contextes par-monde, par-serveur, etc. de LuckPerms passent par `getCachedData()` et sont en théorie supportés, mais non testés extensivement.
- **Pas de GUI** : toute l'administration est en commandes. Pour une interface, utilisez le web editor de LuckPerms.

---

## Licence

MIT — voir [LICENSE](LICENSE).

---

## Crédits

- [NeoForge](https://neoforged.net/) pour le framework de mods.
- [LuckPerms](https://luckperms.net/) pour l'inspiration et l'API d'intégration propre.
- Brigadier (Mojang) pour le système de commandes sous-jacent.
