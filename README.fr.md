# CustomPerm

> Système de permissions granulaires pour Minecraft NeoForge — accordez des commandes vanilla individuelles à des joueurs non-op, avec ou sans LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-source--available%20(All%20Rights%20Reserved)-blue.svg)](LICENSE)
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
- [Mode cluster (plusieurs serveurs)](#mode-cluster-plusieurs-serveurs)
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
- **Entrées temporaires** : un nœud, un grade tenu par un joueur, un parent de grade ou un refus peut durer un temps donné : `/customperm grade assign Steve vip 30d`. Une entrée expirée cesse de compter aussitôt, puis un balayage la retire et renvoie l'arbre de commandes. Les pages Grades et Joueurs acceptent une durée et affichent le temps restant.
- **Permissions des autres mods** : les nœuds que d'autres mods déclarent via l'API de permissions de NeoForge sont répondus depuis les grades, donc `/customperm grade addperm vip unmod.fonction` fonctionne pour eux aussi. CustomPerm devient de lui-même le handler de permissions de NeoForge seulement sans LuckPerms, et ne remplace jamais un handler choisi par un admin. Les mods qui appellent LuckPerms par son nom à la place sont listés par `/customperm modcheck`.
- **Tracks** : une échelle ordonnée de grades, pour que promouvoir et rétrograder fassent monter ou descendre un joueur d'un cran : `/customperm track promote Steve staff`, ou l'onglet Tracks de la page Joueurs. Un track n'accorde rien lui-même ; c'est le confort qu'attend un serveur qui vient de LuckPerms.
- **Entrées par monde** : un nœud sur un grade ou un joueur, ou un grade tenu par un joueur, peut ne valoir que dans un monde : `/customperm grade adddeny member customperm.command.home world=the_nether`. Elle l'emporte sur l'entrée sans monde du même détenteur, et l'arbre de commandes suit le joueur à travers les portails. Les pages Grades et Joueurs acceptent aussi un monde.
- **Mode cluster** : plusieurs serveurs sans LuckPerms partagent grades, commandes, alias, limites et journal d'activité via une base MariaDB ou MySQL, que CustomPerm atteint avec sa propre connexion ou via [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib), dans les deux cas avec un pilote de base fourni par un autre mod ; un changement fait sur l'un s'applique sur tous en deux secondes environ. Désactivé par défaut. Voir [Mode cluster](#mode-cluster-plusieurs-serveurs).
- **Préfixes et suffixes de chat** : un grade, ou un joueur, porte des préfixes et des suffixes autour de son nom dans le chat et partout où le jeu l'affiche, chacun avec une priorité et, si voulu, une durée, comme LuckPerms : la priorité la plus haute s'affiche, ou plusieurs à la suite. Avec LuckPerms, ce sont les préfixes que LuckPerms stocke qui s'affichent. Le nom est décoré, jamais le message, donc le chat reste signé et signalable. Désactivé tant qu'on n'a pas fait `/customperm names on`.
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
- **Côté serveur uniquement** : aucun mod n'est requis côté client pour les fonctionnalités de base. Un client vanilla (ou sans CustomPerm) se connecte sans problème à un serveur CustomPerm : les canaux réseau de l'interface d'administration sont enregistrés en `optional()`, ils ne bloquent jamais la connexion. Cela vaut pour CustomPerm seul ; un mod compagnon ajouté pour le mode cluster peut avoir ses propres exigences, et c'est le cas d'Arcadia Lib — voir [Mode cluster](#mode-cluster-plusieurs-serveurs).
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
| Commandes | Toutes les commandes racines du serveur avec recherche (Ctrl+F) et filtre « exposées », badges pour les alias, les limites et les commandes absentes du serveur ; exposer, masquer (avec confirmation), et l'interrupteur « garder l'exigence d'origine » (`preserveOriginalRequires`) ; en cluster, le bouton maison d'une commande exposée choisit les membres sur lesquels elle est actif, et un badge `OFF` signale une commande limitée à d'autres membres |
| Alias | Tous les alias avec recherche, badges pour les commandes masquées et les limites ; créer un alias avec sa première étape ; par alias : ajouter, remplacer, monter ou descendre et retirer des étapes, supprimer l'alias (avec confirmation) ; en cluster, un onglet **Servers** choisit les membres sur lesquels l'alias est actif, et un badge `OFF` signale un alias qui n'existe pas sur ce serveur |
| Limites d'exécution | Toutes les règles avec leurs valeurs et badges (désactivée, cible ni exposée ni alias) ; ajouter une limite, changer usages et fenêtre, activer ou désactiver, choisir quand l'historique est écrit (sauvegarde du monde ou à chaque usage), supprimer (avec confirmation) ; les commandes exposées et alias sans limite sont listés et remplissent le formulaire en un clic ; en cluster, le bouton maison à côté de Save choisit les membres sur lesquels la règle est actif, et le badge `OFF` signale aussi une règle non appliquée sur ce serveur |
| LuckPerms | Uniquement quand LuckPerms est installé : pas d'entrée de navigation sinon, et `/customperm gui luckperms` explique pourquoi. Installé mais pas démarré (solo, échec au démarrage), la page affiche une bannière au lieu de l'éditeur. **Groupes** : créer, supprimer, nœuds de permission allow/deny avec contextes et durée, parents, poids, nom affiché, préfixe, suffixe, meta. **Joueurs** : joueurs connectés et tout joueur trouvé par pseudo exact, leurs nœuds, groupes avec durée, groupe principal, promotion et rétrogradation sur un track, préfixe, suffixe, meta. **Tracks** : créer, supprimer, ajouter, insérer à une position, retirer un groupe. Les écritures passent par l'API LuckPerms côté serveur, protégées par `customperm.manage.luckperms` |
| Grades | Toujours accessible, pour pouvoir lire le repli quand LuckPerms fonctionne ou tombe. Une bannière indique quand les grades ne décident pas des permissions ; avec LuckPerms actif la page est en lecture seule, comme les commandes de grade : grades avec recherche et création, triés par poids ; par grade, trois onglets : nœuds ALLOW et DENY, grades dont il hérite et ceux qu'il refuse, et les joueurs qui le détiennent à côté de ceux qui le refusent, avec leur état en ligne, attribués par pseudo avec complétion, y compris hors ligne s'ils sont déjà venus sur le serveur ; suppression d'un grade (avec confirmation). Un quatrième onglet, **Chat**, liste les préfixes et suffixes du grade avec leur priorité et leur temps restant, en ajoute un avec un texte, une priorité et une durée facultative, retire celui sélectionné, montre un aperçu de la ligne de chat, et porte les interrupteurs de décoration des noms et d'empilement (`customperm.manage.config`). Une case de durée à côté des champs nœud et joueur accorde pour un temps limité, et les lignes affichent le temps restant. Une case monde à côté limite un nœud ou une attribution à un monde (`the_nether`), affiché sur la ligne ; les onglets Parents et Chat en ont une aussi. Un cinquième onglet, **Meta**, liste et fixe la meta du grade, avec une durée et un contexte |
| Joueurs | Nœuds portés par un joueur plutôt que par un grade : tous les joueurs qui détiennent quelque chose en propre plus tous ceux connectés, avec recherche ; par joueur, ses nœuds ALLOW et DENY et les grades qu'il détient, en lecture seule ici. Un joueur qui ne détient encore rien s'atteint en tapant son pseudo. Écrire demande `customperm.manage.grades`, comme la page Grades. Un onglet **Chat** règle de la même façon les préfixes et suffixes que le joueur porte lui-même. Un onglet **Meta** fixe la meta que le joueur porte lui-même. Un onglet **Tracks** montre chaque track avec le cran du joueur et le promeut ou le rétrograde d'un cran. Le champ nœud accepte aussi une durée et un monde, et les grades tenus dans un seul monde sont listés avec lui |
| Import | Uniquement quand LuckPerms est installé : récupère ses groupes, joueurs et nœuds, en deux temps. Read LuckPerms répond par le rapport et ne change rien, Import applique ce rapport et rien d'autre, après une sauvegarde. Deux options : exposer les commandes dont les nœuds traduits ont besoin, et ajouter aux grades de même nom ou les remplacer. Demande les trois nœuds d'écriture ensemble. Un second onglet, **To LuckPerms**, exporte les grades dans l'autre sens : Read the grades, puis Export, qui reste désactivé tant que l'admin n'a pas indiqué que LuckPerms est sauvegardé ; la page suit la progression pendant l'écriture. Demande `customperm.manage.grades` et `customperm.manage.luckperms` |
| Journaux | Deux onglets, du plus récent au plus ancien, avec recherche. **Admin** : chaque modification faite par les commandes `/customperm`, l'interface et l'éditeur LuckPerms, et les modifications que LuckPerms enregistre lui-même (`/lp`, éditeur web) : quand, qui, d'où, quoi, et le résultat ou le refus. **Joueurs** : chaque commande tapée par les joueurs, seulement quand l'enregistrement est actif (désactivé par défaut) ; arguments des commandes de message privé et de mot de passe masqués sauf si le masquage est désactivé. Changer l'enregistrement et le masquage demande `customperm.manage.logs` |
| Aide | Chaque fonctionnalité expliquée là où on s'en sert : ce qu'elle fait, quand l'utiliser, comment, et les commandes texte derrière. Sujets à gauche, avec recherche (Ctrl+F cherche aussi dans leur texte), le sujet à droite. En anglais, comme le reste de l'interface (entrée « Help »). Lisible par quiconque peut ouvrir l'interface |

**Complétion.** Chaque champ qui désigne quelque chose d'existant le propose pendant la saisie : nœuds de permission, grades, joueurs déjà venus, contextes (`world=`, `gamemode=`, les contextes statiques et `server=` pour chaque membre du cluster), durées, commandes et alias, le premier mot d'une étape d'alias puis ses `${arguments}` et les noms de joueurs, clés de méta et valeurs déjà utilisées pour la clé saisie, textes de préfixe et de suffixe en usage, et dans la recherche les noms listés sur la page. La fin du meilleur candidat s'affiche après le curseur et une liste s'ouvre sous le champ : **Tab** prend le candidat en surbrillance, **Haut** et **Bas** parcourent la liste (Bas l'ouvre aussi sur un champ vide), **Entrée** valide ce qui est tapé sauf si la liste a été parcourue, **Échap** la ferme, et un clic prend un candidat. La recherche ignore la casse et trouve aussi un mot à l'intérieur d'un nœud (`fly` trouve `essentials.fly`). Les champs qui créent un nom (nouveau grade, nouvel alias, nouvel argument, nom affiché, surnom) et les champs numériques ne proposent rien. Le serveur envoie ce vocabulaire avec la première page, puis seulement quand il a changé.

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
| `/customperm command servers <name> [serveurs\|here\|all]` | En cluster, les membres sur lesquels une commande exposée est actif, noms séparés par des espaces ou des virgules ; `here` désigne ce serveur, `all` tous les membres (par défaut). Sans liste, l'affiche. Voir [Mode cluster](#mode-cluster-plusieurs-serveurs). |
| `/customperm command list` | Liste les commandes exposées, avec leur liste de serveurs quand elles en ont une. |

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
| `/customperm alias servers <name> [serveurs\|here\|all]` | En cluster, les membres sur lesquels un alias est actif, noms séparés par des espaces ou des virgules ; `here` désigne ce serveur, `all` tous les membres (par défaut). Sans liste, l'affiche. Voir [Mode cluster](#mode-cluster-plusieurs-serveurs). |
| `/customperm alias list` | Liste tous les aliases définis, avec leur liste de serveurs quand ils en ont une. |
| `/customperm alias param add <alias> <name> <player\|integer\|word\|text>` | Déclare un argument, à la fin de la liste. Un step l'atteint avec `${name}`. |
| `/customperm alias param remove <alias> <name>` | Retire un argument. |
| `/customperm alias param move <alias> <name> <index>` | Déplace un argument à une autre position (0-based). |
| `/customperm alias param optional <alias> <name> <true\|false>` | Si l'argument peut être omis. |
| `/customperm alias param default <alias> <name> [valeur]` | Ce que substitue un argument omis ; sans valeur, rien. |
| `/customperm alias param range <alias> <name> <min> <max>\|clear` | Les bornes acceptées par un argument entier. |
| `/customperm alias param choices <alias> <name> [a,b,c]` | Ce que suggère un argument word, et les seules valeurs qu'il accepte alors. |
| `/customperm alias param selectors <alias> <name> <true\|false>` | Si un argument text peut porter un sélecteur d'entités. Désactivé par défaut. |
| `/customperm alias params <alias>` | Affiche les arguments, dans l'ordre où ils se tapent. |

