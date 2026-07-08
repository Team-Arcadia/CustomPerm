# CustomPerm 1.0.5 — Beta

> ⚠️ **Beta build.** This release is functionally complete but still undergoing final testing. Use it on staging / test servers first, and please report any issue on the tracker before running it on production.

**Compatibility:** Minecraft 1.21.1 · NeoForge 21.1.221+ · Java 21 · LuckPerms 5.4.150+ (optional) · TesseraUI 1.0+ (optional, client-side)

---

## What's new in 1.0.5

### Added

- **Optional TesseraUI admin panel** — `/customperm gui` opens a graphical landing menu with buttons to the **Grades**, **Aliases** and **Status** screens (each screen has a `< Menu` button to return), and `/customperm gui grades|aliases|status` jumps straight to a screen. Requires [TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui) installed **client-side**. TesseraUI is a soft dependency: without it, the command replies that the GUI is unavailable and every text command keeps working exactly as before. The GUI never reimplements CRUD or permission logic — every button dispatches the same `/customperm` command.

### Changed

- **Direct command exposure now works *with* LuckPerms installed**, not only without it. `/customperm command add/remove/list` is accepted regardless of backend, and the `customperm.command.<name>` node is resolved by LuckPerms when present (grant it with `/lp`, wildcards like `customperm.command.*` included) or by the internal grades otherwise. CustomPerm now re-asserts an additive permission gate over each exposed command's whole subtree so it survives LuckPerms' Brigadier injector. The gate is additive — it never removes existing LuckPerms/vanilla gating. Grade subcommands (`/customperm grade ...`) stay LuckPerms-delegated (use `/lp`).
- **Per-command rate limits** — cap how many times a player may run a given exposed command or alias within a sliding window (e.g. `/customperm ratelimit set observable 10 3600` = 10 uses/hour/player).
- **`/customperm ratelimit set|enable|disable|remove|list`** — configure, toggle and inspect limits. `disable` keeps the configured numbers for easy re-enabling.
- New **`ratelimits.json`** config file (per-server, hot-reloadable, backed up alongside the other configs). Counters are in-memory only (reset on restart) and tracked per player and per command; console/command-block invocations are never limited.

### Fixed

- The TesseraUI GUI network channel is registered as `optional()` — vanilla clients (and clients without CustomPerm) can still join a server running this build, preserving the server-side-only guarantee.
- The GUI sync payload handler is dispatched behind an `FMLEnvironment.dist.isClient()` guard, so a real dedicated server never tries to resolve client-only classes.
- A **client** running CustomPerm but **not** TesseraUI no longer crashes with `NoClassDefFoundError: com/tesseraui/TesseraScreen`. All screen interaction is now routed through a lazily-loaded bridge behind the `isTesseraUiPresent()` guard, so TesseraUI types are only linked on a client that actually has TesseraUI.

---

## Please help us test

Because this is a beta, these areas benefit most from field testing:

- [ ] `/customperm command add` under **LuckPerms** — grant `customperm.command.*` via `/lp` and confirm the command unlocks for a non-op player.
- [ ] Rate limits — verify the sliding window, the per-player scoping, and that `disable`/`enable` preserve the numbers.
- [ ] TesseraUI panel on a client **with** the mod, and graceful fallback on a client **without** it.
- [ ] A **vanilla** client still connecting to a server running this build.
- [ ] `/customperm reload` after editing `ratelimits.json`.

Report issues with your server type (dedicated / integrated), LuckPerms version, and whether TesseraUI is installed.

---
---

# CustomPerm 1.0.5 — Bêta

> ⚠️ **Version bêta.** Cette version est fonctionnellement complète mais encore en phase de tests finaux. À utiliser d'abord sur un serveur de test / staging, et merci de signaler tout problème sur le tracker avant une mise en production.

**Compatibilité :** Minecraft 1.21.1 · NeoForge 21.1.221+ · Java 21 · LuckPerms 5.4.150+ (optionnel) · TesseraUI 1.0+ (optionnel, côté client)

