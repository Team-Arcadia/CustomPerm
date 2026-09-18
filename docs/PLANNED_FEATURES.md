# Planned features

What CustomPerm does not do yet, what it will take, and what happens to it meanwhile. These were listed here
as deliberate refusals until 2026-09-18; they were asked for, so they are now work to do. Nothing here is a
schedule, and a feature is only real once it is in the changelog.

The page exists mostly for one question: a server importing from LuckPerms wants to know what becomes of the
parts CustomPerm has no equivalent for. The import names them in its report rather than dropping them in
silence, and this is where the reasons are.

**Français :** [Fonctionnalités envisagées](#fonctionnalités-envisagées)

---

## 1. Temporary entries (expiry)

**Done.** A node on a grade or a player, a grade a player holds and a refusal can carry an expiry, set with
a duration on the commands and the pages; the resolver ignores what has run out and a sweep tidies the file.
See the changelog and the README.

**What is left.** An expiry on a grade parent, and on a prefix or a suffix. An import leaves a group's
temporary parents and temporary prefixes behind and counts them, rather than importing them as permanent,
which would grant more than the source did.

---

## 2. Contextual entries (per world)

**Done.** A node on a grade or a player, and a grade a player holds, can be limited to one world with
`world=<dimension>` on the commands and a world box on the pages. It outranks the same holder's entry
without a world at the same specificity, and a player's command tree is sent again when they change world.
A context is stored as `key=value` pairs, so the `server=<name>` a cluster mode would add fits the same
file and resolver. The import and the export carry entries limited to a single world both ways. An entry
with no context keeps the path it had: the benchmark shows no change. See the changelog and the README.

**What is left.** A grade parent, a refusal and a prefix limited to a world, and an entry both limited to a
world and temporary. Contexts other than one world (`server=`, several worlds, custom keys) are not read. An
import leaves all of these behind and counts them, since importing one as global would grant it everywhere
and dropping it would take away something the source granted.

---

## 3. Chat metadata beyond prefixes and suffixes

**Done so far.** A grade and a player carry a chat prefix and a suffix, resolved like a node (the player's
own, then the heaviest grade, then the nearest ancestor), and names can be decorated with them, from the
grades or from LuckPerms. The written answer on signed chat: the name is decorated through NeoForge's name
event, never the message, so every message stays signed and reportable. The cost is that the name carries
the prefix wherever the game shows it, not only in chat. The import and the export carry prefixes and
suffixes both ways. See the changelog and the README.

**What is left.** Meta (arbitrary key and value pairs other mods read), display names, per-world prefixes
(section 2), and nicknames. Meta has the same problem as the nodes of other mods
(section 5): storing it is pointless while nothing here reads it back.

**Until then.** An import leaves meta and display names behind and counts them. A holder with several
prefixes in LuckPerms arrives with the one LuckPerms shows first, and the report says so.

---

## 4. Tracks (promotion ladders)

**Done.** A track is an ordered list of grades, kept in `grades.json` beside the grades it names;
`/customperm track` builds it and promotes or demotes a player one rung, as does the Tracks tab of the
Players page. The import and the export carry tracks both ways. See the changelog and the README.

**What is left.** Promoting within a world, which LuckPerms allows with a context: a rung here is a grade
held everywhere. And a permission per track, where LuckPerms can let a moderator promote on one ladder
only: here moving a player needs `customperm.manage.grades`, like assigning a grade.

---

## 5. Answering the permission checks of other mods

**What it is.** A LuckPerms setup usually carries nodes that other mods read. CustomPerm does not answer
those checks, so storing such a node would store a string nothing reads.

**What it will take.** NeoForge has the mechanism: a mod can register the handler that answers
`PermissionAPI` checks, and mods declare their nodes through the same API. Registering as that handler is
what turns those nodes from dead strings into permissions CustomPerm decides. One handler is registered for
the server and LuckPerms registers one, so which one answers, and what happens to a node neither knows, is
settled before any of it is written.

**Until then.** Mods that read permissions through LuckPerms need LuckPerms; CustomPerm runs alongside it.
An import leaves those nodes behind and counts them on groups, and does not even read them on players.

---

# Fonctionnalités envisagées

Ce que CustomPerm ne fait pas encore, ce qu'il faudra pour le faire, et ce qui se passe en attendant. Ces
points étaient listés ici comme des refus assumés jusqu'au 2026-09-18 ; ils ont été demandés, ce sont donc
désormais des travaux à faire. Rien ici n'est un calendrier, et une fonctionnalité n'est réelle qu'une fois
dans le changelog.

Cette page existe surtout pour une question : un serveur qui importe depuis LuckPerms veut savoir ce que
deviennent les parties dont CustomPerm n'a pas l'équivalent. L'import les nomme dans son rapport au lieu de
les abandonner en silence, et c'est ici que se trouvent les raisons.

---

## 1. Entrées temporaires (expiration)

**Fait.** Un nœud sur un grade ou un joueur, un grade tenu par un joueur et un refus peuvent porter une
expiration, posée avec une durée dans les commandes et les pages ; le résolveur ignore ce qui a expiré et un
balayage nettoie le fichier. Voir le changelog et le README.

**Ce qui reste.** Une expiration sur un parent de grade, et sur un préfixe ou un suffixe. Un import laisse de
côté les parents temporaires d'un groupe et les préfixes temporaires, et les compte, plutôt que de les
importer comme permanents, ce qui accorderait plus que la source.

---

## 2. Entrées contextuelles (par monde)

**Fait.** Un nœud sur un grade ou un joueur, et un grade tenu par un joueur, peuvent être limités à un monde
avec `world=<dimension>` dans les commandes et une case monde dans les pages. À spécificité égale, l'entrée
l'emporte sur celle sans monde du même détenteur, et l'arbre de commandes d'un joueur est renvoyé quand il
change de monde. Un contexte est stocké en paires `clé=valeur`, donc le `server=<nom>` qu'ajouterait un mode
cluster entre dans le même fichier et le même résolveur. L'import et l'export transportent dans les deux sens
les entrées limitées à un seul monde. Une entrée sans contexte garde son chemin : le benchmark ne montre
aucun écart. Voir le changelog et le README.

**Ce qui reste.** Un parent de grade, un refus et un préfixe limités à un monde, et une entrée à la fois
limitée à un monde et temporaire. Les contextes autres qu'un monde (`server=`, plusieurs mondes, clés
personnalisées) ne sont pas lus. Un import laisse tout cela de côté et le compte, puisque l'importer en
global l'accorderait partout et le jeter retirerait ce que la source accordait.