### Grades (système interne, sans LuckPerms)

Ces commandes sont **bloquées si LuckPerms est actif** — utilisez `/lp` à la place.
Elles gèrent les nodes ALLOW. Les nodes DENY internes sont stockés dans `grades.json` via `deniedPermissions`.

| Commande | Effet |
|---|---|
| `/customperm grade create <name>` | Crée un grade vide. |
| `/customperm grade delete <name>` | Supprime un grade et le désassigne de tous les joueurs. |
| `/customperm grade addperm <grade> <node> [durée] [world=<dim>]` | Ajoute une perm au grade, pour de bon, pour une durée comme `30d`, ou dans un monde comme `world=the_nether`. |
| `/customperm grade removeperm <grade> <node> [world=<dim>]` | Retire une perm du grade, celle limitée à ce monde s'il est donné. |
| `/customperm grade adddeny <grade> <node> [durée] [world=<dim>]` | Ajoute un nœud DENY : refusé, opérateurs compris, sauf si un nœud plus spécifique l'autorise. |
| `/customperm grade removedeny <grade> <node> [world=<dim>]` | Retire un nœud DENY. |
| `/customperm grade weight <grade> <poids>` | Définit le poids de départage, 0 par défaut, négatif accepté. |
| `/customperm grade displayname <grade> [set <texte> \| clear]` | Affiche, définit ou efface le nom que les listes et la page Grades montrent pour le grade, `Très Important (vip)`. Affichage seulement : commandes, fichiers et autres grades continuent de le nommer `vip`. 48 caractères au plus, sur une ligne. |
| `/customperm grade parent add <grade> <parent> [durée] [world=<dim>]` | Fait hériter le grade d'un autre, pour de bon, pour une durée ou dans un monde ; un cycle est refusé, dans n'importe quel monde. |
| `/customperm grade parent remove <grade> <parent>` | Cesse d'en hériter. |
| `/customperm grade parent adddeny <grade> <parent> [durée] [world=<dim>]` | Refuse un grade partout où celui-ci en hériterait, pour de bon, pour une durée ou dans un monde. |
| `/customperm grade parent removedeny <grade> <parent>` | Cesse de le refuser. |
| `/customperm grade parent list <grade>` | Affiche ce dont le grade hérite et ce qu'il refuse. |
| `/customperm grade assign <player> <grade> [durée] [world=<dim>]` | Assigne le grade à un joueur, en ligne ou hors ligne s'il est déjà venu sur le serveur ; avec un monde, il ne vaut que là. |
| `/customperm grade unassign <player> <grade> [world=<dim>]` | Désassigne, en ligne ou hors ligne. |
| `/customperm grade setdefault <grade>` | Applique le grade à tous les joueurs, sous leurs propres grades. |
| `/customperm grade cleardefault` | Plus aucun grade ne s'applique à tous les joueurs. |
| `/customperm grade list` | Liste les grades définis, du plus lourd au plus léger, chacun sous son nom d'affichage s'il en a un. |

Nœuds portés par un joueur, au-dessus de ses grades :

| Commande | Description |
|---|---|
| `/customperm user addperm <joueur> <node> [durée] [world=<dim>]` | Ajoute un nœud ALLOW à ce joueur seul. |
| `/customperm user removeperm <joueur> <node> [world=<dim>]` | Le retire. |
| `/customperm user adddeny <joueur> <node> [durée] [world=<dim>]` | Ajoute un nœud DENY à ce joueur seul. |
| `/customperm user removedeny <joueur> <node> [world=<dim>]` | Le retire. |
| `/customperm user denygrade <joueur> <grade> [durée] [world=<dim>]` | Fait refuser un grade à un joueur, partout où l'un des siens l'apporterait, ou dans un seul monde. |
| `/customperm user undenygrade <joueur> <grade> [world=<dim>]` | Cesse de le refuser. |
| `/customperm user list <joueur>` | Affiche les grades détenus, ceux refusés, et les nœuds portés, avec le temps restant des entrées temporaires et, par monde, ce qui ne vaut que là. |

**Durées.** `w`, `d`, `h`, `m` et `s`, seuls ou combinés : `30d`, `2h`, `1d12h`, `1w`, dix ans au plus.
Sans durée, une entrée est permanente. Ajouter avec une durée une entrée déjà présente la rend temporaire à
partir de maintenant, et sans durée la rend permanente : c'est la dernière chose dite qui compte. Une entrée
expirée cesse de compter aussitôt, et l'entrée en dessous répond (un `a.b.c` expiré laisse `a.b.*` décider).
Chaque seconde, CustomPerm retire ce qui a expiré, sauvegarde, renvoie l'arbre de commandes et l'inscrit au
journal d'activité. Un parent de grade et un grade refusé par un grade acceptent aussi une durée, depuis la
commande ou l'onglet Parents : tant qu'elle court la chaîne le suit, et une fois expirée la chaîne continue
sans lui.