---

## Nouveautés de la 1.0.5

### Ajouts

- **Panneau d'administration TesseraUI optionnel** — `/customperm gui` ouvre un menu graphique avec des boutons vers les écrans **Grades**, **Alias** et **Statut** (chaque écran a un bouton `< Menu` pour revenir), et `/customperm gui grades|aliases|status` saute directement à un écran. Nécessite [TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui) installé **côté client**. TesseraUI est une dépendance douce : sans elle, la commande indique que l'interface est indisponible et toutes les commandes texte continuent de fonctionner comme avant. L'interface ne réimplémente aucune logique CRUD ou de permission — chaque bouton relance la commande `/customperm` correspondante.

### Modifications

- **L'exposition directe de commandes fonctionne désormais *avec* LuckPerms installé**, plus seulement sans lui. `/customperm command add/remove/list` est accepté quel que soit le backend, et le nœud `customperm.command.<nom>` est résolu par LuckPerms s'il est présent (attribution via `/lp`, wildcards `customperm.command.*` inclus) ou par les grades internes sinon. CustomPerm réapplique désormais une barrière de permission additive sur tout le sous-arbre de chaque commande exposée, afin de survivre à l'injecteur Brigadier de LuckPerms. La barrière est additive — elle ne retire jamais le contrôle LuckPerms/vanilla existant. Les sous-commandes de grade (`/customperm grade ...`) restent déléguées à LuckPerms (via `/lp`).
- **Limites d'utilisation par commande** — plafonne le nombre d'exécutions d'une commande exposée ou d'un alias par joueur dans une fenêtre glissante (ex. `/customperm ratelimit set observable 10 3600` = 10 usages/heure/joueur).
- **`/customperm ratelimit set|enable|disable|remove|list`** — configure, active/désactive et inspecte les limites. `disable` conserve les valeurs configurées pour une réactivation simple.
- Nouveau fichier de config **`ratelimits.json`** (par serveur, rechargeable à chaud, sauvegardé avec les autres configs). Les compteurs sont uniquement en mémoire (réinitialisés au redémarrage) et suivis par joueur et par commande ; les invocations console/bloc de commande ne sont jamais limitées.

### Correctifs

- Le canal réseau de l'interface TesseraUI est enregistré en `optional()` — les clients vanilla (et sans CustomPerm) peuvent toujours rejoindre un serveur sous cette version, préservant la garantie « côté serveur uniquement ».
- Le gestionnaire de payload de synchro de l'interface est dispatché derrière une garde `FMLEnvironment.dist.isClient()`, afin qu'un vrai serveur dédié ne tente jamais de résoudre des classes réservées au client.
- Un **client** exécutant CustomPerm mais **pas** TesseraUI ne plante plus avec `NoClassDefFoundError: com/tesseraui/TesseraScreen`. Toute interaction avec les écrans passe désormais par un pont chargé paresseusement derrière la garde `isTesseraUiPresent()`, de sorte que les types TesseraUI ne sont liés que sur un client qui possède réellement TesseraUI.

---

## Aidez-nous à tester

Comme il s'agit d'une bêta, ces points bénéficient le plus de tests en conditions réelles :

- [ ] `/customperm command add` sous **LuckPerms** — attribuez `customperm.command.*` via `/lp` et vérifiez que la commande se débloque pour un joueur non-op.
- [ ] Limites d'usage — vérifiez la fenêtre glissante, le suivi par joueur, et que `disable`/`enable` conservent les valeurs.
- [ ] Panneau TesseraUI sur un client **avec** le mod, et repli propre sur un client **sans**.
- [ ] Un client **vanilla** qui se connecte encore à un serveur sous cette version.
- [ ] `/customperm reload` après édition de `ratelimits.json`.

Signalez les problèmes en précisant le type de serveur (dédié / intégré), la version de LuckPerms, et la présence ou non de TesseraUI.
