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
- **Journal d'activité** : chaque modification d'administration (commandes, interface, éditeur LuckPerms, `/lp`) est enregistrée avec qui, quand et le résultat ; les commandes tapées par les joueurs peuvent l'être aussi, désactivé par défaut, avec les arguments des messages privés et mots de passe masqués. Fichiers quotidiens dans le dossier du monde, gardés 30 jours par défaut, et une page Journaux dans l'interface.
- **Alertes admin** : quand LuckPerms devient indisponible ou qu'un fichier de config ne se charge pas, chaque op connecté (niveau 2+) reçoit une alerte dans le chat, les ops qui se connectent ensuite la reçoivent à la connexion, et `/customperm status` la liste jusqu'à sa résolution.
- **RBAC multi-grades** : un joueur peut avoir plusieurs grades internes ; les permissions sont résolues par union des grades assignés.
- **DENY explicite** : les grades internes supportent `deniedPermissions`. L'entrée la plus spécifique l'emporte, comme LuckPerms (nœud exact, puis `a.b.*`, puis `*`), et un DENY gagne à niveau égal.
- **Nœuds par joueur** : un nœud peut être porté par un joueur plutôt que par un grade, l'exception qu'un joueur seul obtient sans qu'on invente un grade pour lui. Il l'emporte sur ses grades à niveau égal, quel que soit le poids du grade, mais un nœud de grade plus spécifique gagne toujours. `/customperm user addperm|adddeny`, ou la page Joueurs.
- **Entrées temporaires** : un nœud, un grade tenu par un joueur ou un refus peut durer un temps donné : `/customperm grade assign Steve vip 30d`. Une entrée expirée cesse de compter aussitôt, puis un balayage la retire et renvoie l'arbre de commandes. Les pages Grades et Joueurs acceptent une durée et affichent le temps restant.
- **Permissions des autres mods** : les nœuds que d'autres mods déclarent via l'API de permissions de NeoForge sont répondus depuis les grades, donc `/customperm grade addperm vip unmod.fonction` fonctionne pour eux aussi. CustomPerm devient de lui-même le handler de permissions de NeoForge seulement sans LuckPerms, et ne remplace jamais un handler choisi par un admin.
- **Tracks** : une échelle ordonnée de grades, pour que promouvoir et rétrograder fassent monter ou descendre un joueur d'un cran : `/customperm track promote Steve staff`, ou l'onglet Tracks de la page Joueurs. Un track n'accorde rien lui-même ; c'est le confort qu'attend un serveur qui vient de LuckPerms.
- **Entrées par monde** : un nœud sur un grade ou un joueur, ou un grade tenu par un joueur, peut ne valoir que dans un monde : `/customperm grade adddeny member customperm.command.home world=the_nether`. Elle l'emporte sur l'entrée sans monde du même détenteur, et l'arbre de commandes suit le joueur à travers les portails. Les pages Grades et Joueurs acceptent aussi un monde.
- **Préfixes et suffixes de chat** : un grade, ou un joueur, porte un préfixe et un suffixe autour de son nom dans le chat et partout où le jeu l'affiche, résolus comme une permission. Avec LuckPerms, ce sont les préfixes que LuckPerms stocke qui s'affichent. Le nom est décoré, jamais le message, donc le chat reste signé et signalable. Désactivé tant qu'on n'a pas fait `/customperm names on`.
- **Export vers LuckPerms** : un serveur qui a construit ses grades ici et installe LuckPerms plus tard les écrit dans LuckPerms en groupes, utilisateurs et nœuds, pour qu'ils continuent de décider. Mêmes deux temps que l'import, rien n'est traduit. `/customperm export preview` puis `/customperm export confirm`, ou l'onglet To LuckPerms de la page Import.
- **Import depuis LuckPerms** : un serveur qui quitte LuckPerms récupère ses groupes, ses joueurs et leurs nœuds au lieu de tout retaper. La lecture ne change rien et répond par un rapport, y compris ce qu'elle laisse derrière et pourquoi ; ce n'est qu'ensuite qu'on applique, après une sauvegarde de tous les fichiers de config. `/customperm import preview` puis `/customperm import confirm`, ou la page Import.
- **Grades refusés** : un grade peut en refuser un autre partout où il en hériterait, et un joueur peut en refuser un partout où l'un de ses grades l'apporterait, grade par défaut compris. Un refus sort le grade de la résolution ; il ne transforme jamais ce que ce grade autorise en refus. `/customperm grade parent adddeny`, `/customperm user denygrade`, ou la page Grades.
- **Héritage entre grades** : un grade peut hériter d'autres grades, comme le parent d'un groupe LuckPerms. Ce que dit un parent s'applique là où le grade ne dit rien d'aussi précis sur le nœud, l'ancêtre le plus proche d'abord, donc un grade écrase ce qu'il hérite tandis qu'une entrée d'ancêtre plus spécifique gagne toujours. Un cycle est refusé à la création, pas à la résolution.
- **Poids de grade** : un grade porte un poids, comme le weight d'un groupe LuckPerms. Il départage deux grades d'un même joueur qui couvrent un nœud avec la même précision : le plus lourd décide, et un DENY gagne toujours entre poids égaux. Un poids ne bat jamais un nœud plus spécifique : il ne permet pas de contourner celui-ci.
- **Wildcards de permissions** : `*`, `customperm.command.*` et `customperm.alias.*` sont supportés, dans les deux sens : un `*` refusé refuse tout sauf les autorisations explicites.
- **Administration derrière une permission** : `/customperm` et l'interface demandent op level 2 **et** des nœuds explicitement accordés (`customperm.admin` pour entrer, `customperm.manage.<domaine>` pour modifier), donc un joueur mis op par erreur, niveau 4 compris, n'obtient rien. La console garde toujours l'accès, et l'hôte d'un monde solo ou LAN en tient lieu.
- **Opérateurs restreignables** : un DENY explicite s'applique aussi aux opérateurs, sur les deux backends, pour tenir à l'écart un joueur mis op par erreur. Un grade par défaut s'applique à tous les joueurs, et `gateAllCommands` étend le contrôle de CustomPerm à toutes les commandes. Voir [Restreindre les opérateurs](#restreindre-les-opérateurs).
- **Aliases et macros** : créez des commandes racine personnalisées (`/fly`, `/heal`, `/starter`) qui exécutent une ou plusieurs commandes configurées.
- **Edition des steps d'alias** : ajout, suppression et inspection de steps individuels avec indices 0-based.
- **Elévation des aliases** : les steps d'alias s'exécutent avec op level 4 pour permettre aux macros signées par l'admin d'appeler des commandes op-only.
- **Garde-fous sur les aliases** : `/customperm` est réservé, les steps vides sont ignorés, les aliases sans step sont refusés, le shadow d'une commande existante émet un warning et les chaînes récursives sont arrêtées à la profondeur 8.
- **Enregistrement runtime des aliases** : ajout, remplacement ou retrait d'alias sans redémarrage ; `/customperm reload` applique aussi les ajouts, suppressions et changements de steps provenant d'`aliases.json`.
- **Politique indépendante du backend** : l'exposition directe des commandes fonctionne avec ou sans LuckPerms ; le node `customperm.command.<nom>` est résolu par LuckPerms lorsqu'il est installé (accordé via `/lp`), sinon par les grades internes.
- **Préservation des ops par défaut** : un opérateur garde toute commande dont le nœud n'est pas défini ; seul un DENY explicite le restreint. La console n'est jamais restreinte.
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

