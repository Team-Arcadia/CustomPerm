# CustomPerm

> Granular permission system for Minecraft NeoForge — grant individual vanilla commands to non-op players, with or without LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-1.0.5-brightgreen.svg)]()

---

## Why this mod

Vanilla Minecraft has a binary system: a player is either **op** (every command) or **non-op** (no management commands). No middle ground.

CustomPerm lets you grant **precisely** the commands you want to non-op players, without giving them full op. For example:

- You want a player to use `/gamemode spectator` but not `/op`? Done.
- Grant `/give` to a VIP rank without enabling `/ban`? Done.
- Build macros (aliases) that chain multiple commands into one? Done.

The mod natively integrates with **LuckPerms** if installed, otherwise it ships its own JSON-backed grade system. With LuckPerms installed, CustomPerm resolves both its alias nodes (`customperm.alias.*`) and its direct-command nodes (`customperm.command.*`) through LuckPerms. Without LuckPerms, both are resolved through the internal grades.

---

## Table of contents

- [Features](#features)
- [Installation](#installation)
- [Singleplayer and LAN worlds](#singleplayer-and-lan-worlds)
- [Quick start](#quick-start)
- [Commands](#commands)
- [Permission nodes](#permission-nodes)
- [Configuration files](#configuration-files)
- [Common workflows](#common-workflows)
- [Aliases and macros](#aliases-and-macros)
- [Security considerations](#security-considerations)
- [Diagnostics and troubleshooting](#diagnostics-and-troubleshooting)
- [Building from source](#building-from-source)
- [Testing](#testing)
- [How it works (technical)](#how-it-works-technical)
- [Compatibility with other mods](#compatibility-with-other-mods)
- [Known limitations](#known-limitations)
- [License](#license)

---

## Features

- **Granular command permissions without LuckPerms** — the internal backend can expose vanilla or third-party root commands with `/customperm command add <name>`.
- **Default-deny model** — nothing is exposed by default; non-exposed commands keep their original vanilla or modded requirement.
- **Internal JSON backend** — manage grades, player assignments and permission nodes without any external permissions plugin.
- **LuckPerms backend** — automatically uses LuckPerms when a compatible version is installed.
- **LuckPerms version gate** — requires LuckPerms `5.4.150+`; older or prerelease-style versions are rejected for safety.
- **Configurable LP degradation fallback** — if LuckPerms becomes unavailable at runtime, `settings.json` controls whether CustomPerm fails closed (`deny`, default) or switches to the internal backend (`internal`).
- **Backend visibility** — boot logs and `/customperm status`, `/customperm debug`, `/customperm test` report whether the active backend is Internal, LuckPerms, Internal fallback from LuckPerms, or deny mode.
- **Activity log** — every admin change (commands, interface, LuckPerms editor, `/lp`) is recorded with who, when and the result; commands typed by players can be recorded too, off by default, with the arguments of private messages and passwords masked. Daily files in the world folder, kept 30 days by default, and a Logs page in the interface.
- **Admin alerts** — when LuckPerms becomes unavailable or a config file fails to load, every online op (level 2+) gets a chat alert, ops who join later get it on login, and `/customperm status` lists it until it is resolved.
- **Multi-grade RBAC** — a player can hold multiple internal grades; permissions are resolved as a union of all assigned grades.
- **Explicit DENY support** — internal grades support `deniedPermissions`. The most specific entry wins, like LuckPerms (exact node, then `a.b.*`, then `*`), and a DENY wins at the same level.
- **Per-player nodes** — a node can be carried by one player rather than by a grade, the exception a single player gets without inventing a grade for them. It wins over their grades at the same level, whatever a grade weighs, but a more specific grade node still wins. `/customperm user addperm|adddeny`, or the Players page.
- **Refused grades** — a grade can refuse another wherever it would inherit it, and a player can refuse one wherever a grade of theirs would bring it, the default grade included. A refusal takes the grade out of the resolution; it never turns what that grade allows into a denial. `/customperm grade parent adddeny`, `/customperm user denygrade`, or the Grades page.
- **Grade inheritance** — a grade can inherit other grades, like a LuckPerms group parent. What a parent says applies where the grade says nothing as precise about the node, nearest ancestor first, so a grade overrides what it inherits while a more specific ancestor entry still wins. A cycle is refused when it is created, not when it is resolved.
- **Grade weight** — a grade carries a weight, like a LuckPerms group weight. It breaks a tie between two grades held by the same player that cover a node just as specifically: the heaviest decides, and a DENY still wins between equal weights. A weight never beats a more specific node, so it cannot be used to work around one.
- **Wildcard permission nodes** — `*`, `customperm.command.*`, and `customperm.alias.*` are supported, in both directions: a denied `*` refuses everything except explicit allows.
- **Administration behind a permission** — `/customperm` and the admin interface need op level 2 **and** explicitly granted nodes (`customperm.admin` to enter, `customperm.manage.<area>` to change), so a player made operator by mistake, level 4 included, gets nothing. The console always has access, and the host of a singleplayer or LAN world stands in for it.
- **Restrictable operators** — an explicit DENY applies to operators too, on both backends, so a player made op by mistake can be kept away from commands. A default grade applies to every player, and `gateAllCommands` extends CustomPerm's check to every command. See [Restricting operators](#restricting-operators).
- **Aliases and macros** — create custom top-level commands such as `/fly`, `/heal`, `/starter`, backed by one or more configured command steps.
- **Alias step editing** — append, remove and inspect individual alias steps with zero-based indices.
- **Alias elevation** — alias steps execute with op level 4 so admin-signed macros can call op-only commands.
- **Alias safety guards** — `/customperm` is reserved, blank alias steps are ignored, empty aliases are rejected, shadowing an existing command emits a warning, and recursive alias chains stop at depth 8.
- **Runtime alias registration** — aliases are added, replaced or removed on the live dispatcher without server restart; `/customperm reload` also applies additions, removals and edited steps from `aliases.json`.
- **Backend-agnostic command policy** — direct command exposure works with or without LuckPerms; the `customperm.command.<name>` node is resolved by LuckPerms when installed (grant via `/lp`), by the internal grades otherwise.
- **OP preservation by default** — an operator keeps every command whose node is not set; only an explicit DENY restricts them. The console is never restricted.
- **Client command-tree re-sync** — after internal changes or LuckPerms recalculation events, affected players receive an updated command tree.
- **Atomic hot-reload** — `/customperm reload` loads `grades.json`, `aliases.json`, `commands.json`, and `settings.json` as one transaction; invalid JSON keeps the previous snapshot.
- **Automatic config creation and normalization** — missing files, `{}` files, unknown fields, and explicit `null` collections are normalized into safe empty structures.
- **Automatic config backups** — successful reloads write timestamped backups and keep the latest three backups per config file.
- **Concurrent-safe config access** — the active snapshot uses an `AtomicReference`; saves are serialized and each file is replaced through a unique temporary file.
- **Diagnostics** — `/customperm status`, `/customperm scan`, `/customperm debug`, and `/customperm test` cover runtime inspection and troubleshooting.
- **CI release checks** — GitHub Actions runs GameTests, builds the distributable jar, and verifies required jar metadata.
- **Server-side only** — no client mod is required for core functionality. Vanilla clients (and clients without CustomPerm installed) connect to a CustomPerm server without issue: the network channels backing the admin interface are registered as `optional()`, so it never gates the connection handshake.
- **In-game admin interface** — `/customperm gui` opens a native admin interface on clients that have CustomPerm installed: no other client mod is needed. The server stays the authority: every action is re-checked, rate limited and logged. It includes an editor for the LuckPerms store when LuckPerms runs (see [In-game admin interface](#in-game-admin-interface)).

---

## Installation

> **Upgrading from 1.0.x?** Read [MIGRATION.md](MIGRATION.md) first: administering CustomPerm now needs granted
> permissions, and internal grades resolve wildcards differently. The server says so in its log at the first start.

### Requirements

- **Minecraft 1.21.1**
- **NeoForge 21.1.221** or newer
- **Java 21**
- (Optional but recommended) **LuckPerms 5.4.x or 5.5.x** for NeoForge

### Steps

1. Download CustomPerm from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/customperm) or [Modrinth](https://modrinth.com/mod/customperm).
2. Drop the jar into your server's `mods/` folder.
3. (Optional) Drop the [LuckPerms](https://luckperms.net/download) jar (NeoForge 1.21.1 build) alongside.
4. (Optional) Admin players who want the in-game interface install CustomPerm on their client too.
5. Start the server.

> **Where the builds are.** Published builds are distributed on [CurseForge](https://www.curseforge.com/minecraft/mc-mods/customperm) and [Modrinth](https://modrinth.com/mod/customperm). The current version there is **1.0.5 (beta)**; the next release will be **1.1.0**.
>
> **GitHub releases v1.0.3 and v1.0.4 were removed on 2026-09-15.** The repository history was rewritten to remove personal data from commit metadata. Those two releases were locked by GitHub and blocked the cleanup, so they had to go. They were development builds, never published on CurseForge or Modrinth, and 1.0.5 includes everything they contained. Their source remains in the history: commit `65ef813` for 1.0.3 and `ffc4624` for 1.0.4 (the tags were locked the same way and removed too). File contents are unchanged, but every commit hash changed: if you cloned the repository before that date, clone it again.

At boot you will see **one of** these two lines depending on configuration:

```
[CustomPerm] LuckPerms detected — using LuckPerms backend.
[CustomPerm] LuckPerms not present — using internal JSON grade backend.
```

Followed by the readiness summary:

```
[CustomPerm] Ready — backend=LuckPerms dispatcherCommands=89 exposed=0 aliases=0 grades=0
```

If you see neither line, the mod failed to load — check your logs for stack traces.

### In-game admin interface

`/customperm gui [page]` opens the admin interface. It needs CustomPerm on the admin's client, nothing else; without it the command explains that every setting is also available through the text commands, and players without the mod connect normally.

The interface is drawn natively (no UI library) and replaces the former TesseraUI panel. Pages:

| Page | Content |
|---|---|
| Dashboard | Active backend and what it means, counts of exposed commands, aliases, rate limits and grades, every active admin alert, reload of the configuration (with confirmation) |
| Commands | Every root command of the server with search (Ctrl+F) and an exposed-only filter, badges for aliases, rate limits and commands missing from the server; expose, hide (with confirmation), and the keep-original switch (`preserveOriginalRequires`) |
| Aliases | Every alias with search, badges for shadowed commands and rate limits; create an alias with its first step; per alias: add, replace, move up or down and remove steps, delete the alias (with confirmation) |
| Rate limits | Every rule with its numbers and badges (disabled, target neither exposed nor an alias); add a limit, change uses and window, enable or disable, switch when usage history is written (world save or every use), remove (with confirmation); exposed commands and aliases without a limit are listed and fill the form in one click |
| LuckPerms | Only when LuckPerms is installed: no navigation entry otherwise, and `/customperm gui luckperms` explains why. Installed but not running (singleplayer, failed start), the page shows a banner instead of the editor. **Groups**: create, delete, permission nodes with allow/deny, contexts and duration, parents, weight, display name, prefix, suffix, meta. **Players**: online players and any player found by exact name, their nodes, groups with duration, primary group, promote and demote on a track, prefix, suffix, meta. **Tracks**: create, delete, append, insert at a position, remove a group. Writes go through the LuckPerms API server-side, gated by `customperm.manage.luckperms` |
| Grades | Always reachable, so the fallback can be read while LuckPerms runs or fails. A banner says when grades do not decide permissions; while LuckPerms is active the page is read-only, like the grade commands: grades with search and creation, ordered by weight; per grade, three tabs: ALLOW and DENY nodes, the grades it inherits and the ones it refuses, and the players who hold it beside those who refuse it, with their online state, assigned by name with completion, including players who are offline but joined the server before; delete a grade (with confirmation) |
| Players | Nodes carried by one player rather than by a grade: every player holding something of their own plus everyone online, with search; per player, their ALLOW and DENY nodes and the grades they hold, read-only here. A player who holds nothing yet is reached by typing their name. Writing needs `customperm.manage.grades`, like the Grades page |
| Logs | Two tabs, newest first, with search. **Admin**: every change made with `/customperm` commands, the interface and the LuckPerms editor, and the changes LuckPerms itself records (`/lp`, web editor): when, who, from where, what, and the result or refusal. **Players**: every command players type, only while recording is on (off by default); arguments of private-message and password commands masked unless masking is turned off. Switching recording and masking needs `customperm.manage.logs` |

**Permissions.** Reading any page requires the same access as `/customperm`: op level 2 and `customperm.admin`. Writing requires the area's node on top of it, the same node as the matching commands: `customperm.manage.commands`, `customperm.manage.aliases`, `customperm.manage.ratelimits`, `customperm.manage.grades`, `customperm.manage.logs`, `customperm.manage.config` (reload), `customperm.manage.luckperms`. Nothing is granted by the op level alone, level 4 included, so one area can be delegated to a moderator without opening the others. Actions that change no configuration, such as reload, need op level 2 only, like their command. Every action, applied or refused, is recorded in the activity log with the admin's name.

**Compatibility.** The interface uses network protocol 2. A client running an older CustomPerm still connects to a 1.1.0 server but has no interface there, and the other way round.

---

## Singleplayer and LAN worlds

CustomPerm is designed for servers, but it works in a singleplayer world too — the integrated server runs the exact same code. Two things behave differently enough to be worth knowing before you try it.

### You must enable cheats

`/customperm` is gated behind a real op-level-2 check, plus the `customperm.admin` node, from which the host of the world is exempt: a singleplayer or LAN world has no console to grant the first node from. Guests of a LAN world need op level 2 and the nodes, granted by the host. In a singleplayer world you only hold a permission level if **Allow Cheats** is on (`Create New World → More → Allow Cheats`, or open the world to LAN with cheats enabled). Without it the command is hidden from tab-completion and cannot be run — this is not a bug, it is the same gate that protects the command on a server.

The check deliberately inspects your *real* op level rather than the level of the current command source, so an alias can never be used to smuggle a `/customperm` subcommand past it.

### Configuration is per-installation, not per-world

This is the one that surprises people. Configuration lives in your Minecraft installation directory:

```
.minecraft/config/arcadia/customperm/
```

It is **not** stored in the world save. Every grade, alias, exposed command and rate limit you create is therefore shared by **all** of your singleplayer worlds. Create an alias `/heal` while messing about in a creative world and it will exist in your survival world too.

On a dedicated server this is invisible — one installation, one world. In singleplayer, if you want different setups per world, you currently have to swap the config directory yourself.

### What is actually useful offline

- **Aliases and macros** — the main reason to run CustomPerm solo. Chain several commands behind one, executed at op level 4.
- **The admin interface** — `/customperm gui` works in singleplayer like anywhere else.
- **Grades and permission nodes** — of little use while you are alone and already an operator. They become meaningful the moment you **open the world to LAN**: guests join as non-ops, and grades let you hand out exactly the commands you want them to have.
- **Rate limits** — note these apply to you as well. A rule set in one world applies in all of them, per the point above; the usage counters, however, are stored in each world's save.

---

## Quick start

### With LuckPerms

```
# Server console
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
lp user Steve parent add vip
```

`Steve` can now use `/spec` without receiving direct access to `/gamemode`.

### Without LuckPerms (internal system)

```
# Server console
customperm command add gamemode
customperm grade create vip
customperm grade addperm vip customperm.command.gamemode
customperm grade assign Steve vip
```

Same outcome: `Steve` can use `/gamemode`.

---

## Commands

All admin commands live under `/customperm` and require **op level 2 and `customperm.admin`**, explicitly granted; subcommands that change something also require the `customperm.manage.<area>` node of their area, listed with each group below. The op level alone grants nothing, level 4 included (see [Restricting operators](#restricting-operators)). The console always has access, and so does the host of a singleplayer or LAN world, which has no console.

On a fresh install, or right after upgrading from 1.0.x, nobody holds these nodes: grant them from the console
(see [MIGRATION.md](MIGRATION.md)).

```
# Without LuckPerms
customperm grade create admins
customperm grade addperm admins customperm.*
customperm grade assign <name> admins

# With LuckPerms
lp user <name> permission set customperm.* true
```

### Command exposure

Defines which commands are eligible for the permission system. A non-exposed command keeps its vanilla behaviour (op-only).

This feature works with **either backend**. The `customperm.command.<name>` node is resolved by whatever permission backend is active: through **LuckPerms** when it is installed (grant it with `/lp`), or through the internal grades otherwise. Exposing a command is the same command in both cases.

| Command | Effect |
|---|---|
| `/customperm command add <name>` | Exposes `<name>` to the system. |
| `/customperm command remove <name>` | Removes the command, reverts to vanilla behaviour. |
| `/customperm command preserve <name> <true\|false>` | For an exposed command: `true` requires the node AND the command's original requirement, `false` (default) the node alone. Same as `preserveOriginalRequires` in `commands.json`. |
| `/customperm command gateall <true\|false>` | Internal backend only. `true`: every command reads its `customperm.command.<name>` node, not only exposed ones. Same as `gateAllCommands` in `settings.json`. |
| `/customperm command list` | Lists currently exposed commands. |

### Aliases (macros)

Create custom commands that run one or more inner commands. Steps execute with **op level 4** — see [Security considerations](#security-considerations).

| Command | Effect |
|---|---|
| `/customperm alias add <name> <cmd1; cmd2; ...>` | Creates an alias. Inner commands separated by `;`. |
| `/customperm alias addstep <name> <cmd>` | Appends a step (creates the alias if absent). |
| `/customperm alias removestep <name> <index>` | Removes the step at the given 0-based index. |
| `/customperm alias movestep <name> <from> <to>` | Moves a step to another 0-based position. |
| `/customperm alias setstep <name> <index> <command>` | Replaces the step at the given 0-based index. |
| `/customperm alias steps <name>` | Shows all steps with their indices. |
| `/customperm alias remove <name>` | Deletes the alias entirely. |
| `/customperm alias list` | Lists all defined aliases. |

### Grades (internal system, LuckPerms-less)

These commands are **blocked when LuckPerms is active** — use `/lp` instead.
They manage ALLOW nodes. Internal DENY nodes are stored in `grades.json` under `deniedPermissions`.

| Command | Effect |
|---|---|
| `/customperm grade create <name>` | Creates an empty grade. |
| `/customperm grade delete <name>` | Deletes a grade and unassigns it from every user. |
| `/customperm grade addperm <grade> <node>` | Adds a permission node to the grade. |
| `/customperm grade removeperm <grade> <node>` | Removes a node. |
| `/customperm grade adddeny <grade> <node>` | Adds a DENY node: refused, operators included, unless a more specific node allows it. |
| `/customperm grade removedeny <grade> <node>` | Removes a DENY node. |
| `/customperm grade weight <grade> <weight>` | Sets the tie-break weight, 0 by default, negative allowed. |
| `/customperm grade parent add <grade> <parent>` | Makes the grade inherit another; a cycle is refused. |
| `/customperm grade parent remove <grade> <parent>` | Stops inheriting it. |
| `/customperm grade parent adddeny <grade> <parent>` | Refuses a grade wherever this one would inherit it. |
| `/customperm grade parent removedeny <grade> <parent>` | Stops refusing it. |
| `/customperm grade parent list <grade>` | Shows what the grade inherits and what it refuses. |
| `/customperm grade assign <player> <grade>` | Assigns the grade to a player, online or offline if they joined the server before. |
| `/customperm grade unassign <player> <grade>` | Unassigns, online or offline. |
| `/customperm grade setdefault <grade>` | Applies the grade to every player, below their own grades. |
| `/customperm grade cleardefault` | No grade applies to every player any more. |
| `/customperm grade list` | Lists defined grades, heaviest first. |

Nodes carried by one player, above their grades:

| Command | Description |
|---|---|
| `/customperm user addperm <player> <node>` | Adds an ALLOW node to that player alone. |
| `/customperm user removeperm <player> <node>` | Removes it. |
| `/customperm user adddeny <player> <node>` | Adds a DENY node to that player alone. |
| `/customperm user removedeny <player> <node>` | Removes it. |
| `/customperm user denygrade <player> <grade>` | Makes one player refuse a grade, wherever one of theirs would bring it. |
| `/customperm user undenygrade <player> <grade>` | Stops refusing it. |
| `/customperm user list <player>` | Shows the grades they hold, the ones they refuse, and the nodes they carry. |

A grade change that would take away your own access to `/customperm` is refused and undone: allow `customperm.admin` for yourself first, or make the change from the console.

### Rate limits

Cap how many times one player may run a command or an alias within a sliding window. Limits apply to every player, operators included; the console and command blocks are never limited.

| Command | Effect |
|---|---|
| `/customperm ratelimit set <name> <max> <windowSeconds>` | Allows `<max>` uses per player per `<windowSeconds>`. Redefining a rule keeps its persistence mode. |
| `/customperm ratelimit persistence <name> <world_save\|immediate>` | Chooses when the usage history of that command is written to disk (see `ratelimits.json`). |
| `/customperm ratelimit disable <name>` / `enable <name>` | Stops or resumes enforcing a rule without losing its numbers. |
| `/customperm ratelimit remove <name>` | Deletes the rule. |
| `/customperm ratelimit list` | Lists rules with their state and persistence mode. |

### Diagnostic and utilities

| Command | Effect |
|---|---|
| `/customperm test <player> <node>` | Verifies whether a player holds a permission node. Returns `GRANTED` or `DENIED`, with the reason: explicit ALLOW or DENY, or not set (granted to operators). |
| `/customperm debug <player> <command>` | Detailed report: is the command in the dispatcher? exposed? does op-level pass? is the perm granted? what does the wrapper actually return? |
| `/customperm status` | Global snapshot: backend, wrapped commands, exposed commands, aliases, grades, active admin alerts. |
| `/customperm scan [pattern]` | Lists every command in the dispatcher with its state (exposed, alias, mod-internal). Optional substring filter. |
| `/customperm reload` | Reloads config files from disk. |
| `/customperm log admin [count]` | The latest admin changes (10 by default, up to 100), refused ones in red. |
| `/customperm log players [count]` | The latest commands typed by players, when recording is on. |
| `/customperm log record <true\|false>` | Starts or stops recording player commands. Same as `playerCommandLog` in `settings.json`. |
| `/customperm log mask <true\|false>` | Masks or keeps the arguments of the commands listed in `maskedCommands`. |
| `/customperm gui [dashboard\|commands\|aliases\|ratelimits\|grades\|logs]` | Opens the in-game admin interface (needs CustomPerm on the client). Reading needs `customperm.admin`; writing needs the area's `customperm.manage.<area>` node. |
| `/customperm gui luckperms [groups\|players\|tracks]` | Opens the in-game LuckPerms editor. Only when LuckPerms is installed; if it is not running, the page says why. Writing needs `customperm.manage.luckperms`. |

---

## Permission nodes

CustomPerm uses a hierarchical node scheme compatible with LuckPerms (and with the internal store).

| Node | Effect |
|---|---|
| `*` | Global wildcard. Allowed, it grants every node; denied, it refuses every node except the ones allowed more specifically, operators included. Use sparingly. |
| `customperm.admin` | Entry: use `/customperm` and read every page of the interface, plus admin alerts. Required, op level alone is not enough. |
| `customperm.manage.commands` | Expose, hide, `preserve`, `gateall`, and the Commands page. |
| `customperm.manage.aliases` | Create, edit and delete aliases, and the Aliases page. |
| `customperm.manage.ratelimits` | Create, change, enable, disable and delete rate limits, and the Rate limits page. |
| `customperm.manage.grades` | Grades, their nodes, assignments and the default grade, and the Grades page. |
| `customperm.manage.logs` | Turn the player command log and argument masking on or off. |
| `customperm.manage.config` | `/customperm reload` and the reload button. |
| `customperm.manage.luckperms` | Write through the in-game LuckPerms editor. |
| `customperm.manage.*` | Every area above. `customperm.*` adds `customperm.admin` on top: the full administrator. |
| `customperm.command.<name>` | Authorizes command `<name>`, once the command is exposed (or for every command with `gateAllCommands`). Denied, it refuses the command to operators too. Resolved by LuckPerms when installed, by the internal grades otherwise. |
| `customperm.command.*` | Wildcard covering every exposed command (every command with `gateAllCommands`). With LuckPerms, LuckPerms' own wildcard engine resolves it. |
| `customperm.alias.<name>` | Authorizes alias `<name>`. E.g. `customperm.alias.fly` |
| `customperm.alias.*` | Alias wildcard. |

> ℹ️ With LuckPerms active, grant both `customperm.command.<name>` (for exposed commands) and `customperm.alias.<name>` (for aliases) via `/lp`. Grade subcommands (`/customperm grade ...`) remain disabled under LuckPerms — user/group membership is managed with `/lp`.

---

## Configuration files

Stored in `config/arcadia/customperm/`. Auto-created on first launch and editable on the fly (use `/customperm reload` to apply). If an older `config/customperm/` directory exists and the new directory does not, CustomPerm copies the known config files into the new location without deleting the old files.

### `commands.json`

Set of commands exposed to the system.

This file lists exposed commands regardless of backend. The `customperm.command.<name>` node that authorizes each one is resolved by LuckPerms when installed (grant via `/lp`), or by the internal grades otherwise.

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

`preserveOriginalRequires` is optional per command. Missing entries default to `false` to preserve the historical CustomPerm behavior. Set it to `true` for sensitive modded commands whose original Brigadier `requires` predicate must remain mandatory in addition to the CustomPerm permission node.

### `settings.json`

Runtime safety settings.

```json
{
  "luckPermsFallbackMode": "deny",
  "gateAllCommands": false,
  "defaultGrade": "",
  "playerCommandLog": false,
  "maskPlayerCommandArguments": true,
  "maskedCommands": ["msg", "tell", "w", "teammsg", "tm", "login", "l", "register", "reg", "changepassword", "changepw"],
  "logRetentionDays": 30
}
```

- `gateAllCommands` (internal backend, default `false`): `true` makes every command read its `customperm.command.<name>` node. An explicit DENY then blocks any command for operators too, and an ALLOW opens any command, so a grade holding `*` or `customperm.command.*` gets every command of the server. No effect with LuckPerms installed, which already checks every command.
- `defaultGrade` (internal backend, default empty): a grade applied to every player, below their own grades.
- `playerCommandLog` (default `false`): records every command players type in the activity log. Admin changes are always recorded.
- `maskPlayerCommandArguments` (default `true`) and `maskedCommands`: the arguments of these root commands are stored as `[masked]` (`/msg Alex hi` becomes `/msg [masked]`). A namespace prefix is ignored.
- `logRetentionDays` (default `30`): daily log files older than this are deleted at start and at each day change; `0` keeps them forever. Files: `<world>/customperm/logs/admin-YYYY-MM-DD.jsonl` and `players-YYYY-MM-DD.jsonl`, one JSON object per line.
- `configVersion`: the settings format this file was written with. A fresh install is stamped with the current one; a file from an older CustomPerm has none, which makes the server log what changed and tell every operator once (see [MIGRATION.md](MIGRATION.md)). Leave it alone.

`luckPermsFallbackMode` accepts:

- `deny`: default and recommended for public servers. If LuckPerms is loaded but unavailable, CustomPerm permission checks return false.
- `internal`: compatibility mode. If LuckPerms is loaded but unavailable, CustomPerm falls back to `grades.json`.

### `ratelimits.json`

Rate-limit rules, keyed by command or alias name.

```json
{
  "rules": {
    "gamemode": { "enabled": true, "maxExecutions": 3, "windowSeconds": 60, "persistence": "world_save" },
    "heal": { "enabled": true, "maxExecutions": 1, "windowSeconds": 3600, "persistence": "immediate" }
  }
}
```

Usage history is kept across restarts and vanilla `/reload`. It is a server state, not a setting, so it lives in the world save, in `<world>/data/customperm_ratelimits.json` (Unix timestamps in milliseconds), not in this folder. `persistence` decides when it is written:

- `world_save` (default): with the world, on autosave, `/save-all` and server stop. No cost per command; a server crash loses at most the uses since the last save.
- `immediate`: right after each accepted use of that command. Nothing is lost on a crash, at the price of one disk write per use. Keep it for rare, sensitive commands.

On load, history older than the rule's current window is dropped, so a window shortened while the server was stopped applies at once. If the system clock moves backwards, uses recorded "in the future" count from now for one window instead of locking players out. An unreadable history file is renamed `customperm_ratelimits.json.corrupt-<date>` and counters start empty.

### `aliases.json`

Aliases with their steps.

```json
{
  "aliases": {
    "fly": ["gamemode spectator"],
    "heal": [
      "effect give @s minecraft:instant_health 10 100",
      "effect give @s minecraft:saturation 1 100",
      "say healed!"
    ]
  }
}
```

### `grades.json` (Internal mode only)

Grades and user assignments.

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
  }
}
```

When LuckPerms is active, this file is ignored (permissions go through LP).

`userPermissions` and `userDeniedPermissions` carry nodes for one player, above every grade they hold. `deniedPermissions` is only used by the internal backend. The most specific entry wins across what the player carries and all their grades (exact node, then `a.b.*`, then `a.*`, then `*`); at the same level a node on the player wins, then the heaviest grade, then a DENY between equals. `parents` lists the grades a grade inherits: their entries apply where it says nothing as precise about the node, nearest ancestor first, and a chain competes with the other grades at the weight of the grade the player actually holds. `deniedParents` and `userDeniedGrades` take a grade out of the resolution, respectively for that grade's own chain and for that player everywhere, the default grade included; a refusal never turns what the refused grade allows into a denial. `weight`, `parents`, `deniedParents` and `userDeniedGrades` are all optional and empty when absent, which makes the DENY rule the only tie-break, as it was before the fields existed. What the player carries and the grades they hold decide first; only a node none of it mentions falls through to the default grade.

---

## Common workflows

### Grant `/gamemode` to a VIP rank

**With LuckPerms** — two options:

*Direct exposure* (whole `/gamemode`):
```
customperm command add gamemode
lp group vip permission set customperm.command.gamemode true
```

*Controlled alias* (only spectator, safer):
```
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
```

In both cases LuckPerms provides the permission assignment; CustomPerm's command wrapper is what actually lets the node through Brigadier's `requires` check (LuckPerms alone does not bypass it on NeoForge). Prefer the alias when you want sub-command granularity (spectator but not creative).

**Without LuckPerms**:
```
customperm command add gamemode
customperm grade create vip
customperm grade addperm vip customperm.command.gamemode
customperm grade assign <player> vip
```

### Create a `/fly` shortcut that switches to spectator

```
customperm alias add fly gamemode spectator
lp group vip permission set customperm.alias.fly true       # or via grade
```

### Healing macro with several effects

```
customperm alias add heal effect give @s minecraft:instant_health 10 100; effect give @s minecraft:saturation 1 100; effect give @s minecraft:regeneration 30 2
lp group vip permission set customperm.alias.heal true
```

### Grant several commands at once (wildcard)

Without LuckPerms:
```
customperm command add gamemode
customperm command add give
customperm command add tp
customperm command add effect
customperm grade addperm staff customperm.command.*
```

The wildcard only covers **exposed** commands. Other vanilla commands stay op-only, unless `gateAllCommands` is on.

### Allow only `/gamemode spectator`, not creative

The current API exposes commands at the root level — it does not differentiate sub-modes. For this case, **use aliases**:

```
customperm alias add spec gamemode spectator
lp group vip permission set customperm.alias.spec true
# do NOT expose /gamemode itself
```

Players use `/spec` instead of `/gamemode spectator`. The real `/gamemode` stays op-only, so no access to `/gamemode creative`.

---

## Aliases and macros

Aliases are central to the mod and deserve a closer look.

### Format

An alias = an **ordered list of commands**. When an authorized player runs the alias, each step executes **sequentially** with **op level 4 authority**.

### Multi-step creation

```
customperm alias add starter give @s diamond_sword; give @s shield; effect give @s minecraft:resistance 60 1; tp @s 0 100 0
```

Separate inner commands with `;` (a trailing space is optional, just for readability).

### Incremental editing

To add/remove steps after creation:

```
customperm alias steps heal           # show steps with their indices
customperm alias addstep heal say "You are healed!"
customperm alias removestep heal 0    # remove the first step
```

### Minecraft selectors

Selectors (`@s`, `@p`, `@a`, etc.) work as expected. The source during execution is the player who invoked the alias.

### Error behaviour

If a step fails, subsequent steps **still run** (predictable, command-block-like behaviour). Errors are logged with the alias name and the failing step.

Recursive aliases are bounded. A direct or indirect cycle is aborted when nested alias execution reaches depth 8 instead of overflowing the server thread.

### Reloading file edits

After editing `aliases.json`, run `/customperm reload`. Added aliases are registered, removed aliases are deleted (restoring any shadowed original command), and changed step lists replace the previously captured steps.

### Why op level 4 during execution

Without the elevation, an alias such as `gamemode spectator` would fail: the inner `/gamemode` re-checks `requires(2)` and the player isn't op. The alias is designed as an **admin-signed macro** — the admin decides what the alias contains, and the player just receives a delegation to execute that exact content.

---

## Security considerations

### ⚠ Alias elevation

**Everything inside an alias runs with op-4 authority.** Granting `customperm.alias.X` to a player effectively grants them the right to run X **with admin privileges**.

**Consequence**: never put inside an alias commands you would not give that player as plain op, for instance:
- `op @s` → the player permanently becomes op
- `whitelist remove ...`, `ban ...` → moderation tooling
- `gamerule keepInventory false` → mutates server-wide state
- `data modify ...` → mutates any entity or block
- `function <namespace>:<malicious>` → arbitrary function execution

**Best practice**: regularly audit your aliases via `customperm alias list` then `customperm alias steps <name>`.

### Alias name colliding with a vanilla command

Creating `/customperm alias add gamemode ...` **shadows** the vanilla command. The mod prints an explicit warning when this happens. Players will need `customperm.alias.gamemode` (not `customperm.command.gamemode`) to use that version.

### Restricting operators

Administering CustomPerm needs a granted node, never the op level alone: `/customperm` and the interface are hidden from an operator who does not hold `customperm.admin`, level 4 included, and each change needs its `customperm.manage.<area>` node. Only the console, and the host of a world with no console, are exempt.

For the commands CustomPerm gates for ordinary players, being op still matters where nothing says otherwise. An explicit DENY applies to operators, owners included, on both backends: a denied `customperm.command.<name>` refuses the command, a denied `customperm.alias.<name>` the alias, a denied `customperm.admin` (or `*`, or `customperm.*`) takes away `/customperm` and the admin interface. The console and command blocks are never asked for a node.

With LuckPerms, deny nodes as usual (`/lp group default permission set * false`, then allow what staff needs): LuckPerms already checks every command, and CustomPerm now honours a `false` on the commands it exposes instead of letting operators through.

Without LuckPerms, to protect the server against a player made op by mistake:

```
customperm grade create everyone
customperm grade adddeny everyone *
customperm grade addperm everyone customperm.command.list
customperm grade create owner
customperm grade addperm owner *
customperm grade assign <you> owner
customperm grade setdefault everyone
customperm command gateall true
```

`owner` holds `*`, which includes `customperm.admin` and every `customperm.manage.*`: that is what keeps you able to administer the mod in game.

Every player now follows `everyone` below their own grades, and every command reads its node: an op without a grade of their own can run `/list` and nothing else, `/customperm` included. Run these from the console, or assign yourself `owner` before `setdefault`: in game, a change that would lock you out of `/customperm` is refused.

### Wildcards must be granted carefully

`customperm.command.*` covers **every** exposed command. If you expose `/op` (not recommended) or `/whitelist`, the wildcard covers them too. With `gateAllCommands`, it and `*` cover every command of the server. **Prefer** explicit nodes for sensitive commands.

### Player command log and personal data

The player tab records which player ran which command and when: that is personal data. It is off by default. Before turning it on, tell your players, keep the retention as short as you need, and leave masking on unless you have a reason to read private messages. Entries already recorded are not rewritten when masking changes. Anyone who can administer CustomPerm can read the log, and the files sit in the world folder, readable by whoever has access to the server files.

### Regular audit

Inspect `commands.json`, `aliases.json`, and (in internal mode) `grades.json` periodically, or use `/customperm status` and `/customperm scan` in-game.

---

## Diagnostics and troubleshooting

### The mod isn't loading

- Check the boot log — the line `[CustomPerm] Ready —` must appear.
- If LP is present but its initialisation throws, `settings.json` decides the behavior: `deny` fails closed by default, `internal` falls back to `grades.json`. Check that your LP version is compatible.

### A red `[CustomPerm] ALERT` appears in chat

Ops (permission level 2+) receive it when something needs action, once when it happens and again on each login while it lasts. `/customperm status` lists the active ones.

- **LuckPerms is unavailable**: CustomPerm stopped using LuckPerms until the next restart and now follows `luckPermsFallbackMode` (`internal` grades or `deny`). Check the server log for the LuckPerms error, fix it, restart.
- **Configuration failed to load**: the alert names the invalid file. Changes made in game stay in memory but are not written to disk, so the broken file is not overwritten. Fix the file, then run `/customperm reload`: the alert is replaced by a green "Resolved" message and saving resumes.

### An exposed command doesn't work for an authorized player

```
/customperm debug <player> <command>
```

This prints a line-by-line report:
- Presence in dispatcher
- Presence in the exposed list
- Player's op level
- Permission check result
- Expected logical decision
- **Actual decision returned by the wrapper**

If the actual decision ≠ expected → mismatch, please open an issue.

### Verify a permission is actually granted

```
/customperm test <player> <node>
```

Returns `GRANTED` (green) or `DENIED` (red) along with the active backend and the reason: an explicit ALLOW or DENY, or a node that is not set, which only operators pass.

### Player can't see the command in autocomplete

The command tree is cached client-side. The mod auto-resyncs when permissions change (via LP's `UserDataRecalculateEvent` or `/customperm grade ...` commands). If that's not enough:
- The player can disconnect/reconnect to force a refresh.
- The admin can run `/customperm reload` then `/reload`.

### Verify a third-party mod's commands are detected

```
/customperm scan <partial_name>
```

Lists dispatcher commands containing that substring. Third-party mod commands appear as long as the mod registered them via the standard `RegisterCommandsEvent` (the common case).

---

## Building from source

### Requirements

- JDK 21
- Git

### Build

```bash
git clone https://github.com/<user>/CustomPerm.git
cd CustomPerm
./gradlew build               # Linux/Mac
.\gradlew.bat build           # Windows
```

Gradle generates the distributable mod artifact during `build`. Generated artifacts are not committed to Git; builds are published on CurseForge and Modrinth.

### Dev environment

```bash
./gradlew runServer           # dev server with hot-reload
./gradlew runClient           # dev client
```

### Tunable versions

In `gradle.properties`:

```properties
minecraft_version=1.21.1
neo_version=21.1.221
luckperms_api_version=5.4
```

---

## Testing

The mod ships with three validation layers: pure JUnit tests, NeoForge GameTests, and manual release checks.

### Run the suite locally

```bash
./gradlew runGameTestServer             # internal backend, no LuckPerms
./gradlew runGameTestServerLuckPerms    # LuckPerms backend
```

Each task launches a dedicated Minecraft test server in its own folder (`run/gametest/`, `run/gametest-luckperms/`), executes the registered GameTests, and exits with a code equal to the number of failed tests (zero = all pass). The internal mode always runs without LuckPerms, whatever `run/mods/` contains; the LuckPerms mode fetches LuckPerms NeoForge 5.4.150 through CurseMaven. Tests that only apply to one backend skip themselves in the other, and a guard test fails if a mode does not run with the backend it claims.

GameTests use real connected players (`TestPlayer`): a server-side player with a chosen permission level whose received chat, command trees and CustomPerm packets are recorded, so permission, alias, rate-limit and GUI-packet behaviour is checked end to end without a client.

Pure Java tests can be run with:

```bash
./gradlew test
```

Performance benchmarks can be run with:

```bash
./gradlew jmh
```

### What's covered

| Area | Validates |
|---|---|
| Permission resolver | Default deny, direct ALLOW, wildcard ALLOW, global wildcard, explicit DENY, most specific entry wins across grades, grade weight breaking a tie whatever the assignment order, DENY on equal weights, weight never beating specificity, a node on the player outranking their grades without beating a more specific one, inheritance with the nearest ancestor deciding, diamonds, cycles and a chain competing at the weight of the grade held, a grade refused by another grade or by a player being unreachable without becoming a denial, denied `*` with explicit allows, default grade layer. |
| Internal grades | Create/list/delete grades, assign/unassign players, prevent duplicates, cascade grade deletion through player assignments. |
| Command exposure | Add/remove/list exposed commands, idempotent changes, non-exposed commands remain denied by CustomPerm. |
| Alias config | Create, overwrite, remove, list aliases, preserve order, split semicolon-delimited steps, ignore blank steps. |
| Alias execution | Permission node shape, op-level 4 execution, step ordering, continue-after-error behavior, recursive-cycle limit, live step replacement. |
| Config manager | Atomic snapshot reads, serialized atomic saves, concurrent reload rejection, rollback after invalid JSON, backup creation, backup rotation. |
| Backward compatibility | Missing files, `{}` files, explicit `null` collections, unknown future fields, partial config files. |
| LuckPerms selection | Internal backend when LP is absent, version parsing, minimum version gate, stable backend selection. |
| GameTests, both modes | Command exposure and removal with a non-op player, operator preservation, `/customperm` refused to non-ops, reconnection, aliases run with op-4 elevation by node holders only and unable to reach `/customperm`, step editing, recursion and shadowing guards, reload of hand-edited `aliases.json`, rate limits (refusal message, shared counter per root, per-player isolation, console exemption, window expiry, reconnection, repeated reloads, rule removal, aliases), all-or-nothing reload, concurrent reload refusal, unsaved changes after a failed reload, `null` entries, command-tree repush on reload, admin alerts in operators' chat, GUI and editor packets refused to non-operators, diagnostics output, tab-completion of every `/customperm` argument and no suggestions for non-operators, operators refused an exposed command, an alias, `/customperm` or an interface area by an explicit DENY while the console keeps access, a denied `*` blocking everything but explicit allows, admin changes from commands and the interface recorded with refusals, player commands recorded only when on and masked by default, files on disk, reload from disk skipping unreadable lines, retention, Logs page switches gated by their node, `/lp` changes recorded (LuckPerms mode), operators without the nodes refused `/customperm` and the interface while the console keeps access, each area needing its own `customperm.manage` node for the command and the page alike, the nodes alone opening nothing to a non-operator, the upgrade notice announced once for a configuration written before 1.1.0 and the configuration stamped afterwards. |
| GameTests, internal mode | Grade commands, union of grades, most specific entry wins, grade weight breaking a tie, nodes carried by a player, the Players page and its lockout guard, grade parents with inheritance applied live and cycles refused, refusals applied live and contradictions answered, every wildcard form, editor without LuckPerms, `gateAllCommands` and an allowed `*`, a default grade restricting an accidental operator, refused self-lockout by command and interface. |
| GameTests, LuckPerms mode | In-game editor against a real LuckPerms: groups, nodes with contexts and expiry, inheritance, meta, prefix and suffix, weight, display name, player groups and primary group, tracks, promote and demote, write gating by node and level, edit and sync rate limits; command tree resent after a LuckPerms change; `deny` and `internal` fallback when LuckPerms becomes unavailable. |
| Performance | `PermissionResolver.resolve()` and concurrent config snapshot reads via JMH. |

### Continuous integration

Every push to `main` or `dev` and every pull request targeting them triggers `.github/workflows/gametest.yml`, which:

1. Sets up JDK 21 on Ubuntu.
2. Caches Gradle dependencies for fast subsequent runs.
3. Runs `gradlew runGameTestServer`, then `gradlew runGameTestServerLuckPerms`.
4. Builds the distributable jar with `gradlew build`.
5. Verifies that the jar contains `META-INF/neoforge.mods.toml` and `META-INF/MANIFEST.MF`.
6. Fails the build if any required test or jar check fails.
7. Uploads the run logs as a build artifact on failure for inspection.

### Manual validation in dev

GameTests cover server-side behaviour; for what still needs a real client (GUI rendering, a vanilla client joining, a real modpack), run a dev server and client in two terminals:

```bash
./gradlew runServer    # terminal 1
./gradlew runClient    # terminal 2 — connect to 127.0.0.1
```

Then exercise the recipes from [Common workflows](#common-workflows). The in-game diagnostic commands `/customperm debug`, `/customperm test`, `/customperm status`, and `/customperm scan` are designed for live verification.

---

## How it works (technical)

### Dispatcher wrapping

At `RegisterCommandsEvent`, the mod walks Brigadier's command tree and **clones** every root node into a fresh `LiteralCommandNode` whose `requires` chains:

```
1. If the command is not exposed, keep the original vanilla/modded requirement
2. If the command is exposed and the source is op level 2+, allow it
3. Otherwise, ask the PermissionService whether the source has customperm.command.<root>
```

Cloned nodes are inserted into the root's internal `Map` fields (`children`/`literals`/`arguments`) via reflection. This approach sidesteps JIT inlining traps on `final` fields.

### Pluggable backend

`PermissionService` is an interface with two implementations:

- `LuckPermsService`: queries LP via the public API (`LuckPermsProvider.get()`).
- `InternalPermService`: looks up grades in `grades.json`.

Selection happens at boot through `ModList.get().isLoaded("luckperms")` plus a minimum version check (`5.4.150+`). If LuckPerms is absent, CustomPerm uses the internal backend. If LuckPerms is present but incompatible, fails to initialise, or later throws during permission checks, `settings.json` decides the fallback: `deny` fails closed, `internal` uses `grades.json`.

The internal resolver applies this order:

```
1. Null player or node                                  => UNSET
2. Among what the player carries (their own nodes) and the
   grades they hold with everything those grades inherit, the
   entry covering the node most specifically wins
   (exact, a.b.*, a.*, *)
   (a grade the holder refuses is never reached, whichever
   path would have led to it)
   - tie inside one chain => the nearest holder decides
   - tie on specificity   => a node on the player, else the heaviest grade
   - tie on rank          => DENY wins
3. Node mentioned by none of it => same rule on the default grade
4. Nothing matching at all => UNSET, the caller decides from the op level
```

### Re-sync

When a permission changes via LP, the `UserDataRecalculateEvent` is captured and `Commands.sendCommands(player)` is invoked for the affected player. The client tree is updated without a disconnect.

For changes via `/customperm` (internal mode), `sendCommands` is invoked directly after the mutation.

### Aliases

Registered as `Commands.literal(name).requires(...).executes(...)`. The `executes` normalizes each step, strips any leading `/`, and executes the step through the original command node when CustomPerm has wrapped that command, using a `CommandSourceStack` whose `permissionLevel = 4`. Failed steps are reported and logged, but later steps still run.

---

## Compatibility with other mods

### Mods that add commands

**Compatible automatically with either backend.** Commands registered through the standard `RegisterCommandsEvent` are processed at `LOWEST` priority, followed by a repair pass when the server starts. No dedicated integration is normally required.

Expose a third-party mod's command with `customperm command add <name>`, then grant `customperm.command.<name>` — via `/lp` when LuckPerms is installed, or via an internal grade otherwise. A narrow CustomPerm alias remains an option when you want tighter, sub-command scope. To verify detection: `customperm scan <pattern>`.

### Mods that mutate the dispatcher dynamically

Edge case. If a mod adds commands **after** `RegisterCommandsEvent`, they aren't wrapped and keep their original `requires` (typically op-only). To force a re-wrap: `/reload` (server-side).

### LuckPerms

Privileged target. The full LP machinery works:
- Groups (`/lp creategroup`)
- Hierarchy (`/lp group <name> parent add <parent>`)
- Contexts (servers, worlds — not extensively tested but the API is honoured)
- Web editor
- SQL/MySQL/MongoDB storage

LuckPerms stores and resolves both `customperm.command.*` and `customperm.alias.*` nodes. LuckPerms does not by itself bypass vanilla Brigadier requirements on NeoForge — it is CustomPerm's command wrapper that consults the node and lets the source through. So `customperm.command.gamemode` granted in `/lp` **does** unlock `/gamemode` once the command is exposed via `customperm command add gamemode`; a controlled alias is only needed for sub-command granularity (e.g. spectator but not creative).

---

## Known limitations

- **No sub-command granularity**: `customperm.command.gamemode` covers every sub-mode (creative, spectator, etc.). To split, use aliases.
- **No alias parameters**: an alias is a no-arg command. To build `/heal <player>`, write `/heal_target` using `effect give @p` etc., or create multiple aliases.
- **LP contexts partially tested**: per-world, per-server contexts go through `getCachedData()` and are theoretically supported but not extensively tested.
- **The admin interface needs CustomPerm client-side**: without it, administration stays fully command-driven.
- **The in-game LuckPerms editor is not the web editor**: it covers groups, users, tracks, nodes, meta and chat meta, but not bulk operations, node search across all holders, or the web editor's undo history. For those, `/lp editor` remains the tool.
- **Shortcut commands have their own rules**: some commands are shortcuts that redirect to another one (`/tp` to `/teleport`, `/msg` and `/w` to `/tell`, `/xp` to `/experience`). Each spelling is exposed and rate limited under the name the player types: `tp` governs `/tp`, `teleport` governs `/teleport`. Exposing one does not open the other; configure both if both should be available.
- **Player commands need a player known to the server**: `/customperm grade assign|unassign`, `/customperm user`, and the interface accept players online or who joined the server before; they never query the session service, so a name that never joined cannot be assigned in advance. For that case, edit `userGrades`, `userPermissions` or `userDeniedPermissions` in `grades.json` (UUID as key) and run `/customperm reload`.
- **DENY nodes are file-only**: `deniedPermissions` is honoured by the resolver but has no `/customperm grade` subcommand yet; edit `grades.json` and reload.

---

## License

Copyright (C) 2026 THEFricadelle. All rights reserved.

CustomPerm is **proprietary, source-available software** — the source is public
for reference and interoperability, but it is **not** open-source. You may
download the official builds and run the mod on your own server(s); you may
**not** redistribute, re-upload, repackage, sell, or create derivative works
without prior written permission.

**Modpacks are allowed** in CurseForge / Modrinth modpacks that reference the
**unmodified official file** from the platform. Bundling the jar into an
exported/offline pack, re-hosting it elsewhere, or shipping a modified build
still requires written permission.

**Server operators may send the mod to their own players.** Because CustomPerm
has client-side components, automatic mod-synchronization to the players joining
*your* server is explicitly permitted, as long as the official file is
transmitted unmodified. Offering it as a general download or a hosting-panel
"one-click install" product is not.

**Contributions are welcome.** You may fork the repository to submit a pull
request — that specific use is explicitly permitted. Publishing any build made
from your fork, rebranding it, or reusing the code elsewhere is not.

The name, mod id, and logo may not be used for another project or to imply
endorsement, and the code may not be used to train AI models.

**Earlier releases keep their own terms.** The proprietary license was adopted
on 2026-07-14 and applies to builds published from the next release onward.
Every version up to and including **1.0.5** was distributed under the license
shipped with it (MIT or `GPL-3.0-only` depending on the release) and stays
available on those terms. The per-version table is in
[NOTICE.md](NOTICE.md#license-history-by-version).

See [LICENSE](LICENSE) for the full terms, [NOTICE.md](NOTICE.md) for a
plain-language summary of what is and isn't allowed, and
[CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request.

---

## Credits

Author and maintainer: **THEFricadelle**.

Thanks to everyone who has contributed code, fixes, or documentation — they are
listed in [CONTRIBUTORS.md](CONTRIBUTORS.md).

Built on:

- [NeoForge](https://neoforged.net/) for the modding framework.
- [LuckPerms](https://luckperms.net/) for the inspiration and a clean integration API.
- Brigadier (Mojang) for the underlying command system.
