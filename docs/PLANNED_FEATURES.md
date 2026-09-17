# Planned features

Ideas that are deliberately not implemented, with the reason and what implementing them would take.
Nothing here is a commitment or a schedule. What is actually being worked on lives in the changelog.

**Français :** [Fonctionnalités envisagées](#fonctionnalités-envisagées)

---

## Context

CustomPerm governs command permissions. LuckPerms governs permissions, chat metadata, promotion ladders
and contextual rules. The items below are the part of that surface CustomPerm does not cover, listed
because a server coming from LuckPerms will ask what happens to them.

---

## 1. Temporary nodes (expiry)

**What it is.** LuckPerms can attach an expiry date to a node or to a group membership: a donor rank for
thirty days, a trial moderator for a week.

**Why it is not implemented.** An internal grade holds plain node strings in a set. Adding an expiry turns
every node into a node plus metadata, which reaches the config format, the admin commands, the interface
and the resolver at once, and adds a scheduled sweep plus a command-tree resync when an entry lapses.

**What it would take.** A per-entry timestamp, a periodic sweep on the server thread, a resync of the
command tree of the affected players, and the migration of existing files whose entries have no timestamp.

**Until then.** Nothing expires. An entry stays until it is removed by hand.

---

## 2. Contextual permissions (per world, per server)

**What it is.** In LuckPerms a node can apply only in one world, on one server of a network, or under any
other context an admin defines.

**Why it is not implemented.** A permission check here takes a player and a node, nothing else. Contexts
would add a dimension to every check on a path called from a Brigadier `requires()` predicate, which runs
for every player when the command tree is built and re-sent. The cost is real and the demand, for a single
server governing commands, is low.

**What it would take.** A context on each entry, a context resolved at check time, and a resolution rule
saying how a contextual entry ranks against a global one.

**Until then.** Every entry is global. A node granted is granted in every dimension.

---

## 3. Chat metadata: prefix, suffix, meta, display name

**What it is.** LuckPerms carries the prefix and suffix shown in chat, arbitrary meta key-value pairs, and a
display name per group.

**Why it is not implemented.** CustomPerm is not a chat plugin and does not render chat. This is out of
scope rather than postponed.

**Until then.** Use a chat mod for it, or keep LuckPerms, which CustomPerm integrates with rather than
replaces.

---

## 4. Tracks (promotion ladders)

**What it is.** An ordered list of groups, so `promote` and `demote` move a player one rung at a time.

**Why it is not implemented.** A track is an admin convenience built on top of group assignment. It grants
nothing on its own, and assigning a grade already does the work in one command.

**What it would take.** A `tracks.json`, the two commands and an interface section. Small, but it buys
convenience, not capability.

---

## 5. Permission nodes belonging to other mods

**What it is.** A LuckPerms setup usually carries nodes read by other mods, not by CustomPerm.

**Why it is not implemented.** CustomPerm's internal backend answers permission checks for its own nodes and
for the commands it governs. It is not a general-purpose permission provider for other mods, so storing
their nodes would store strings nothing reads.

**Until then.** Mods that read permissions through LuckPerms need LuckPerms. CustomPerm runs alongside it.

---

# Fonctionnalités envisagées

Idées volontairement non implémentées, avec la raison et ce que leur implémentation demanderait. Rien ici
n'est un engagement ni un calendrier. Ce qui est réellement en cours est dans le changelog.

---

## Contexte

CustomPerm gère les permissions de commandes. LuckPerms gère les permissions, les métadonnées de chat, les
échelles de promotion et les règles contextuelles. Les points ci-dessous sont la part de cette surface que
CustomPerm ne couvre pas, listée parce qu'un serveur qui vient de LuckPerms demandera ce qu'elle devient.

---

## 1. Nœuds temporaires (expiration)

**De quoi il s'agit.** LuckPerms peut attacher une date d'expiration à un nœud ou à une appartenance de
groupe : un rang de donateur pour trente jours, un modérateur à l'essai pour une semaine.

**Pourquoi ce n'est pas fait.** Un grade interne contient des chaînes de nœuds dans un ensemble. Ajouter une
expiration transforme chaque nœud en nœud plus métadonnées, ce qui touche d'un coup le format de config, les
commandes d'administration, l'interface et le résolveur, et ajoute un balayage périodique plus une
resynchronisation de l'arbre de commandes quand une entrée arrive à terme.

**Ce qu'il faudrait.** Un horodatage par entrée, un balayage périodique sur le thread serveur, une
resynchronisation de l'arbre de commandes des joueurs concernés, et la migration des fichiers existants dont
les entrées n'ont pas d'horodatage.

**En attendant.** Rien n'expire. Une entrée reste jusqu'à ce qu'elle soit retirée à la main.

---

## 2. Permissions contextuelles (par monde, par serveur)

**De quoi il s'agit.** Dans LuckPerms, un nœud peut ne s'appliquer que dans un monde, sur un serveur d'un
réseau, ou sous n'importe quel autre contexte défini par l'admin.

**Pourquoi ce n'est pas fait.** Un test de permission ici prend un joueur et un nœud, rien d'autre. Les
contextes ajouteraient une dimension à chaque test, sur un chemin appelé depuis un prédicat `requires()` de
Brigadier, exécuté pour chaque joueur à la construction et au renvoi de l'arbre de commandes. Le coût est
réel et la demande, pour un serveur unique qui gère des commandes, est faible.

**Ce qu'il faudrait.** Un contexte sur chaque entrée, un contexte résolu au moment du test, et une règle de
résolution disant comment une entrée contextuelle se classe face à une entrée globale.

**En attendant.** Toutes les entrées sont globales. Un nœud accordé l'est dans toutes les dimensions.

---

## 3. Métadonnées de chat : préfixe, suffixe, meta, nom d'affichage

**De quoi il s'agit.** LuckPerms porte le préfixe et le suffixe affichés dans le chat, des paires clé-valeur
arbitraires, et un nom d'affichage par groupe.

**Pourquoi ce n'est pas fait.** CustomPerm n'est pas un mod de chat et n'affiche pas le chat. C'est hors
périmètre, pas reporté.

**En attendant.** Utiliser un mod de chat, ou garder LuckPerms, avec lequel CustomPerm s'intègre au lieu de
le remplacer.

---

## 4. Tracks (échelles de promotion)

**De quoi il s'agit.** Une liste ordonnée de groupes, pour que `promote` et `demote` fassent monter ou
descendre un joueur d'un cran.

**Pourquoi ce n'est pas fait.** Un track est un confort d'administration posé sur l'assignation de groupe. Il
n'accorde rien par lui-même, et assigner un grade fait déjà le travail en une commande.

**Ce qu'il faudrait.** Un `tracks.json`, les deux commandes et une section d'interface. Petit, mais cela
achète du confort, pas une capacité.

---

## 5. Nœuds de permission appartenant à d'autres mods

**De quoi il s'agit.** Une installation LuckPerms porte en général des nœuds lus par d'autres mods, pas par
CustomPerm.

**Pourquoi ce n'est pas fait.** Le backend interne de CustomPerm répond aux tests de permission pour ses
propres nœuds et pour les commandes qu'il gère. Ce n'est pas un fournisseur de permissions généraliste pour
les autres mods : stocker leurs nœuds reviendrait à stocker des chaînes que rien ne lit.

**En attendant.** Les mods qui lisent les permissions via LuckPerms ont besoin de LuckPerms. CustomPerm
fonctionne à côté.