> **Vous venez de la 1.0.x ?** Lisez d'abord [MIGRATION.md](MIGRATION.md) : administrer CustomPerm demande
> désormais des permissions accordées, et les grades internes résolvent les wildcards différemment. Le serveur le
> signale dans son log au premier démarrage.

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
| LuckPerms | Uniquement quand LuckPerms est installé : pas d'entrée de navigation sinon, et `/customperm gui luckperms` explique pourquoi. Installé mais pas démarré (solo, échec au démarrage), la page affiche une bannière au lieu de l'éditeur. **Groupes** : créer, supprimer, nœuds de permission allow/deny avec contextes et durée, parents, poids, nom affiché, préfixe, suffixe, meta. **Joueurs** : joueurs connectés et tout joueur trouvé par pseudo exact, leurs nœuds, groupes avec durée, groupe principal, promotion et rétrogradation sur un track, préfixe, suffixe, meta. **Tracks** : créer, supprimer, ajouter, insérer à une position, retirer un groupe. Les écritures passent par l'API LuckPerms côté serveur, protégées par `customperm.manage.luckperms` |
| Grades | Toujours accessible, pour pouvoir lire le repli quand LuckPerms fonctionne ou tombe. Une bannière indique quand les grades ne décident pas des permissions ; avec LuckPerms actif la page est en lecture seule, comme les commandes de grade : grades avec recherche et création, triés par poids ; par grade, trois onglets : nœuds ALLOW et DENY, grades dont il hérite et ceux qu'il refuse, et les joueurs qui le détiennent à côté de ceux qui le refusent, avec leur état en ligne, attribués par pseudo avec complétion, y compris hors ligne s'ils sont déjà venus sur le serveur ; suppression d'un grade (avec confirmation). Un quatrième onglet, **Chat**, règle le préfixe et le suffixe du grade avec un aperçu de la ligne de chat, et porte l'interrupteur qui décore les noms (`customperm.manage.config`). Une case de durée à côté des champs nœud et joueur accorde pour un temps limité, et les lignes affichent le temps restant. Une case monde à côté limite un nœud ou une attribution à un monde (`the_nether`), affiché sur la ligne |
| Joueurs | Nœuds portés par un joueur plutôt que par un grade : tous les joueurs qui détiennent quelque chose en propre plus tous ceux connectés, avec recherche ; par joueur, ses nœuds ALLOW et DENY et les grades qu'il détient, en lecture seule ici. Un joueur qui ne détient encore rien s'atteint en tapant son pseudo. Écrire demande `customperm.manage.grades`, comme la page Grades. Un onglet **Chat** règle le préfixe et le suffixe que le joueur porte lui-même, au-dessus de ses grades. Un onglet **Tracks** montre chaque track avec le cran du joueur et le promeut ou le rétrograde d'un cran. Le champ nœud accepte aussi une durée et un monde, et les grades tenus dans un seul monde sont listés avec lui |
| Import | Uniquement quand LuckPerms est installé : récupère ses groupes, joueurs et nœuds, en deux temps. Read LuckPerms répond par le rapport et ne change rien, Import applique ce rapport et rien d'autre, après une sauvegarde. Deux options : exposer les commandes dont les nœuds traduits ont besoin, et ajouter aux grades de même nom ou les remplacer. Demande les trois nœuds d'écriture ensemble. Un second onglet, **To LuckPerms**, exporte les grades dans l'autre sens : Read the grades, puis Export, qui reste désactivé tant que l'admin n'a pas indiqué que LuckPerms est sauvegardé ; la page suit la progression pendant l'écriture. Demande `customperm.manage.grades` et `customperm.manage.luckperms` |
| Journaux | Deux onglets, du plus récent au plus ancien, avec recherche. **Admin** : chaque modification faite par les commandes `/customperm`, l'interface et l'éditeur LuckPerms, et les modifications que LuckPerms enregistre lui-même (`/lp`, éditeur web) : quand, qui, d'où, quoi, et le résultat ou le refus. **Joueurs** : chaque commande tapée par les joueurs, seulement quand l'enregistrement est actif (désactivé par défaut) ; arguments des commandes de message privé et de mot de passe masqués sauf si le masquage est désactivé. Changer l'enregistrement et le masquage demande `customperm.manage.logs` |

**Permissions.** Lire une page demande le même accès que `/customperm` : op level 2 et `customperm.admin`. Écrire demande en plus le nœud du domaine, le même que pour les commandes correspondantes : `customperm.manage.commands`, `customperm.manage.aliases`, `customperm.manage.ratelimits`, `customperm.manage.grades`, `customperm.manage.logs`, `customperm.manage.config` (rechargement), `customperm.manage.luckperms`. Le niveau op seul n'accorde rien, niveau 4 compris, ce qui permet de déléguer un domaine à un modérateur sans ouvrir les autres. Les actions qui ne modifient pas la configuration, comme le rechargement, demandent seulement op level 2, comme leur commande. Chaque action, appliquée ou refusée, est enregistrée dans le journal d'activité avec le nom de l'admin.

**Compatibilité.** L'interface utilise le protocole réseau 2. Un client avec un CustomPerm plus ancien se connecte toujours à un serveur 1.1.0 mais n'y a pas d'interface, et inversement.

---

## Mondes solo et LAN

CustomPerm est conçu pour les serveurs, mais fonctionne également en monde solo — le serveur intégré exécute exactement le même code. Deux comportements diffèrent suffisamment pour être connus avant d'essayer.

### Les commandes de triche doivent être activées

`/customperm` est protégée par un contrôle op niveau 2 réel, plus le nœud `customperm.admin`, dont l'hôte du monde est dispensé : un monde solo ou LAN n'a pas de console pour accorder le premier nœud. Les invités d'un monde LAN ont besoin d'op niveau 2 et des nœuds, accordés par l'hôte. En monde solo, vous ne disposez d'un niveau de permission que si **Autoriser les commandes de triche** est actif (`Créer un nouveau monde → Plus → Autoriser les commandes de triche`, ou ouverture en LAN avec la triche activée). Sans cela, la commande est masquée de la tab-complétion et inexécutable — ce n'est pas un bug, c'est la même protection que sur un serveur.

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

Toutes les commandes admin sont sous `/customperm` et demandent **op level 2 et `customperm.admin`**, explicitement accordé ; les sous-commandes qui modifient quelque chose demandent en plus le nœud `customperm.manage.<domaine>` de leur domaine, indiqué avec chaque groupe ci-dessous. Le niveau op seul n'accorde rien, niveau 4 compris (voir [Restreindre les opérateurs](#restreindre-les-opérateurs)). La console y a toujours accès, ainsi que l'hôte d'un monde solo ou LAN, qui n'a pas de console.

Sur une installation neuve, ou juste après une mise à jour depuis 1.0.x, personne ne détient ces nœuds : accordez-les
depuis la console (voir [MIGRATION.md](MIGRATION.md)).

