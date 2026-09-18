# Planned features

Features CustomPerm once lacked, mostly the parts of a LuckPerms setup it had no equivalent for, and how
each was answered. They were listed here as deliberate refusals until 2026-09-18, then asked for. Whatever an
import still cannot carry is named in its report rather than dropped in silence.

**Français :** [Fonctionnalités envisagées](#fonctionnalités-envisagées)

---

## 1. Temporary entries (expiry)

**Done.** A node on a grade or a player, a grade a player holds and a refusal can carry an expiry, set with
a duration on the commands and the pages; the resolver ignores what has run out and a sweep tidies the file.
See the changelog and the README.

---

## 2. Contextual entries (per world)

**Done.** A node on a grade or a player, and a grade a player holds, can be limited to one world with
`world=<dimension>` on the commands and a world box on the pages. It outranks the same holder's entry
without a world at the same specificity, and a player's command tree is sent again when they change world.
A context is stored as `key=value` pairs, so the `server=<name>` a cluster mode would add fits the same
file and resolver. The import and the export carry entries limited to a single world both ways. An entry
with no context keeps the path it had: the benchmark shows no change. See the changelog and the README.

---

## 3. Chat metadata beyond prefixes and suffixes

**Done.** A grade and a player carry chat prefixes and suffixes, each with a priority and an optional
expiry: the highest priority shows, or several in a row when stacking is on, and at equal priority the
player's own, then the heaviest grade, then the nearest ancestor. Names can be decorated with them, from the
grades or from LuckPerms. The written answer on signed chat: the name is decorated through NeoForge's name
event, never the message, so every message stays signed and reportable. The cost is that the name carries
the prefix wherever the game shows it, not only in chat. The import and the export carry prefixes and
suffixes both ways. See the changelog and the README.

---

## 4. Tracks (promotion ladders)

**Done.** A track is an ordered list of grades, kept in `grades.json` beside the grades it names;
`/customperm track` builds it and promotes or demotes a player one rung, as does the Tracks tab of the
Players page. The import and the export carry tracks both ways. See the changelog and the README.

---

## 5. Answering the permission checks of other mods

**Done.** CustomPerm offers a NeoForge permission handler that answers, from the grades, the checks mods
make on the nodes they declare. NeoForge keeps a single handler, named in `neoforge-server.toml`, with no
chain between handlers, and LuckPerms takes that value when it is still the default. So CustomPerm selects
itself only without LuckPerms, when the value is still the default and `answerOtherMods` allows, and never
replaces a value an admin chose. The import carries the nodes mods declared, on groups and players. See the
changelog and the README.

---

# Fonctionnalités envisagées

Les fonctionnalités qui manquaient à CustomPerm, surtout les parties d'une installation LuckPerms dont il
n'avait pas l'équivalent, et la réponse apportée à chacune. Elles étaient listées ici comme des refus assumés
jusqu'au 2026-09-18, puis ont été demandées. Ce qu'un import ne peut toujours pas reprendre est nommé dans
son rapport au lieu d'être abandonné en silence.

---

## 1. Entrées temporaires (expiration)

**Fait.** Un nœud sur un grade ou un joueur, un grade tenu par un joueur et un refus peuvent porter une
expiration, posée avec une durée dans les commandes et les pages ; le résolveur ignore ce qui a expiré et un
balayage nettoie le fichier. Voir le changelog et le README.

---

## 2. Entrées contextuelles (par monde)

**Fait.** Un nœud sur un grade ou un joueur, et un grade tenu par un joueur, peuvent être limités à un monde
avec `world=<dimension>` dans les commandes et une case monde dans les pages. À spécificité égale, l'entrée
l'emporte sur celle sans monde du même détenteur, et l'arbre de commandes d'un joueur est renvoyé quand il
change de monde. Un contexte est stocké en paires `clé=valeur`, donc le `server=<nom>` qu'ajouterait un mode
cluster entre dans le même fichier et le même résolveur. L'import et l'export transportent dans les deux sens
les entrées limitées à un seul monde. Une entrée sans contexte garde son chemin : le benchmark ne montre
aucun écart. Voir le changelog et le README.

---

## 3. Métadonnées de chat au-delà des préfixes et suffixes

**Fait.** Un grade et un joueur portent des préfixes et des suffixes de chat, chacun avec une priorité et
une expiration facultative : la priorité la plus haute s'affiche, ou plusieurs à la suite quand l'empilement
est actif, et à priorité égale celui du joueur, puis le grade le plus lourd, puis l'ancêtre le plus proche.
Les noms peuvent en être décorés,
depuis les grades ou depuis LuckPerms. La réponse écrite sur le chat signé : le nom est décoré par
l'événement de nom de NeoForge, jamais le message, donc chaque message reste signé et signalable. Le prix est
que le nom porte le préfixe partout où le jeu l'affiche, pas seulement dans le chat. L'import et l'export
transportent préfixes et suffixes dans les deux sens. Voir le changelog et le README.

---

## 4. Tracks (échelles de promotion)

**Fait.** Un track est une liste ordonnée de grades, rangée dans `grades.json` à côté des grades qu'il
nomme ; `/customperm track` le construit et promeut ou rétrograde un joueur d'un cran, comme l'onglet Tracks
de la page Joueurs. L'import et l'export transportent les tracks dans les deux sens. Voir le changelog et le
README.

---

## 5. Répondre aux tests de permission des autres mods

**Fait.** CustomPerm propose un handler de permissions NeoForge qui répond, depuis les grades, aux tests
que les mods font sur les nœuds qu'ils déclarent. NeoForge garde un seul handler, nommé dans
`neoforge-server.toml`, sans chaîne entre handlers, et LuckPerms prend cette valeur quand elle est encore
celle par défaut. CustomPerm ne se sélectionne donc que sans LuckPerms, quand la valeur est encore celle par
défaut et que `answerOtherMods` le permet, et ne remplace jamais une valeur choisie par un admin. L'import
transporte les nœuds déclarés par les mods, sur les groupes et les joueurs. Voir le changelog et le README.
