# CustomPerm

> Granular permission system for Minecraft NeoForge — grant individual vanilla commands to non-op players, with or without LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-source--available%20(All%20Rights%20Reserved)-blue.svg)](LICENSE)
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
- [Cluster mode (several servers)](#cluster-mode-several-servers)
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
- **Import from LuckPerms** — a server moving off LuckPerms brings its groups, its players and their nodes over instead of retyping them. Reading changes nothing and answers with a report, including what it leaves behind and why; only then can it be applied, after a backup of every config file. `/customperm import preview` then `/customperm import confirm`, or the Import page.
- **Temporary entries** — a node, a grade a player holds, a grade parent or a refusal can last a set time: `/customperm grade assign Steve vip 30d`. An expired entry stops counting at once, and a sweep then removes it and resends the command tree. The Grades and Players pages take a duration and show the time left.
- **Permissions of other mods** — nodes other mods declare through NeoForge's permission API are answered from the grades, so `/customperm grade addperm vip somemod.feature` works for them too. CustomPerm becomes NeoForge's permission handler on its own only without LuckPerms, and never replaces a handler an admin chose. Mods that call LuckPerms by name instead are listed by `/customperm modcheck`.
- **Tracks** — an ordered ladder of grades, so promoting and demoting move a player one rung at a time: `/customperm track promote Steve staff`, or the Tracks tab of the Players page. A track grants nothing itself; it is the convenience a server coming from LuckPerms expects.
- **Per-world entries** — a node on a grade or a player, or a grade a player holds, can apply in one world only: `/customperm grade adddeny member customperm.command.home world=the_nether`. It outranks the same holder's entry without a world, and the command tree follows the player through portals. The Grades and Players pages take a world too.
- **Cluster mode** — several servers without LuckPerms share their grades, commands, aliases, rate limits and activity log through one MariaDB or MySQL database, which CustomPerm reaches with its own connection or through [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib), either way using a database driver another mod provides; a change made on one applies on all within about two seconds. Off by default. See [Cluster mode](#cluster-mode-several-servers).
- **Chat prefixes and suffixes** — a grade, or one player, carries prefixes and suffixes around their name in chat and wherever the game shows it, each with a priority and, if wanted, a duration, like LuckPerms: the highest priority shows, or several in a row. With LuckPerms, the prefixes LuckPerms stores are shown instead. The name is decorated, never the message, so chat stays signed and reportable. Off until `/customperm names on`.
- **Export to LuckPerms** — a server that built its grades here and installs LuckPerms later writes them into LuckPerms as groups, users and nodes, so they keep deciding. Same two steps as the import, nothing translated. `/customperm export preview` then `/customperm export confirm`, or the To LuckPerms tab of the Import page.
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
| Grades | Always reachable, so the fallback can be read while LuckPerms runs or fails. A banner says when grades do not decide permissions; while LuckPerms is active the page is read-only, like the grade commands: grades with search and creation, ordered by weight; per grade, three tabs: ALLOW and DENY nodes, the grades it inherits and the ones it refuses, and the players who hold it beside those who refuse it, with their online state, assigned by name with completion, including players who are offline but joined the server before; delete a grade (with confirmation). A fourth tab, **Chat**, lists the grade's prefixes and suffixes with their priority and time left, adds one with a text, a priority and an optional duration, removes the one selected, previews the chat line, and carries the switches for decorating names and for stacking (`customperm.manage.config`). A duration box beside the node and player fields grants for a limited time, and the rows show the time left. A world box beside it limits a node or an assignment to one world (`the_nether`), shown on the row; the Parents and Chat tabs have one too. A fifth tab, **Meta**, lists and sets the grade's meta, with a duration and a context |
| Players | Nodes carried by one player rather than by a grade: every player holding something of their own plus everyone online, with search; per player, their ALLOW and DENY nodes and the grades they hold, read-only here. A player who holds nothing yet is reached by typing their name. Writing needs `customperm.manage.grades`, like the Grades page. A **Chat** tab edits the prefixes and suffixes the player carries themselves, the same way. A **Meta** tab sets the meta the player carries themselves. A **Tracks** tab shows every track with the player's rung and promotes or demotes them one rung. The node field takes a duration and a world too, and the grades held in one world are listed with it |
| Import | Only when LuckPerms is installed: brings its groups, players and nodes over, in two steps. Read LuckPerms answers with the report and changes nothing, Import applies that report and nothing else, after a backup. Two options: exposing the commands the translated nodes need, and adding to grades of the same name or replacing them. Needs the three write nodes together. A second tab, **To LuckPerms**, exports the grades the other way: Read the grades, then Export, which stays disabled until the admin says LuckPerms is backed up; the page follows the progress while it runs. Needs `customperm.manage.grades` and `customperm.manage.luckperms` |
| Logs | Two tabs, newest first, with search. **Admin**: every change made with `/customperm` commands, the interface and the LuckPerms editor, and the changes LuckPerms itself records (`/lp`, web editor): when, who, from where, what, and the result or refusal. **Players**: every command players type, only while recording is on (off by default); arguments of private-message and password commands masked unless masking is turned off. Switching recording and masking needs `customperm.manage.logs` |
| Help | Every feature explained where it is used: what it does, when to use it, how, and the text commands behind it. Topics on the left, searchable (Ctrl+F looks in their text too), the topic on the right. Readable by anyone who can open the interface |

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
| `/customperm alias param add <alias> <name> <player\|integer\|word\|text>` | Declares an argument, at the end of the list. A step reaches it with `${name}`. |
| `/customperm alias param remove <alias> <name>` | Drops an argument. |
| `/customperm alias param move <alias> <name> <index>` | Moves an argument to another 0-based position. |
| `/customperm alias param optional <alias> <name> <true\|false>` | Whether the argument may be left out. |
| `/customperm alias param default <alias> <name> [value]` | What a left-out argument substitutes; without a value, nothing. |
| `/customperm alias param range <alias> <name> <min> <max>\|clear` | The bounds an integer argument accepts. |
| `/customperm alias param choices <alias> <name> [a,b,c]` | What a word argument suggests, and the only values it then accepts. |
| `/customperm alias param selectors <alias> <name> <true\|false>` | Whether a text argument may carry an entity selector. Off by default. |
| `/customperm alias params <alias>` | Shows the arguments, in the order they are typed. |

### Grades (internal system, LuckPerms-less)

These commands are **blocked when LuckPerms is active** — use `/lp` instead.
They manage ALLOW nodes. Internal DENY nodes are stored in `grades.json` under `deniedPermissions`.

| Command | Effect |
|---|---|
| `/customperm grade create <name>` | Creates an empty grade. |
| `/customperm grade delete <name>` | Deletes a grade and unassigns it from every user. |
| `/customperm grade addperm <grade> <node> [duration] [world=<dim>]` | Adds a permission node to the grade, for good, for a duration such as `30d`, or in one world such as `world=the_nether`. |
| `/customperm grade removeperm <grade> <node> [world=<dim>]` | Removes a node, the one limited to that world when one is given. |
| `/customperm grade adddeny <grade> <node> [duration] [world=<dim>]` | Adds a DENY node: refused, operators included, unless a more specific node allows it. |
| `/customperm grade removedeny <grade> <node> [world=<dim>]` | Removes a DENY node. |
| `/customperm grade weight <grade> <weight>` | Sets the tie-break weight, 0 by default, negative allowed. |
| `/customperm grade displayname <grade> [set <text> \| clear]` | Shows, sets or clears the name the listings and the Grades page show for the grade, `Very Important (vip)`. Display only: commands, files and other grades keep naming it `vip`. 48 characters at most, on one line. |
| `/customperm grade parent add <grade> <parent> [duration] [world=<dim>]` | Makes the grade inherit another, for good, for a duration or in one world; a cycle is refused, in any world. |
| `/customperm grade parent remove <grade> <parent>` | Stops inheriting it. |
| `/customperm grade parent adddeny <grade> <parent> [duration] [world=<dim>]` | Refuses a grade wherever this one would inherit it, for good, for a duration or in one world. |
| `/customperm grade parent removedeny <grade> <parent>` | Stops refusing it. |
| `/customperm grade parent list <grade>` | Shows what the grade inherits and what it refuses. |
| `/customperm grade assign <player> <grade> [duration] [world=<dim>]` | Assigns the grade to a player, online or offline if they joined the server before; with a world, it applies there only. |
| `/customperm grade unassign <player> <grade> [world=<dim>]` | Unassigns, online or offline. |
| `/customperm grade setdefault <grade>` | Applies the grade to every player, below their own grades. |
| `/customperm grade cleardefault` | No grade applies to every player any more. |
| `/customperm grade list` | Lists defined grades, heaviest first, each under its display name when it has one. |

Nodes carried by one player, above their grades:

| Command | Description |
|---|---|
| `/customperm user addperm <player> <node> [duration] [world=<dim>]` | Adds an ALLOW node to that player alone. |
| `/customperm user removeperm <player> <node> [world=<dim>]` | Removes it. |
| `/customperm user adddeny <player> <node> [duration] [world=<dim>]` | Adds a DENY node to that player alone. |
| `/customperm user removedeny <player> <node> [world=<dim>]` | Removes it. |
| `/customperm user denygrade <player> <grade> [duration] [world=<dim>]` | Makes one player refuse a grade, wherever one of theirs would bring it, or in one world only. |
| `/customperm user undenygrade <player> <grade> [world=<dim>]` | Stops refusing it. |
| `/customperm user list <player>` | Shows the grades they hold, the ones they refuse, and the nodes they carry, with the time left on temporary ones and, per world, what they hold there only. |

**Durations.** `w`, `d`, `h`, `m` and `s`, alone or combined: `30d`, `2h`, `1d12h`, `1w`, ten years at most.
Without one, an entry is permanent. Adding an entry already there with a duration makes it temporary from
now, and without one makes it permanent: the last thing said holds. An expired entry stops counting at once,
and the entry below it answers (an expired `a.b.c` leaves `a.b.*` to decide). Once a second CustomPerm
removes what has run out, saves, sends the command tree again and records it in the activity log. A grade
parent and a grade refused by a grade take a duration too, from the command or the Parents tab: while it
lasts the chain follows it, and once it has run out the chain goes on without it.

**Worlds.** `world=<dimension>` limits a node on a grade or a player, or a grade a player holds, to one
world: `world=the_nether`, `world=the_end`, `world=overworld`, or a modded dimension by its full id
(`world=mymod:mining`). Tab-completion offers the worlds the server has loaded. At the same specificity and
from the same holder, an entry limited to the player's world outranks the same entry without one, like a
contextual node in LuckPerms: a grade that allows `/home` everywhere and denies it in the Nether refuses it
there. The holder still comes first: a heavier grade, or the player's own node, decides over a lighter
grade's world node. A player's command tree is sent again when they change world. An entry limited to a
world can be temporary too: give both, in either order (`/customperm grade assign Steve vip 7d
world=the_nether`), and it lasts there that long, the time left shown per world in listings and on the
pages. A grade parent, a refusal (by a grade or a player)
and a prefix or suffix can be limited to a world the same way: `/customperm grade parent add vip builder
world=the_nether`, `/customperm user denygrade Steve vip world=the_end`, `/customperm grade prefix vip in
the_nether add 10 &c[Hot] `. A parent inherited in one world is followed there only, at the same depth
as a global one; a prefix limited to a world shows there before the same holder's global one at the same
priority, and names are rebuilt on a world change. `parent remove|removedeny` and `undenygrade` take the same
`world=` to name the one limited to it, and the prefix `remove` and `clear` the same `in <world>`.

**Other contexts.** `world=` is one context among several, joined with a comma after the node, grade or
parent: `gamemode=creative` (`survival`, `creative`, `adventure`, `spectator`), and any key a static context
sets for the whole server, like LuckPerms' `static-contexts` (`region=eu`). Two values of one key are either
one: `world=the_nether,world=the_end` applies in both; two keys must both hold:
`world=the_nether,gamemode=creative`. At the same specificity and holder, an entry naming more keys outranks
one naming fewer. A key nothing on this server sets is refused, since the entry would apply nowhere; `server=`
is set only in a cluster (see [Cluster mode](#cluster-mode-several-servers)). The command tree and names follow a change of game mode as they follow a change of
world. For prefixes, `in <world>` stays, and any other context goes quoted after `where`:
`/customperm grade prefix vip where "gamemode=creative" add 5 [Builder] `. The world box of the pages takes
the same text (`the_nether,gamemode=creative`).

| Command | Description |
|---|---|
| `/customperm contexts` | Lists the static contexts, true for every player here. |
| `/customperm contexts <player>` | What holds for that player now: world, game mode and the static contexts. |
| `/customperm contexts set <key> <value>` | Sets a static context (`region eu`); `world`, `gamemode` and `server` cannot be set. |
| `/customperm contexts unset <key>` | Removes it; entries limited to it apply nowhere until it is set again. |

### Tracks

A track is a ladder of grades, lowest first, like a LuckPerms track. It grants nothing by itself: promoting
a player replaces the grade they stand on with the next one, in one change.

| Command | Description |
|---|---|
| `/customperm track create <track>` | Creates an empty track. |
| `/customperm track delete <track>` | Deletes it; its grades and who holds them are untouched. |
| `/customperm track append <track> <grade>` | Adds a grade as the new top rung. |
| `/customperm track insert <track> <grade> <position>` | Puts a grade at a rung, 1 being the lowest. |
| `/customperm track remove <track> <grade>` | Takes a grade off the track; players holding it keep it. |
| `/customperm track promote <player> <track> [world=<dim>]` | One rung up; a player on no rung gets the first one. With a context, among the grades held there only. |
| `/customperm track demote <player> <track> [world=<dim>]` | One rung down; from the first rung, off the track. With a context, the same there only. |
| `/customperm track list [track]` | Shows the tracks and their rungs. |

Promote and demote also open to `customperm.track.<track>`, on top of `customperm.admin`: a moderator trusted
with one ladder moves players on that track only, from the command and the Tracks tab, and nothing else of
the grades opens to them. `customperm.track.*` covers every track. Moving oneself needs
`customperm.manage.grades`: with the track node alone it is refused, since a rung up the ladder may be the
grade that hands out `customperm.manage.grades`. The lockout guard applies to every move.

The other track commands need `customperm.manage.grades`, like the grades, and are refused while LuckPerms is active, whose own
tracks decide there. Only the grades a player holds everywhere count as rungs, unless a context follows the
track: then only the grades held in that context count, and the move gives and takes grades there only, like
LuckPerms' promote with a context. A grade held everywhere is not a rung there, and the result says when it
already covers that context. A grade refused everywhere or in that context is not given. The Tracks tab of
the Players page takes the same context in its world box, and shows each player's rung there. A player holding two grades of
the same track is refused rather than guessed at: unassign one first. The grade given up takes its expiry
with it, and the one given is permanent. Deleting a grade takes it off every track. A grade may sit on
several tracks.

### Meta

Meta is data a grade or a player carries for other mods to read, key to value, like LuckPerms meta. A mod
that declares a number or text permission node through NeoForge (a home limit, a rank label) reads the meta
named after that node, the way LuckPerms answers those nodes: a number node takes the value read as a whole
number, and its own default when there is none or the value is not a number; a text node takes the value as
it is. The player's own value wins, then the heaviest grade's, then the nearest ancestor's; one limited to a
context outranks the same holder's global one, and refusals, durations and the default grade apply as they
do to a node. Keys are written like permission nodes (lowercase, dotted); a value holds up to 256 characters,
quoted when it has a space.

| Command | Description |
|---|---|
| `/customperm grade meta <grade>` | Lists the grade's meta, with the time left and the context of each. |
| `/customperm grade meta <grade> set <key> <value> [duration] [context]` | Sets a value, replacing the one of that key there. |
| `/customperm grade meta <grade> unset <key> [context]` | Removes it. |
| `/customperm user meta <player> ...` | The same for one player's own meta. |

Completion offers the number and text nodes mods declared. The Grades and Players pages have a **Meta** tab
with the same boxes. Meta is answered from here only while CustomPerm is NeoForge's permission handler (see
[Mods that check permissions through NeoForge](#mods-that-check-permissions-through-neoforge)); with
LuckPerms, LuckPerms answers from its own meta.

### Chat prefixes and suffixes

| Command | Description |
|---|---|
| `/customperm grade prefix <grade>` | Lists the prefixes of a grade, highest priority first, with the time left on temporary ones. |
| `/customperm grade prefix <grade> add <priority> <text>` | Gives the grade a prefix at that priority, replacing one already there. |
| `/customperm grade prefix <grade> addtemp <priority> <duration> <text>` | The same for a duration such as `30d`. |
| `/customperm grade prefix <grade> remove <priority>` | Removes the prefix at that priority. |
| `/customperm grade prefix <grade> clear` | Removes every prefix of the grade. |
| `/customperm grade prefix <grade> in <world> ...` | The same edits for the prefixes limited to one world (`in the_nether`). |
| `/customperm grade suffix <grade> ...` | The same for suffixes. |
| `/customperm user prefix\|suffix <player> ...` | The same for the prefixes and suffixes one player carries themselves. |
| `/customperm names` | Says whether names are decorated, how, and from which backend. |
| `/customperm names on\|off` | Decorates names with their prefix and suffix, or stops. |
| `/customperm names format <format>` | How the name is built, `{prefix}{name}{suffix}` by default; `{name}` is required. |
| `/customperm names stack <prefix\|suffix\|both> <highest\|stacked> [limit]` | Shows the highest priority only, or up to `limit` (3 by default) in a row. |

Prefixes need `customperm.manage.grades` and are refused while LuckPerms is active, where they are set with
`/lp` and shown from there; `names` needs `customperm.manage.config` and works with either backend.

Which prefix shows: the one with the highest priority among everything the player reaches, like LuckPerms.
At equal priority, the player's own, then the heaviest of their grades, then the nearest grade a grade
inherits; a refused grade gives none, and the default grade applies only when nothing the player holds has
one. A holder carries one prefix per priority, so a priority names the one to remove. A temporary prefix
stops showing once its time is up, and the sweep removes it. Stacked, the prefixes show in that same order,
each text once, up to the limit, with the spacers set in `settings.json` between them. Text takes `&` colour
codes (`&6`, `&l`, `&r`) and `&#RRGGBB`, 64 characters at most. With LuckPerms, LuckPerms' own meta
formatting decides what its prefix is, and the stacking setting does not apply.

**The name is decorated, never the message.** Chat messages are signed: a mod that rewrites one makes the
client mark it as modified, and one that sends a system message instead loses reporting and the secure chat
indicator. The sender name is not part of what is signed, so CustomPerm decorates it and leaves every message
untouched. What that means: the prefix is on the name everywhere the game shows it (chat, death and
advancement messages, `/msg`, `/me`, the join message, the tab list), not on the name above a player's head,
and the `<Name>` around it is vanilla's. If another mod decorates names too, the two apply one inside the
other; that is why it is off by default.

### Nicknames

| Command | Description |
|---|---|
| `/customperm user nick <player>` | Shows the nickname of a player, online or who joined before. |
| `/customperm user nick <player> set <nickname>` | Shows them under that name, `&` codes allowed. |
| `/customperm user nick <player> clear` | Gives them their own name back. |
| `/nick [<nickname> \| clear]` | A player's own nickname, with `customperm.nick`; codes need `customperm.nick.color`. |

The admin command needs `customperm.manage.grades`, and the Players page has a nickname box beside the
selected player. Both work with LuckPerms too: a nickname is not a permission, so CustomPerm shows it whoever
decides permissions, and it is not imported or exported.

A nickname replaces the name itself, the `{name}` of the name format, wherever the game shows the name (see
above), and it applies with decoration off as well, since it was set for that one player on purpose. A team
colour still applies around it, and hovering the name in chat still shows the real one; `/customperm user
list` prints both. At most 16 visible characters, codes aside, on one line.

**A nickname cannot pass for another player.** One that reads as another player's name, or as another
player's nickname, is refused, whoever sets it. Letters are compared without their case, their codes, their
spaces or the separators `_ - .`, so `&cMod Team` is refused while `mod_team` plays here. One's own name is
always allowed. Look-alike letters from other alphabets are not caught: keep `customperm.nick` for players you
trust not to try.

`/nick` is left out when another mod already registers it, which the log says; nicknames are then set with
`/customperm user nick` only.

### Moving from LuckPerms

| Command | Description |
|---|---|
| `/customperm import preview` | Reads LuckPerms and says what an import would do. Changes nothing. |
| `/customperm import preview nocommands` | Same, without exposing the commands the translated nodes need. |
| `/customperm import confirm` | Applies what was previewed, adding to grades of the same name. |
| `/customperm import confirm replace` | Same, emptying a grade of the same name first. |

Needs `customperm.manage.grades`, `customperm.manage.commands` and `customperm.manage.luckperms` together,
since it writes grades, exposes commands and reads LuckPerms. A preview older than 10 minutes is read again
rather than trusted, and applying it forgets it, so a second confirm cannot import twice.

What carries over: a group becomes a grade with its weight, its display name, its parents and the groups it refuses; a node
set to false becomes a DENY; a player keeps their groups, the ones they refuse and their own nodes.
`minecraft.command.<x>` becomes `customperm.command.<x>` and `<x>` is exposed with it, without which the
node would grant nothing.

Prefixes and suffixes carry over with their priority and expiry. Of two at the same priority on one holder,
which CustomPerm cannot hold, the text that sorts first is imported and the report says so.

Temporary entries carry over with their expiry: a node, a player's group, a group's parent, a refusal. Entries limited to a
context carry over with it, temporary ones with their expiry: a node on a group or a player, a player's group,
a group's parent, a refusal, a prefix or suffix, and meta. On NeoForge LuckPerms names the dimension
`dimension-type`, which becomes `world=` here; `gamemode` stays as it is, and another key comes over when a
static context sets it here. Tracks carry over with their
groups in order; adding keeps a track that already exists here as it is, replacing takes LuckPerms' order.

What is left behind, and said in the report rather than dropped in silence: LuckPerms' `world` context, which on NeoForge is the save's name and not a dimension, a `server=` context unless a cluster runs,
and a key no static context sets here; a display name that is temporary, limited to a context or longer than 48
characters, which a grade's cannot be; regular expression permissions; and the nodes other mods read without declaring them to NeoForge, which nothing here would
read back. Adding keeps the display name of a grade that already has one. Nodes mods declared are imported as they are, on groups and players, since CustomPerm answers them. On players, only
what CustomPerm can read is looked at at all: their groups, their prefixes and suffixes, their `customperm`,
`minecraft.command` and `*` nodes, and the nodes mods declared.

**While LuckPerms is installed it still decides permissions**, so what is imported waits: it is readable on
the Grades page, and takes over the day LuckPerms is removed.

### Moving to LuckPerms

For the server that built its grades here and installs LuckPerms later: the grades are written into
LuckPerms so they keep deciding.

| Command | Description |
|---|---|
| `/customperm export preview` | Reads the grades and says what an export would write. Changes nothing. |
| `/customperm export confirm` | Writes what was previewed, adding to what LuckPerms already holds. |
| `/customperm export confirm replace` | Same, clearing the customperm nodes and parents of each group and player written first. |

Needs `customperm.manage.grades` and `customperm.manage.luckperms` together. **Run `/lp export <file>`
first**: an export writes into LuckPerms' storage, which nothing here can copy or undo, and one that fails
part way leaves LuckPerms half written. `/lp import <file>` is the way back.

What is written, as it is: a grade becomes a group with its weight, its display name, its parents and the groups it refuses; a
denied node becomes a node set to false; a player keeps their grades, the ones they refuse and their own
nodes; the default grade becomes a parent of the LuckPerms `default` group; a track becomes a track with its
exported grades in order, after the players. Nothing is translated: on the LuckPerms backend CustomPerm reads
`customperm.*` as it is. Adding leaves a track LuckPerms already has as it is; replacing sets its groups.

What is left out, and named in the report: a grade whose name LuckPerms would refuse or lowercase (it
accepts lowercase letters, digits, `_`, `.` and `-`, 36 at most), and every parent, assignment or default
grade naming it. Adding keeps what LuckPerms already holds, weight and display name included, and replacing
writes the grade's display name, or none, in place of the one LuckPerms shows everywhere; where it sets a node the other
way, its value is kept and counted. Prefixes and suffixes are written with their own priority, temporary
ones temporary, and adding keeps one LuckPerms already has at the same priority. Replacing touches prefixes or
suffixes only where the grade sets some,
and never a temporary entry, a context it does not write, or the nodes of other mods. Meta is written by
key, in its context and with its expiry; adding keeps a value LuckPerms already has for that key, and
replacing clears only the keys the grade sets. A temporary
entry is written temporary, and one that has already run out is not written. An entry limited to a world is
written with LuckPerms' `dimension-type` context, the dimension on NeoForge (`the_nether` for a vanilla
world, the full id for a modded one); a game mode and a static context go as they are. Replacing clears the
customperm nodes and parents limited to such contexts along with the global ones.

It runs in the background, groups before players, one load and one save per holder, and reports where it
stopped if it fails. One export runs at a time. An export that would take away your own `customperm.admin`
or `customperm.manage.grades` is refused before anything is written. `grades.json` is not changed.

A grade change that would take away your own access to `/customperm` is refused and undone: allow `customperm.admin` for yourself first, or make the change from the console.

### Rate limits

Cap how many times one player may run a command or an alias within a sliding window. Limits apply to every player, operators included; the console and command blocks are never limited.

| Command | Effect |
|---|---|
| `/customperm ratelimit set <name> <max> <windowSeconds>` | Allows `<max>` uses per player per `<windowSeconds>`. Redefining a rule keeps its persistence mode. |
| `/customperm ratelimit persistence <name> <world_save\|immediate>` | Chooses when the usage history of that command is written to disk (see `ratelimits.json`). |
| `/customperm ratelimit scope <name> <server\|network\|hub,survival>` | In cluster mode, who shares the budget: each server alone (default), every server, or the servers named. See [Cluster mode](#cluster-mode-several-servers). |
| `/customperm ratelimit disable <name>` / `enable <name>` | Stops or resumes enforcing a rule without losing its numbers. |
| `/customperm ratelimit remove <name>` | Deletes the rule. |
| `/customperm ratelimit list` | Lists rules with their state and persistence mode. |

### Diagnostic and utilities

| Command | Effect |
|---|---|
| `/customperm test <player> <node>` | Verifies whether a player holds a permission node. Returns `GRANTED` or `DENIED`, with the reason: explicit ALLOW or DENY, or not set (granted to operators). |
| `/customperm debug <player> <command>` | Detailed report: is the command in the dispatcher? exposed? does op-level pass? is the perm granted? what does the wrapper actually return? |
| `/customperm status` | Global snapshot: backend, wrapped commands, exposed commands, aliases, grades, active admin alerts. |
| `/customperm modcheck` | Lists the installed mods that call LuckPerms' own API, which CustomPerm cannot answer without LuckPerms. See [Mods that call LuckPerms by name](#mods-that-call-luckperms-by-name). |
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
| `customperm.track.<track>` | With `customperm.admin`: promote and demote other players on that track only. `customperm.track.*` for every track. |
| `customperm.nick` | `/nick`: a player sets their own nickname. |
| `customperm.nick.color` | `&` codes in one's own nickname. |

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
  "logRetentionDays": 30,
  "decorateNames": false,
  "nameFormat": "{prefix}{name}{suffix}",
  "prefixStack": { "mode": "highest", "limit": 3, "start": "", "middle": "", "end": "" },
  "suffixStack": { "mode": "highest", "limit": 3, "start": "", "middle": "", "end": "" },
  "answerOtherMods": true
}
```

- `gateAllCommands` (internal backend, default `false`): `true` makes every command read its `customperm.command.<name>` node. An explicit DENY then blocks any command for operators too, and an ALLOW opens any command, so a grade holding `*` or `customperm.command.*` gets every command of the server. No effect with LuckPerms installed, which already checks every command.
- `defaultGrade` (internal backend, default empty): a grade applied to every player, below their own grades.
- `playerCommandLog` (default `false`): records every command players type in the activity log. Admin changes are always recorded.
- `maskPlayerCommandArguments` (default `true`) and `maskedCommands`: the arguments of these root commands are stored as `[masked]` (`/msg Alex hi` becomes `/msg [masked]`). A namespace prefix is ignored.
- `logRetentionDays` (default `30`): daily log files older than this are deleted at start and at each day change; `0` keeps them forever. Files: `<world>/customperm/logs/admin-YYYY-MM-DD.jsonl` and `players-YYYY-MM-DD.jsonl`, one JSON object per line.
- `decorateNames` (default `false`): puts each player's chat prefix and suffix around their name, from the grades or from LuckPerms. The name is decorated, never the message (see [Chat prefixes and suffixes](#chat-prefixes-and-suffixes)).
- `nameFormat` (default `{prefix}{name}{suffix}`): how the name is built; `&` codes allowed between the placeholders. A format without `{name}` is replaced by the default, so a prefix can never pass for a player.
- `prefixStack` and `suffixStack` (internal backend): `mode` is `highest` (one, the default) or `stacked` (several in a row, highest priority first, at most `limit`, 1 to 16); `start`, `middle` and `end` are written before the first, between two and after the last when stacked, `&` codes allowed. `/customperm names stack` sets the mode and the limit.
- `answerOtherMods` (default `true`): answer the permission checks other mods make through NeoForge, from the grades. Read at start; see [Mods that check permissions through NeoForge](#mods-that-check-permissions-through-neoforge).
- `cluster` (internal backend, off by default): several servers sharing one configuration through Arcadia Lib; read at start. See [Cluster mode](#cluster-mode-several-servers).
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

Aliases with their steps, and the arguments they take. An alias that takes none is absent from
`aliasParameters`, which is what every file written before arguments existed looks like: it loads
unchanged.

```json
{
  "aliases": {
    "fly": ["gamemode spectator"],
    "heal": [
      "effect give @s minecraft:instant_health 10 100",
      "effect give @s minecraft:saturation 1 100",
      "say healed!"
    ],
    "warn": ["say [WARNING] ${target}: ${reason}"]
  },
  "aliasParameters": {
    "warn": [
      { "name": "target", "type": "player" },
      { "name": "reason", "type": "text", "optional": true, "defaultValue": "no reason given" }
    ]
  }
}
```

An argument carries `name` and `type` (`player`, `integer`, `word` or `text`), and optionally
`optional`, `defaultValue`, `min` and `max` (integer), `choices` (word) and `allowSelectors` (text).

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

When LuckPerms is active, this file is ignored (permissions go through LP).

Prefixes and suffixes are lists of `{ "priority", "text", "expires" }`: `prefixes` and `suffixes` on a
grade, `userPrefixEntries` and `userSuffixEntries` at the top, per player. `expires` is in epoch seconds, 0
or absent for good. A file written before priorities, with a single `prefix` on a grade or `userPrefixes` at
the top, is read into these at priority 0, which shows the same prefix it did.

`userNicknames` at the top maps a player to their nickname, codes included. It is read on both backends.

`displayName` on a grade is the name the listings and the Grades page show for it; absent, the grade is
shown by its name. It is never looked up: `userGrades`, `parents` and `tracks` name the grade by its key.

Temporary entries keep their collections and add a map of epoch seconds beside each: `permissionExpiries`
and `deniedPermissionExpiries` on a grade, `userPermissionExpiries`, `userDeniedPermissionExpiries`,
`userGradeExpiries` and `userDeniedGradeExpiries` at the top, player first, then the node or grade, and
`parentExpiries` and `deniedParentExpiries` on a grade, keyed by the grade inherited or refused. An entry
absent from them is permanent, and an expiry naming no entry is dropped when the file is read.

Entries limited to a world sit beside the others, keyed by their context: `contexts` on a grade maps a
context to its `permissions`, `deniedPermissions`, `parents`, `refused`, `prefixes` and `suffixes` there, and
`userContexts` at the top maps a player, then a context, to the `grades`, `refused`, `permissions`,
`deniedPermissions`, `prefixes` and `suffixes` they hold there. A context is written
`key=value`, `world=minecraft:the_nether`; `world=the_nether` written by hand is read as the same one. A key
this version does not read is kept as it is and matches nothing, so a file written by a later version is not
damaged.

`userPermissions` and `userDeniedPermissions` carry nodes for one player, above every grade they hold. `deniedPermissions` is only used by the internal backend. The most specific entry wins across what the player carries and all their grades (exact node, then `a.b.*`, then `a.*`, then `*`); at the same level a node on the player wins, then the heaviest grade, then a DENY between equals. `parents` lists the grades a grade inherits: their entries apply where it says nothing as precise about the node, nearest ancestor first, and a chain competes with the other grades at the weight of the grade the player actually holds. `deniedParents` and `userDeniedGrades` take a grade out of the resolution, respectively for that grade's own chain and for that player everywhere, the default grade included; a refusal never turns what the refused grade allows into a denial. `weight`, `parents`, `deniedParents` and `userDeniedGrades` are all optional and empty when absent, which makes the DENY rule the only tie-break, as it was before the fields existed. What the player carries and the grades they hold decide first; only a node none of it mentions falls through to the default grade.

`tracks` maps a track to its grades, lowest first. It only names grades, which is why it lives in this file:
deleting a grade takes it off every track in the same write. It decides nothing when permissions are
checked.

---

## Cluster mode (several servers)

Several servers running CustomPerm **without LuckPerms** can share one configuration: a grade created on the hub
applies on survival and creative within about two seconds. Off by default; a server that does not turn it on
behaves exactly as before. With LuckPerms, cluster mode does nothing: LuckPerms shares its own storage between
servers, and CustomPerm reads it.

**What it needs**

- A dedicated server (a singleplayer or LAN world always runs alone), and one MySQL or MariaDB database every
  server reaches. Two ways to connect, chosen by `cluster.connection`:
  - `"direct"`: CustomPerm connects itself, nothing else to install. Fill `cluster.serverName` (different on each
    server) and `cluster.database` (`host`, `port`, `name`, `user`, `password`, `tls`) in `settings.json`. The
    password sits in that file: keep it readable by the server's account only.
  - `"arcadia"` (the default): through [Arcadia Lib](https://www.curseforge.com/minecraft/mc-mods/arcadia-lib) 1.3.0
    or later, for servers that already run it. The database is set in Arcadia Lib's
    `<world>/serverconfig/arcadia/lib/database.toml`, and the name is its `server_id` in
    `<world>/serverconfig/arcadia/lib/server.toml` (Arcadia Lib's default is `server1` for everyone).
- A server whose name another running server already uses stays out of the cluster and says so.
- `"cluster": { "enabled": true }` in each server's `settings.json`, then a restart. Cluster settings apply at
  start only.

**What is shared**

Each part can be shared or kept local, in `settings.json`:

```json
"cluster": {
  "enabled": false,
  "connection": "arcadia",
  "serverName": "",
  "database": { "host": "localhost", "port": 3306, "name": "customperm", "user": "", "password": "", "tls": "off" },
  "share": { "grades": true, "commands": true, "aliases": true, "rateLimits": true,
             "log": true },
  "pollSeconds": 2,
  "whenDatabaseLost": "last-known"
}
```

- `grades`: every grade and everything players hold (`grades.json`), tracks included.
- `commands`, `aliases`, `rateLimits`: exposed commands, aliases, rate-limit rules.
- Rate-limit **counters** are shared per rule, by its scope: `server` (the default, each server counts its own
  uses), `network` (one budget for every server: 3 an hour means 3 across the cluster, to within `pollSeconds`), or
  server names such as `hub,survival` (those servers share one budget, the others count on their own). Set it with
  `/customperm ratelimit scope <command> <scope>` or on the Rate limits page. Only uses of shared rules reach the
  database.
- `log`: the activity log. Entries from other servers show in the Logs page and `/customperm log` with
  `@<server>`; each server's files keep only what happened there.
- `settings.json` itself stays local: the default grade, `gateAllCommands`, name display and static contexts are
  set per server.

**How changes travel**

- Each grade, player, command, alias and rule is its own row with a version. A change is written before the
  command answers. Two admins changing two different grades never collide; if two servers change the same grade
  at the same moment, the second is **refused**, sees the first change, and is told which server made it.
- The other servers read what changed every `pollSeconds` (2 by default) and apply it: permissions, command trees
  and names follow.
- The first server to join fills the database from its files. A server joining a filled database takes what the
  database holds; its previous files are copied to `backup/` first.
- Permission checks never wait on the database: they read memory, as on a single server.
- Load on the database, per server at rest: one query every `pollSeconds` for all shared parts, one for the log, one
  for rate-limit uses when a rule is shared, and a heartbeat every 10 seconds; nothing is read twice. Measured on
  MariaDB: about 2 queries a second and under 1 KB/s per server. The tables are `customperm_rows`, `customperm_seq`,
  `customperm_servers`, `customperm_log` and `customperm_uses`, created in the configured database at first start.

**`server=` context**

In a cluster, `server=<name>` is a context like `world=`: `/customperm grade addperm vip customperm.command.fly
server=creative` grants `/fly` on the server named `creative` only. Outside a cluster it is refused, since it would
apply nowhere. Import from LuckPerms carries LuckPerms' `server=` nodes while a cluster runs, and export writes
them back; both match only when LuckPerms' server names are the same as the cluster's.

**When the database is lost**

The server keeps the rights it last read, refuses admin changes until the database answers again, and tells the
admins online. Its files are kept up to date as a copy, so a restart during the outage starts from the last known
state. The dashboard shows the cluster: this server's name, the parts it shares and the other servers it hears.

**Security**

`tls` (direct connection) is `off` by default, since most self-hosted databases have no certificate; `trust`
encrypts without checking the certificate, `verify` encrypts and checks it. Arcadia Lib connects without TLS. Either
way, without `verify` keep the database on a private network or the same machine: anyone able to read or alter that
traffic can read or change every permission.

`verify` only succeeds when the database's certificate chains to a certificate authority the **Java runtime already
trusts**. A self-signed certificate, or one from your own authority, is not, and CustomPerm has no setting for a CA
file: the server's JVM has to be told, by adding `-Djavax.net.ssl.trustStore=<truststore>` and
`-Djavax.net.ssl.trustStorePassword=<password>` to its start-up arguments, with the authority imported into that
truststore. Checked against MariaDB 10.4 with a private authority, on both drivers: `verify` fails while the
authority is unknown to the runtime, and succeeds as soon as it is trusted. Use `trust` when you want the traffic
encrypted without that setup.

The direct connection needs a JDBC driver for MariaDB or MySQL **already loaded by another mod on the server**:
CustomPerm ships none, and instantiates by name whichever one it finds. Arcadia Lib brings one, and so does any
mod that carries a driver. On a server carrying none, cluster mode does not start and says why. See
[NOTICE.md](NOTICE.md).

> **Cluster mode through Arcadia Lib asks something of your players.** CustomPerm itself never does: its network
> channel is optional, so a client without the mod joins and simply has no admin interface. Arcadia Lib registers
> its own channels as required, so **every player has to install Arcadia Lib client-side to join a server running
> it**. That is a property of Arcadia Lib, not of CustomPerm, and it applies the moment you install it, whether or
> not cluster mode is on. Plan for it: ship it in your pack, or use another mod that provides a driver without
> claiming the client.

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

### Arguments

An alias can take arguments, which its steps reach with `${name}`:

```
customperm alias add warn say [WARNING] ${target}: ${reason}
customperm alias param add warn target player
customperm alias param add warn reason text
customperm alias params warn                   # <target> <reason>
```

`/warn Steve stop digging there` then runs `say [WARNING] Steve: stop digging there`. Tab completion
offers what the type knows: a player argument suggests the online players, a word argument its declared
choices.

Four types:

| Type | Accepts | Substituted with |
|------|---------|------------------|
| `player` | one online player, by name or by a selector resolving to one | that player's name |
| `integer` | a whole number, inside the declared range | the number |
| `word` | one word, no space; one of the declared choices when there are any | the word |
| `text` | the rest of the line, spaces included; only the last argument may be one | the text |

Each argument can be made optional and given a default, which is what it substitutes when left out:

```
customperm alias param add kit count integer
customperm alias param range kit count 1 64
customperm alias param default kit count 8     # optional from here on, 8 when left out
```

An argument that may be left out cannot be followed by one that may not, and nothing can follow a
`text` argument: both are Brigadier's rules, and both are refused when you declare them.

`${name}` is deliberate: no command syntax produces a dollar sign followed by a brace, so the NBT and
JSON braces of a real step (`give @s diamond_sword{Enchantments:[]}`) are never mistaken for an
argument. A `${name}` naming an argument the alias does not take is left in the step exactly as typed,
and saying so is a warning rather than a refusal, since the step and the argument are declared in
either order.

**A value never carries an entity selector**, unless the argument says otherwise. Steps run at op
level 4, so `@a` reaching one would act on everyone rather than on the player named. A `word` cannot
hold an `@` at all (Brigadier refuses it), a `player` is resolved to a name before substitution, and a
`text` refuses one until `customperm alias param selectors <alias> <name> true` says it may.

### Minecraft selectors

Selectors (`@s`, `@p`, `@a`, etc.) work as expected in the steps themselves. The source during execution is the player who invoked the alias.

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
| Permission resolver | Default deny, direct ALLOW, wildcard ALLOW, global wildcard, explicit DENY, most specific entry wins across grades, grade weight breaking a tie whatever the assignment order, DENY on equal weights, weight never beating specificity, a node on the player outranking their grades without beating a more specific one, inheritance with the nearest ancestor deciding, diamonds, cycles and a chain competing at the weight of the grade held, a grade refused by another grade or by a player being unreachable without becoming a denial, denied `*` with explicit allows, default grade layer. Entries limited to a world: parsing and refusal of a context, matching without allocating, a world node outranking the holder's global one without beating a more specific node or a heavier holder, a grade held in one world, inheritance and the default grade carrying world nodes, hand-written spellings merged, a parent inherited in one world with its own chain, a grade refused in one world by a grade or by the player, and a prefix limited to a world. |
| Internal grades | Create/list/delete grades, assign/unassign players, prevent duplicates, cascade grade deletion through player assignments. |
| Command exposure | Add/remove/list exposed commands, idempotent changes, non-exposed commands remain denied by CustomPerm. |
| Alias config | Create, overwrite, remove, list aliases, preserve order, split semicolon-delimited steps, ignore blank steps. |
| Alias execution | Permission node shape, op-level 4 execution, step ordering, continue-after-error behavior, recursive-cycle limit, live step replacement. |
| Config manager | Atomic snapshot reads, serialized atomic saves, concurrent reload rejection, rollback after invalid JSON, backup creation, backup rotation. |
| Backward compatibility | Missing files, `{}` files, explicit `null` collections, unknown future fields, partial config files. |
| LuckPerms selection | Internal backend when LP is absent, version parsing, minimum version gate, stable backend selection. |
| GameTests, both modes | Nicknames set by command, by the Players page and by `/nick` with its node, shown with decoration off, codes gated by their node, a name or nickname of another player refused whatever its case, codes or separators. Command exposure and removal with a non-op player, operator preservation, `/customperm` refused to non-ops, reconnection, aliases run with op-4 elevation by node holders only and unable to reach `/customperm`, step editing, recursion and shadowing guards, reload of hand-edited `aliases.json`, rate limits (refusal message, shared counter per root, per-player isolation, console exemption, window expiry, reconnection, repeated reloads, rule removal, aliases), all-or-nothing reload, concurrent reload refusal, unsaved changes after a failed reload, `null` entries, command-tree repush on reload, admin alerts in operators' chat, GUI and editor packets refused to non-operators, diagnostics output, tab-completion of every `/customperm` argument and no suggestions for non-operators, operators refused an exposed command, an alias, `/customperm` or an interface area by an explicit DENY while the console keeps access, a denied `*` blocking everything but explicit allows, admin changes from commands and the interface recorded with refusals, player commands recorded only when on and masked by default, files on disk, reload from disk skipping unreadable lines, retention, Logs page switches gated by their node, `/lp` changes recorded (LuckPerms mode), operators without the nodes refused `/customperm` and the interface while the console keeps access, each area needing its own `customperm.manage` node for the command and the page alike, the nodes alone opening nothing to a non-operator, the upgrade notice announced once for a configuration written before 1.1.0 and the configuration stamped afterwards. |
| GameTests, internal mode | Grade commands, union of grades, most specific entry wins, grade weight breaking a tie, a display name shown by the listings and never a key, nodes carried by a player, the Players page and its lockout guard, grade parents with inheritance applied live and cycles refused, refusals applied live and contradictions answered, every wildcard form, editor without LuckPerms, `gateAllCommands` and an allowed `*`, a default grade restricting an accidental operator, refused self-lockout by command and interface. Chat prefixes: `&` codes and the name format, the name chat binds decorated from the grades with the heaviest grade and the player's own winning at equal priority and a higher priority winning over both, stacking, removal by priority, the commands with their listing, a temporary prefix expiring and swept, the tab list, the switch and the format applied at once, and the Chat tabs through the interface with priority and time left. Temporary entries: durations read and refused, a grant expiring before any sweep, the sweep tidying the file, logging and resending the tree, a held grade and a refusal expiring, a grade parent and a grade's refusal expiring and swept, and durations through the interface with the time left read back. Entries limited to a world: contexts read and refused, a node granted in the Nether only, the command tree resent and the verdict changing as a real player teleports between worlds, a grade and a player's own DENY held in one world, removal by world, a deleted grade leaving no world assignment behind, and a parent, a player's refusal and a prefix limited to the Nether followed as the player moves, the name included. Tracks: building one, a real player promoted and demoted along it with the command tree following, a temporary rung given up with its expiry, several rungs and a refused grade refused, a deleted grade leaving the ladder, and promote and demote from the Players page; a track node moving others on its track only, by command and page, never oneself, nothing else of the grades opening; within one world, the rungs held there only, a refusal there stopping the move, nothing moved everywhere, from the command and the page. Permissions of other mods: CustomPerm selected as handler, nodes declared under a foreign namespace answered with their default, an exact DENY, a wildcard ALLOW, a number node and an offline check; a number and a text node answered from a grade's meta, a player's own winning, a value that is no number leaving the default, an expired value swept, one limited to another world not applying, and meta set from the Grades page. |
| GameTests, LuckPerms mode | Import from a source with one of everything: what carries over, a group's display name included and a temporary one left behind, what is left behind with its reason, reading writing nothing, what lands in the configuration, the three nodes asked together, and a spent preview refused. Export into a real LuckPerms: groups, weight, display name kept by adding and written by replacing, parents, denials, the default group and a player's nodes written, a renamed grade refused, adding keeping LuckPerms' own values and prefix, replacing clearing only the customperm nodes, progress per holder, the two nodes asked together, the lockout guard, and a spent preview refused. In-game editor against a real LuckPerms: groups, nodes with contexts and expiry, inheritance, meta, prefix and suffix, weight, display name, player groups and primary group, tracks, promote and demote, write gating by node and level, edit and sync rate limits; command tree resent after a LuckPerms change; `deny` and `internal` fallback when LuckPerms becomes unavailable. A LuckPerms prefix reaching the name without a reconnect; several prefixes carried by the import with their priority, and exported with theirs, a temporary one temporary. Temporary nodes, groups and group parents imported with their expiry and exported temporary. Nodes, a player's grade and refusal, a group's parent and refusal, and a prefix limited to a world imported and exported with LuckPerms' `dimension-type` context, a node limited to a game mode, LuckPerms' `world` context (the save's name) left behind, a `server=` node left behind unexposed. A track imported and exported with its groups in order. A node another mod declared imported from a group and a player, an undeclared one left behind, and LuckPerms keeping the permission handler. |
| Performance | `PermissionResolver.resolve()` and concurrent config snapshot reads via JMH, including a check made from a world with no entry limited to one (the path every server takes) and one a world node decides. |

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

When a player changes world, the tree is sent again: always with LuckPerms, whose nodes may be per world, and
on the internal backend only while some entry is limited to a world. A respawn needs nothing, vanilla sends
the tree then.

### Aliases

Registered as `Commands.literal(name).requires(...).executes(...)`. The `executes` normalizes each step, strips any leading `/`, and executes the step through the original command node when CustomPerm has wrapped that command, using a `CommandSourceStack` whose `permissionLevel = 4`. Failed steps are reported and logged, but later steps still run.

---

## Compatibility with other mods

### Mods that add commands

**Compatible automatically with either backend.** Commands registered through the standard `RegisterCommandsEvent` are processed at `LOWEST` priority, followed by a repair pass when the server starts. No dedicated integration is normally required.

Expose a third-party mod's command with `customperm command add <name>`, then grant `customperm.command.<name>` — via `/lp` when LuckPerms is installed, or via an internal grade otherwise. A narrow CustomPerm alias remains an option when you want tighter, sub-command scope. To verify detection: `customperm scan <pattern>`.

### Mods that check permissions through NeoForge

Mods that declare their permission nodes through NeoForge's permission API (`PermissionAPI`) ask one
handler, the one `permissionHandler` names in `config/neoforge-server.toml`. CustomPerm offers
`customperm:handler`, which answers those checks from the grades, so such a node is granted or denied like
any other: `/customperm grade addperm vip somemod.feature`, wildcards included (`somemod.*`). Completion
lists the nodes mods declared.

- **Without LuckPerms**, CustomPerm selects its handler itself at start, as long as `permissionHandler` is
  still NeoForge's default and `answerOtherMods` in `settings.json` is `true` (the default). The change is
  made in memory, the file keeps its value, so setting `answerOtherMods` to `false` takes effect at the next
  start. A value you chose in that file is never replaced.
- **With LuckPerms**, LuckPerms is the handler and CustomPerm never takes it: there is only ever one, and two
  mods rewriting it would be impossible to diagnose.
- The start log says which handler answers and why.

What is answered: a yes/no node gets `true` for an explicit ALLOW, `false` for a DENY, and when nothing
here mentions it, the default its mod gave it (often an operator check), never a refusal. A node holding a
number or a text is answered from the meta of the same name (see [Meta](#meta)), and its default when
there is none. An offline player is resolved from
the grades too.

### Mods that call LuckPerms by name

Some mods do not ask NeoForge: they call LuckPerms' own API (`net.luckperms.api`) directly. CustomPerm cannot
answer those checks. With LuckPerms installed, LuckPerms answers them. **Without LuckPerms, such a mod falls
back to its own default, often the operator level, and a node granted to it in a grade does nothing.** This
is the one limit of running CustomPerm alone, and it comes from the other mod's choice, not from a CustomPerm
feature. Answering them would mean shipping a copy of LuckPerms' API, which cannot load next to the real
LuckPerms.

`/customperm modcheck` tells you which installed mods are in that case. It reads the compiled classes of every
mod once, in the background, and lists the mods whose classes name LuckPerms' API:

- **LuckPerms only**: without LuckPerms, CustomPerm cannot answer its checks. Its nodes belong in LuckPerms, or
  ask its author to declare them through NeoForge's permission API, which CustomPerm then answers.
- **Also uses NeoForge's permission API**: the checks it makes through NeoForge are answered; it may use
  LuckPerms only when LuckPerms is there.

A mod listed here may only use LuckPerms when it is present; its page or config says. LuckPerms and CustomPerm
themselves are left out. The answer is kept until the next start, since installed mods cannot change before.

What can still be done without LuckPerms, when such a mod falls back to the operator level as most do:

| What the mod checks | Without LuckPerms |
|---|---|
| Who may run one of its commands | Expose it with `/customperm command add <command>` and grant `customperm.command.<command>` in a grade: CustomPerm's check replaces the mod's. |
| Whether the command then runs (checked inside it) | An alias: its steps run at operator level 4, for the players holding `customperm.alias.<name>` only. |
| A feature in game, outside any command (a limit, an item, an ability) | Nothing a permission mod can answer. Look in the mod's config for a level or an allow-all setting, ask its author to use NeoForge's permission API, or install LuckPerms. |

If what you need is only to forbid actions in a place (building, breaking, using items or other mods' spells
there), that is zone protection rather than permissions: [ArcadiaGuard](https://github.com/Team-Arcadia/ArcadiaGuard)
does it on its own, with its own zone members and an operator bypass, with or without LuckPerms.

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
- **LP contexts beyond worlds untested**: LuckPerms resolves its own contexts through `getCachedData()`. Per-world nodes are covered, the command tree being resent on a world change; per-server and custom contexts are passed through but not tested.
- **The admin interface needs CustomPerm client-side**: without it, administration stays fully command-driven.
- **The in-game LuckPerms editor is not the web editor**: it covers groups, users, tracks, nodes, meta and chat meta, but not bulk operations, node search across all holders, or the web editor's undo history. For those, `/lp editor` remains the tool.
- **Shortcut commands have their own rules**: some commands are shortcuts that redirect to another one (`/tp` to `/teleport`, `/msg` and `/w` to `/tell`, `/xp` to `/experience`). Each spelling is exposed and rate limited under the name the player types: `tp` governs `/tp`, `teleport` governs `/teleport`. Exposing one does not open the other; configure both if both should be available.
- **Player commands need a player known to the server**: `/customperm grade assign|unassign`, `/customperm user`, and the interface accept players online or who joined the server before; they never query the session service, so a name that never joined cannot be assigned in advance. For that case, edit `userGrades`, `userPermissions` or `userDeniedPermissions` in `grades.json` (UUID as key) and run `/customperm reload`.

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

**Server operators may send the mod to their own players.** Automatic
mod-synchronization to the players joining *your* server is explicitly
permitted, as long as the official file is transmitted unmodified. A vanilla
client can still join a server running CustomPerm — the admin interface needs
the mod client-side, joining does not. Offering it as a general download or a
hosting-panel "one-click install" product is not permitted.

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