```
# Sans LuckPerms
customperm grade create admins
customperm grade addperm admins customperm.*
customperm grade assign <pseudo> admins

# Avec LuckPerms
lp user <pseudo> permission set customperm.* true
```

### Exposition des commandes

Cette fonctionnalité fonctionne avec **l'un ou l'autre backend**. Le node `customperm.command.<nom>` est résolu par le backend actif : via **LuckPerms** lorsqu'il est installé (à accorder avec `/lp`), sinon via les grades internes. La commande d'exposition est la même dans les deux cas.

Définit quelles commandes sont éligibles au système de permissions. Une commande non-exposée garde son comportement vanilla (op-only).

| Commande | Effet |
|---|---|
| `/customperm command add <name>` | Expose la commande `<name>` au système. |
| `/customperm command remove <name>` | Retire la commande, retour au comportement vanilla. |
| `/customperm command preserve <name> <true\|false>` | Pour une commande exposée : `true` exige le nœud ET l'exigence d'origine de la commande, `false` (défaut) le nœud seul. Équivaut à `preserveOriginalRequires` dans `commands.json`. |
| `/customperm command gateall <true\|false>` | Backend interne uniquement. `true` : toutes les commandes lisent leur nœud `customperm.command.<nom>`, pas seulement les exposées. Équivaut à `gateAllCommands` dans `settings.json`. |
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
| `/customperm grade addperm <grade> <node> [durée\|world=<dim>]` | Ajoute une perm au grade, pour de bon, pour une durée comme `30d`, ou dans un monde comme `world=the_nether`. |
| `/customperm grade removeperm <grade> <node> [world=<dim>]` | Retire une perm du grade, celle limitée à ce monde s'il est donné. |
| `/customperm grade adddeny <grade> <node> [durée\|world=<dim>]` | Ajoute un nœud DENY : refusé, opérateurs compris, sauf si un nœud plus spécifique l'autorise. |
| `/customperm grade removedeny <grade> <node> [world=<dim>]` | Retire un nœud DENY. |
| `/customperm grade weight <grade> <poids>` | Définit le poids de départage, 0 par défaut, négatif accepté. |
| `/customperm grade parent add <grade> <parent>` | Fait hériter le grade d'un autre ; un cycle est refusé. |
| `/customperm grade parent remove <grade> <parent>` | Cesse d'en hériter. |
| `/customperm grade parent adddeny <grade> <parent>` | Refuse un grade partout où celui-ci en hériterait. |
| `/customperm grade parent removedeny <grade> <parent>` | Cesse de le refuser. |
| `/customperm grade parent list <grade>` | Affiche ce dont le grade hérite et ce qu'il refuse. |
| `/customperm grade assign <player> <grade> [durée\|world=<dim>]` | Assigne le grade à un joueur, en ligne ou hors ligne s'il est déjà venu sur le serveur ; avec un monde, il ne vaut que là. |
| `/customperm grade unassign <player> <grade> [world=<dim>]` | Désassigne, en ligne ou hors ligne. |
| `/customperm grade setdefault <grade>` | Applique le grade à tous les joueurs, sous leurs propres grades. |
| `/customperm grade cleardefault` | Plus aucun grade ne s'applique à tous les joueurs. |
| `/customperm grade list` | Liste les grades définis, du plus lourd au plus léger. |

Nœuds portés par un joueur, au-dessus de ses grades :

| Commande | Description |
|---|---|
| `/customperm user addperm <joueur> <node> [durée\|world=<dim>]` | Ajoute un nœud ALLOW à ce joueur seul. |
| `/customperm user removeperm <joueur> <node> [world=<dim>]` | Le retire. |
| `/customperm user adddeny <joueur> <node> [durée\|world=<dim>]` | Ajoute un nœud DENY à ce joueur seul. |
| `/customperm user removedeny <joueur> <node> [world=<dim>]` | Le retire. |
| `/customperm user denygrade <joueur> <grade> [durée]` | Fait refuser un grade à un joueur, partout où l'un des siens l'apporterait. |
| `/customperm user undenygrade <joueur> <grade>` | Cesse de le refuser. |
| `/customperm user list <joueur>` | Affiche les grades détenus, ceux refusés, et les nœuds portés, avec le temps restant des entrées temporaires et, par monde, ce qui ne vaut que là. |

**Durées.** `w`, `d`, `h`, `m` et `s`, seuls ou combinés : `30d`, `2h`, `1d12h`, `1w`, dix ans au plus.
Sans durée, une entrée est permanente. Ajouter avec une durée une entrée déjà présente la rend temporaire à
partir de maintenant, et sans durée la rend permanente : c'est la dernière chose dite qui compte. Une entrée
expirée cesse de compter aussitôt, et l'entrée en dessous répond (un `a.b.c` expiré laisse `a.b.*` décider).
Chaque seconde, CustomPerm retire ce qui a expiré, sauvegarde, renvoie l'arbre de commandes et l'inscrit au
journal d'activité. Les parents de grade restent toujours permanents.

**Mondes.** `world=<dimension>` limite à un monde un nœud sur un grade ou un joueur, ou un grade tenu par un
joueur : `world=the_nether`, `world=the_end`, `world=overworld`, ou une dimension moddée par son identifiant
complet (`world=mymod:mining`). La complétion propose les mondes chargés par le serveur. À spécificité égale
et chez le même détenteur, une entrée limitée au monde du joueur l'emporte sur la même entrée sans monde,
comme un nœud contextuel dans LuckPerms : un grade qui autorise `/home` partout et le refuse dans le Nether
le refuse là-bas. Le détenteur passe toujours d'abord : un grade plus lourd, ou le nœud propre du joueur,
décide face au nœud de monde d'un grade plus léger. L'arbre de commandes d'un joueur est renvoyé quand il
change de monde. Une entrée limitée à un monde est permanente : donnez une durée ou un monde, pas les deux.
Parents, refus et préfixes valent partout.

### Tracks

Un track est une échelle de grades, du plus bas au plus haut, comme un track LuckPerms. Il n'accorde rien
par lui-même : promouvoir un joueur remplace le grade sur lequel il se tient par le suivant, en un seul
changement.

| Commande | Description |
|---|---|
| `/customperm track create <track>` | Crée un track vide. |
| `/customperm track delete <track>` | Le supprime ; ses grades et ceux qui les détiennent ne bougent pas. |
| `/customperm track append <track> <grade>` | Ajoute un grade comme nouveau cran du haut. |
| `/customperm track insert <track> <grade> <position>` | Place un grade à un cran, 1 étant le plus bas. |
| `/customperm track remove <track> <grade>` | Retire un grade du track ; les joueurs qui le détiennent le gardent. |
| `/customperm track promote <joueur> <track>` | Un cran plus haut ; un joueur sur aucun cran reçoit le premier. |
| `/customperm track demote <joueur> <track>` | Un cran plus bas ; depuis le premier cran, hors du track. |
| `/customperm track list [track]` | Affiche les tracks et leurs crans. |

