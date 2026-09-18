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

**What it is.** An expiry on a node, on a grade a player holds, or on a refusal: a donor rank for thirty
days, a trial moderator for a week. LuckPerms has it, CustomPerm does not.

**What it will take.** A timestamp beside each entry rather than a richer node type, so a file written
before the field stays valid and reads as permanent. The resolver ignores an entry whose time has passed, so
an expiry is right even if nothing swept; a periodic sweep then tidies the file and resyncs the command tree
of the players concerned, or their client keeps offering a command that now refuses.

**Until then.** Nothing expires: an entry stays until it is removed by hand. An import leaves temporary
entries behind and counts them, rather than importing them as permanent, which would grant more than the
source did.

---

## 2. Contextual entries (per world)

**What it is.** An entry that applies in one world only. LuckPerms can key on far more than that; the demand
here is worlds.

**What it will take.** A permission check takes a player and a node today. A context adds a dimension on a
path called from a Brigadier `requires()` predicate, which runs for every player each time the command tree
is built, and the tree is built per player rather than per world: a permission that changes with the world
means resyncing that player when they change dimension. An entry with no context has to keep the path it has
now, or every server pays for a feature few use.

**Until then.** Every entry is global: a node granted is granted in every dimension. An import leaves
contextual entries behind and counts them, since importing one as global would grant it everywhere and
dropping it would take away something the source granted.

---

## 3. Chat prefixes and suffixes

**What it is.** A prefix and a suffix shown in chat, carried by a grade or by a player. LuckPerms stores
them; on NeoForge nothing renders them by itself.

**What it will take.** The ranking that already decides between grades decides between prefixes too, minus
the specificity step, since there is no such thing between two prefixes. The part that needs an answer first
is not the resolution: chat messages are signed, so rewriting one breaks the signature chain, and the usual
workaround, sending a system message instead, loses reporting and the secure chat indicator. That trade-off
gets a written answer before any of it is built.

**Until then.** Use a chat mod, or keep LuckPerms and a mod that renders its metadata. An import leaves
prefixes, suffixes and meta behind and counts them.

---

## 4. Tracks (promotion ladders)

**What it is.** An ordered list of grades, so promote and demote move a player one rung at a time.

**What it will take.** A `tracks.json`, the commands and an interface section. Small, and it buys
convenience rather than capability: a track grants nothing by itself, and assigning a grade already does the
work in one command. It is worth doing because a server coming from LuckPerms has tracks and expects to find
them.

**Until then.** Assign and unassign grades directly. An import leaves tracks behind and counts them.

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

**De quoi il s'agit.** Une expiration sur un nœud, sur un grade détenu par un joueur, ou sur un refus : un
rang de donateur pour trente jours, un modérateur à l'essai pour une semaine. LuckPerms l'a, CustomPerm non.

**Ce qu'il faudra.** Un horodatage à côté de chaque entrée plutôt qu'un type de nœud plus riche, pour qu'un
fichier écrit avant le champ reste valide et se lise comme permanent. Le résolveur ignore une entrée dont
l'heure est passée, donc une expiration est juste même si rien n'a balayé ; un balayage périodique range
ensuite le fichier et resynchronise l'arbre de commandes des joueurs concernés, sinon leur client continue de
proposer une commande qui refuse désormais.

**En attendant.** Rien n'expire : une entrée reste jusqu'à ce qu'elle soit retirée à la main. Un import
laisse les entrées temporaires et les compte, plutôt que de les importer comme permanentes, ce qui
accorderait plus que la source.

---

## 2. Entrées contextuelles (par monde)

**De quoi il s'agit.** Une entrée qui ne s'applique que dans un monde. LuckPerms sait indexer sur bien plus
que cela ; la demande ici porte sur les mondes.

**Ce qu'il faudra.** Un test de permission prend aujourd'hui un joueur et un nœud. Un contexte ajoute une
dimension sur un chemin appelé depuis un prédicat `requires()` de Brigadier, exécuté pour chaque joueur à
chaque construction de l'arbre de commandes, et cet arbre est construit par joueur et non par monde : une
permission qui change avec le monde impose de resynchroniser ce joueur au changement de dimension. Une entrée
sans contexte doit garder le chemin actuel, sinon tous les serveurs paient pour une fonctionnalité que peu
utilisent.

**En attendant.** Toutes les entrées sont globales : un nœud accordé l'est dans toutes les dimensions. Un
import laisse les entrées contextuelles et les compte, puisque en importer une en global l'accorderait
partout et la jeter retirerait ce que la source accordait.

---

## 3. Préfixes et suffixes de chat

**De quoi il s'agit.** Un préfixe et un suffixe affichés dans le chat, portés par un grade ou par un joueur.
LuckPerms les stocke ; sur NeoForge, rien ne les affiche de lui-même.

**Ce qu'il faudra.** Le classement qui départage déjà les grades départage aussi les préfixes, sans l'étape
de spécificité, qui n'a pas de sens entre deux préfixes. Ce qui demande une réponse d'abord n'est pas la
résolution : les messages de chat sont signés, donc en réécrire un casse la chaîne de signature, et le
contournement habituel, envoyer un message système à la place, perd le signalement et l'indicateur de chat
sécurisé. Cet arbitrage reçoit une réponse écrite avant qu'on en construise quoi que ce soit.

**En attendant.** Utiliser un mod de chat, ou garder LuckPerms avec un mod qui affiche ses métadonnées. Un
import laisse préfixes, suffixes et meta, et les compte.

---

## 4. Tracks (échelles de promotion)

**De quoi il s'agit.** Une liste ordonnée de grades, pour que promote et demote fassent monter ou descendre
un joueur d'un cran.

**Ce qu'il faudra.** Un `tracks.json`, les commandes et une section d'interface. Petit, et cela achète du
confort plutôt qu'une capacité : un track n'accorde rien par lui-même, et assigner un grade fait déjà le
travail en une commande. Cela vaut d'être fait parce qu'un serveur qui vient de LuckPerms a des tracks et
s'attend à les retrouver.

**En attendant.** Assigner et désassigner les grades directement. Un import laisse les tracks et les compte.

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