**Mondes.** `world=<dimension>` limite à un monde un nœud sur un grade ou un joueur, ou un grade tenu par un
joueur : `world=the_nether`, `world=the_end`, `world=overworld`, ou une dimension moddée par son identifiant
complet (`world=mymod:mining`). La complétion propose les mondes chargés par le serveur. À spécificité égale
et chez le même détenteur, une entrée limitée au monde du joueur l'emporte sur la même entrée sans monde,
comme un nœud contextuel dans LuckPerms : un grade qui autorise `/home` partout et le refuse dans le Nether
le refuse là-bas. Le détenteur passe toujours d'abord : un grade plus lourd, ou le nœud propre du joueur,
décide face au nœud de monde d'un grade plus léger. L'arbre de commandes d'un joueur est renvoyé quand il
change de monde. Une entrée limitée à un monde peut aussi être temporaire : donnez les deux, dans n'importe quel ordre
(`/customperm grade assign Steve vip 7d world=the_nether`), et elle dure ce temps-là dans ce monde, le temps
restant affiché par monde dans les listes et sur les pages.
Un parent de grade, un refus (par un grade ou un joueur) et un préfixe ou suffixe peuvent être limités à un
monde de la même façon : `/customperm grade parent add vip builder world=the_nether`, `/customperm user
denygrade Steve vip world=the_end`, `/customperm grade prefix vip in the_nether add 10 &c[Chaud] `. Un parent
hérité dans un monde n'est suivi que là, à la même profondeur qu'un parent global ; un préfixe limité à un
monde s'y affiche avant le préfixe global du même détenteur à priorité égale, et les noms sont reconstruits au
changement de monde. `parent remove|removedeny` et `undenygrade` acceptent le même `world=` pour
désigner celui qui y est limité, et les `remove` et `clear` de préfixe le même `in <monde>`.