Elles demandent `customperm.manage.grades`, comme les grades, et sont refusées quand LuckPerms est actif, ses
propres tracks décidant alors. Seuls les grades qu'un joueur détient partout comptent comme crans. Un joueur
qui détient deux grades du même track est refusé plutôt que deviné : désassignez-en un d'abord. Le grade
quitté emporte son expiration, et celui reçu est permanent. Supprimer un grade le retire de tous les tracks.
Un grade peut figurer sur plusieurs tracks.

### Préfixes et suffixes de chat

| Commande | Description |
|---|---|
| `/customperm grade prefix <grade> [texte]` | Définit le préfixe d'un grade ; sans texte, l'efface. |
| `/customperm grade suffix <grade> [texte]` | Pareil pour le suffixe. |
| `/customperm user prefix <joueur> [texte]` | Définit le préfixe propre d'un joueur, au-dessus de tous ses grades. |
| `/customperm user suffix <joueur> [texte]` | Pareil pour le suffixe. |
| `/customperm names` | Dit si les noms sont décorés, comment, et depuis quel backend. |
| `/customperm names on\|off` | Décore les noms avec leur préfixe et leur suffixe, ou arrête. |
| `/customperm names format <format>` | Comment le nom est construit, `{prefix}{name}{suffix}` par défaut ; `{name}` est obligatoire. |

Les préfixes demandent `customperm.manage.grades` et sont refusés tant que LuckPerms est actif : ils se
règlent alors avec `/lp` et s'affichent depuis LuckPerms. `names` demande `customperm.manage.config` et
fonctionne avec les deux backends.

Quel préfixe s'affiche : celui du joueur, puis celui du plus lourd de ses grades, puis celui du grade hérité
le plus proche ; un grade refusé n'en donne aucun, et le grade par défaut ne s'applique que si rien de ce que
tient le joueur n'en a. Le texte accepte les codes couleur `&` (`&6`, `&l`, `&r`) et `&#RRGGBB`, 64
caractères au plus.

**Le nom est décoré, jamais le message.** Les messages de chat sont signés : un mod qui en réécrit un fait
marquer le message comme modifié par le client, et un mod qui envoie un message système à la place perd le
signalement et l'indicateur de chat sécurisé. Le nom de l'expéditeur ne fait pas partie de ce qui est signé :
CustomPerm le décore et ne touche à aucun message. Conséquence : le préfixe est sur le nom partout où le jeu
l'affiche (chat, messages de mort et de progrès, `/msg`, `/me`, message de connexion, liste des joueurs),
pas sur le nom au-dessus de la tête, et le `<Nom>` autour reste celui de vanilla. Si un autre mod décore
aussi les noms, les deux s'appliquent l'un dans l'autre : c'est pourquoi c'est désactivé par défaut.

### Migrer depuis LuckPerms

| Commande | Description |
|---|---|
| `/customperm import preview` | Lit LuckPerms et dit ce qu'un import ferait. Ne change rien. |
| `/customperm import preview nocommands` | Pareil, sans exposer les commandes dont les nœuds traduits ont besoin. |
| `/customperm import confirm` | Applique ce qui a été prévisualisé, en ajoutant aux grades de même nom. |
| `/customperm import confirm replace` | Pareil, en vidant d'abord un grade de même nom. |

Demande `customperm.manage.grades`, `customperm.manage.commands` et `customperm.manage.luckperms` ensemble,
puisque l'opération écrit des grades, expose des commandes et lit LuckPerms. Un preview de plus de 10 minutes
est relu plutôt que cru, et l'appliquer l'oublie : un second confirm n'importe pas deux fois.

Ce qui passe : un groupe devient un grade avec son poids, ses parents et les groupes qu'il refuse ; un nœud à
false devient un DENY ; un joueur garde ses groupes, ceux qu'il refuse et ses nœuds propres.
`minecraft.command.<x>` devient `customperm.command.<x>` et `<x>` est exposée avec, faute de quoi le nœud
n'accorderait rien.

Le préfixe et le suffixe passent aussi, un de chaque par groupe ou joueur : s'il y en a plusieurs, celui que
LuckPerms affiche en premier (la priorité la plus haute).

Les entrées temporaires passent avec leur expiration : un nœud, un groupe d'un joueur, un refus. Les entrées
limitées à un seul monde passent avec lui : un nœud sur un groupe ou un joueur, et un groupe d'un joueur.
Les tracks passent avec leurs groupes dans l'ordre ; ajouter garde tel quel un track qui existe déjà ici,
remplacer prend l'ordre de LuckPerms.

Ce qui est laissé derrière, et dit dans le rapport plutôt qu'abandonné en silence : les parents temporaires
d'un groupe et les préfixes temporaires, qui sont posés pour de bon ici ; tout autre contexte (`server=`,
plusieurs mondes), une entrée temporaire limitée à un monde, et les parents d'un groupe, un refus ou un
préfixe limités à un monde ; meta, noms affichés, et les nœuds que d'autres mods lisent sans les déclarer à
NeoForge, que plus rien ici ne lirait. Les nœuds déclarés par les mods sont importés tels quels, sur les
groupes et les joueurs, puisque CustomPerm y répond. Sur les
joueurs, seul ce que CustomPerm sait lire est regardé : leurs groupes, leur préfixe et leur suffixe, leurs
nœuds `customperm`, `minecraft.command` et `*`, et les nœuds déclarés par les mods.

**Tant que LuckPerms est installé, c'est lui qui décide des permissions**, donc ce qui est importé attend :
c'est lisible sur la page Grades, et cela prend le relais le jour où LuckPerms est retiré.

### Migrer vers LuckPerms

Pour le serveur qui a construit ses grades ici et installe LuckPerms plus tard : les grades sont écrits dans
LuckPerms pour continuer de décider.

| Commande | Description |
|---|---|
| `/customperm export preview` | Lit les grades et dit ce qu'un export écrirait. Ne change rien. |
| `/customperm export confirm` | Écrit ce qui a été prévisualisé, en ajoutant à ce que LuckPerms contient déjà. |
| `/customperm export confirm replace` | Pareil, en vidant d'abord les nœuds customperm et les parents de chaque groupe et joueur écrit. |

Demande `customperm.manage.grades` et `customperm.manage.luckperms` ensemble. **Lancez d'abord
`/lp export <fichier>`** : un export écrit dans le stockage de LuckPerms, que rien ici ne peut copier ni
annuler, et un export qui échoue en cours de route laisse LuckPerms à moitié écrit. `/lp import <fichier>`
est le chemin du retour.

Ce qui est écrit, tel quel : un grade devient un groupe avec son poids, ses parents et les groupes qu'il
refuse ; un nœud refusé devient un nœud à false ; un joueur garde ses grades, ceux qu'il refuse et ses
propres nœuds ; le grade par défaut devient un parent du groupe `default` de LuckPerms ; un track devient un
track avec ses grades exportés dans l'ordre, après les joueurs. Rien n'est traduit : sur le backend
LuckPerms, CustomPerm lit `customperm.*` tel quel. Ajouter laisse tel quel un track que LuckPerms a déjà ;
remplacer fixe ses groupes.

