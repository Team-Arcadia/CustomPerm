# CustomPerm

> Système de permissions granulaires pour Minecraft NeoForge — accordez des commandes vanilla individuelles à des joueurs non-op, avec ou sans LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.5-brightgreen.svg)]()

---

## Pourquoi ce mod

Minecraft vanilla a un système binaire : un joueur est **op** (toutes les commandes) ou **non-op** (aucune commande de gestion). Pas d'entre-deux.

CustomPerm permet de donner **précisément** les commandes que vous voulez à des joueurs non-op, sans leur accorder l'op complet. Concrètement :

- Vous voulez qu'un joueur puisse `/gamemode spectator` mais pas `/op` ? Possible.
- Donner `/give` à un grade VIP sans qu'ils puissent `/ban` ? Possible.
- Créer des macros (alias) qui chaînent plusieurs commandes en une seule ? Possible.

Le mod s'intègre nativement à **LuckPerms** s'il est installé, sinon il fournit son propre système de grades stocké en JSON. Avec LuckPerms installé, CustomPerm résout à la fois ses nodes d'aliases (`customperm.alias.*`) et ses nodes de commandes directes (`customperm.command.*`) via LuckPerms. Sans LuckPerms, les deux passent par les grades internes.

---

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Installation](#installation)
- [Mondes solo et LAN](#mondes-solo-et-lan)
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

- **Permissions granulaires sans LuckPerms** : le backend interne peut exposer une commande racine vanilla ou de mod tiers avec `/customperm command add <name>`.
- **Modèle deny-by-default** : aucune commande n'est exposée par défaut ; une commande non exposée garde son comportement vanilla ou moddé.
- **Backend JSON interne** : gestion des grades, assignations joueurs et nodes de permission sans plugin externe.
- **Backend LuckPerms** : utilisation automatique de LuckPerms lorsqu'une version compatible est installée.
- **Contrôle de version LuckPerms** : LuckPerms `5.4.150+` requis ; les versions trop anciennes ou prerelease sont refusées par sécurité.
- **Fallback LP configurable** : si LuckPerms devient indisponible au runtime, `settings.json` décide si CustomPerm refuse les permissions (`deny`, défaut) ou bascule sur le backend interne (`internal`).
- **Visibilité du backend** : logs de boot, `/customperm status`, `/customperm debug` et `/customperm test` indiquent Internal, LuckPerms, Internal fallback from LuckPerms ou le mode deny.
- **Alertes admin** : quand LuckPerms devient indisponible ou qu'un fichier de config ne se charge pas, chaque op connecté (niveau 2+) reçoit une alerte dans le chat, les ops qui se connectent ensuite la reçoivent à la connexion, et `/customperm status` la liste jusqu'à sa résolution.
- **RBAC multi-grades** : un joueur peut avoir plusieurs grades internes ; les permissions sont résolues par union des grades assignés.
- **DENY explicite** : les grades internes supportent `deniedPermissions`, et tout DENY correspondant l'emporte sur les ALLOW.
- **Wildcards de permissions** : `*`, `customperm.command.*` et `customperm.alias.*` sont supportés.
- **Aliases et macros** : créez des commandes racine personnalisées (`/fly`, `/heal`, `/starter`) qui exécutent une ou plusieurs commandes configurées.
- **Edition des steps d'alias** : ajout, suppression et inspection de steps individuels avec indices 0-based.
- **Elévation des aliases** : les steps d'alias s'exécutent avec op level 4 pour permettre aux macros signées par l'admin d'appeler des commandes op-only.
- **Garde-fous sur les aliases** : `/customperm` est réservé, les steps vides sont ignorés, les aliases sans step sont refusés, le shadow d'une commande existante émet un warning et les chaînes récursives sont arrêtées à la profondeur 8.
- **Enregistrement runtime des aliases** : ajout, remplacement ou retrait d'alias sans redémarrage ; `/customperm reload` applique aussi les ajouts, suppressions et changements de steps provenant d'`aliases.json`.
- **Politique indépendante du backend** : l'exposition directe des commandes fonctionne avec ou sans LuckPerms ; le node `customperm.command.<nom>` est résolu par LuckPerms lorsqu'il est installé (accordé via `/lp`), sinon par les grades internes.
- **Préservation des ops** : les sources réellement op level 2+ gardent toujours l'accès ; le mod ne retire pas les droits opérateur.
- **Re-sync du command tree client** : après changement interne ou event LuckPerms, les joueurs concernés reçoivent un arbre de commandes à jour.
- **Hot-reload atomique** : `/customperm reload` charge `grades.json`, `aliases.json`, `commands.json` et `settings.json` en transaction ; un JSON invalide conserve le snapshot précédent.
- **Création et normalisation automatique des configs** : fichiers manquants, `{}`, champs inconnus et collections explicitement `null` sont normalisés vers des structures vides sûres.
- **Backups automatiques** : les reloads réussis écrivent des backups horodatés et conservent les trois dernières sauvegardes par fichier.
- **Accès config concurrent sûr** : le snapshot actif utilise un `AtomicReference` ; les sauvegardes sont sérialisées et chaque fichier est remplacé via un temporaire unique.
- **Diagnostics** : `/customperm status`, `/customperm scan`, `/customperm debug` et `/customperm test` couvrent l'inspection runtime et le dépannage.
- **Checks CI release** : GitHub Actions lance les GameTests, construit le jar distribuable et vérifie les métadonnées requises du jar.
- **Côté serveur uniquement** : aucun mod n'est requis côté client pour les fonctionnalités de base. Un client vanilla (ou sans CustomPerm) se connecte sans problème à un serveur CustomPerm : les canaux réseau de l'interface d'administration sont enregistrés en `optional()`, ils ne bloquent jamais la connexion.
- **Interface d'administration en jeu** : `/customperm gui` ouvre une interface native sur les clients où CustomPerm est installé, sans autre mod client. Le serveur reste l'autorité : chaque action est revérifiée, limitée et journalisée. Elle inclut un éditeur du magasin LuckPerms quand LuckPerms fonctionne (voir [Interface d'administration en jeu](#interface-dadministration-en-jeu)).

---

## Installation

### Prérequis

- **Minecraft 1.21.1**
- **NeoForge 21.1.221** ou supérieur
- **Java 21**
- (Optionnel mais recommandé) **LuckPerms 5.4.x ou 5.5.x** pour NeoForge

### Étapes

1. Téléchargez CustomPerm sur [CurseForge](https://www.curseforge.com/minecraft/mc-mods/customperm) ou [Modrinth](https://modrinth.com/mod/customperm).
2. Déposez le jar dans le dossier `mods/` de votre serveur.
3. (Optionnel) Déposez aussi le jar de [LuckPerms](https://luckperms.net/download) (build NeoForge 1.21.1).
4. (Optionnel) Les joueurs admin qui veulent l'interface en jeu installent aussi CustomPerm sur leur client.
5. Démarrez le serveur.

> **Où trouver les builds.** Les builds publiés sont distribués sur [CurseForge](https://www.curseforge.com/minecraft/mc-mods/customperm) et [Modrinth](https://modrinth.com/mod/customperm). La version actuelle y est la **1.0.5 (bêta)** ; la prochaine release sera la **1.1.0**.
>
> **Les releases GitHub v1.0.3 et v1.0.4 ont été retirées le 2026-09-15.** L'historique du dépôt a été réécrit pour retirer des données personnelles des métadonnées de commit. Ces deux releases étaient verrouillées par GitHub et bloquaient ce nettoyage, elles ont donc dû être supprimées. C'étaient des builds de développement, jamais publiés sur CurseForge ni Modrinth, et la 1.0.5 contient tout ce qu'ils apportaient. Leur code source reste dans l'historique : commit `65ef813` pour la 1.0.3 et `ffc4624` pour la 1.0.4 (les tags, verrouillés de la même façon, ont aussi été retirés). Le contenu des fichiers est inchangé, mais tous les identifiants de commit ont changé : si vous avez cloné le dépôt avant cette date, clonez-le à nouveau.

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

### Interface d'administration en jeu

`/customperm gui [page]` ouvre l'interface d'administration. Elle demande CustomPerm sur le client de l'admin, rien d'autre ; sans lui, la commande explique que tous les réglages restent accessibles par les commandes texte, et les joueurs sans le mod se connectent normalement.

L'interface est dessinée nativement (sans bibliothèque d'interface) et remplace l'ancien panneau TesseraUI. Pages :

| Page | Contenu |
|---|---|
| Tableau de bord | Backend actif et sa signification, nombre de commandes exposées, d'alias, de limites et de grades, toutes les alertes admin actives, rechargement de la configuration (avec confirmation) |
| Commandes | Toutes les commandes racines du serveur avec recherche (Ctrl+F) et filtre « exposées », badges pour les alias, les limites et les commandes absentes du serveur ; exposer, masquer (avec confirmation), et l'interrupteur « garder l'exigence d'origine » (`preserveOriginalRequires`) |
| Alias | Tous les alias avec recherche, badges pour les commandes masquées et les limites ; créer un alias avec sa première étape ; par alias : ajouter, remplacer, monter ou descendre et retirer des étapes, supprimer l'alias (avec confirmation) |
| Limites d'exécution | Toutes les règles avec leurs valeurs et badges (désactivée, cible ni exposée ni alias) ; ajouter une limite, changer usages et fenêtre, activer ou désactiver, choisir quand l'historique est écrit (sauvegarde du monde ou à chaque usage), supprimer (avec confirmation) ; les commandes exposées et alias sans limite sont listés et remplissent le formulaire en un clic |
| LuckPerms | Uniquement quand LuckPerms est installé : pas d'entrée de navigation sinon, et `/customperm gui luckperms` explique pourquoi. Installé mais pas démarré (solo, échec au démarrage), la page affiche une bannière au lieu de l'éditeur. **Groupes** : créer, supprimer, nœuds de permission allow/deny avec contextes et durée, parents, poids, nom affiché, préfixe, suffixe, meta. **Joueurs** : joueurs connectés et tout joueur trouvé par pseudo exact, leurs nœuds, groupes avec durée, groupe principal, promotion et rétrogradation sur un track, préfixe, suffixe, meta. **Tracks** : créer, supprimer, ajouter, insérer à une position, retirer un groupe. Les écritures passent par l'API LuckPerms côté serveur, protégées par `customperm.gui.luckperms.edit` |
| Grades | Toujours accessible, pour pouvoir lire le repli quand LuckPerms fonctionne ou tombe. Une bannière indique quand les grades ne décident pas des permissions ; avec LuckPerms actif la page est en lecture seule, comme les commandes de grade : grades avec recherche et création ; par grade, nœuds ALLOW et DENY, et joueurs avec leur état en ligne, attribués par pseudo avec complétion, y compris hors ligne s'ils sont déjà venus sur le serveur ; suppression d'un grade (avec confirmation) |

**Permissions.** Lire une page demande op level 2, le même contrôle que `/customperm`. Écrire demande en plus le nœud du domaine : `customperm.gui.commands.edit`, `customperm.gui.aliases.edit`, `customperm.gui.ratelimits.edit`, `customperm.gui.grades.edit`, `customperm.gui.luckperms.edit`. Ces nœuds sont vérifiés comme accordés au joueur, sans le court-circuit opérateur habituel du backend interne, pour pouvoir déléguer un domaine à un modérateur de niveau 2 sans ouvrir les autres. Le niveau de permission 4 (propriétaire du serveur) les contourne. Les actions qui ne modifient pas la configuration, comme le rechargement, demandent seulement op level 2, comme leur commande. Chaque action appliquée est journalisée côté serveur avec le nom de l'admin.

**Compatibilité.** L'interface utilise le protocole réseau 2. Un client avec un CustomPerm plus ancien se connecte toujours à un serveur 1.1.0 mais n'y a pas d'interface, et inversement.

---

## Mondes solo et LAN

CustomPerm est conçu pour les serveurs, mais fonctionne également en monde solo — le serveur intégré exécute exactement le même code. Deux comportements diffèrent suffisamment pour être connus avant d'essayer.

### Les commandes de triche doivent être activées

`/customperm` est protégée par un contrôle op niveau 2 réel. En monde solo, vous ne disposez d'un niveau de permission que si **Autoriser les commandes de triche** est actif (`Créer un nouveau monde → Plus → Autoriser les commandes de triche`, ou ouverture en LAN avec la triche activée). Sans cela, la commande est masquée de la tab-complétion et inexécutable — ce n'est pas un bug, c'est la même protection que sur un serveur.

Ce contrôle inspecte volontairement votre niveau OP *réel* et non celui de la source de commande courante, afin qu'un alias ne puisse jamais servir à faire passer une sous-commande `/customperm` en fraude.

### La configuration est par installation, pas par monde

C'est le point qui surprend. La configuration se trouve dans le répertoire d'installation de Minecraft :

```
.minecraft/config/arcadia/customperm/
```

Elle n'est **pas** stockée dans la sauvegarde du monde. Chaque grade, alias, commande exposée et limite d'exécution que vous créez est donc partagé par **tous** vos mondes solo. Créez un alias `/heal` en bricolant dans un monde créatif, et il existera aussi dans votre monde survie.

Sur un serveur dédié, cela reste invisible — une installation, un monde. En solo, si vous voulez des configurations différentes par monde, il faut pour l'instant permuter vous-même le dossier de configuration.

### Ce qui est réellement utile hors ligne

- **Les alias et macros** — la principale raison d'utiliser CustomPerm en solo. Enchaînez plusieurs commandes derrière une seule, exécutées en op niveau 4.
- **L'interface d'administration** — `/customperm gui` fonctionne en solo comme ailleurs.
- **Les grades et nodes de permission** — de peu d'intérêt tant que vous êtes seul et déjà opérateur. Ils prennent tout leur sens dès que vous **ouvrez le monde en LAN** : les invités rejoignent en non-op, et les grades permettent de leur accorder exactement les commandes voulues.
- **Les limites d'exécution** — attention, elles s'appliquent aussi à vous. Une règle définie dans un monde s'applique dans tous, conformément au point ci-dessus ; les compteurs d'utilisation, eux, sont stockés dans la sauvegarde de chaque monde.

---

## Démarrage rapide

### Avec LuckPerms

```
# Console serveur
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
lp user Steve parent add vip
```

`Steve` peut maintenant utiliser `/spec` sans recevoir l'accès direct à `/gamemode`.

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

Cette fonctionnalité fonctionne avec **l'un ou l'autre backend**. Le node `customperm.command.<nom>` est résolu par le backend actif : via **LuckPerms** lorsqu'il est installé (à accorder avec `/lp`), sinon via les grades internes. La commande d'exposition est la même dans les deux cas.

Définit quelles commandes sont éligibles au système de permissions. Une commande non-exposée garde son comportement vanilla (op-only).

| Commande | Effet |
|---|---|
| `/customperm command add <name>` | Expose la commande `<name>` au système. |
| `/customperm command remove <name>` | Retire la commande, retour au comportement vanilla. |
| `/customperm command preserve <name> <true\|false>` | Pour une commande exposée : `true` exige le nœud ET l'exigence d'origine de la commande, `false` (défaut) le nœud seul. Équivaut à `preserveOriginalRequires` dans `commands.json`. |
| `/customperm command list` | Liste les commandes exposées. |

### Aliases (macros)

Crée des commandes personnalisées qui exécutent une ou plusieurs commandes. Les steps s'exécutent avec **op level 4** — voir [Considérations de sécurité](#considérations-de-sécurité).

| Commande | Effet |
|---|---|
| `/customperm alias add <name> <cmd1; cmd2; ...>` | Crée un alias. Les commandes sont séparées par `;`. |
| `/customperm alias addstep <name> <cmd>` | Ajoute un step à un alias existant (ou en crée un). |
| `/customperm alias removestep <name> <index>` | Retire le step d'index donné (0-based). |
| `/customperm alias movestep <name> <from> <to>` | Déplace un step à une autre position (0-based). |
| `/customperm alias setstep <name> <index> <command>` | Remplace le step d'index donné (0-based). |
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
| `/customperm grade adddeny <grade> <node>` | Ajoute un nœud DENY : refusé même si un autre grade du joueur l'autorise. |
| `/customperm grade removedeny <grade> <node>` | Retire un nœud DENY. |
| `/customperm grade assign <player> <grade>` | Assigne le grade à un joueur, en ligne ou hors ligne s'il est déjà venu sur le serveur. |
| `/customperm grade unassign <player> <grade>` | Désassigne, en ligne ou hors ligne. |
| `/customperm grade list` | Liste les grades définis. |

### Limites d'exécution

Plafonne le nombre d'utilisations d'une commande ou d'un alias par joueur sur une fenêtre glissante. Les limites s'appliquent à tous les joueurs, ops compris ; la console et les blocs de commande ne sont jamais limités.

| Commande | Effet |
|---|---|
| `/customperm ratelimit set <name> <max> <windowSeconds>` | Autorise `<max>` utilisations par joueur toutes les `<windowSeconds>`. Redéfinir une règle conserve son mode de persistance. |
| `/customperm ratelimit persistence <name> <world_save\|immediate>` | Choisit quand l'historique d'utilisation de cette commande est écrit sur le disque (voir `ratelimits.json`). |
| `/customperm ratelimit disable <name>` / `enable <name>` | Suspend ou reprend l'application d'une règle sans perdre ses valeurs. |
| `/customperm ratelimit remove <name>` | Supprime la règle. |
| `/customperm ratelimit list` | Liste les règles avec leur état et leur mode de persistance. |

### Diagnostic et utilitaires

| Commande | Effet |
|---|---|
| `/customperm test <player> <node>` | Vérifie si un joueur a un node de permission donné. Retourne `GRANTED` ou `DENIED`. |
| `/customperm debug <player> <command>` | Rapport détaillé : commande dans le dispatcher ? exposée ? l'op-level passe ? la perm est granted ? le wrapper renvoie quoi ? |
| `/customperm status` | Snapshot global : backend, nb de commandes wrappées, exposées, aliases, grades, alertes admin actives. |
| `/customperm scan [pattern]` | Liste toutes les commandes du dispatcher avec leur état (exposée, alias, mod-interne). Filtre optionnel. |
| `/customperm reload` | Recharge les fichiers de config depuis le disque. |
| `/customperm gui [dashboard\|commands\|aliases\|ratelimits\|grades]` | Ouvre l'interface d'administration en jeu (demande CustomPerm côté client). La lecture demande op level 2, l'écriture le nœud `customperm.gui.<domaine>.edit` du domaine. |
| `/customperm gui luckperms [groups\|players\|tracks]` | Ouvre l'éditeur LuckPerms en jeu. Uniquement quand LuckPerms est installé ; s'il ne fonctionne pas, la page explique pourquoi. L'écriture demande `customperm.gui.luckperms.edit`. |

---

## Nodes de permission

CustomPerm utilise un schéma de nodes hiérarchique compatible LuckPerms (et son système interne).

| Node | Effet |
|---|---|
| `*` | Wildcard global du backend interne. À utiliser avec précaution. |
| `customperm.command.<name>` | Autorise `<name>` (effectif seulement une fois la commande exposée). Résolu par LuckPerms s'il est installé, sinon par les grades internes. |
| `customperm.command.*` | Wildcard couvrant toutes les commandes exposées. Avec LuckPerms, c'est son propre moteur de wildcard qui le résout. |
| `customperm.alias.<name>` | Autorise l'alias `<name>`. Ex: `customperm.alias.fly` |
| `customperm.alias.*` | Wildcard alias. |

> ℹ️ Avec LuckPerms actif, accordez `customperm.command.<name>` (commandes exposées) et `customperm.alias.<name>` (aliases) via `/lp`. Les sous-commandes de grade (`/customperm grade ...`) restent désactivées sous LuckPerms — l'appartenance utilisateur/groupe se gère avec `/lp`.

---

## Fichiers de configuration

Stockés dans `config/arcadia/customperm/`. Auto-créés au premier lancement, modifiables à chaud (utilisez `/customperm reload` pour appliquer). Si un ancien dossier `config/customperm/` existe et que le nouveau dossier n'existe pas encore, CustomPerm copie les fichiers de configuration connus vers le nouvel emplacement sans supprimer les anciens fichiers.

### `commands.json`

Liste des commandes exposées au système.

Ce fichier liste les commandes exposées quel que soit le backend. Le node `customperm.command.<nom>` qui autorise chacune est résolu par LuckPerms lorsqu'il est installé (à accorder via `/lp`), sinon par les grades internes.

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

### `ratelimits.json`

Règles de limite d'exécution, indexées par nom de commande ou d'alias.

```json
{
  "rules": {
    "gamemode": { "enabled": true, "maxExecutions": 3, "windowSeconds": 60, "persistence": "world_save" },
    "heal": { "enabled": true, "maxExecutions": 1, "windowSeconds": 3600, "persistence": "immediate" }
  }
}
```

L'historique d'utilisation est conservé entre les redémarrages et les `/reload` vanilla. C'est un état du serveur, pas un réglage : il vit dans la sauvegarde du monde, dans `<monde>/data/customperm_ratelimits.json` (timestamps Unix en millisecondes), et non dans ce dossier. `persistence` décide quand il est écrit :

- `world_save` (défaut) : avec le monde, à la sauvegarde automatique, à `/save-all` et à l'arrêt du serveur. Aucun coût par commande ; un crash du serveur perd au plus les utilisations depuis la dernière sauvegarde.
- `immediate` : juste après chaque utilisation acceptée de cette commande. Rien n'est perdu en cas de crash, au prix d'une écriture disque par utilisation. À réserver aux commandes rares et sensibles.

Au chargement, l'historique plus ancien que la fenêtre actuelle de la règle est ignoré : une fenêtre raccourcie pendant l'arrêt du serveur s'applique immédiatement. Si l'horloge système recule, les utilisations enregistrées « dans le futur » comptent à partir de maintenant pour une fenêtre au lieu de bloquer les joueurs. Un fichier d'historique illisible est renommé `customperm_ratelimits.json.corrupt-<date>` et les compteurs repartent de zéro.

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

**Avec LuckPerms** — deux options :

*Exposition directe* (toute la commande `/gamemode`) :
```
customperm command add gamemode
lp group vip permission set customperm.command.gamemode true
```

*Alias contrôlé* (spectator uniquement, plus sûr) :
```
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
```

Dans les deux cas LuckPerms fournit l'assignation de permission ; c'est le wrapper de commande de CustomPerm qui laisse effectivement passer le node à travers le `requires` de Brigadier (LuckPerms seul ne le contourne pas sur NeoForge). Préférez l'alias quand vous voulez une granularité par sous-commande (spectator mais pas creative).

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

Sans LuckPerms :
```
customperm command add gamemode
customperm command add give
customperm command add tp
customperm command add effect
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

Les aliases récursifs sont bornés. Un cycle direct ou indirect est interrompu lorsque l'exécution imbriquée atteint la profondeur 8, au lieu de provoquer un dépassement de pile sur le thread serveur.

### Recharger les modifications de fichier

Après modification d'`aliases.json`, exécutez `/customperm reload`. Les nouveaux aliases sont enregistrés, les aliases retirés sont supprimés (avec restauration d'une éventuelle commande shadowée) et les listes de steps modifiées remplacent les anciennes.

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
- Si LP est présent mais l'init échoue, `settings.json` décide le comportement : `deny` refuse les permissions par défaut, `internal` bascule sur `grades.json`. Vérifiez que votre version de LP est compatible.

### Un `[CustomPerm] ALERT` rouge apparaît dans le chat

Les ops (niveau de permission 2+) le reçoivent quand une action est nécessaire, une fois au moment où ça arrive puis à chaque connexion tant que ça dure. `/customperm status` liste les alertes actives.

- **LuckPerms is unavailable** : CustomPerm n'utilise plus LuckPerms jusqu'au prochain redémarrage et suit `luckPermsFallbackMode` (grades `internal` ou `deny`). Cherchez l'erreur LuckPerms dans le log du serveur, corrigez, redémarrez.
- **Configuration failed to load** : l'alerte nomme le fichier invalide. Les changements faits en jeu restent en mémoire mais ne sont pas écrits sur le disque, pour ne pas écraser le fichier cassé. Corrigez le fichier puis lancez `/customperm reload` : l'alerte est remplacée par un message vert "Resolved" et la sauvegarde reprend.

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

Gradle génère l'artefact distribuable du mod pendant `build`. Les artefacts générés ne sont pas commités dans Git ; les builds sont publiés sur CurseForge et Modrinth.

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
./gradlew runGameTestServer             # backend interne, sans LuckPerms
./gradlew runGameTestServerLuckPerms    # backend LuckPerms
```

Chaque tâche démarre un serveur Minecraft de test dans son propre dossier (`run/gametest/`, `run/gametest-luckperms/`), exécute les GameTests enregistrés, et sort avec un code égal au nombre de tests échoués (zéro = tout passe). Le mode interne tourne toujours sans LuckPerms, quel que soit le contenu de `run/mods/` ; le mode LuckPerms récupère LuckPerms NeoForge 5.4.150 via CurseMaven. Les tests propres à un backend s'ignorent dans l'autre mode, et un test de garde échoue si un mode ne tourne pas avec le backend annoncé.

Les GameTests utilisent de vrais joueurs connectés (`TestPlayer`) : un joueur côté serveur avec un niveau de permission choisi, dont le chat reçu, les arbres de commandes et les paquets CustomPerm sont enregistrés. Permissions, aliases, limites de débit et paquets du GUI sont ainsi vérifiés de bout en bout, sans client.

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
| Exécution aliases | Forme du node, exécution op level 4, ordre, continuation après erreur, limite des cycles récursifs, remplacement live des steps. |
| Config manager | Lectures atomiques du snapshot, sauvegardes atomiques sérialisées, rejet de reload concurrent, rollback après JSON invalide, création et rotation des backups. |
| Compatibilité config | Fichiers manquants, fichiers `{}`, collections explicitement `null`, champs futurs inconnus, configs partielles. |
| Sélection LuckPerms | Backend interne sans LP, parsing de versions, version minimale, sélection stable du backend. |
| GameTests, deux modes | Exposition et retrait de commande avec un joueur non-op, préservation des ops, `/customperm` refusé aux non-ops, reconnexion, aliases exécutés en op 4 par les seuls détenteurs du node et incapables d'atteindre `/customperm`, édition des steps, gardes de récursion et de shadowing, reload d'un `aliases.json` modifié à la main, limites de débit (message de refus, compteur partagé par racine, isolation par joueur, console exemptée, expiration de fenêtre, reconnexion, reloads répétés, suppression de règle, aliases), reload tout-ou-rien, refus du reload concurrent, changements non sauvegardés après un reload en échec, entrées `null`, repush du command tree au reload, alertes admin dans le chat des ops, paquets du GUI et de l'éditeur refusés aux non-ops, sorties de diagnostic, autocomplétion de chaque argument de `/customperm` et aucune suggestion pour un non-op. |
| GameTests, mode interne | Commandes de grade, union des grades, DENY sur un ancêtre prioritaire sur ALLOW, toutes les formes de wildcard, éditeur sans LuckPerms. |
| GameTests, mode LuckPerms | Éditeur en jeu face à un vrai LuckPerms : groupes, nodes avec contextes et expiration, héritage, meta, prefix et suffix, poids, nom d'affichage, groupes et groupe principal d'un joueur, tracks, promote et demote, verrouillage des écritures par node et niveau, limites d'édition et de sync ; command tree renvoyé après un changement LuckPerms ; repli `deny` et `internal` quand LuckPerms devient indisponible. |
| Performance | `PermissionResolver.resolve()` et lecture concurrente du snapshot config via JMH. |

### Intégration continue

Chaque push sur `main` ou `dev` et chaque pull request qui les cible déclenche `.github/workflows/gametest.yml`, qui :

1. Configure JDK 21 sur Ubuntu.
2. Cache les dépendances Gradle pour accélérer les runs suivants.
3. Lance `gradlew runGameTestServer`, puis `gradlew runGameTestServerLuckPerms`.
4. Construit le jar distribuable avec `gradlew build`.
5. Vérifie que le jar contient `META-INF/neoforge.mods.toml` et `META-INF/MANIFEST.MF`.
6. Fait échouer le build si un test ou contrôle jar échoue.
7. Upload les logs de run en artifact en cas d'échec, pour inspection.

### Validation manuelle en dev

Les GameTests couvrent le comportement côté serveur ; pour ce qui demande encore un vrai client (rendu du GUI, connexion d'un client vanilla, modpack réel), lancez un serveur et un client de dev dans deux terminaux :

```bash
./gradlew runServer    # terminal 1
./gradlew runClient    # terminal 2 — se connecter sur 127.0.0.1
```

Puis exécutez les recettes des [Cas d'usage courants](#cas-dusage-courants). Les commandes in-game `/customperm debug`, `/customperm test`, `/customperm status` et `/customperm scan` sont conçues pour la vérification en direct.

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

Enregistrés comme des `Commands.literal(name).requires(...).executes(...)`. Le `executes` normalise chaque step, retire le `/` initial éventuel, puis exécute la step via le node de commande original lorsque CustomPerm a wrappé cette commande, avec une `CommandSourceStack` ayant `permissionLevel = 4`. Les steps en échec sont signalées et journalisées, mais les steps suivantes continuent de s'exécuter.

---

## Compatibilité avec d'autres mods

### Mods qui ajoutent des commandes

**Compatible automatiquement en mode interne.** Les commandes enregistrées au `RegisterCommandsEvent` standard sont traitées en priorité `LOWEST`, puis une passe de réparation est exécutée au démarrage du serveur. Aucune intégration dédiée n'est normalement nécessaire.

Exposez une commande de mod tiers avec `customperm command add <addon_command>`, puis accordez `customperm.command.<addon_command>` — via `/lp` si LuckPerms est installé, sinon via un grade interne. Un alias CustomPerm limité reste une option quand vous voulez une portée plus fine (par sous-commande). Pour vérifier la détection : `customperm scan <pattern>`.

### Mods qui modifient le dispatcher dynamiquement

Cas rare. Si un mod ajoute des commandes **après** le `RegisterCommandsEvent`, elles ne sont pas wrappées et gardent leur `requires` original (souvent op-only). Pour forcer un re-wrapping : `/reload` (côté serveur).

### LuckPerms

Cible privilégiée. Toute la machinerie LP standard fonctionne :
- Groupes (`/lp creategroup`)
- Hiérarchie (`/lp group <name> parent add <parent>`)
- Contextes (servers, worlds — non testé extensivement, mais l'API est respectée)
- Web editor
- Stockage SQL/MySQL/MongoDB

LuckPerms stocke et résout à la fois les nodes `customperm.command.*` et `customperm.alias.*`. LuckPerms ne contourne pas à lui seul les requirements Brigadier vanilla sur NeoForge — c'est le wrapper de commande de CustomPerm qui consulte le node et laisse passer la source. Donc `customperm.command.gamemode` accordé dans `/lp` **déverrouille bien** `/gamemode` une fois la commande exposée via `customperm command add gamemode` ; un alias contrôlé n'est nécessaire que pour une granularité par sous-commande (spectator mais pas creative).

---

## Limitations connues

- **Pas de granularité par sous-commande** : `customperm.command.gamemode` couvre tous les sous-modes (creative, spectator, etc.). Pour scinder, utilisez les aliases.
- **Pas de paramètres dans les aliases** : un alias est une commande sans argument. Pour faire `/heal <player>`, écrivez `/heal_target` avec `effect give @p` etc., ou créez plusieurs aliases.
- **Contextes LP partiellement testés** : les contextes par-monde, par-serveur, etc. de LuckPerms passent par `getCachedData()` et sont en théorie supportés, mais non testés extensivement.
- **L'interface d'administration demande CustomPerm côté client** : sans lui, l'administration reste entièrement en commandes.
- **L'éditeur LuckPerms en jeu n'est pas le web editor** : il couvre groupes, joueurs, tracks, nœuds, meta et chat meta, mais pas les opérations en masse, la recherche de nœud sur tous les détenteurs, ni l'historique d'annulation du web editor. Pour cela, `/lp editor` reste l'outil.
- **Les commandes raccourcis ont leurs propres règles** : certaines commandes sont des raccourcis qui redirigent vers une autre (`/tp` vers `/teleport`, `/msg` et `/w` vers `/tell`, `/xp` vers `/experience`). Chaque écriture est exposée et limitée sous le nom tapé par le joueur : `tp` gouverne `/tp`, `teleport` gouverne `/teleport`. Exposer l'une n'ouvre pas l'autre ; configurez les deux si les deux doivent être disponibles.
- **L'assignation de grade exige un joueur connu du serveur** : `/customperm grade assign|unassign` et l'interface acceptent les joueurs en ligne ou déjà venus sur le serveur ; ils n'interrogent jamais le service de session, donc un pseudo jamais venu ne peut pas être assigné à l'avance. Dans ce cas, éditez `userGrades` dans `grades.json` (UUID en clé) puis `/customperm reload`.
- **Les nœuds DENY se gèrent uniquement dans le fichier** : `deniedPermissions` est pris en compte par le résolveur mais n'a pas encore de sous-commande `/customperm grade` ; éditez `grades.json` puis rechargez.

---

## Licence

Copyright (C) 2026 THEFricadelle. Tous droits réservés.

CustomPerm est un **logiciel propriétaire à source visible** — le code source
est public à titre de référence et d'interopérabilité, mais il n'est **pas**
open-source. Vous pouvez télécharger les builds officiels et exécuter le mod sur
votre/vos serveur(s) ; vous ne pouvez **pas** le redistribuer, le ré-uploader,
le repackager, le vendre ni en créer des œuvres dérivées sans autorisation
écrite préalable.

**Les modpacks sont autorisés** dans les modpacks CurseForge / Modrinth qui
référencent le **fichier officiel non modifié** depuis la plateforme. Empaqueter
le jar dans un pack exporté/hors-ligne, le ré-héberger ailleurs ou livrer une
version modifiée nécessite une autorisation écrite.

**Les opérateurs de serveur peuvent transmettre le mod à leurs propres
joueurs.** CustomPerm comportant des composants côté client, la synchronisation
automatique des mods vers les joueurs rejoignant *votre* serveur est
explicitement autorisée, tant que le fichier officiel est transmis non modifié.
Le proposer en téléchargement général ou en « installation en un clic » dans le
catalogue d'un hébergeur ne l'est pas.

**Les contributions sont les bienvenues.** Vous pouvez forker le dépôt pour
soumettre une pull request — cet usage précis est explicitement autorisé.
Publier un build issu de votre fork, le rebrander ou réutiliser le code
ailleurs ne l'est pas.

Le nom, le mod id et le logo ne peuvent pas être utilisés pour un autre projet
ni pour suggérer une caution, et le code ne peut pas servir à entraîner des
modèles d'IA.

**Les versions antérieures conservent leurs propres conditions.** La licence
propriétaire a été adoptée le 2026-07-14 et s'applique aux builds publiés à
partir de la prochaine version. Chaque version jusqu'à la **1.0.5** incluse a
été distribuée sous la licence livrée avec elle (MIT ou `GPL-3.0-only` selon la
version) et reste disponible à ces conditions. Le tableau par version se trouve
dans [NOTICE.md](NOTICE.md#historique-des-licences-par-version).

Voir [LICENSE](LICENSE) pour les conditions complètes, [NOTICE.md](NOTICE.md)
pour un résumé en langage clair de ce qui est autorisé ou non, et
[CONTRIBUTING.md](CONTRIBUTING.md) avant d'ouvrir une pull request.

---

## Crédits

Auteur et mainteneur : **THEFricadelle**.

Merci à toutes les personnes ayant contribué du code, des correctifs ou de la
documentation — elles sont listées dans [CONTRIBUTORS.md](CONTRIBUTORS.md).

Construit sur :

- [NeoForge](https://neoforged.net/) pour le framework de mods.
- [LuckPerms](https://luckperms.net/) pour l'inspiration et l'API d'intégration propre.
- Brigadier (Mojang) pour le système de commandes sous-jacent.
