# CustomPerm 1.1.0 (Beta)

> **Beta build.** This release is feature-complete but still in final testing. Try it on a test server first, and report any issue on the [tracker](https://github.com/Team-Arcadia/CustomPerm/issues) before running it in production.

**Compatibility:** Minecraft 1.21.1 · NeoForge 21.1.221+ · Java 21 · LuckPerms 5.4.150+ (optional) · Arcadia Lib 1.3.0+ (cluster mode only)

---

## Before you upgrade

Three changes can lock you out or change what a player may do. The full list, and how to go back, is in the [migration guide](https://github.com/Team-Arcadia/CustomPerm/blob/main/MIGRATION.md).

1. **Administration needs a granted permission.** `/customperm` and the interface used to open for any operator of level 2. They now need op level 2 **and** `customperm.admin`, and each change needs the node of its area (`customperm.manage.commands`, `.aliases`, `.ratelimits`, `.grades`, `.logs`, `.config`, `.luckperms`). The op level alone opens nothing, level 4 included. **After upgrading, nobody holds these nodes.** Grant them from the console:

   ```
   customperm grade create admins
   customperm grade addperm admins customperm.*
   customperm grade assign <name> admins
   ```

   or, with LuckPerms, `lp user <name> permission set customperm.* true`. The server log says so at every start, and the console always has access.

2. **The most specific entry wins (internal grades).** A DENY anywhere used to beat any ALLOW. Resolution now follows LuckPerms: the exact node beats `a.b.*`, which beats `a.*`, which beats `*`, and a DENY wins only at the same level. A grade that denies a wildcard and allows a narrower node now grants that node.

3. **An explicit DENY applies to operators.** Operators keep every command whose node is not set, but a node explicitly denied to them (internal grade, or `false` in LuckPerms) is now refused to them too.

The interface write nodes `customperm.gui.<area>.edit` are replaced by the `customperm.manage.<area>` nodes above.

**Cluster mode needs a mod providing a database driver.** CustomPerm ships no MariaDB or MySQL driver. On every server of a cluster, install [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib) 1.3.0 or later, or another mod that brings one. Without it the server starts normally, alone, and `/customperm status` says the driver is missing. Arcadia Lib must also be installed on your players' clients: its network channels are required, so a client without it is refused. CustomPerm on its own never requires a client mod.

---

## What's new

### Native admin interface

`/customperm gui` opens an interface drawn with Minecraft's own widgets. It replaces the TesseraUI panel and needs CustomPerm on the admin's client, nothing else. Pages: dashboard with admin alerts, commands, aliases, rate limits, grades, players, logs, LuckPerms import and export, a LuckPerms editor when LuckPerms runs, and a help page explaining each feature. Every field completes grades, players, nodes, commands, contexts and durations as you type. The server stays the authority: each action is checked, rate limited and logged.

### A permission model close to LuckPerms

Internal grades gain inheritance, refused grades, weights, nodes carried by one player, a default grade, temporary entries (`30d`), entries limited to a world, a game mode or a static context, tracks with `promote` and `demote`, meta, chat prefixes and suffixes, and nicknames. Nodes declared by other mods through NeoForge's permission API are answered from the grades, and `/customperm modcheck` lists the mods that only speak to LuckPerms.

### Moving between CustomPerm and LuckPerms

`/customperm import` brings LuckPerms groups, players and nodes over; `/customperm export` writes grades into LuckPerms. Both show a report before writing anything, back up first, and let you choose which groups, players, tracks and kinds of entries go.

### Cluster mode

Several servers without LuckPerms share their grades, what players hold, exposed commands, aliases, rate limits and the activity log through one MariaDB or MySQL database, directly or through Arcadia Lib. A change on one server applies on all within about two seconds. Each server chooses which parts it follows. A command, an alias or a rate limit can be limited to some servers, and a grade or a player can be allowed or refused on one server only (`server=<name>`, or `server=here`). Off by default.

### Aliases and rate limits

- Aliases take typed arguments (`player`, `integer`, `word`, `text`), optional with defaults, used in steps as `${name}`.
- Rate limits get levels: the rule, then a server's own limit, then a grade's value, then a player's value (`10/1h`, `unlimited`), set with `/customperm ratelimit grade|player|server` or from the interface. Counters can be shared across a cluster, and usage history survives restarts.

### Activity log and alerts

Every admin change is recorded with who, when, from where and the result, including changes made through `/lp`. Recording the commands players type is available, off by default, with private messages and passwords masked. Operators get an alert in chat when LuckPerms becomes unavailable or a configuration file fails to load.

---

## Removed

- **The TesseraUI panel** and the `tesseraui` optional dependency. The native interface replaces it; admins can uninstall TesseraUI.

---

## Fixed

- `/function` and `/return` failed on every call.
- Shortcut commands could bypass command exposure and rate limits.
- A command that stopped being exposed could still be granted by its old gate.
- Exposing a command no longer weakens LuckPerms for operators.
- LuckPerms import and export used the wrong context for a world.
- A hand-edited file with `null` entries could break permission checks, and an admin command could wipe a file that had failed to load.
- A save could fail on Windows while an antivirus held the file.
- Per-player rate limit history no longer grows without bound.

The complete list is in the [changelog](https://github.com/Team-Arcadia/CustomPerm/blob/main/CHANGELOG.md).

---

## Licence

1.1.0 is the first build published under the proprietary, source-available licence (All Rights Reserved). The source stays readable on GitHub. Modpacks on CurseForge and Modrinth that fetch the unmodified official file are allowed; anything else needs written permission. Versions up to 1.0.5 stay available on the licence they shipped with. See [NOTICE.md](https://github.com/Team-Arcadia/CustomPerm/blob/main/NOTICE.md).

---

## Please help us test

- [ ] Upgrade from 1.0.5: grant the admin nodes from the console, then check your grades still give what you expect.
- [ ] The interface on a normal and a narrow game window.
- [ ] A vanilla client still joining a server running CustomPerm without Arcadia Lib.
- [ ] Import from or export to LuckPerms on a copy of your data.
- [ ] Cluster mode on two servers sharing a test database.

Report issues with your server type (dedicated or singleplayer), LuckPerms version if any, and whether cluster mode is on.

---
---

# CustomPerm 1.1.0 (Bêta)

> **Version bêta.** Cette version est complète mais encore en phase de tests finaux. Essayez-la d'abord sur un serveur de test, et signalez tout problème sur le [tracker](https://github.com/Team-Arcadia/CustomPerm/issues) avant une mise en production.

**Compatibilité :** Minecraft 1.21.1 · NeoForge 21.1.221+ · Java 21 · LuckPerms 5.4.150+ (optionnel) · Arcadia Lib 1.3.0+ (mode cluster uniquement)

---

## Avant de mettre à jour

Trois changements peuvent vous bloquer l'accès ou modifier ce qu'un joueur peut faire. La liste complète, et le retour arrière, sont dans le [guide de migration](https://github.com/Team-Arcadia/CustomPerm/blob/main/MIGRATION.md).

1. **L'administration exige une permission accordée.** `/customperm` et l'interface s'ouvraient pour tout opérateur de niveau 2. Il faut désormais le niveau op 2 **et** `customperm.admin`, et chaque modification exige le nœud de son domaine (`customperm.manage.commands`, `.aliases`, `.ratelimits`, `.grades`, `.logs`, `.config`, `.luckperms`). Le niveau op seul n'ouvre rien, niveau 4 compris. **Après la mise à jour, personne ne détient ces nœuds.** Accordez-les depuis la console :

   ```
   customperm grade create admins
   customperm grade addperm admins customperm.*
   customperm grade assign <nom> admins
   ```

   ou, avec LuckPerms, `lp user <nom> permission set customperm.* true`. Le journal du serveur le rappelle à chaque démarrage, et la console garde toujours l'accès.

2. **L'entrée la plus précise l'emporte (grades internes).** Un DENY quelque part l'emportait sur tout ALLOW. La résolution suit désormais LuckPerms : le nœud exact bat `a.b.*`, qui bat `a.*`, qui bat `*`, et un DENY ne l'emporte qu'au même niveau. Un grade qui refuse un wildcard et autorise un nœud plus précis accorde désormais ce nœud.

3. **Un DENY explicite s'applique aux opérateurs.** Les opérateurs gardent toute commande dont le nœud n'est pas défini, mais un nœud qui leur est explicitement refusé (grade interne, ou `false` dans LuckPerms) leur est désormais refusé aussi.

Les nœuds d'écriture de l'interface `customperm.gui.<domaine>.edit` sont remplacés par les nœuds `customperm.manage.<domaine>` ci-dessus.

**Le mode cluster exige un mod fournissant un pilote de base de données.** CustomPerm n'embarque aucun pilote MariaDB ou MySQL. Sur chaque serveur du cluster, installez [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib) 1.3.0 ou plus, ou un autre mod qui en apporte un. Sans lui, le serveur démarre normalement, seul, et `/customperm status` signale le pilote manquant. Arcadia Lib doit aussi être installé sur le client des joueurs : ses canaux réseau sont obligatoires, un client qui ne l'a pas est refusé. CustomPerm seul n'exige jamais de mod client.

---

## Nouveautés

### Interface d'administration native

`/customperm gui` ouvre une interface dessinée avec les widgets de Minecraft. Elle remplace le panneau TesseraUI et n'exige que CustomPerm sur le client de l'admin. Pages : tableau de bord avec les alertes admin, commandes, alias, limites, grades, joueurs, journaux, import et export LuckPerms, un éditeur LuckPerms quand LuckPerms tourne, et une page d'aide qui explique chaque fonctionnalité. Chaque champ complète grades, joueurs, nœuds, commandes, contextes et durées pendant la saisie. Le serveur reste l'autorité : chaque action est vérifiée, limitée en débit et journalisée.

### Un modèle de permissions proche de LuckPerms

Les grades internes gagnent l'héritage, les grades refusés, les poids, les nœuds portés par un seul joueur, un grade par défaut, les entrées temporaires (`30d`), les entrées limitées à un monde, un mode de jeu ou un contexte statique, les tracks avec `promote` et `demote`, les méta, les préfixes et suffixes de chat, et les surnoms. Les nœuds déclarés par d'autres mods via l'API de permissions de NeoForge sont résolus par les grades, et `/customperm modcheck` liste les mods qui ne parlent qu'à LuckPerms.

### Passer entre CustomPerm et LuckPerms

`/customperm import` reprend les groupes, joueurs et nœuds de LuckPerms ; `/customperm export` écrit les grades dans LuckPerms. Les deux affichent un rapport avant toute écriture, sauvegardent d'abord, et laissent choisir quels groupes, joueurs, tracks et types d'entrées partent.

### Mode cluster

Plusieurs serveurs sans LuckPerms partagent leurs grades, ce que détiennent les joueurs, les commandes exposées, les alias, les limites et le journal d'activité via une base MariaDB ou MySQL, en direct ou via Arcadia Lib. Un changement sur un serveur s'applique à tous en deux secondes environ. Chaque serveur choisit les parties qu'il suit. Une commande, un alias ou une limite peut être limité à certains serveurs, et un grade ou un joueur peut être autorisé ou refusé sur un seul serveur (`server=<nom>`, ou `server=here`). Désactivé par défaut.

### Alias et limites d'usage

- Les alias prennent des arguments typés (`player`, `integer`, `word`, `text`), optionnels avec valeur par défaut, utilisés dans les étapes via `${nom}`.
- Les limites d'usage ont des niveaux : la règle, puis la limite propre d'un serveur, puis la valeur d'un grade, puis celle d'un joueur (`10/1h`, `unlimited`), réglés avec `/customperm ratelimit grade|player|server` ou depuis l'interface. Les compteurs peuvent être partagés sur un cluster, et l'historique survit aux redémarrages.

### Journal d'activité et alertes

Chaque modification admin est enregistrée avec qui, quand, d'où et le résultat, y compris les changements faits via `/lp`. L'enregistrement des commandes tapées par les joueurs est disponible, désactivé par défaut, avec les messages privés et les mots de passe masqués. Les opérateurs reçoivent une alerte dans le chat quand LuckPerms devient indisponible ou qu'un fichier de configuration ne se charge pas.

---

## Retraits

- **Le panneau TesseraUI** et la dépendance optionnelle `tesseraui`. L'interface native le remplace ; les admins peuvent désinstaller TesseraUI.

---

## Correctifs

- `/function` et `/return` échouaient à chaque appel.
- Les commandes raccourcies pouvaient contourner l'exposition et les limites d'usage.
- Une commande qui n'était plus exposée pouvait encore être accordée par son ancienne barrière.
- Exposer une commande n'affaiblit plus LuckPerms pour les opérateurs.
- L'import et l'export LuckPerms utilisaient le mauvais contexte pour un monde.
- Un fichier édité à la main avec des entrées `null` pouvait casser les vérifications, et une commande admin pouvait effacer un fichier qui avait échoué au chargement.
- Une sauvegarde pouvait échouer sous Windows quand un antivirus tenait le fichier.
- L'historique des limites par joueur ne grossit plus sans borne.

La liste complète est dans le [changelog](https://github.com/Team-Arcadia/CustomPerm/blob/main/CHANGELOG.md).

---

## Licence

La 1.1.0 est la première version publiée sous la licence propriétaire à source visible (Tous droits réservés). Le code reste lisible sur GitHub. Les modpacks CurseForge et Modrinth qui récupèrent le fichier officiel non modifié sont autorisés ; tout le reste demande une autorisation écrite. Les versions jusqu'à la 1.0.5 restent disponibles sous la licence avec laquelle elles ont été publiées. Voir [NOTICE.md](https://github.com/Team-Arcadia/CustomPerm/blob/main/NOTICE.md).

---

## Aidez-nous à tester

- [ ] Mise à jour depuis la 1.0.5 : accordez les nœuds admin depuis la console, puis vérifiez que vos grades donnent toujours ce que vous attendez.
- [ ] L'interface dans une fenêtre de jeu normale et étroite.
- [ ] Un client vanilla qui rejoint un serveur sous CustomPerm sans Arcadia Lib.
- [ ] Import depuis ou export vers LuckPerms sur une copie de vos données.
- [ ] Le mode cluster sur deux serveurs partageant une base de test.

Signalez les problèmes en précisant le type de serveur (dédié ou solo), la version de LuckPerms le cas échéant, et si le mode cluster est actif.