Ce qui est écarté, et nommé dans le rapport : un grade dont LuckPerms refuserait ou passerait en minuscules
le nom (il accepte minuscules, chiffres, `_`, `.` et `-`, 36 au plus), et chaque parent, attribution ou
grade par défaut qui le nomme. Ajouter garde ce que LuckPerms contient déjà, poids compris ; là où il pose
un nœud dans l'autre sens, sa valeur est gardée et comptée. Préfixes et suffixes sont écrits avec le poids
du grade comme priorité, et ceux d'un joueur au-dessus de ceux de tout grade. Remplacer ne touche un préfixe
ou un suffixe que là où le grade en définit un, et jamais une meta, une entrée temporaire, un contexte autre
qu'un monde, ni les nœuds des autres mods. Une entrée temporaire est écrite temporaire, et une entrée déjà
expirée n'est pas écrite. Une entrée limitée à un monde est écrite avec le contexte `world` de LuckPerms
(`the_nether` pour un monde vanilla, l'identifiant complet pour un monde moddé), et remplacer vide les nœuds
customperm et les parents limités à un monde en même temps que les globaux.

L'écriture tourne en arrière-plan, groupes avant joueurs, un chargement et une sauvegarde par détenteur, et
dit où elle s'est arrêtée si elle échoue. Un seul export à la fois. Un export qui vous retirerait votre propre
`customperm.admin` ou `customperm.manage.grades` est refusé avant toute écriture. `grades.json` n'est pas
modifié.

Une modification de grade qui vous retirerait votre propre accès à `/customperm` est refusée et annulée : autorisez d'abord `customperm.admin` pour vous-même, ou faites la modification depuis la console.

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
| `/customperm test <player> <node>` | Vérifie si un joueur a un node de permission donné. Retourne `GRANTED` ou `DENIED`, avec la raison : ALLOW ou DENY explicite, ou non défini (accordé aux opérateurs). |
| `/customperm debug <player> <command>` | Rapport détaillé : commande dans le dispatcher ? exposée ? l'op-level passe ? la perm est granted ? le wrapper renvoie quoi ? |
| `/customperm status` | Snapshot global : backend, nb de commandes wrappées, exposées, aliases, grades, alertes admin actives. |
| `/customperm scan [pattern]` | Liste toutes les commandes du dispatcher avec leur état (exposée, alias, mod-interne). Filtre optionnel. |
| `/customperm reload` | Recharge les fichiers de config depuis le disque. |
| `/customperm log admin [nombre]` | Les dernières modifications d'administration (10 par défaut, jusqu'à 100), les refus en rouge. |
| `/customperm log players [nombre]` | Les dernières commandes tapées par les joueurs, quand l'enregistrement est actif. |
| `/customperm log record <true\|false>` | Démarre ou arrête l'enregistrement des commandes des joueurs. Équivaut à `playerCommandLog` dans `settings.json`. |
| `/customperm log mask <true\|false>` | Masque ou garde les arguments des commandes listées dans `maskedCommands`. |
| `/customperm gui [dashboard\|commands\|aliases\|ratelimits\|grades\|logs]` | Ouvre l'interface d'administration en jeu (demande CustomPerm côté client). La lecture demande `customperm.admin`, l'écriture le nœud `customperm.manage.<domaine>` du domaine. |
| `/customperm gui luckperms [groups\|players\|tracks]` | Ouvre l'éditeur LuckPerms en jeu. Uniquement quand LuckPerms est installé ; s'il ne fonctionne pas, la page explique pourquoi. L'écriture demande `customperm.manage.luckperms`. |

---

## Nodes de permission

CustomPerm utilise un schéma de nodes hiérarchique compatible LuckPerms (et son système interne).

| Node | Effet |
|---|---|
| `*` | Wildcard global. Autorisé, il accorde tous les nœuds ; refusé, il refuse tous les nœuds sauf ceux autorisés plus spécifiquement, opérateurs compris. À utiliser avec précaution. |
| `customperm.admin` | Entrée : utiliser `/customperm` et lire toutes les pages de l'interface, plus les alertes admin. Obligatoire, le niveau op ne suffit pas. |
| `customperm.manage.commands` | Exposer, retirer, `preserve`, `gateall`, et la page Commandes. |
| `customperm.manage.aliases` | Créer, modifier et supprimer des alias, et la page Alias. |
| `customperm.manage.ratelimits` | Créer, modifier, activer, désactiver et supprimer des limites, et la page Limites. |
| `customperm.manage.grades` | Grades, leurs nœuds, les assignations et le grade par défaut, et la page Grades. |
| `customperm.manage.logs` | Activer ou désactiver le journal des commandes joueurs et le masquage. |
| `customperm.manage.config` | `/customperm reload` et le bouton de rechargement. |
| `customperm.manage.luckperms` | Écrire via l'éditeur LuckPerms en jeu. |
| `customperm.manage.*` | Tous les domaines ci-dessus. `customperm.*` ajoute `customperm.admin` : l'administrateur complet. |
| `customperm.command.<name>` | Autorise `<name>`, une fois la commande exposée (ou pour toutes les commandes avec `gateAllCommands`). Refusé, il refuse la commande aux opérateurs aussi. Résolu par LuckPerms s'il est installé, sinon par les grades internes. |
| `customperm.command.*` | Wildcard couvrant toutes les commandes exposées (toutes les commandes avec `gateAllCommands`). Avec LuckPerms, c'est son propre moteur de wildcard qui le résout. |
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
  "luckPermsFallbackMode": "deny",
  "gateAllCommands": false,
  "defaultGrade": "",
  "playerCommandLog": false,
  "maskPlayerCommandArguments": true,
  "maskedCommands": ["msg", "tell", "w", "teammsg", "tm", "login", "l", "register", "reg", "changepassword", "changepw"],
  "logRetentionDays": 30,
  "decorateNames": false,
  "nameFormat": "{prefix}{name}{suffix}",
  "answerOtherMods": true
}
```

- `gateAllCommands` (backend interne, `false` par défaut) : `true` fait lire à chaque commande son nœud `customperm.command.<nom>`. Un DENY explicite bloque alors n'importe quelle commande pour les opérateurs aussi, et un ALLOW ouvre n'importe quelle commande : un grade qui a `*` ou `customperm.command.*` obtient toutes les commandes du serveur. Sans effet avec LuckPerms installé, qui contrôle déjà toutes les commandes.
- `defaultGrade` (backend interne, vide par défaut) : un grade appliqué à tous les joueurs, sous leurs propres grades.
- `playerCommandLog` (`false` par défaut) : enregistre chaque commande tapée par les joueurs dans le journal d'activité. Les modifications d'administration sont toujours enregistrées.
- `maskPlayerCommandArguments` (`true` par défaut) et `maskedCommands` : les arguments de ces commandes racines sont stockés sous la forme `[masked]` (`/msg Alex salut` devient `/msg [masked]`). Un préfixe de namespace est ignoré.
- `logRetentionDays` (`30` par défaut) : les fichiers quotidiens plus anciens sont supprimés au démarrage et à chaque changement de jour ; `0` les garde indéfiniment. Fichiers : `<monde>/customperm/logs/admin-AAAA-MM-JJ.jsonl` et `players-AAAA-MM-JJ.jsonl`, un objet JSON par ligne.
- `decorateNames` (`false` par défaut) : met le préfixe et le suffixe de chat de chaque joueur autour de son nom, depuis les grades ou depuis LuckPerms. Le nom est décoré, jamais le message (voir [Préfixes et suffixes de chat](#préfixes-et-suffixes-de-chat)).
- `answerOtherMods` (`true` par défaut) : répondre depuis les grades aux tests de permission que les autres mods font via NeoForge. Lu au démarrage ; voir [Mods qui testent les permissions via NeoForge](#mods-qui-testent-les-permissions-via-neoforge).
- `nameFormat` (`{prefix}{name}{suffix}` par défaut) : comment le nom est construit ; codes `&` permis entre les marqueurs. Un format sans `{name}` est remplacé par celui par défaut, pour qu'un préfixe ne puisse jamais se faire passer pour un joueur.
- `configVersion` : la version du format de réglages avec laquelle ce fichier a été écrit. Une installation neuve est estampillée avec la version actuelle ; un fichier d'une version plus ancienne n'en a pas, ce qui fait écrire les changements dans le log et prévenir chaque opérateur une fois (voir [MIGRATION.md](MIGRATION.md)). N'y touchez pas.

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
      "weight": 10,
      "parents": ["vip"],
      "deniedParents": ["banned"],
      "permissions": ["customperm.command.*", "customperm.alias.*"],
      "deniedPermissions": ["customperm.command.op"]
    }
  },
  "userGrades": {
    "550e8400-e29b-41d4-a716-446655440000": ["vip"],
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": ["staff", "vip"]
  },
  "userDeniedGrades": {
    "550e8400-e29b-41d4-a716-446655440000": ["banned"]
  },
  "userPermissions": {
    "550e8400-e29b-41d4-a716-446655440000": ["customperm.command.weather"]
  },
  "userDeniedPermissions": {
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": ["customperm.command.time"]
  },
  "userGradeExpiries": {
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": { "staff": 1792000000 }
  },
  "tracks": {
    "ranks": ["vip", "staff"]
  },
  "userContexts": {
    "550e8400-e29b-41d4-a716-446655440000": {
      "world=minecraft:the_nether": { "grades": ["staff"], "permissions": [], "deniedPermissions": [] }
    }
  }
}
```

