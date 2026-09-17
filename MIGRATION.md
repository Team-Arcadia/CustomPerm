# Migration guide

Upgrading an existing CustomPerm install. English first, French below.

**Français :** [Guide de migration](#guide-de-migration)

---

## 1.0.x to 1.1.0

1.1.0 changes who may administer the mod, how permission nodes resolve, and how the in-game interface is
delivered. Nothing in your configuration files is rewritten, but two changes take effect the moment the
server starts, so read [What breaks on the first start](#what-breaks-on-the-first-start) before upgrading a
live server.

CustomPerm tells you about this itself: the configuration is stamped with a format version, and a
configuration written before 1.1.0 makes the server log the list of changes once, raise an admin alert, and
tell every operator who joins that the new permissions are needed.

### Before you upgrade

- Stop the server. Keep a copy of `config/arcadia/customperm/` and of your world.
- Note which players administer CustomPerm. You are about to grant them a permission.
- Check the exposed commands, aliases and grades you rely on: `/customperm status`, `/customperm scan`,
  `/customperm grade list`.
- CustomPerm 1.1.0 needs NeoForge 1.21.1 and, if you use it, LuckPerms 5.4.150 or later.

### What breaks on the first start

**1. Administering CustomPerm now needs a granted permission.**

Being an operator is no longer enough, permission level 4 included. `/customperm` and the admin interface
need op level 2 **and** `customperm.admin`; every change also needs the `customperm.manage.<area>` node of
its area. Straight after the upgrade nobody holds these nodes, so `/customperm` is hidden from everyone in
game. Grant them from the server console:

```
# Internal backend (no LuckPerms)
customperm grade create admins
customperm grade addperm admins customperm.*
customperm grade assign <name> admins

# With LuckPerms
lp user <name> permission set customperm.* true
```

The console always has access, and so does the host of a singleplayer or LAN world, which has no console.
Grant `customperm.admin` alone to a moderator who should read without changing anything, and add single
areas as needed: `customperm.manage.commands`, `.aliases`, `.ratelimits`, `.grades`, `.logs`, `.config`
(reload), `.luckperms`.

If you used the interface write nodes of the development builds, `customperm.gui.<area>.edit`, replace them
with the matching `customperm.manage.<area>`.

**2. Internal grades resolve the most specific node first.**

The old rule was "a DENY anywhere wins". The new one follows LuckPerms: the exact node beats `a.b.*`, which
beats `a.*`, which beats `*`, across every grade a player holds, and a DENY only wins against an ALLOW of the
same precision.

Grades that deny a wildcard while allowing something under it change meaning. For example, a grade with
`customperm.*` denied and `customperm.command.gamemode` allowed used to refuse `/gamemode`; it now grants it.
Review with:

```
customperm grade list
customperm test <player> <node>
```

`/customperm test` says whether the value is an explicit ALLOW, an explicit DENY, or not set.

**3. An explicit DENY now applies to operators.**

A DENY in a grade an operator holds, or in LuckPerms, now refuses the command, the alias or the admin access
to that operator too. Before, operators passed every CustomPerm check. This is what makes an accidental `/op`
harmless, and it can also refuse something to a staff member who used to pass regardless.

### What changes without breaking anything

- **The in-game interface needs CustomPerm on the admin's client.** It is drawn natively; the TesseraUI
  dependency and its panel are gone, and so is the `gui_sync` channel. Players without the mod connect
  normally and lose nothing but the interface.
- **Network protocol 2.** Clients that still run 1.0.x connect, but `/customperm gui` tells them the
  interface needs the current CustomPerm.
- **`/customperm gui` is a server command.** It opens the interface on the player who runs it.
- **`/customperm grade assign|unassign` take a player name**, not a selector. Offline players are resolved
  from the players who joined this server before, and a name several accounts used is refused rather than
  guessed.
- **New `settings.json` fields**, all with defaults that keep the previous behaviour: `gateAllCommands`,
  `defaultGrade`, `playerCommandLog`, `maskPlayerCommandArguments`, `maskedCommands`, `logRetentionDays`,
  `configVersion`.
- **The activity log** records admin changes from the first start, in `<world>/customperm/logs/`, kept 30
  days. Recording player commands is off until you turn it on.
- **New commands:** `command preserve`, `command gateall`, `alias movestep`, `alias setstep`,
  `grade adddeny`, `grade removedeny`, `grade setdefault`, `grade cleardefault`, `log admin|players`,
  `log record`, `log mask`.

### Already automatic

- The configuration directory moved from `config/customperm/` to `config/arcadia/customperm/` in an earlier
  version, and is still moved on start if the old one is found.
- Missing files, `{}` files, unknown fields and `null` collections are normalised into safe empty structures,
  so a 1.0.x configuration loads as it is.
- Rate limit rules without a `persistence` field default to `world_save`.

### After the upgrade

1. From the console, grant the admin nodes, then check in game that `/customperm status` answers.
2. Re-read the grades that deny a wildcard (point 2 above).
3. Optional, to protect against an accidental `/op`: a default grade plus gating every command, see
   "Restricting operators" in the README.
4. Optional: turn on the player command log if you want it, and tell your players.

### Going back to 1.0.x

Put the 1.0.5 jar back and restore the configuration you copied. A configuration written by 1.1.0 still loads
in 1.0.x: the fields it does not know are ignored. What you lose is what 1.1.0 stored in them, and grades
written with the new resolution in mind must be reviewed again, since 1.0.x applies "a DENY anywhere wins".

---

# Guide de migration

Mise à jour d'une installation CustomPerm existante.

## 1.0.x vers 1.1.0

La 1.1.0 change qui peut administrer le mod, la façon dont les nœuds de permission sont résolus, et la façon
dont l'interface en jeu est fournie. Aucun fichier de configuration n'est réécrit, mais deux changements
s'appliquent dès le démarrage du serveur : lisez [Ce qui casse au premier démarrage](#ce-qui-casse-au-premier-démarrage)
avant de mettre à jour un serveur en production.

CustomPerm vous prévient de lui-même : la configuration porte une version de format, et une configuration
écrite avant la 1.1.0 fait écrire la liste des changements dans le log au démarrage, lève une alerte admin, et
prévient chaque opérateur qui se connecte que les nouvelles permissions sont nécessaires.

### Avant la mise à jour

- Arrêtez le serveur. Gardez une copie de `config/arcadia/customperm/` et de votre monde.
- Notez quels joueurs administrent CustomPerm. Vous allez leur accorder une permission.
- Vérifiez les commandes exposées, alias et grades dont vous dépendez : `/customperm status`,
  `/customperm scan`, `/customperm grade list`.
- CustomPerm 1.1.0 demande NeoForge 1.21.1 et, si vous l'utilisez, LuckPerms 5.4.150 ou plus récent.

### Ce qui casse au premier démarrage

**1. Administrer CustomPerm demande désormais une permission accordée.**

Être opérateur ne suffit plus, niveau 4 compris. `/customperm` et l'interface d'administration demandent
op level 2 **et** `customperm.admin` ; chaque modification demande en plus le nœud `customperm.manage.<domaine>`
de son domaine. Juste après la mise à jour, personne ne détient ces nœuds : `/customperm` est donc masqué pour
tout le monde en jeu. Accordez-les depuis la console du serveur :

```
# Backend interne (sans LuckPerms)
customperm grade create admins
customperm grade addperm admins customperm.*
customperm grade assign <pseudo> admins

# Avec LuckPerms
lp user <pseudo> permission set customperm.* true
```

La console garde toujours l'accès, ainsi que l'hôte d'un monde solo ou LAN, qui n'a pas de console. Accordez
`customperm.admin` seul à un modérateur qui doit lire sans rien modifier, et ajoutez les domaines un par un :
`customperm.manage.commands`, `.aliases`, `.ratelimits`, `.grades`, `.logs`, `.config` (rechargement),
`.luckperms`.

Si vous utilisiez les nœuds d'écriture des versions de développement, `customperm.gui.<domaine>.edit`,
remplacez-les par les `customperm.manage.<domaine>` correspondants.

**2. Les grades internes résolvent d'abord le nœud le plus spécifique.**

L'ancienne règle était « un DENY n'importe où l'emporte ». La nouvelle suit LuckPerms : le nœud exact passe
avant `a.b.*`, qui passe avant `a.*`, qui passe avant `*`, sur l'ensemble des grades d'un joueur, et un DENY ne
gagne que face à un ALLOW de même précision.

Les grades qui refusent un wildcard tout en autorisant quelque chose en dessous changent de sens. Par exemple,
un grade avec `customperm.*` refusé et `customperm.command.gamemode` autorisé refusait `/gamemode` ; il
l'accorde maintenant. À revoir avec :

```
customperm grade list
customperm test <pseudo> <nœud>
```

`/customperm test` indique si la valeur est un ALLOW explicite, un DENY explicite, ou non définie.

**3. Un DENY explicite s'applique désormais aux opérateurs.**

Un DENY dans un grade que détient un opérateur, ou dans LuckPerms, lui refuse désormais la commande, l'alias ou
l'accès d'administration. Avant, les opérateurs passaient tous les contrôles de CustomPerm. C'est ce qui rend un
`/op` accidentel inoffensif, et cela peut aussi refuser quelque chose à un membre du staff qui passait jusque-là.

### Ce qui change sans rien casser

- **L'interface en jeu demande CustomPerm sur le client de l'admin.** Elle est dessinée nativement ; la
  dépendance TesseraUI et son panneau ont disparu, ainsi que le canal `gui_sync`. Les joueurs sans le mod se
  connectent normalement et ne perdent que l'interface.
- **Protocole réseau 2.** Les clients encore en 1.0.x se connectent, mais `/customperm gui` leur explique que
  l'interface demande la version actuelle de CustomPerm.
- **`/customperm gui` est une commande serveur.** Elle ouvre l'interface sur le joueur qui la lance.
- **`/customperm grade assign|unassign` prennent un pseudo**, pas un sélecteur. Les joueurs hors ligne sont
  résolus parmi ceux déjà venus sur le serveur, et un pseudo utilisé par plusieurs comptes est refusé plutôt que
  deviné.
- **Nouveaux champs de `settings.json`**, tous avec des valeurs par défaut qui conservent le comportement
  précédent : `gateAllCommands`, `defaultGrade`, `playerCommandLog`, `maskPlayerCommandArguments`,
  `maskedCommands`, `logRetentionDays`, `configVersion`.
- **Le journal d'activité** enregistre les modifications d'administration dès le premier démarrage, dans
  `<monde>/customperm/logs/`, gardées 30 jours. L'enregistrement des commandes des joueurs reste désactivé tant
  que vous ne l'activez pas.
- **Nouvelles commandes :** `command preserve`, `command gateall`, `alias movestep`, `alias setstep`,
  `grade adddeny`, `grade removedeny`, `grade setdefault`, `grade cleardefault`, `log admin|players`,
  `log record`, `log mask`.

### Déjà automatique

- Le dossier de configuration est passé de `config/customperm/` à `config/arcadia/customperm/` dans une version
  antérieure, et il est toujours déplacé au démarrage si l'ancien est trouvé.
- Les fichiers manquants, les fichiers `{}`, les champs inconnus et les collections `null` sont normalisés en
  structures vides sûres : une configuration 1.0.x se charge telle quelle.
- Les règles de limite sans champ `persistence` prennent `world_save` par défaut.

### Après la mise à jour

1. Depuis la console, accordez les nœuds d'administration, puis vérifiez en jeu que `/customperm status` répond.
2. Relisez les grades qui refusent un wildcard (point 2 ci-dessus).
3. Facultatif, pour se protéger d'un `/op` accidentel : un grade par défaut et le contrôle de toutes les
   commandes, voir « Restreindre les opérateurs » dans le README.
4. Facultatif : activez le journal des commandes des joueurs si vous le souhaitez, et prévenez vos joueurs.

### Revenir en 1.0.x

Remettez le jar 1.0.5 et restaurez la configuration copiée. Une configuration écrite par la 1.1.0 se charge
encore en 1.0.x : les champs qu'elle ne connaît pas sont ignorés. Ce que vous perdez, c'est ce que la 1.1.0 y
stockait, et les grades écrits en pensant à la nouvelle résolution doivent être revus, puisque la 1.0.x
applique « un DENY n'importe où l'emporte ».
