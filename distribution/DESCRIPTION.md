# CustomPerm

**Granular server-side command permissions for Minecraft NeoForge. Grant individual vanilla or modded commands to players who are not operators, with or without LuckPerms.**

> **1.1.0 is published on the Beta channel** while final testing wraps up. It is feature-complete and safe to try on a test server; feedback is welcome. Upgrading from 1.0.x? Read the [migration guide](https://github.com/Team-Arcadia/CustomPerm/blob/main/MIGRATION.md) first.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg) ![NeoForge 21.1.221+](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg) ![Java 21](https://img.shields.io/badge/Java-21-red.svg) ![License All Rights Reserved](https://img.shields.io/badge/license-source--available%20(All%20Rights%20Reserved)-blue.svg)

---

## Why CustomPerm?

Vanilla Minecraft only knows **op** (every command) or **non-op** (no management command). There is no middle ground.

CustomPerm lets you hand out **exactly** the commands you choose, without making anyone an operator:

- Let a player use `/gamemode spectator` but never `/op`.
- Give `/give` to a VIP rank without unlocking `/ban`.
- Chain several commands into one custom command, with arguments (`/heal <player>`).
- Keep an operator made by mistake away from the commands that matter.

It works with **LuckPerms** when installed, and otherwise brings its own grade system, so it runs on any server, alone or across several.

---

## Features

**Permissions**

- **Command exposure** - expose any vanilla or modded root command with `/customperm command add <name>`, then grant `customperm.command.<name>` per rank. Nothing is exposed by default; a command you do not expose keeps its original requirement.
- **Works with and without LuckPerms** - the node is resolved by LuckPerms when it is installed (`/lp`, wildcards included), and by the internal grades otherwise.
- **Internal grades built like LuckPerms groups** - allowed and denied nodes, wildcards, the most specific entry wins, inheritance, refused grades, weights, nodes carried by one player, and a default grade for everyone.
- **Temporary and per-world entries** - a node or a grade can last a set time (`30d`) and apply in one world only (`world=the_nether`), with game mode and static contexts too.
- **Tracks** - ladders of grades, with `/customperm track promote|demote`.
- **Permissions of other mods** - nodes that other mods declare through NeoForge's permission API are answered from the grades. `/customperm modcheck` lists the mods that only speak to LuckPerms.
- **Restrictable operators** - an explicit DENY applies to operators too, and `gateAllCommands` extends the check to every command. The console is never restricted.

**Aliases and limits**

- **Aliases and macros** - build `/fly`, `/heal`, `/starter` from one or more steps, run with op level 4 so an admin-written macro can call op-only commands. Aliases take typed arguments (`player`, `integer`, `word`, `text`) with defaults. Recursion is bounded, shadowing is warned, and `/customperm` is reserved.
- **Rate limits** - cap how often a command or alias can be used in a sliding window. The rule sets the default; a server, a grade or a player can have its own value (`10/1h`, `unlimited`). Usage history survives restarts.

**Players' names**

- **Chat prefixes and suffixes** - per grade or per player, with priorities, stacking and durations. The name is decorated, never the message, so chat stays signed and reportable.
- **Nicknames** - set by an admin, or by players granted `customperm.nick`.

**Administration**

- **Native in-game interface** - `/customperm gui` opens an admin interface drawn with Minecraft's own widgets: dashboard, commands, aliases, rate limits, grades, players, logs, import and export, and a help page. Every field completes what exists. It needs CustomPerm on the admin's client and nothing else; players without it join normally.
- **Administration behind a permission** - op level 2 **and** a granted `customperm.admin` node open it; each area (`customperm.manage.grades`, `.commands`, ...) needs its own node, so a moderator can be given one area only.
- **Activity log** - who changed what, when and from where, with an optional record of the commands players type.
- **LuckPerms import, export and editor** - bring groups and players over from LuckPerms, write grades into LuckPerms, choosing what goes, or edit LuckPerms from the interface.
- **Cluster mode** - several servers without LuckPerms share their grades, commands, aliases, rate limits and activity log through one MariaDB or MySQL database. A change on one applies on all within about two seconds. Each element can be limited to some servers, and a grade or a player can be allowed or refused on one server only. Off by default.
- **Safe configuration** - atomic hot-reload, invalid JSON keeps the previous configuration, automatic backups, and admin alerts when something needs action.
- **Diagnostics** - `/customperm status`, `scan`, `debug`, `test`.

---

## Installation

1. Install **NeoForge 21.1.221+** for **Minecraft 1.21.1** (Java 21).
2. Drop `customperm-1.1.0.jar` into your server's `mods/` folder.
3. *(Optional)* Add **[LuckPerms](https://luckperms.net/download)** (NeoForge 1.21.1 build).
4. *(Optional)* Admins who want the in-game interface install CustomPerm on their client too.
5. Start the server, then grant yourself administration from the console:

```
customperm grade create admins
customperm grade addperm admins customperm.*
customperm grade assign <your name> admins
```

With LuckPerms: `lp user <your name> permission set customperm.* true`.

**Cluster mode** needs a mod that provides a MariaDB or MySQL driver on every server: [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib) 1.3.0 or later does, and is also the default way to connect. CustomPerm ships no driver itself. Arcadia Lib must be installed on the players' clients as well; CustomPerm alone never requires it.

---

## Quick start

**With LuckPerms**
```
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
lp user Steve parent add vip
```
Steve can now use `/spec` without ever getting `/gamemode`.

**Without LuckPerms (internal grades)**
```
customperm command add gamemode
customperm grade create vip
customperm grade addperm vip customperm.command.gamemode
customperm grade assign Steve vip
```

---

## Singleplayer and LAN

CustomPerm also works in a singleplayer world, since the integrated server runs the same code. Two caveats:

- **Cheats must be enabled.** `/customperm` needs a real op level 2. The host of the world stands in for the console and needs no node; LAN guests need op level 2 and the nodes, granted by the host.
- **Configuration is per installation, not per world.** It lives in `.minecraft/config/arcadia/customperm/`, so grades, aliases, exposed commands and rate limits are shared by **all** your singleplayer worlds.

Offline, aliases and the interface are the main draw. Grades come into their own when you **open the world to LAN**: guests join as non-ops, and grades grant them exactly the commands you want.

---

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ |
| Java | 21 |
| LuckPerms | 5.4.150+ (optional) |
| Arcadia Lib | 1.3.0+ (cluster mode only, server and clients) |

**License:** Proprietary, All Rights Reserved (source-available; no redistribution without permission) · **Author:** THEFricadelle

**Modpacks:** ✅ Allowed in CurseForge / Modrinth modpacks that fetch the **unmodified official file** from the platform. ❌ Bundling the jar into an exported/offline pack, re-hosting it elsewhere, or including a modified build requires written permission.

---
---

# CustomPerm (Version Française)

**Permissions de commandes côté serveur pour Minecraft NeoForge. Accordez des commandes vanilla ou moddées précises à des joueurs qui ne sont pas opérateurs, avec ou sans LuckPerms.**

> **La 1.1.0 est publiée sur le canal Bêta** le temps de finaliser les tests. Elle est complète et sûre à essayer sur un serveur de test ; vos retours sont les bienvenus. Vous venez de la 1.0.x ? Lisez d'abord le [guide de migration](https://github.com/Team-Arcadia/CustomPerm/blob/main/MIGRATION.md).

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg) ![NeoForge 21.1.221+](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg) ![Java 21](https://img.shields.io/badge/Java-21-red.svg) ![License All Rights Reserved](https://img.shields.io/badge/license-source--available%20(All%20Rights%20Reserved)-blue.svg)

---

## Pourquoi CustomPerm ?

Minecraft vanilla ne connaît que **op** (toutes les commandes) ou **non-op** (aucune commande de gestion). Aucun intermédiaire.

CustomPerm vous laisse distribuer **exactement** les commandes de votre choix, sans rendre personne opérateur :

- Autoriser `/gamemode spectator` mais jamais `/op`.
- Donner `/give` à un rang VIP sans débloquer `/ban`.
- Enchaîner plusieurs commandes en une seule, avec des arguments (`/heal <joueur>`).
- Tenir un opérateur nommé par erreur à l'écart des commandes sensibles.

Il fonctionne avec **LuckPerms** s'il est installé, et apporte sinon son propre système de grades : il tourne sur n'importe quel serveur, seul ou sur plusieurs.

---

## Fonctionnalités

**Permissions**

- **Exposition de commandes** : exposez n'importe quelle commande racine vanilla ou moddée avec `/customperm command add <nom>`, puis accordez `customperm.command.<nom>` par rang. Rien n'est exposé par défaut ; une commande non exposée garde son exigence d'origine.
- **Avec et sans LuckPerms** : le nœud est résolu par LuckPerms s'il est installé (`/lp`, wildcards inclus), et par les grades internes sinon.
- **Grades internes pensés comme les groupes LuckPerms** : nœuds autorisés et refusés, wildcards, l'entrée la plus précise l'emporte, héritage, grades refusés, poids, nœuds portés par un seul joueur, et un grade par défaut pour tous.
- **Entrées temporaires et par monde** : un nœud ou un grade peut durer un temps donné (`30d`) et ne s'appliquer que dans un monde (`world=the_nether`), avec aussi le mode de jeu et des contextes statiques.
- **Tracks** : des échelles de grades, avec `/customperm track promote|demote`.
- **Permissions des autres mods** : les nœuds que les autres mods déclarent via l'API de permissions de NeoForge sont résolus par les grades. `/customperm modcheck` liste les mods qui ne parlent qu'à LuckPerms.
- **Opérateurs restreignables** : un DENY explicite s'applique aussi aux opérateurs, et `gateAllCommands` étend le contrôle à toutes les commandes. La console n'est jamais restreinte.

**Alias et limites**

- **Alias et macros** : créez `/fly`, `/heal`, `/starter` à partir d'une ou plusieurs étapes, exécutées au niveau op 4 pour qu'une macro écrite par un admin puisse appeler des commandes réservées aux op. Les alias prennent des arguments typés (`player`, `integer`, `word`, `text`) avec valeurs par défaut. La récursion est bornée, le masquage est signalé, et `/customperm` est réservé.
- **Limites d'usage** : plafonnez l'utilisation d'une commande ou d'un alias sur une fenêtre glissante. La règle donne la valeur par défaut ; un serveur, un grade ou un joueur peut avoir la sienne (`10/1h`, `unlimited`). L'historique survit aux redémarrages.

**Noms des joueurs**

- **Préfixes et suffixes de chat** : par grade ou par joueur, avec priorités, empilement et durées. Le nom est décoré, jamais le message : le chat reste signé et signalable.
- **Surnoms** : définis par un admin, ou par les joueurs ayant `customperm.nick`.

**Administration**

- **Interface native en jeu** : `/customperm gui` ouvre une interface d'administration dessinée avec les widgets de Minecraft : tableau de bord, commandes, alias, limites, grades, joueurs, journaux, import et export, et une page d'aide. Chaque champ complète ce qui existe. Il faut CustomPerm sur le client de l'admin et rien d'autre ; les joueurs qui ne l'ont pas se connectent normalement.
- **Administration derrière une permission** : il faut le niveau op 2 **et** le nœud `customperm.admin` accordé ; chaque domaine (`customperm.manage.grades`, `.commands`, ...) a son propre nœud, pour confier un seul domaine à un modérateur.
- **Journal d'activité** : qui a changé quoi, quand et d'où, avec en option l'enregistrement des commandes tapées par les joueurs.
- **Import, export et éditeur LuckPerms** : reprenez les groupes et joueurs de LuckPerms, écrivez les grades dans LuckPerms en choisissant ce qui part, ou éditez LuckPerms depuis l'interface.
- **Mode cluster** : plusieurs serveurs sans LuckPerms partagent leurs grades, commandes, alias, limites et journal via une base MariaDB ou MySQL. Un changement sur l'un s'applique à tous en deux secondes environ. Chaque élément peut être limité à certains serveurs, et un grade ou un joueur peut être autorisé ou refusé sur un seul serveur. Désactivé par défaut.
- **Configuration sûre** : rechargement à chaud atomique, un JSON invalide conserve la configuration précédente, sauvegardes automatiques, et alertes admin quand une action est nécessaire.
- **Diagnostics** : `/customperm status`, `scan`, `debug`, `test`.

---

## Installation

1. Installez **NeoForge 21.1.221+** pour **Minecraft 1.21.1** (Java 21).
2. Placez `customperm-1.1.0.jar` dans le dossier `mods/` du serveur.
3. *(Optionnel)* Ajoutez **[LuckPerms](https://luckperms.net/download)** (build NeoForge 1.21.1).
4. *(Optionnel)* Les admins qui veulent l'interface en jeu installent aussi CustomPerm sur leur client.
5. Démarrez le serveur, puis donnez-vous l'administration depuis la console :

```
customperm grade create admins
customperm grade addperm admins customperm.*
customperm grade assign <votre nom> admins
```

Avec LuckPerms : `lp user <votre nom> permission set customperm.* true`.

**Le mode cluster** exige sur chaque serveur un mod qui fournit un pilote MariaDB ou MySQL : [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib) 1.3.0 ou plus le fait, et c'est aussi le mode de connexion par défaut. CustomPerm n'embarque aucun pilote. Arcadia Lib doit aussi être installé sur le client des joueurs ; CustomPerm seul ne l'exige jamais.

---

## Démarrage rapide

**Avec LuckPerms**
```
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
lp user Steve parent add vip
```
Steve peut désormais utiliser `/spec` sans jamais obtenir `/gamemode`.

**Sans LuckPerms (grades internes)**
```
customperm command add gamemode
customperm grade create vip
customperm grade addperm vip customperm.command.gamemode
customperm grade assign Steve vip
```

---

## Solo et LAN

CustomPerm fonctionne aussi en monde solo : le serveur intégré exécute le même code. Deux réserves :

- **Les commandes de triche doivent être activées.** `/customperm` exige un vrai niveau op 2. L'hôte du monde tient lieu de console et n'a besoin d'aucun nœud ; les invités LAN ont besoin du niveau op 2 et des nœuds, accordés par l'hôte.
- **La configuration est par installation, pas par monde.** Elle réside dans `.minecraft/config/arcadia/customperm/` : grades, alias, commandes exposées et limites sont partagés par **tous** vos mondes solo.

Hors ligne, les alias et l'interface sont l'intérêt principal. Les grades prennent leur sens dès que vous **ouvrez le monde en LAN** : les invités rejoignent en non-op, et les grades leur accordent exactement les commandes voulues.

---

## Prérequis

| Composant | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ |
| Java | 21 |
| LuckPerms | 5.4.150+ (optionnel) |
| Arcadia Lib | 1.3.0+ (mode cluster uniquement, serveur et clients) |

**Licence :** Propriétaire, Tous droits réservés (source visible ; redistribution interdite sans autorisation) · **Auteur :** THEFricadelle

**Modpacks :** ✅ Autorisé dans les modpacks CurseForge / Modrinth qui récupèrent le **fichier officiel non modifié** depuis la plateforme. ❌ Empaqueter le jar dans un pack exporté/hors-ligne, le ré-héberger ailleurs, ou inclure une version modifiée nécessite une autorisation écrite.