Avec LuckPerms actif, ce fichier est ignoré (les perms passent par LP).

Les entrées temporaires gardent leurs collections et ajoutent à côté de chacune une map de secondes epoch :
`permissionExpiries` et `deniedPermissionExpiries` sur un grade, `userPermissionExpiries`,
`userDeniedPermissionExpiries`, `userGradeExpiries` et `userDeniedGradeExpiries` au niveau racine, joueur
d'abord, puis nœud ou grade. Une entrée absente de ces maps est permanente, et une expiration qui ne nomme
aucune entrée est écartée à la lecture du fichier.

Les entrées limitées à un monde se rangent à côté des autres, sous leur contexte : `contexts` sur un grade
associe un contexte à ses `permissions` et `deniedPermissions`, et `userContexts` au niveau racine associe un
joueur, puis un contexte, aux `grades`, `permissions` et `deniedPermissions` qu'il détient là. Un contexte
s'écrit `clé=valeur`, `world=minecraft:the_nether` ; `world=the_nether` écrit à la main est lu comme le
même. Une clé que cette version ne lit pas est gardée telle quelle et ne correspond à rien, pour qu'un
fichier écrit par une version ultérieure ne soit pas abîmé.

`userPermissions` et `userDeniedPermissions` portent des nœuds pour un joueur seul, au-dessus de tous ses grades. `deniedPermissions` est utilisé uniquement par le backend interne. L'entrée la plus spécifique l'emporte sur l'ensemble de ce que porte le joueur et de ses grades (nœud exact, puis `a.b.*`, puis `a.*`, puis `*`) ; à niveau égal un nœud porté par le joueur l'emporte, puis le grade le plus lourd, puis un DENY entre égaux. `parents` liste les grades dont un grade hérite : leurs entrées s'appliquent là où il ne dit rien d'aussi précis sur le nœud, l'ancêtre le plus proche d'abord, et une chaîne concourt avec les autres grades au poids du grade que le joueur détient réellement. `deniedParents` et `userDeniedGrades` sortent un grade de la résolution, respectivement pour la chaîne de ce grade et pour ce joueur partout, grade par défaut compris ; un refus ne transforme jamais ce que le grade refusé autorise en refus. `weight`, `parents`, `deniedParents` et `userDeniedGrades` sont facultatifs et vides s'ils sont absents, ce qui laisse la règle du DENY comme seul départage, comme avant l'existence de ces champs. Ce que porte le joueur et ses grades décident d'abord ; seul un nœud qu'aucun d'eux ne mentionne passe au grade par défaut.

`tracks` associe un track à ses grades, du plus bas au plus haut. Il ne fait que nommer des grades, d'où sa
place dans ce fichier : supprimer un grade le retire de tous les tracks dans la même écriture. Il ne décide
rien lors d'un test de permission.

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

Le wildcard ne couvre que les commandes **exposées**. Les autres commandes vanilla restent op-only, sauf si `gateAllCommands` est actif.

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

### Restreindre les opérateurs

Administrer CustomPerm demande un nœud accordé, jamais le niveau op seul : `/customperm` et l'interface sont masqués à un opérateur qui n'a pas `customperm.admin`, niveau 4 compris, et chaque modification demande le nœud `customperm.manage.<domaine>` de son domaine. Seuls la console, et l'hôte d'un monde sans console, en sont dispensés.

Pour les commandes que CustomPerm contrôle pour les joueurs ordinaires, être op compte encore là où rien ne dit le contraire. Un DENY explicite s'applique aux opérateurs, propriétaires compris, sur les deux backends : `customperm.command.<nom>` refusé refuse la commande, `customperm.alias.<nom>` refusé l'alias, `customperm.admin` refusé (ou `*`, ou `customperm.*`) retire `/customperm` et l'interface d'administration. La console et les blocs de commande ne sont jamais soumis à un nœud.

Avec LuckPerms, refusez les nœuds comme d'habitude (`/lp group default permission set * false`, puis autorisez ce dont l'équipe a besoin) : LuckPerms contrôle déjà toutes les commandes, et CustomPerm respecte désormais un `false` sur les commandes qu'il expose au lieu de laisser passer les opérateurs.

Sans LuckPerms, pour protéger le serveur contre un joueur mis op par erreur :

```
customperm grade create everyone
customperm grade adddeny everyone *
customperm grade addperm everyone customperm.command.list
customperm grade create owner
customperm grade addperm owner *
customperm grade assign <vous> owner
customperm grade setdefault everyone
customperm command gateall true
```

`owner` détient `*`, ce qui inclut `customperm.admin` et tous les `customperm.manage.*` : c'est ce qui vous garde capable d'administrer le mod en jeu.

Chaque joueur suit désormais `everyone` sous ses propres grades, et chaque commande lit son nœud : un op sans grade à lui peut lancer `/list` et rien d'autre, `/customperm` compris. Lancez ces commandes depuis la console, ou assignez-vous `owner` avant `setdefault` : en jeu, une modification qui vous fermerait `/customperm` est refusée.

### Wildcards à manier avec précaution