---

## 3. Métadonnées de chat au-delà des préfixes et suffixes

**Déjà fait.** Un grade et un joueur portent un préfixe et un suffixe de chat, résolus comme un nœud (celui
du joueur, puis le grade le plus lourd, puis l'ancêtre le plus proche), et les noms peuvent en être décorés,
depuis les grades ou depuis LuckPerms. La réponse écrite sur le chat signé : le nom est décoré par
l'événement de nom de NeoForge, jamais le message, donc chaque message reste signé et signalable. Le prix est
que le nom porte le préfixe partout où le jeu l'affiche, pas seulement dans le chat. L'import et l'export
transportent préfixes et suffixes dans les deux sens. Voir le changelog et le README.

**Ce qui reste.** Les meta (paires clé et valeur arbitraires que lisent d'autres mods), les noms d'affichage,
les préfixes par monde (section 2), et les surnoms. Les meta posent le même problème
que les nœuds des autres mods (section 5) : les stocker ne sert à rien tant que rien ici ne les relit.

**En attendant.** Un import laisse les meta et les noms d'affichage de côté et les compte. Un détenteur qui
a plusieurs préfixes dans LuckPerms arrive avec celui que LuckPerms affiche en premier, et le rapport le dit.

---

## 4. Tracks (échelles de promotion)

**Fait.** Un track est une liste ordonnée de grades, rangée dans `grades.json` à côté des grades qu'il
nomme ; `/customperm track` le construit et promeut ou rétrograde un joueur d'un cran, comme l'onglet Tracks
de la page Joueurs. L'import et l'export transportent les tracks dans les deux sens. Voir le changelog et le
README.

**Ce qui reste.** Promouvoir dans un monde, ce que LuckPerms permet avec un contexte : un cran ici est un
grade détenu partout. Et une permission par track, là où LuckPerms peut laisser un modérateur promouvoir
sur une seule échelle : ici déplacer un joueur demande `customperm.manage.grades`, comme assigner un grade.

---

## 5. Répondre aux tests de permission des autres mods

**De quoi il s'agit.** Une installation LuckPerms porte en général des nœuds que d'autres mods lisent.
CustomPerm ne répond pas à ces tests, donc stocker un tel nœud reviendrait à stocker une chaîne que rien ne
lit.

**Ce qu'il faudra.** NeoForge a le mécanisme : un mod peut enregistrer le handler qui répond aux tests de
`PermissionAPI`, et les mods déclarent leurs nœuds via la même API. S'enregistrer comme ce handler est ce qui
transforme ces nœuds de chaînes mortes en permissions que CustomPerm décide. Un seul handler est enregistré
pour le serveur et LuckPerms en enregistre un : lequel répond, et ce qu'il advient d'un nœud qu'aucun des
deux ne connaît, se tranche avant d'écrire quoi que ce soit.

**En attendant.** Les mods qui lisent les permissions via LuckPerms ont besoin de LuckPerms ; CustomPerm
fonctionne à côté. Un import laisse ces nœuds et les compte sur les groupes, et ne les lit même pas sur les
joueurs.
