# CustomPerm

**Granular server-side command permissions for Minecraft NeoForge — grant individual vanilla or modded commands to non-op players, with or without LuckPerms.**

> 🧪 **Current build 1.0.5 is published on the Beta channel** while final testing wraps up. It is feature-complete and safe to try on a test server; feedback is welcome.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg) ![NeoForge 21.1.221+](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg) ![Java 21](https://img.shields.io/badge/Java-21-red.svg) ![License GPL-3.0-only](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)

---

## Why CustomPerm?

Vanilla Minecraft only knows **op** (every command) or **non-op** (no management commands). There is no middle ground.

CustomPerm lets you hand out **exactly** the commands you choose to non-op players — no full op required:

- Let a player use `/gamemode spectator` but never `/op`.
- Give `/give` to a VIP rank without unlocking `/ban`.
- Chain several commands into a single custom command (aliases / macros).

It integrates natively with **LuckPerms** when installed, and otherwise ships its own JSON grade system — so it works on any server.

---

## Features

- **Granular command permissions** — expose any vanilla or modded root command with `/customperm command add <name>`, then grant it per rank.
- **Works with *and* without LuckPerms** — `customperm.command.<name>` is resolved by LuckPerms when installed (grant via `/lp`, wildcards included) or by the internal grades otherwise.
- **Default-deny** — nothing is exposed by default; non-exposed commands keep their original vanilla/modded requirement.
- **Internal JSON RBAC** — multi-grade permissions, explicit `DENY` over `ALLOW`, and wildcard nodes (`*`, `customperm.command.*`, `customperm.alias.*`), no external plugin needed.
- **Aliases / macros** — build custom commands like `/fly`, `/heal`, `/starter` from one or more steps; steps run with op-4 authority so admin-signed macros can call op-only commands. Recursion is bounded, shadowing is warned, and `/customperm` is reserved.
- **Per-command rate limits** *(new in 1.0.5)* — cap how many times a player may run a command or alias in a sliding window via `/customperm ratelimit ...`.
- **Optional TesseraUI admin panel** *(new in 1.0.5)* — `/customperm gui` opens a graphical menu for Grades, Aliases and Status when [TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui) is installed client-side. Purely optional — everything stays fully usable from chat.
- **Server-side only** — no client mod required. Vanilla clients connect without issue; the optional GUI channel is registered `optional()` so it never blocks the handshake.
- **Atomic hot-reload** — `/customperm reload` loads all configs as one transaction; invalid JSON keeps the previous snapshot. Timestamped backups are kept automatically.
- **Configurable LuckPerms fail-safe** — if LuckPerms becomes unavailable at runtime, `settings.json` chooses fail-closed (`deny`, default) or internal fallback (`internal`).
- **Built-in diagnostics** — `/customperm status`, `/customperm scan`, `/customperm debug`, `/customperm test`.

---

## Installation

1. Install **NeoForge 21.1.221+** for **Minecraft 1.21.1** (Java 21).
2. Drop `customperm-1.0.5.jar` into your server's `mods/` folder.
3. *(Optional)* Add **[LuckPerms](https://luckperms.net/download)** (NeoForge 1.21.1 build) for full RBAC.
4. *(Optional)* Have admins install **[TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui)** client-side for `/customperm gui`.
5. Start the server. You should see `[CustomPerm] Ready — backend=...` in the log.

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

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ |
| Java | 21 |
| LuckPerms | 5.4.150+ (optional) |
| TesseraUI | 1.0+ (optional, client-side) |

**License:** GNU General Public License v3.0 only (`GPL-3.0-only`) · **Author:** THEFricadelle

---
---

# CustomPerm (Version Française)

**Système de permissions de commandes côté serveur pour Minecraft NeoForge — accordez des commandes vanilla ou moddées précises à des joueurs non-op, avec ou sans LuckPerms.**

> 🧪 **La version 1.0.5 est publiée sur le canal Bêta** le temps de finaliser les tests. Elle est complète et sûre à essayer sur un serveur de test ; vos retours sont les bienvenus.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg) ![NeoForge 21.1.221+](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg) ![Java 21](https://img.shields.io/badge/Java-21-red.svg) ![License GPL-3.0-only](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)

---

## Pourquoi CustomPerm ?

Minecraft vanilla ne connaît que **op** (toutes les commandes) ou **non-op** (aucune commande de gestion). Aucun intermédiaire.

CustomPerm vous laisse distribuer **exactement** les commandes de votre choix à des joueurs non-op — sans op complet :

- Autoriser `/gamemode spectator` mais jamais `/op`.
- Donner `/give` à un rang VIP sans débloquer `/ban`.
- Enchaîner plusieurs commandes en une seule commande personnalisée (alias / macros).

Il s'intègre nativement à **LuckPerms** s'il est installé, et embarque sinon son propre système de grades JSON — il fonctionne donc sur n'importe quel serveur.

---

## Fonctionnalités

- **Permissions de commandes granulaires** — exposez n'importe quelle commande racine vanilla ou moddée avec `/customperm command add <nom>`, puis accordez-la par rang.
- **Fonctionne *avec et sans* LuckPerms** — `customperm.command.<nom>` est résolu par LuckPerms s'il est installé (via `/lp`, wildcards inclus) ou par les grades internes sinon.
- **Refus par défaut** — rien n'est exposé par défaut ; les commandes non exposées conservent leur exigence vanilla/moddée d'origine.
- **RBAC JSON interne** — permissions multi-grades, `DENY` explicite prioritaire sur `ALLOW`, et nœuds wildcard (`*`, `customperm.command.*`, `customperm.alias.*`), sans plugin externe.
- **Alias / macros** — créez des commandes comme `/fly`, `/heal`, `/starter` à partir d'une ou plusieurs étapes ; les étapes s'exécutent avec l'autorité op-4 pour que des macros validées par l'admin appellent des commandes op. La récursion est bornée, le masquage est signalé, et `/customperm` est réservé.
- **Limites d'usage par commande** *(nouveau en 1.0.5)* — plafonnez le nombre d'exécutions d'une commande ou d'un alias par joueur sur une fenêtre glissante via `/customperm ratelimit ...`.
- **Panneau d'administration TesseraUI optionnel** *(nouveau en 1.0.5)* — `/customperm gui` ouvre un menu graphique pour les Grades, Alias et Statut lorsque [TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui) est installé côté client. Totalement optionnel — tout reste utilisable depuis le chat.
- **Côté serveur uniquement** — aucun mod client requis. Les clients vanilla se connectent sans souci ; le canal de l'interface optionnelle est en `optional()` et ne bloque jamais la connexion.
- **Rechargement à chaud atomique** — `/customperm reload` charge toutes les configs en une transaction ; un JSON invalide conserve l'instantané précédent. Des sauvegardes horodatées sont conservées automatiquement.
- **Repli LuckPerms configurable** — si LuckPerms devient indisponible à l'exécution, `settings.json` choisit le refus (`deny`, défaut) ou le repli interne (`internal`).
- **Diagnostics intégrés** — `/customperm status`, `/customperm scan`, `/customperm debug`, `/customperm test`.

---

## Installation

1. Installez **NeoForge 21.1.221+** pour **Minecraft 1.21.1** (Java 21).
2. Placez `customperm-1.0.5.jar` dans le dossier `mods/` de votre serveur.
3. *(Optionnel)* Ajoutez **[LuckPerms](https://luckperms.net/download)** (build NeoForge 1.21.1) pour un RBAC complet.
4. *(Optionnel)* Faites installer **[TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui)** côté client aux admins pour `/customperm gui`.
5. Démarrez le serveur. La ligne `[CustomPerm] Ready — backend=...` doit apparaître dans les logs.

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

## Prérequis

| Composant | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ |
| Java | 21 |
| LuckPerms | 5.4.150+ (optionnel) |
| TesseraUI | 1.0+ (optionnel, côté client) |

**Licence :** GNU General Public License v3.0 only (`GPL-3.0-only`) · **Auteur :** THEFricadelle