`customperm.command.*` couvre **toutes** les commandes exposées. Si vous exposez `/op` (déconseillé) ou `/whitelist`, le wildcard les couvre aussi. Avec `gateAllCommands`, lui et `*` couvrent toutes les commandes du serveur. **Préférez** des nodes explicites pour les commandes sensibles.

### Journal des commandes joueurs et données personnelles

L'onglet joueurs enregistre quel joueur a lancé quelle commande et quand : c'est une donnée personnelle. Il est désactivé par défaut. Avant de l'activer, prévenez vos joueurs, gardez la rétention aussi courte que nécessaire, et laissez le masquage actif sauf raison de lire les messages privés. Les entrées déjà enregistrées ne sont pas réécrites quand le masquage change. Toute personne qui administre CustomPerm peut lire le journal, et les fichiers sont dans le dossier du monde, lisibles par qui a accès aux fichiers du serveur.

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

Retourne `GRANTED` (vert) ou `DENIED` (rouge) avec le backend en clair et la raison : ALLOW ou DENY explicite, ou nœud non défini, que seuls les opérateurs passent.

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
| Résolution de permissions | Deny par défaut, ALLOW direct, wildcard ALLOW, wildcard global, DENY explicite, entrée la plus spécifique gagnante entre grades, poids de grade départageant quel que soit l'ordre d'assignation, DENY à poids égaux, poids ne battant jamais la précision, nœud du joueur au-dessus de ses grades sans battre un nœud plus spécifique, héritage avec l'ancêtre le plus proche qui décide, diamants, cycles et chaîne concourant au poids du grade détenu, grade refusé par un autre grade ou par un joueur rendu inatteignable sans devenir un refus, `*` refusé avec autorisations explicites, couche du grade par défaut. Entrées limitées à un monde : lecture et refus d'un contexte, comparaison sans allocation, nœud de monde l'emportant sur le nœud global du même détenteur sans battre un nœud plus spécifique ni un détenteur plus lourd, grade tenu dans un seul monde, héritage et grade par défaut portant des nœuds de monde, et graphies écrites à la main fusionnées. |
| Grades internes | Création/listage/suppression de grades, assignation/désassignation joueurs, prévention des doublons, cascade lors de la suppression d'un grade. |
| Exposition de commandes | Ajout/retrait/listage de commandes exposées, changements idempotents, commandes non exposées refusées par CustomPerm. |
| Config aliases | Création, overwrite, suppression, listage, ordre des aliases, parsing par `;`, steps vides ignorés. |
| Exécution aliases | Forme du node, exécution op level 4, ordre, continuation après erreur, limite des cycles récursifs, remplacement live des steps. |
| Config manager | Lectures atomiques du snapshot, sauvegardes atomiques sérialisées, rejet de reload concurrent, rollback après JSON invalide, création et rotation des backups. |
| Compatibilité config | Fichiers manquants, fichiers `{}`, collections explicitement `null`, champs futurs inconnus, configs partielles. |
| Sélection LuckPerms | Backend interne sans LP, parsing de versions, version minimale, sélection stable du backend. |
| GameTests, deux modes | Exposition et retrait de commande avec un joueur non-op, préservation des ops, `/customperm` refusé aux non-ops, reconnexion, aliases exécutés en op 4 par les seuls détenteurs du node et incapables d'atteindre `/customperm`, édition des steps, gardes de récursion et de shadowing, reload d'un `aliases.json` modifié à la main, limites de débit (message de refus, compteur partagé par racine, isolation par joueur, console exemptée, expiration de fenêtre, reconnexion, reloads répétés, suppression de règle, aliases), reload tout-ou-rien, refus du reload concurrent, changements non sauvegardés après un reload en échec, entrées `null`, repush du command tree au reload, alertes admin dans le chat des ops, paquets du GUI et de l'éditeur refusés aux non-ops, sorties de diagnostic, autocomplétion de chaque argument de `/customperm` et aucune suggestion pour un non-op, opérateurs refusés sur une commande exposée, un alias, `/customperm` ou un domaine de l'interface par un DENY explicite pendant que la console garde l'accès, `*` refusé bloquant tout sauf les autorisations explicites, modifications d'administration par commande et par l'interface enregistrées avec les refus, commandes des joueurs enregistrées seulement si actif et masquées par défaut, fichiers sur disque, rechargement depuis le disque ignorant les lignes illisibles, rétention, boutons de la page Journaux soumis à leur nœud, modifications `/lp` enregistrées (mode LuckPerms), opérateurs sans les nœuds refusés sur `/customperm` et l'interface pendant que la console garde l'accès, chaque domaine exigeant son propre nœud `customperm.manage` pour la commande comme pour la page, les nœuds seuls n'ouvrant rien à un non-op, avertissement de mise à jour donné une fois pour une configuration écrite avant la 1.1.0 puis configuration estampillée. |
| GameTests, mode interne | Commandes de grade, union des grades, entrée la plus spécifique gagnante, poids de grade départageant, nœuds portés par un joueur, page Joueurs et son garde anti-verrouillage, parents de grade avec héritage appliqué à chaud et cycles refusés, refus appliqués à chaud et contradictions répondues, toutes les formes de wildcard, éditeur sans LuckPerms, `gateAllCommands` et `*` autorisé, grade par défaut restreignant un op accidentel, auto-verrouillage refusé par commande et par l'interface. Préfixes de chat : codes `&` et format du nom, nom utilisé par le chat décoré depuis les grades avec le grade le plus lourd et celui du joueur qui l'emportent, liste des joueurs, interrupteur et format appliqués aussitôt, et onglets Chat via l'interface. Entrées temporaires : durées lues et refusées, accès qui expire avant tout balayage, balayage qui nettoie le fichier, journalise et renvoie l'arbre, grade tenu et refus qui expirent, et durées via l'interface avec le temps restant relu. Entrées limitées à un monde : contextes lus et refusés, nœud accordé dans le Nether seulement, arbre de commandes renvoyé et verdict qui change quand un vrai joueur se téléporte d'un monde à l'autre, grade et DENY propre à un joueur tenus dans un seul monde, retrait par monde, et grade supprimé qui ne laisse aucune attribution par monde. Tracks : construction, vrai joueur promu et rétrogradé avec l'arbre de commandes qui suit, cran temporaire quitté avec son expiration, plusieurs crans et grade refusé refusés, grade supprimé qui quitte l'échelle, et promotion et rétrogradation depuis la page Joueurs. Permissions des autres mods : CustomPerm sélectionné comme handler, nœuds déclarés sous un espace de noms étranger répondus avec leur valeur par défaut, un DENY exact, un ALLOW par wildcard, un nœud nombre et un test hors ligne. |
| GameTests, mode LuckPerms | Import depuis une source avec un exemplaire de chaque cas : ce qui passe, ce qui est écarté avec sa raison, la lecture qui n'écrit rien, ce qui atterrit en config, les trois nœuds demandés ensemble, et un preview déjà consommé refusé. Export dans un vrai LuckPerms : groupes, poids, parents, refus, groupe par défaut et nœuds d'un joueur écrits, grade renommé refusé, l'ajout qui garde les valeurs et le préfixe de LuckPerms, le remplacement qui ne vide que les nœuds customperm, la progression par détenteur, les deux nœuds demandés ensemble, la garde anti-verrouillage, et un preview déjà consommé refusé. Éditeur en jeu face à un vrai LuckPerms : groupes, nodes avec contextes et expiration, héritage, meta, prefix et suffix, poids, nom d'affichage, groupes et groupe principal d'un joueur, tracks, promote et demote, verrouillage des écritures par node et niveau, limites d'édition et de sync ; command tree renvoyé après un changement LuckPerms ; repli `deny` et `internal` quand LuckPerms devient indisponible. Un préfixe LuckPerms qui atteint le nom sans reconnexion ; préfixes transportés par l'import et l'export. Nœuds et groupes temporaires importés avec leur expiration et exportés temporaires. Nœuds et grade d'un joueur limités à un monde importés et exportés avec le contexte `world` de LuckPerms, nœud `server=` laissé derrière sans exposer sa commande. Un track importé et exporté avec ses groupes dans l'ordre. Un nœud déclaré par un autre mod importé depuis un groupe et un joueur, un nœud non déclaré laissé derrière, et LuckPerms qui garde le handler de permissions. |
| Performance | `PermissionResolver.resolve()` et lecture concurrente du snapshot config via JMH, dont un test fait depuis un monde sans aucune entrée limitée à un monde (le chemin de tous les serveurs) et un que tranche un nœud de monde. |

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
1. Joueur ou nœud null                                  => UNSET
2. Parmi ce que porte le joueur (ses propres nœuds) et les
   grades qu'il détient avec tout ce dont ces grades héritent,
   l'entrée qui couvre le nœud le plus précisément gagne
   (exact, a.b.*, a.*, *)
   (un grade que le détenteur refuse n'est jamais atteint,
   quel que soit le chemin qui y menait)
   - égalité dans une chaîne => le détenteur le plus proche décide
   - égalité de précision    => un nœud du joueur, sinon le grade le plus lourd
   - égalité de rang         => le DENY gagne