**Autres contextes.** `world=` est un contexte parmi d'autres, joints par une virgule après le nœud, le
grade ou le parent : `gamemode=creative` (`survival`, `creative`, `adventure`, `spectator`), et toute clé
qu'un contexte statique fixe pour tout le serveur, comme les `static-contexts` de LuckPerms (`region=eu`).
Deux valeurs d'une même clé valent l'une ou l'autre : `world=the_nether,world=the_end` s'applique dans les
deux ; deux clés doivent tenir toutes les deux : `world=the_nether,gamemode=creative`. À spécificité et
détenteur égaux, une entrée qui nomme plus de clés l'emporte sur une qui en nomme moins. Une clé que rien ne
fixe sur ce serveur est refusée, l'entrée ne s'appliquerait nulle part ; `server=` n'est fixé que dans un cluster
(voir [Mode cluster](#mode-cluster-plusieurs-serveurs)).
L'arbre de commandes et les noms suivent un changement de mode de jeu comme un changement de monde. Pour les
préfixes, `in <monde>` reste, et tout autre contexte se donne entre guillemets après `where` :
`/customperm grade prefix vip where "gamemode=creative" add 5 [Bâtisseur] `. La case monde des pages prend le
même texte (`the_nether,gamemode=creative`).

| Commande | Description |
|---|---|
| `/customperm contexts` | Liste les contextes statiques, vrais pour chaque joueur ici. |
| `/customperm contexts <joueur>` | Ce qui tient pour ce joueur maintenant : monde, mode de jeu et contextes statiques. |
| `/customperm contexts set <clé> <valeur>` | Fixe un contexte statique (`region eu`) ; `world`, `gamemode` et `server` ne peuvent pas l'être. |
| `/customperm contexts unset <clé>` | Le retire ; les entrées qui y sont limitées ne s'appliquent plus nulle part jusqu'à ce qu'il revienne. |

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
| `/customperm track promote <joueur> <track> [world=<dim>]` | Un cran plus haut ; un joueur sur aucun cran reçoit le premier. Avec un contexte, parmi les grades détenus là seulement. |
| `/customperm track demote <joueur> <track> [world=<dim>]` | Un cran plus bas ; depuis le premier cran, hors du track. Avec un contexte, pareil là seulement. |
| `/customperm track list [track]` | Affiche les tracks et leurs crans. |

Promote et demote s'ouvrent aussi à `customperm.track.<track>`, en plus de `customperm.admin` : un modérateur
à qui l'on confie une échelle déplace des joueurs sur ce track seulement, par la commande et l'onglet Tracks,
et rien d'autre des grades ne lui est ouvert. `customperm.track.*` couvre tous les tracks. Se déplacer
soi-même demande `customperm.manage.grades` : avec le seul nœud de track c'est refusé, puisqu'un cran plus
haut peut être le grade qui donne `customperm.manage.grades`. La garde anti-verrouillage s'applique à chaque
déplacement.

Les autres commandes de track demandent `customperm.manage.grades`, comme les grades, et sont refusées quand LuckPerms est actif, ses
propres tracks décidant alors. Seuls les grades qu'un joueur détient partout comptent comme crans, sauf si un
contexte suit le track : seuls comptent alors les grades détenus dans ce contexte, et le déplacement donne et
retire des grades là seulement, comme le promote de LuckPerms avec un contexte. Un grade détenu partout n'est
pas un cran là, et le résultat dit quand il couvre déjà ce contexte. Un grade refusé partout ou dans ce
contexte n'est pas donné. L'onglet Tracks de la page Joueurs prend le même contexte dans sa case monde, et
montre le cran de chaque joueur là. Un joueur
qui détient deux grades du même track est refusé plutôt que deviné : désassignez-en un d'abord. Le grade
quitté emporte son expiration, et celui reçu est permanent. Supprimer un grade le retire de tous les tracks.
Un grade peut figurer sur plusieurs tracks.

### Meta

La meta est une donnée qu'un grade ou un joueur porte pour que d'autres mods la lisent, clé vers valeur,
comme la meta de LuckPerms. Un mod qui déclare via NeoForge un nœud de permission nombre ou texte (une limite
de homes, un libellé de rang) lit la meta qui porte le nom de ce nœud, comme LuckPerms répond à ces nœuds : un
nœud nombre prend la valeur lue comme un nombre entier, et sa valeur par défaut s'il n'y en a pas ou si ce
n'est pas un nombre ; un nœud texte prend la valeur telle quelle. La valeur propre du joueur l'emporte, puis
celle du grade le plus lourd, puis celle de l'ancêtre le plus proche ; une valeur limitée à un contexte passe
avant la valeur globale du même détenteur, et refus, durées et grade par défaut s'appliquent comme pour un
nœud. Les clés s'écrivent comme des nœuds de permission (minuscules, avec des points) ; une valeur tient en 256
caractères, entre guillemets si elle contient un espace.

| Commande | Description |
|---|---|
| `/customperm grade meta <grade>` | Liste la meta du grade, avec le temps restant et le contexte de chaque valeur. |
| `/customperm grade meta <grade> set <clé> <valeur> [durée] [contexte]` | Fixe une valeur, en remplaçant celle de cette clé à cet endroit. |
| `/customperm grade meta <grade> unset <clé> [contexte]` | La retire. |
| `/customperm user meta <joueur> ...` | La même chose pour la meta propre d'un joueur. |

La complétion propose les nœuds nombre et texte déclarés par les mods. Les pages Grades et Joueurs ont un
onglet **Meta** avec les mêmes cases. La meta n'est répondue d'ici que tant que CustomPerm est le handler de
permissions de NeoForge (voir [Mods qui testent les permissions via
NeoForge](#mods-qui-testent-les-permissions-via-neoforge)) ; avec LuckPerms, LuckPerms répond depuis sa propre
meta.

### Préfixes et suffixes de chat

| Commande | Description |
|---|---|
| `/customperm grade prefix <grade>` | Liste les préfixes d'un grade, priorité la plus haute d'abord, avec le temps restant des temporaires. |
| `/customperm grade prefix <grade> add <priorité> <texte>` | Donne au grade un préfixe à cette priorité, en remplaçant celui qui y est déjà. |
| `/customperm grade prefix <grade> addtemp <priorité> <durée> <texte>` | Pareil pour une durée comme `30d`. |
| `/customperm grade prefix <grade> remove <priorité>` | Retire le préfixe à cette priorité. |
| `/customperm grade prefix <grade> clear` | Retire tous les préfixes du grade. |
| `/customperm grade prefix <grade> in <monde> ...` | Les mêmes modifications pour les préfixes limités à un monde (`in the_nether`). |
| `/customperm grade suffix <grade> ...` | Pareil pour les suffixes. |
| `/customperm user prefix\|suffix <joueur> ...` | Pareil pour les préfixes et suffixes qu'un joueur porte lui-même. |
| `/customperm names` | Dit si les noms sont décorés, comment, et depuis quel backend. |
| `/customperm names on\|off` | Décore les noms avec leur préfixe et leur suffixe, ou arrête. |
| `/customperm names format <format>` | Comment le nom est construit, `{prefix}{name}{suffix}` par défaut ; `{name}` est obligatoire. |
| `/customperm names stack <prefix\|suffix\|both> <highest\|stacked> [limite]` | N'affiche que la priorité la plus haute, ou jusqu'à `limite` (3 par défaut) à la suite. |

Les préfixes demandent `customperm.manage.grades` et sont refusés tant que LuckPerms est actif : ils se
règlent alors avec `/lp` et s'affichent depuis LuckPerms. `names` demande `customperm.manage.config` et
fonctionne avec les deux backends.

Quel préfixe s'affiche : celui de priorité la plus haute parmi tout ce qu'atteint le joueur, comme LuckPerms.
À priorité égale, celui du joueur, puis celui du plus lourd de ses grades, puis celui du grade hérité le plus
proche ; un grade refusé n'en donne aucun, et le grade par défaut ne s'applique que si rien de ce que tient le
joueur n'en a. Un détenteur porte un préfixe par priorité, donc une priorité désigne celui à retirer. Un
préfixe temporaire cesse de s'afficher à son expiration, puis le balayage le retire. Empilés, les préfixes
s'affichent dans ce même ordre, chaque texte une fois, jusqu'à la limite, avec entre eux les séparateurs
réglés dans `settings.json`. Le texte accepte les codes couleur `&` (`&6`, `&l`, `&r`) et `&#RRGGBB`, 64
caractères au plus. Avec LuckPerms, c'est la mise en forme des meta de LuckPerms qui décide de son préfixe,
et le réglage d'empilement ne s'applique pas.

**Le nom est décoré, jamais le message.** Les messages de chat sont signés : un mod qui en réécrit un fait
marquer le message comme modifié par le client, et un mod qui envoie un message système à la place perd le
signalement et l'indicateur de chat sécurisé. Le nom de l'expéditeur ne fait pas partie de ce qui est signé :
CustomPerm le décore et ne touche à aucun message. Conséquence : le préfixe est sur le nom partout où le jeu
l'affiche (chat, messages de mort et de progrès, `/msg`, `/me`, message de connexion, liste des joueurs),
pas sur le nom au-dessus de la tête, et le `<Nom>` autour reste celui de vanilla. Si un autre mod décore
aussi les noms, les deux s'appliquent l'un dans l'autre : c'est pourquoi c'est désactivé par défaut.

### Surnoms

| Commande | Description |
|---|---|
| `/customperm user nick <joueur>` | Affiche le surnom d'un joueur, en ligne ou déjà venu. |
| `/customperm user nick <joueur> set <surnom>` | Le montre sous ce nom, codes `&` acceptés. |
| `/customperm user nick <joueur> clear` | Lui rend son propre nom. |
| `/nick [<surnom> \| clear]` | Le surnom d'un joueur pour lui-même, avec `customperm.nick` ; les codes demandent `customperm.nick.color`. |

La commande d'admin demande `customperm.manage.grades`, et la page Joueurs a une case surnom à côté du joueur
sélectionné. Les deux marchent aussi avec LuckPerms : un surnom n'est pas une permission, CustomPerm le
montre quel que soit ce qui décide des permissions, et il n'est ni importé ni exporté.

Un surnom remplace le nom lui-même, le `{name}` du format, partout où le jeu montre le nom (voir plus haut),
et il s'applique aussi avec la décoration désactivée, puisqu'il a été posé exprès pour ce joueur. Une couleur
d'équipe s'applique toujours autour, et survoler le nom dans le chat montre toujours le vrai ;
`/customperm user list` affiche les deux. 16 caractères visibles au plus, codes à part, sur une ligne.

**Un surnom ne peut pas se faire passer pour un autre joueur.** Un surnom qui se lit comme le nom d'un autre
joueur, ou comme le surnom d'un autre joueur, est refusé, qui que ce soit qui le pose. Les lettres sont
comparées sans leur casse, leurs codes, leurs espaces ni les séparateurs `_ - .` : `&cMod Team` est refusé
quand `mod_team` joue ici. Son propre nom est toujours accepté. Les lettres d'autres alphabets qui se
ressemblent ne sont pas détectées : réservez `customperm.nick` aux joueurs à qui vous faites confiance.

`/nick` est laissé de côté quand un autre mod l'enregistre déjà, ce que le journal indique ; les surnoms se
posent alors avec `/customperm user nick` seulement.

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

Ce qui passe : un groupe devient un grade avec son poids, son nom d'affichage, ses parents et les groupes qu'il refuse ; un nœud à
false devient un DENY ; un joueur garde ses groupes, ceux qu'il refuse et ses nœuds propres.
`minecraft.command.<x>` devient `customperm.command.<x>` et `<x>` est exposée avec, faute de quoi le nœud
n'accorderait rien.

Préfixes et suffixes passent avec leur priorité et leur expiration. De deux à la même priorité sur un
détenteur, ce que CustomPerm ne peut pas porter, le texte qui se trie en premier est importé et le rapport le
dit.

Les entrées temporaires passent avec leur expiration : un nœud, un groupe d'un joueur, un parent de groupe, un refus. Les entrées
limitées à un contexte passent avec lui, les temporaires avec leur expiration : un nœud sur un groupe ou un
joueur, un groupe d'un joueur, un parent de groupe, un refus, un préfixe ou suffixe, et la meta. Sur NeoForge,
LuckPerms nomme la dimension `dimension-type`, qui devient `world=` ici ; `gamemode` reste tel quel, et une
autre clé passe quand un contexte statique la fixe ici.
Les tracks passent avec leurs groupes dans l'ordre ; ajouter garde tel quel un track qui existe déjà ici,
remplacer prend l'ordre de LuckPerms.

Ce qui est laissé derrière, et dit dans le rapport plutôt qu'abandonné en silence : le contexte `world` de LuckPerms, qui
sur NeoForge est le nom de la sauvegarde et pas une dimension, un contexte `server=` hors cluster, et une clé qu'aucun
contexte statique ne fixe ici ; un nom d'affichage temporaire, limité à un contexte ou de plus de 48
caractères, ce que celui d'un grade ne peut pas être ; les permissions en expression régulière ; et les nœuds que d'autres mods lisent sans les déclarer à
NeoForge, que plus rien ici ne lirait. Ajouter garde le nom d'affichage d'un grade qui en a déjà un. Les nœuds déclarés par les mods sont importés tels quels, sur les
groupes et les joueurs, puisque CustomPerm y répond. Sur les
joueurs, seul ce que CustomPerm sait lire est regardé : leurs groupes, leurs préfixes et suffixes, leurs
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

Ce qui est écrit, tel quel : un grade devient un groupe avec son poids, son nom d'affichage, ses parents et les groupes qu'il
refuse ; un nœud refusé devient un nœud à false ; un joueur garde ses grades, ceux qu'il refuse et ses
propres nœuds ; le grade par défaut devient un parent du groupe `default` de LuckPerms ; un track devient un
track avec ses grades exportés dans l'ordre, après les joueurs. Rien n'est traduit : sur le backend
LuckPerms, CustomPerm lit `customperm.*` tel quel. Ajouter laisse tel quel un track que LuckPerms a déjà ;
remplacer fixe ses groupes.

Ce qui est écarté, et nommé dans le rapport : un grade dont LuckPerms refuserait ou passerait en minuscules
le nom (il accepte minuscules, chiffres, `_`, `.` et `-`, 36 au plus), et chaque parent, attribution ou
grade par défaut qui le nomme. Ajouter garde ce que LuckPerms contient déjà, poids et nom d'affichage compris,
et remplacer écrit le nom d'affichage du grade, ou aucun, à la place de celui que LuckPerms montre partout ; là où il pose
un nœud dans l'autre sens, sa valeur est gardée et comptée. Préfixes et suffixes sont écrits avec leur propre
priorité, les temporaires temporaires, et ajouter garde celui que LuckPerms a déjà à la même priorité.
Remplacer ne touche aux préfixes ou aux suffixes que là où le grade en définit, et jamais une entrée temporaire, un contexte qu'il n'écrit
pas, ni les nœuds des autres mods. La meta est écrite par clé, dans son contexte et avec son expiration ;
ajouter garde une valeur que LuckPerms a déjà pour cette clé, et remplacer ne vide que les clés que le grade
définit. Une entrée temporaire est écrite temporaire, et une entrée déjà
expirée n'est pas écrite. Une entrée limitée à un monde est écrite avec le contexte `dimension-type` de
LuckPerms, la dimension sur NeoForge (`the_nether` pour un monde vanilla, l'identifiant complet pour un monde
moddé) ; un mode de jeu et un contexte statique passent tels quels. Remplacer vide les nœuds customperm et
les parents limités à ces contextes en même temps que les globaux.

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
| `/customperm ratelimit scope <name> <server\|network\|hub,survival>` | En mode cluster, qui partage le budget : chaque serveur seul (par défaut), tous les serveurs, ou ceux nommés. Voir [Mode cluster](#mode-cluster-plusieurs-serveurs). |
| `/customperm ratelimit persistence <name> <world_save\|immediate>` | Choisit quand l'historique d'utilisation de cette commande est écrit sur le disque (voir `ratelimits.json`). |
| `/customperm ratelimit servers <name> [serveurs\|here\|all]` | En cluster, les membres sur lesquels une règle est actif, noms séparés par des espaces ou des virgules ; `here` désigne ce serveur, `all` tous les membres (par défaut). Sans liste, l'affiche. Voir [Mode cluster](#mode-cluster-plusieurs-serveurs). |
| `/customperm ratelimit disable <name>` / `enable <name>` | Suspend ou reprend l'application d'une règle sans perdre ses valeurs. |
| `/customperm ratelimit remove <name>` | Supprime la règle. |
| `/customperm ratelimit list` | Liste les règles avec leur état et leur mode de persistance. |

### Diagnostic et utilitaires

| Commande | Effet |
|---|---|
| `/customperm test <player> <node>` | Vérifie si un joueur a un node de permission donné. Retourne `GRANTED` ou `DENIED`, avec la raison : ALLOW ou DENY explicite, ou non défini (accordé aux opérateurs). |
| `/customperm debug <player> <command>` | Rapport détaillé : commande dans le dispatcher ? exposée ? l'op-level passe ? la perm est granted ? le wrapper renvoie quoi ? |
| `/customperm status` | Snapshot global : backend, nb de commandes wrappées, exposées, aliases, grades, alertes admin actives. |
| `/customperm modcheck` | Liste les mods installés qui appellent l'API propre de LuckPerms, à laquelle CustomPerm ne peut pas répondre sans LuckPerms. Voir [Mods qui appellent LuckPerms par son nom](#mods-qui-appellent-luckperms-par-son-nom). |
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
| `customperm.track.<track>` | Avec `customperm.admin` : promouvoir et rétrograder d'autres joueurs sur ce track seulement. `customperm.track.*` pour tous. |
| `customperm.nick` | `/nick` : un joueur pose son propre surnom. |
| `customperm.nick.color` | Codes `&` dans son propre surnom. |

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

`commandServers` est optionnel aussi : en cluster, les membres sur lesquels une commande est exposée (voir le mode
cluster). Une commande absente de cette table est exposée sur tous les membres.

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
  "prefixStack": { "mode": "highest", "limit": 3, "start": "", "middle": "", "end": "" },
  "suffixStack": { "mode": "highest", "limit": 3, "start": "", "middle": "", "end": "" },
  "answerOtherMods": true
}
```

- `gateAllCommands` (backend interne, `false` par défaut) : `true` fait lire à chaque commande son nœud `customperm.command.<nom>`. Un DENY explicite bloque alors n'importe quelle commande pour les opérateurs aussi, et un ALLOW ouvre n'importe quelle commande : un grade qui a `*` ou `customperm.command.*` obtient toutes les commandes du serveur. Sans effet avec LuckPerms installé, qui contrôle déjà toutes les commandes.
- `defaultGrade` (backend interne, vide par défaut) : un grade appliqué à tous les joueurs, sous leurs propres grades.
- `playerCommandLog` (`false` par défaut) : enregistre chaque commande tapée par les joueurs dans le journal d'activité. Les modifications d'administration sont toujours enregistrées.
- `maskPlayerCommandArguments` (`true` par défaut) et `maskedCommands` : les arguments de ces commandes racines sont stockés sous la forme `[masked]` (`/msg Alex salut` devient `/msg [masked]`). Un préfixe de namespace est ignoré.
- `logRetentionDays` (`30` par défaut) : les fichiers quotidiens plus anciens sont supprimés au démarrage et à chaque changement de jour ; `0` les garde indéfiniment. Fichiers : `<monde>/customperm/logs/admin-AAAA-MM-JJ.jsonl` et `players-AAAA-MM-JJ.jsonl`, un objet JSON par ligne.
- `decorateNames` (`false` par défaut) : met le préfixe et le suffixe de chat de chaque joueur autour de son nom, depuis les grades ou depuis LuckPerms. Le nom est décoré, jamais le message (voir [Préfixes et suffixes de chat](#préfixes-et-suffixes-de-chat)).
- `prefixStack` et `suffixStack` (backend interne) : `mode` vaut `highest` (un seul, par défaut) ou `stacked` (plusieurs à la suite, priorité la plus haute d'abord, au plus `limit`, de 1 à 16) ; `start`, `middle` et `end` s'écrivent avant le premier, entre deux et après le dernier quand ils sont empilés, codes `&` permis. `/customperm names stack` règle le mode et la limite.
- `answerOtherMods` (`true` par défaut) : répondre depuis les grades aux tests de permission que les autres mods font via NeoForge. Lu au démarrage ; voir [Mods qui testent les permissions via NeoForge](#mods-qui-testent-les-permissions-via-neoforge).
- `cluster` (backend interne, désactivé par défaut) : plusieurs serveurs partagent une configuration via Arcadia Lib ; lu au démarrage. Voir [Mode cluster](#mode-cluster-plusieurs-serveurs).
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

Aliases avec leurs steps, et les arguments qu'ils prennent. Un alias qui n'en prend aucun est absent
d'`aliasParameters`, ce à quoi ressemble tout fichier écrit avant l'existence des arguments : il se
charge sans changement.

```json
{
  "aliases": {
    "fly": ["gamemode spectator"],
    "heal": [
      "effect give @s minecraft:instant_health 10 100",
      "effect give @s minecraft:saturation 1 100",
      "say bien soigné !"
    ],
    "warn": ["say [ATTENTION] ${target} : ${reason}"]
  },
  "aliasParameters": {
    "warn": [
      { "name": "target", "type": "player" },
      { "name": "reason", "type": "text", "optional": true, "defaultValue": "sans motif" }
    ]
  }
}
```

`aliasServers`, optionnel, liste en cluster les membres sur lesquels un alias existe ; un alias absent de cette
table existe sur tous les membres.

Un argument porte `name` et `type` (`player`, `integer`, `word` ou `text`), et en option `optional`,
`defaultValue`, `min` et `max` (integer), `choices` (word) et `allowSelectors` (text).

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
      "displayName": "Staff team",
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

Préfixes et suffixes sont des listes de `{ "priority", "text", "expires" }` : `prefixes` et `suffixes` sur
un grade, `userPrefixEntries` et `userSuffixEntries` au niveau racine, par joueur. `expires` est en secondes
epoch, 0 ou absent pour de bon. Un fichier écrit avant les priorités, avec un seul `prefix` sur un grade ou
`userPrefixes` au niveau racine, est lu dans ces listes à la priorité 0, qui affiche le même préfixe qu'avant.

`userNicknames` au niveau racine associe un joueur à son surnom, codes compris. Il est lu avec les deux
backends.

`displayName` sur un grade est le nom que les listes et la page Grades montrent pour lui ; absent, le grade
est montré par son nom. Il n'est jamais cherché : `userGrades`, `parents` et `tracks` nomment le grade par sa
clé.

Les entrées temporaires gardent leurs collections et ajoutent à côté de chacune une map de secondes epoch :
`permissionExpiries` et `deniedPermissionExpiries` sur un grade, `userPermissionExpiries`,
`userDeniedPermissionExpiries`, `userGradeExpiries` et `userDeniedGradeExpiries` au niveau racine, joueur
d'abord, puis nœud ou grade, et `parentExpiries` et `deniedParentExpiries` sur un grade, sous le grade hérité
ou refusé. Une entrée absente de ces maps est permanente, et une expiration qui ne nomme
aucune entrée est écartée à la lecture du fichier.

Les entrées limitées à un monde se rangent à côté des autres, sous leur contexte : `contexts` sur un grade
associe un contexte à ses `permissions`, `deniedPermissions`, `parents`, `refused`, `prefixes` et `suffixes`
de là, et `userContexts` au niveau racine associe un joueur, puis un contexte, aux `grades`, `refused`,
`permissions`, `deniedPermissions`, `prefixes` et `suffixes` qu'il détient là. Un contexte
s'écrit `clé=valeur`, `world=minecraft:the_nether` ; `world=the_nether` écrit à la main est lu comme le
même. Une clé que cette version ne lit pas est gardée telle quelle et ne correspond à rien, pour qu'un
fichier écrit par une version ultérieure ne soit pas abîmé.

`userPermissions` et `userDeniedPermissions` portent des nœuds pour un joueur seul, au-dessus de tous ses grades. `deniedPermissions` est utilisé uniquement par le backend interne. L'entrée la plus spécifique l'emporte sur l'ensemble de ce que porte le joueur et de ses grades (nœud exact, puis `a.b.*`, puis `a.*`, puis `*`) ; à niveau égal un nœud porté par le joueur l'emporte, puis le grade le plus lourd, puis un DENY entre égaux. `parents` liste les grades dont un grade hérite : leurs entrées s'appliquent là où il ne dit rien d'aussi précis sur le nœud, l'ancêtre le plus proche d'abord, et une chaîne concourt avec les autres grades au poids du grade que le joueur détient réellement. `deniedParents` et `userDeniedGrades` sortent un grade de la résolution, respectivement pour la chaîne de ce grade et pour ce joueur partout, grade par défaut compris ; un refus ne transforme jamais ce que le grade refusé autorise en refus. `weight`, `parents`, `deniedParents` et `userDeniedGrades` sont facultatifs et vides s'ils sont absents, ce qui laisse la règle du DENY comme seul départage, comme avant l'existence de ces champs. Ce que porte le joueur et ses grades décident d'abord ; seul un nœud qu'aucun d'eux ne mentionne passe au grade par défaut.

`tracks` associe un track à ses grades, du plus bas au plus haut. Il ne fait que nommer des grades, d'où sa
place dans ce fichier : supprimer un grade le retire de tous les tracks dans la même écriture. Il ne décide
rien lors d'un test de permission.

---

## Mode cluster (plusieurs serveurs)

Plusieurs serveurs qui font tourner CustomPerm **sans LuckPerms** peuvent partager une même configuration : un grade
créé sur le hub s'applique sur survie et créatif en deux secondes environ. Désactivé par défaut ; un serveur qui ne
l'active pas se comporte exactement comme avant. Avec LuckPerms, le mode cluster ne fait rien : LuckPerms partage
déjà son stockage entre serveurs, et CustomPerm le lit.

**Ce qu'il faut**

- Un serveur dédié (un monde solo ou LAN tourne toujours seul), et une même base MySQL ou MariaDB que chaque serveur
  atteint. Deux façons de s'y connecter, choisies par `cluster.connection` :
  - `"direct"` : CustomPerm se connecte lui-même, rien d'autre à installer. Remplir `cluster.serverName` (différent
    sur chaque serveur) et `cluster.database` (`host`, `port`, `name`, `user`, `password`, `tls`) dans
    `settings.json`. Le mot de passe est dans ce fichier : le garder lisible par le seul compte du serveur.
  - `"arcadia"` (par défaut) : via [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib) 1.3.0 ou
    plus, pour les serveurs qui l'ont déjà. La base se règle dans `<monde>/serverconfig/arcadia/lib/database.toml`
    d'Arcadia Lib, et le nom est son `server_id` dans `<monde>/serverconfig/arcadia/lib/server.toml` (Arcadia Lib met
    `server1` pour tout le monde par défaut).
- Un serveur dont le nom est déjà pris par un autre serveur en marche reste hors du cluster et le dit.
- `"cluster": { "enabled": true }` dans le `settings.json` de chaque serveur, puis un redémarrage. Les réglages du
  cluster ne s'appliquent qu'au démarrage.

**Ce qui est partagé**

Chaque partie peut être partagée ou gardée locale, dans `settings.json` (même bloc `cluster` que dans la partie
anglaise) :

- `grades` : tous les grades et tout ce que tiennent les joueurs (`grades.json`), tracks comprises.
- `commands`, `aliases`, `rateLimits` : commandes exposées, alias, règles de limites.
- Les **compteurs** des limites se partagent règle par règle, selon sa portée : `server` (par défaut, chaque
  serveur compte ses usages), `network` (un seul budget pour tous les serveurs : 3 par heure vaut 3 sur tout le
  cluster, à `pollSeconds` près), ou des noms de serveurs comme `hub,survival` (ces serveurs partagent un budget,
  les autres comptent seuls). Se règle avec `/customperm ratelimit scope <commande> <portée>` ou dans la page Rate
  limits. Seuls les usages des règles partagées vont en base.
- `log` : le journal d'activité. Les entrées des autres serveurs apparaissent dans la page Logs et `/customperm log`
  avec `@<serveur>` ; les fichiers de chaque serveur ne gardent que ce qui s'y est passé.
- `settings.json` lui-même reste local : grade par défaut, `gateAllCommands`, affichage des noms et contextes
  statiques se règlent par serveur.

**Comment les changements circulent**

- Chaque grade, joueur, commande, alias et règle est une ligne à part, avec une version. Un changement est écrit
  avant que la commande réponde. Deux admins qui modifient deux grades différents ne se gênent jamais ; si deux
  serveurs modifient le même grade au même moment, le second est **refusé**, voit le premier changement, et apprend
  quel serveur l'a fait.
- Les autres serveurs lisent ce qui a changé toutes les `pollSeconds` (2 par défaut) et l'appliquent : permissions,
  arbres de commandes et noms suivent.
- Le premier serveur à rejoindre remplit la base depuis ses fichiers. Un serveur qui rejoint une base déjà remplie
  prend ce qu'elle contient ; ses fichiers précédents sont d'abord copiés dans `backup/`.
- Les vérifications de permission n'attendent jamais la base : elles lisent la mémoire, comme sur un seul serveur.
- Charge sur la base, par serveur au repos : une requête toutes les `pollSeconds` pour toutes les parties partagées,
  une pour le journal, une pour les usages des limites quand une règle est partagée, et un battement toutes les 10
  secondes ; rien n'est relu deux fois. Mesuré sur MariaDB : environ 2 requêtes par seconde et moins de 1 Ko/s par
  serveur. Les tables sont `customperm_rows`, `customperm_seq`, `customperm_servers`, `customperm_log` et
  `customperm_uses`, créées dans la base configurée au premier démarrage.

**Commandes, alias et limites de débit par serveur**

Une commande exposée, un alias ou une limite de débit peut être limité à certains membres du cluster. La liste est
rangée avec l'élément et partagée avec lui : chaque membre tient la même configuration et décide de ce qu'il
active :

```jsonc
// commands.json
"grantedCommands": ["tp", "seed"],
"commandServers": { "tp": ["hub"] }

// aliases.json
"aliasServers": { "spawn": ["hub", "survival"] }

// ratelimits.json
"rules": { "home": { "maxExecutions": 3, "windowSeconds": 3600, "servers": ["survival"] } }
```

- Un membre présent dans la liste active l'élément. Un membre absent ne l'active pas : la commande n'y est pas
  exposée et garde sa condition d'origine, l'alias n'y est pas enregistré (une vraie commande du même nom reste),
  la règle n'y compte rien.
- Pas de liste, ou une liste vide, veut dire tous les membres : les fichiers écrits avant les listes se comportent
  comme avant.
- Un nom qui n'est pas membre aujourd'hui est conservé : un serveur arrêté pour maintenance garde sa configuration.
- La liste est lue contre le nom du membre, connu au démarrage par `serverName` ou par le `server_id` d'Arcadia Lib,
  même quand la base est injoignable : une panne ne rend jamais à un membre ce que le cluster lui retire. Hors
  cluster, les listes ne sont pas lues et tout s'applique.
- Ce n'est pas le bloc `share`. `share` décide si un membre suit le cluster pour une partie entière ; un membre qui
  ne partage pas ses alias garde son propre `aliases.json` et ne voit jamais les listes posées par les autres. La
  liste décide, parmi les membres qui partagent une partie, lesquels en activent un élément.
- Ce n'est pas non plus la portée d'une limite de débit : la portée dit quels membres comptent les utilisations
  ensemble, la liste dit où la règle s'applique.
- Un membre qui tourne encore un CustomPerm plus ancien ignore la liste et garde l'élément actif : mettez à jour
  tous les membres.
- Dans l'interface, le même choix est une rangée de bascules : **All**, puis chaque membre entendu, ce serveur
  marqué d'une maison, et une marque d'alerte sur un nom listé qu'aucun membre ne porte en ce moment. Une note en
  dessous dit quand ce serveur garde cette partie en local ou n'a pas de nom de cluster.
- Se règle avec `/customperm command servers tp hub`, `/customperm alias servers spawn hub survival` ou
  `/customperm ratelimit servers home survival` ; `here` désigne le serveur sur lequel on tape, `all` efface la
  liste. La réponse dit si l'élément est actif sur ce serveur, avertit quand ce serveur ne partage pas cette partie
  (la liste reste alors dans son propre fichier), et nomme tout serveur qu'aucun membre ne porte en ce moment.

**Contexte `server=`**

Dans un cluster, `server=<nom>` est un contexte comme `world=` : `/customperm grade addperm vip
customperm.command.fly server=creative` n'accorde `/fly` que sur le serveur nommé `creative`. Hors cluster il est
refusé, puisqu'il ne s'appliquerait nulle part. L'import depuis LuckPerms reprend les nœuds `server=` de LuckPerms
quand un cluster tourne, et l'export les réécrit ; les deux ne correspondent que si les serveurs LuckPerms portent
les mêmes noms que ceux du cluster.

**Quand la base est perdue**

Le serveur garde les droits lus en dernier, refuse les modifications admin jusqu'à ce que la base réponde de
nouveau, et prévient les admins en ligne. Ses fichiers restent à jour comme copie, donc un redémarrage pendant la
panne repart du dernier état connu. Le tableau de bord montre le cluster : le nom de ce serveur, les parties
partagées et les autres serveurs entendus.

**Sécurité**

`tls` (connexion directe) vaut `off` par défaut, la plupart des bases auto-hébergées n'ayant pas de certificat ;
`trust` chiffre sans vérifier le certificat, `verify` chiffre et le vérifie. Arcadia Lib se connecte sans TLS. Dans
les deux cas, sans `verify`, gardez la base sur un réseau privé ou sur la même machine : quiconque peut lire ou
modifier ce trafic peut lire ou changer toutes les permissions.

`verify` ne réussit que si le certificat de la base remonte à une autorité que **le runtime Java connaît déjà**. Un
certificat auto-signé, ou issu de votre propre autorité, n'en fait pas partie, et CustomPerm n'a aucun réglage pour
désigner un fichier de CA : il faut le dire à la JVM du serveur, en ajoutant
`-Djavax.net.ssl.trustStore=<truststore>` et `-Djavax.net.ssl.trustStorePassword=<motdepasse>` à ses arguments de
démarrage, l'autorité étant importée dans ce truststore. Vérifié contre MariaDB 10.4 avec une autorité privée, sur
les deux pilotes : `verify` échoue tant que l'autorité est inconnue du runtime, et réussit dès qu'elle est
approuvée. Utilisez `trust` si vous voulez le chiffrement sans cette mise en place.

La connexion directe a besoin d'un pilote JDBC MariaDB ou MySQL **déjà chargé par un autre mod du serveur** :
CustomPerm n'en embarque aucun et instancie par son nom celui qu'il trouve. Arcadia Lib en apporte un, comme tout
mod qui en porte un. Sur un serveur qui n'en a aucun, le mode cluster ne démarre pas et dit pourquoi. Voir
[NOTICE.md](NOTICE.md).

> **Le mode cluster via Arcadia Lib exige quelque chose de vos joueurs.** CustomPerm, lui, n'exige jamais rien :
> son canal réseau est optionnel, donc un client sans le mod se connecte et perd seulement l'interface
> d'administration. Arcadia Lib enregistre ses propres canaux comme requis, donc **chaque joueur doit installer
> Arcadia Lib côté client pour rejoindre un serveur qui la fait tourner**. C'est une propriété d'Arcadia Lib et non
> de CustomPerm, et elle s'applique dès que vous l'installez, que le mode cluster soit actif ou non. Prévoyez-le :
> livrez-la dans votre pack, ou utilisez un autre mod fournissant un pilote sans réclamer le client.

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

### Arguments

Un alias peut prendre des arguments, que ses steps atteignent avec `${name}` :

```
customperm alias add warn say [ATTENTION] ${target} : ${reason}
customperm alias param add warn target player
customperm alias param add warn reason text
customperm alias params warn                   # <target> <reason>
```

`/warn Steve arrête de creuser ici` exécute alors `say [ATTENTION] Steve : arrête de creuser ici`.
La complétion propose ce que le type connaît : un argument player suggère les joueurs connectés, un
argument word ses choix déclarés.

Quatre types :

| Type | Accepte | Substitué par |
|------|---------|---------------|
| `player` | un joueur connecté, par son nom ou par un sélecteur qui n'en désigne qu'un | le nom de ce joueur |
| `integer` | un entier, dans les bornes déclarées | le nombre |
| `word` | un mot, sans espace ; l'un des choix déclarés s'il y en a | le mot |
| `text` | le reste de la ligne, espaces compris ; seul le dernier argument peut l'être | le texte |

Chaque argument peut devenir facultatif et recevoir une valeur par défaut, qui est ce qu'il substitue
quand il est omis :

```
customperm alias param add kit count integer
customperm alias param range kit count 1 64
customperm alias param default kit count 8     # facultatif dès lors, 8 si omis
```

Un argument qui peut être omis ne peut pas être suivi d'un argument obligatoire, et rien ne peut
suivre un argument `text` : ce sont les règles de Brigadier, refusées à la déclaration.

`${name}` est un choix délibéré : aucune syntaxe de commande ne produit un dollar suivi d'une
accolade, donc les accolades NBT et JSON d'un vrai step (`give @s diamond_sword{Enchantments:[]}`) ne
sont jamais prises pour un argument. Un `${name}` qui désigne un argument que l'alias ne prend pas
reste dans le step tel quel, et c'est un avertissement plutôt qu'un refus, le step et l'argument
pouvant se déclarer dans n'importe quel ordre.

**Une valeur ne porte jamais de sélecteur d'entités**, sauf si l'argument l'autorise. Les steps
s'exécutent en op level 4, donc un `@a` qui y arriverait agirait sur tout le monde au lieu du joueur
désigné. Un `word` ne peut pas contenir d'`@` (Brigadier le refuse), un `player` est résolu en nom
avant substitution, et un `text` le refuse tant que
`customperm alias param selectors <alias> <name> true` ne l'autorise pas.

### Sélecteurs Minecraft

Les sélecteurs (`@s`, `@p`, `@a`, etc.) fonctionnent normalement dans les steps eux-mêmes. La source pendant l'exécution est le joueur qui a invoqué l'alias.

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
| GameTests, deux modes | Surnoms posés par commande, par la page Joueurs et par `/nick` avec son nœud, montrés décoration désactivée, codes soumis à leur nœud, nom ou surnom d'un autre joueur refusé quels que soient casse, codes ou séparateurs. Exposition et retrait de commande avec un joueur non-op, préservation des ops, `/customperm` refusé aux non-ops, reconnexion, aliases exécutés en op 4 par les seuls détenteurs du node et incapables d'atteindre `/customperm`, édition des steps, gardes de récursion et de shadowing, reload d'un `aliases.json` modifié à la main, limites de débit (message de refus, compteur partagé par racine, isolation par joueur, console exemptée, expiration de fenêtre, reconnexion, reloads répétés, suppression de règle, aliases), reload tout-ou-rien, refus du reload concurrent, changements non sauvegardés après un reload en échec, entrées `null`, repush du command tree au reload, alertes admin dans le chat des ops, paquets du GUI et de l'éditeur refusés aux non-ops, sorties de diagnostic, autocomplétion de chaque argument de `/customperm` et aucune suggestion pour un non-op, opérateurs refusés sur une commande exposée, un alias, `/customperm` ou un domaine de l'interface par un DENY explicite pendant que la console garde l'accès, `*` refusé bloquant tout sauf les autorisations explicites, modifications d'administration par commande et par l'interface enregistrées avec les refus, commandes des joueurs enregistrées seulement si actif et masquées par défaut, fichiers sur disque, rechargement depuis le disque ignorant les lignes illisibles, rétention, boutons de la page Journaux soumis à leur nœud, modifications `/lp` enregistrées (mode LuckPerms), opérateurs sans les nœuds refusés sur `/customperm` et l'interface pendant que la console garde l'accès, chaque domaine exigeant son propre nœud `customperm.manage` pour la commande comme pour la page, les nœuds seuls n'ouvrant rien à un non-op, avertissement de mise à jour donné une fois pour une configuration écrite avant la 1.1.0 puis configuration estampillée. |
| GameTests, mode interne | Commandes de grade, union des grades, entrée la plus spécifique gagnante, poids de grade départageant, nom d'affichage montré par les listes et jamais une clé, nœuds portés par un joueur, page Joueurs et son garde anti-verrouillage, parents de grade avec héritage appliqué à chaud et cycles refusés, refus appliqués à chaud et contradictions répondues, toutes les formes de wildcard, éditeur sans LuckPerms, `gateAllCommands` et `*` autorisé, grade par défaut restreignant un op accidentel, auto-verrouillage refusé par commande et par l'interface. Préfixes de chat : codes `&` et format du nom, nom utilisé par le chat décoré depuis les grades avec le grade le plus lourd et celui du joueur qui l'emportent à priorité égale et une priorité plus haute qui l'emporte sur les deux, empilement, retrait par priorité, commandes avec leur liste, préfixe temporaire qui expire et est balayé, liste des joueurs, interrupteur et format appliqués aussitôt, et onglets Chat via l'interface avec priorité et temps restant. Entrées temporaires : durées lues et refusées, accès qui expire avant tout balayage, balayage qui nettoie le fichier, journalise et renvoie l'arbre, grade tenu et refus qui expirent, parent de grade et refus d'un grade qui expirent et sont balayés, et durées via l'interface avec le temps restant relu. Entrées limitées à un monde : contextes lus et refusés, nœud accordé dans le Nether seulement, arbre de commandes renvoyé et verdict qui change quand un vrai joueur se téléporte d'un monde à l'autre, grade et DENY propre à un joueur tenus dans un seul monde, retrait par monde, grade supprimé qui ne laisse aucune attribution par monde, et parent, refus d'un joueur et préfixe limités au Nether suivis quand le joueur se déplace, nom compris. Tracks : construction, vrai joueur promu et rétrogradé avec l'arbre de commandes qui suit, cran temporaire quitté avec son expiration, plusieurs crans et grade refusé refusés, grade supprimé qui quitte l'échelle, et promotion et rétrogradation depuis la page Joueurs ; un nœud de track qui déplace les autres sur son seul track, par commande et page, jamais soi-même, rien d'autre des grades ouvert ; dans un seul monde, les crans détenus là seulement, un refus là qui bloque, rien de déplacé partout, par la commande et la page. Permissions des autres mods : CustomPerm sélectionné comme handler, nœuds déclarés sous un espace de noms étranger répondus avec leur valeur par défaut, un DENY exact, un ALLOW par wildcard, un nœud nombre et un test hors ligne. |
| GameTests, mode LuckPerms | Import depuis une source avec un exemplaire de chaque cas : ce qui passe, nom d'affichage d'un groupe compris et un temporaire laissé derrière, ce qui est écarté avec sa raison, la lecture qui n'écrit rien, ce qui atterrit en config, les trois nœuds demandés ensemble, et un preview déjà consommé refusé. Export dans un vrai LuckPerms : groupes, poids, nom d'affichage gardé par l'ajout et écrit par le remplacement, parents, refus, groupe par défaut et nœuds d'un joueur écrits, grade renommé refusé, l'ajout qui garde les valeurs et le préfixe de LuckPerms, le remplacement qui ne vide que les nœuds customperm, la progression par détenteur, les deux nœuds demandés ensemble, la garde anti-verrouillage, et un preview déjà consommé refusé. Éditeur en jeu face à un vrai LuckPerms : groupes, nodes avec contextes et expiration, héritage, meta, prefix et suffix, poids, nom d'affichage, groupes et groupe principal d'un joueur, tracks, promote et demote, verrouillage des écritures par node et niveau, limites d'édition et de sync ; command tree renvoyé après un changement LuckPerms ; repli `deny` et `internal` quand LuckPerms devient indisponible. Un préfixe LuckPerms qui atteint le nom sans reconnexion ; plusieurs préfixes importés avec leur priorité, et exportés avec la leur, un temporaire restant temporaire. Nœuds, groupes et parents de groupe temporaires importés avec leur expiration et exportés temporaires. Nœuds, grade et refus d'un joueur, parent et refus d'un groupe, et préfixe limités à un monde importés et exportés avec le contexte `dimension-type` de LuckPerms, nœud limité à un mode de jeu, contexte `world` de LuckPerms (le nom de la sauvegarde) laissé derrière, nœud `server=` laissé derrière sans exposer sa commande. Un track importé et exporté avec ses groupes dans l'ordre. Un nœud déclaré par un autre mod importé depuis un groupe et un joueur, un nœud non déclaré laissé derrière, et LuckPerms qui garde le handler de permissions. |
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
refus. Un nœud qui porte un nombre ou un texte reçoit la meta du même nom (voir
[Meta](#meta)), et sa valeur par défaut s'il n'y en a pas. Un joueur hors ligne est résolu depuis les grades lui aussi.

### Mods qui appellent LuckPerms par son nom

Certains mods ne passent pas par NeoForge : ils appellent directement l'API propre de LuckPerms
(`net.luckperms.api`). CustomPerm ne peut pas répondre à ces vérifications. Avec LuckPerms installé, c'est
LuckPerms qui répond. **Sans LuckPerms, un tel mod retombe sur son propre défaut, souvent le niveau d'opérateur,
et un nœud qu'un grade lui donne ne fait rien.** C'est la seule limite de CustomPerm utilisé seul, et elle vient
du choix de l'autre mod, pas d'une fonction de CustomPerm. Leur répondre voudrait dire livrer une copie de l'API
de LuckPerms, qui ne peut pas se charger à côté du vrai LuckPerms.

`/customperm modcheck` dit quels mods installés sont dans ce cas. Il lit une fois, en arrière-plan, les classes
compilées de chaque mod et liste ceux dont les classes nomment l'API de LuckPerms :

- **LuckPerms seulement** : sans LuckPerms, CustomPerm ne peut pas répondre à ses vérifications. Ses nœuds se
  gèrent dans LuckPerms, ou demandez à son auteur de les déclarer via l'API de permissions de NeoForge, à
  laquelle CustomPerm répond alors.
- **Utilise aussi l'API de permissions de NeoForge** : les vérifications qu'il fait via NeoForge reçoivent une
  réponse ; il n'utilise peut-être LuckPerms que quand LuckPerms est là.

Un mod listé ici n'utilise peut-être LuckPerms que lorsqu'il est présent ; sa page ou sa config le dit. LuckPerms
et CustomPerm eux-mêmes sont exclus. La réponse est gardée jusqu'au prochain démarrage, puisque les mods
installés ne peuvent pas changer avant.

Ce qui reste possible sans LuckPerms, quand un tel mod retombe sur le niveau d'opérateur comme la plupart :

| Ce que le mod vérifie | Sans LuckPerms |
|---|---|
| Qui peut lancer une de ses commandes | L'exposer avec `/customperm command add <commande>` et donner `customperm.command.<commande>` dans un grade : la vérification de CustomPerm remplace celle du mod. |
| Si la commande s'exécute ensuite (vérifié à l'intérieur) | Un alias : ses étapes tournent au niveau d'opérateur 4, pour les seuls joueurs qui ont `customperm.alias.<nom>`. |
| Une fonction en jeu, hors de toute commande (une limite, un objet, une capacité) | Rien qu'un mod de permissions puisse répondre. Chercher dans la config du mod un réglage de niveau ou « tout le monde », demander à son auteur de passer par l'API de permissions de NeoForge, ou installer LuckPerms. |

Si le besoin est seulement d'interdire des actions à un endroit (construire, casser, utiliser des objets ou les
sorts d'autres mods), c'est de la protection de zone, pas des permissions :
[ArcadiaGuard](https://github.com/Team-Arcadia/ArcadiaGuard) le fait seul, avec ses propres membres de zone et un
bypass pour les opérateurs, avec ou sans LuckPerms.

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
- **Contextes LP au-delà des mondes non testés** : LuckPerms résout ses propres contextes via `getCachedData()`. Les nœuds par monde sont couverts, l'arbre de commandes étant renvoyé à chaque changement de monde ; les contextes par serveur et personnalisés passent tels quels mais ne sont pas testés.
- **L'interface d'administration demande CustomPerm côté client** : sans lui, l'administration reste entièrement en commandes.
- **L'éditeur LuckPerms en jeu n'est pas le web editor** : il couvre groupes, joueurs, tracks, nœuds, meta et chat meta, mais pas les opérations en masse, la recherche de nœud sur tous les détenteurs, ni l'historique d'annulation du web editor. Pour cela, `/lp editor` reste l'outil.
- **Les commandes raccourcis ont leurs propres règles** : certaines commandes sont des raccourcis qui redirigent vers une autre (`/tp` vers `/teleport`, `/msg` et `/w` vers `/tell`, `/xp` vers `/experience`). Chaque écriture est exposée et limitée sous le nom tapé par le joueur : `tp` gouverne `/tp`, `teleport` gouverne `/teleport`. Exposer l'une n'ouvre pas l'autre ; configurez les deux si les deux doivent être disponibles.
- **Les commandes joueur exigent un joueur connu du serveur** : `/customperm grade assign|unassign`, `/customperm user` et l'interface acceptent les joueurs en ligne ou déjà venus sur le serveur ; ils n'interrogent jamais le service de session, donc un pseudo jamais venu ne peut pas être assigné à l'avance. Dans ce cas, éditez `userGrades`, `userPermissions` ou `userDeniedPermissions` dans `grades.json` (UUID en clé) puis `/customperm reload`.

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
joueurs.** La synchronisation automatique des mods vers les joueurs rejoignant
*votre* serveur est explicitement autorisée, tant que le fichier officiel est
transmis non modifié. Un client vanilla peut tout de même rejoindre un serveur
qui fait tourner CustomPerm : l'interface d'administration exige le mod côté
client, pas la connexion. Le proposer en téléchargement général ou en
« installation en un clic » dans le catalogue d'un hébergeur ne l'est pas.

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