3. Nœud que rien de tout cela ne mentionne => même règle sur le grade par défaut
4. Rien ne correspond => UNSET, l'appelant tranche selon le niveau d'op
```

### Re-synchronisation

Quand une perm change via LP, l'event `UserDataRecalculateEvent` est captée et `Commands.sendCommands(player)` est appelé pour le joueur affecté. Le tree client est mis à jour sans déconnexion.

Pour les changements via `/customperm` (mode interne), `sendCommands` est appelé directement après la modification.

Quand un joueur change de monde, l'arbre est renvoyé : toujours avec LuckPerms, dont les nœuds peuvent
dépendre du monde, et sur le backend interne seulement tant qu'une entrée est limitée à un monde. Une
réapparition n'a besoin de rien, vanilla renvoie l'arbre à ce moment-là.

### Aliases

Enregistrés comme des `Commands.literal(name).requires(...).executes(...)`. Le `executes` normalise chaque step, retire le `/` initial éventuel, puis exécute la step via le node de commande original lorsque CustomPerm a wrappé cette commande, avec une `CommandSourceStack` ayant `permissionLevel = 4`. Les steps en échec sont signalées et journalisées, mais les steps suivantes continuent de s'exécuter.

---

## Compatibilité avec d'autres mods

### Mods qui ajoutent des commandes

**Compatible automatiquement en mode interne.** Les commandes enregistrées au `RegisterCommandsEvent` standard sont traitées en priorité `LOWEST`, puis une passe de réparation est exécutée au démarrage du serveur. Aucune intégration dédiée n'est normalement nécessaire.

Exposez une commande de mod tiers avec `customperm command add <addon_command>`, puis accordez `customperm.command.<addon_command>` — via `/lp` si LuckPerms est installé, sinon via un grade interne. Un alias CustomPerm limité reste une option quand vous voulez une portée plus fine (par sous-commande). Pour vérifier la détection : `customperm scan <pattern>`.

### Mods qui testent les permissions via NeoForge

Les mods qui déclarent leurs nœuds via l'API de permissions de NeoForge (`PermissionAPI`) interrogent un
seul handler, celui que nomme `permissionHandler` dans `config/neoforge-server.toml`. CustomPerm propose
`customperm:handler`, qui répond à ces tests depuis les grades : un tel nœud s'accorde ou se refuse comme
n'importe quel autre, `/customperm grade addperm vip unmod.fonction`, wildcards compris (`unmod.*`). La
complétion liste les nœuds déclarés par les mods.

- **Sans LuckPerms**, CustomPerm sélectionne lui-même son handler au démarrage, tant que `permissionHandler`
  vaut encore la valeur par défaut de NeoForge et que `answerOtherMods` dans `settings.json` vaut `true` (par
  défaut). Le changement est fait en mémoire, le fichier garde sa valeur, donc passer `answerOtherMods` à
  `false` prend effet au démarrage suivant. Une valeur que vous avez choisie dans ce fichier n'est jamais
  remplacée.
- **Avec LuckPerms**, LuckPerms est le handler et CustomPerm ne le prend jamais : il n'y en a qu'un, et deux
  mods qui le réécrivent seraient impossibles à diagnostiquer.
- Le log de démarrage dit quel handler répond et pourquoi.

Ce qui est répondu : un nœud oui/non vaut `true` pour un ALLOW explicite, `false` pour un DENY, et quand rien
ici ne le mentionne, la valeur par défaut que son mod lui a donnée (souvent un test d'opérateur), jamais un
refus. Un nœud qui porte un nombre ou un texte n'a pas de stockage ici et répond toujours sa valeur par
défaut. Un joueur hors ligne est résolu depuis les grades lui aussi.

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
- **Contextes LP au-delà des mondes non testés** : LuckPerms résout ses propres contextes via `getCachedData()`. Les nœuds par monde sont couverts, l'arbre de commandes étant renvoyé à chaque changement de monde ; les contextes par serveur et personnalisés passent tels quels mais ne sont pas testés.
- **L'interface d'administration demande CustomPerm côté client** : sans lui, l'administration reste entièrement en commandes.
- **L'éditeur LuckPerms en jeu n'est pas le web editor** : il couvre groupes, joueurs, tracks, nœuds, meta et chat meta, mais pas les opérations en masse, la recherche de nœud sur tous les détenteurs, ni l'historique d'annulation du web editor. Pour cela, `/lp editor` reste l'outil.
- **Les commandes raccourcis ont leurs propres règles** : certaines commandes sont des raccourcis qui redirigent vers une autre (`/tp` vers `/teleport`, `/msg` et `/w` vers `/tell`, `/xp` vers `/experience`). Chaque écriture est exposée et limitée sous le nom tapé par le joueur : `tp` gouverne `/tp`, `teleport` gouverne `/teleport`. Exposer l'une n'ouvre pas l'autre ; configurez les deux si les deux doivent être disponibles.
- **Les commandes joueur exigent un joueur connu du serveur** : `/customperm grade assign|unassign`, `/customperm user` et l'interface acceptent les joueurs en ligne ou déjà venus sur le serveur ; ils n'interrogent jamais le service de session, donc un pseudo jamais venu ne peut pas être assigné à l'avance. Dans ce cas, éditez `userGrades`, `userPermissions` ou `userDeniedPermissions` dans `grades.json` (UUID en clé) puis `/customperm reload`.
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
