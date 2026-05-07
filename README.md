# CustomPerm

> Granular permission system for Minecraft NeoForge — grant individual vanilla commands to non-op players, with or without LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-MIT-blue.svg)]()
[![Version](https://img.shields.io/badge/version-1.0.0-brightgreen.svg)]()

---

## Why this mod

Vanilla Minecraft has a binary system: a player is either **op** (every command) or **non-op** (no management commands). No middle ground.

CustomPerm lets you grant **precisely** the commands you want to non-op players, without giving them full op. For example:

- You want a player to use `/gamemode spectator` but not `/op`? Done.
- Grant `/give` to a VIP rank without enabling `/ban`? Done.
- Build macros (aliases) that chain multiple commands into one? Done.

The mod natively integrates with **LuckPerms** if installed, otherwise it ships its own JSON-backed grade system.

---

## Table of contents

- [Features](#features)
- [Installation](#installation)
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

- **Granular permissions on any command** — vanilla or third-party mod, no patching required.
- **LuckPerms soft-dependency** — uses LP automatically when present, transparent fallback to internal otherwise.
- **Wildcards** — `customperm.command.*` covers every exposed command.
- **Aliases and macros** — create freely-named commands (`/fly`, `/heal`, `/spawn`...) that fire one or many commands.
- **Hot-reload** — every change applies immediately, no `/reload` or restart needed.
- **Auto re-sync** — when a permission changes via LuckPerms, the player's command tree is refreshed automatically.
- **Diagnostic tooling** — `/customperm debug`, `/customperm test`, `/customperm scan`, `/customperm status`.
- **Op preserved** — operators always retain access to all vanilla commands; the mod never strips their rights.
- **Server-side only** — no client mod required.
- **Battle-tested** — 10 automated GameTests run on every commit via GitHub Actions.

---

## Installation

### Requirements

- **Minecraft 1.21.1**
- **NeoForge 21.1.221** or newer
- **Java 21**
- (Optional but recommended) **LuckPerms 5.4.x or 5.5.x** for NeoForge

### Steps

1. Download `customperm-1.0.0.jar` from the [Releases page](../../releases).
2. Drop the jar into your server's `mods/` folder.
3. (Optional) Drop the [LuckPerms](https://luckperms.net/download) jar (NeoForge 1.21.1 build) alongside.
4. Start the server.

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

---

## Quick start

### With LuckPerms

```
# Server console
customperm command add gamemode
lp creategroup vip
lp group vip permission set customperm.command.gamemode true
lp user Steve parent add vip
```

`Steve` can now use `/gamemode creative` even though he is not op.

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

All admin commands live under `/customperm` and **require op level 2**.

### Command exposure

Defines which commands are eligible for the permission system. A non-exposed command keeps its vanilla behaviour (op-only).

| Command | Effect |
|---|---|
| `/customperm command add <name>` | Exposes `<name>` to the system. |
| `/customperm command remove <name>` | Removes the command, reverts to vanilla behaviour. |
| `/customperm command list` | Lists currently exposed commands. |

### Aliases (macros)

Create custom commands that run one or more inner commands. Steps execute with **op level 4** — see [Security considerations](#security-considerations).

| Command | Effect |
|---|---|
| `/customperm alias add <name> <cmd1; cmd2; ...>` | Creates an alias. Inner commands separated by `;`. |
| `/customperm alias addstep <name> <cmd>` | Appends a step (creates the alias if absent). |
| `/customperm alias removestep <name> <index>` | Removes the step at the given 0-based index. |
| `/customperm alias steps <name>` | Shows all steps with their indices. |
| `/customperm alias remove <name>` | Deletes the alias entirely. |
| `/customperm alias list` | Lists all defined aliases. |

### Grades (internal system, LuckPerms-less)

These commands are **blocked when LuckPerms is active** — use `/lp` instead.

| Command | Effect |
|---|---|
| `/customperm grade create <name>` | Creates an empty grade. |
| `/customperm grade delete <name>` | Deletes a grade and unassigns it from every user. |
| `/customperm grade addperm <grade> <node>` | Adds a permission node to the grade. |
| `/customperm grade removeperm <grade> <node>` | Removes a node. |
| `/customperm grade assign <player> <grade>` | Assigns the grade to a player. |
| `/customperm grade unassign <player> <grade>` | Unassigns. |
| `/customperm grade list` | Lists defined grades. |

### Diagnostic and utilities

| Command | Effect |
|---|---|
| `/customperm test <player> <node>` | Verifies whether a player holds a permission node. Returns `GRANTED` or `DENIED`. |
| `/customperm debug <player> <command>` | Detailed report: is the command in the dispatcher? exposed? does op-level pass? is the perm granted? what does the wrapper actually return? |
| `/customperm status` | Global snapshot: backend, wrapped commands, exposed commands, aliases, grades. |
| `/customperm scan [pattern]` | Lists every command in the dispatcher with its state (exposed, alias, mod-internal). Optional substring filter. |
| `/customperm reload` | Reloads config files from disk. |

---

## Permission nodes

CustomPerm uses a hierarchical node scheme compatible with LuckPerms (and with the internal store).

| Node | Effect |
|---|---|
| `customperm.command.<name>` | Authorizes command `<name>` (only effective if exposed). E.g. `customperm.command.gamemode` |
| `customperm.command.*` | Wildcard: covers every exposed command. |
| `customperm.alias.<name>` | Authorizes alias `<name>`. E.g. `customperm.alias.fly` |
| `customperm.alias.*` | Alias wildcard. |

> ⚠️ **Important**: `customperm.command.<name>` only grants `<name>` if it has been exposed via `/customperm command add <name>`. Otherwise the command stays op-only regardless of permissions.

---

## Configuration files

Stored in `config/customperm/`. Auto-created on first launch and editable on the fly (use `/customperm reload` to apply).

### `commands.json`

Set of commands exposed to the system.

```json
{
  "grantedCommands": ["gamemode", "give", "effect", "tp"]
}
```

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
      "permissions": ["customperm.command.gamemode", "customperm.alias.fly"]
    },
    "staff": {
      "name": "staff",
      "permissions": ["customperm.command.*", "customperm.alias.*"]
    }
  },
  "userGrades": {
    "550e8400-e29b-41d4-a716-446655440000": ["vip"],
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": ["staff", "vip"]
  }
}
```

When LuckPerms is active, this file is ignored (permissions go through LP).

---

## Common workflows

### Grant `/gamemode` to a VIP rank

**With LuckPerms**:
```
customperm command add gamemode
lp creategroup vip
lp group vip permission set customperm.command.gamemode true
lp user <player> parent add vip
```

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

```
customperm command add gamemode
customperm command add give
customperm command add tp
customperm command add effect
lp group staff permission set customperm.command.* true       # OR
customperm grade addperm staff customperm.command.*
```

The wildcard only covers **exposed** commands. Other vanilla commands stay op-only.

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

### Wildcards must be granted carefully

`customperm.command.*` covers **every** exposed command. If you expose `/op` (not recommended) or `/whitelist`, the wildcard covers them too. **Prefer** explicit nodes for sensitive commands.

### Regular audit

Inspect `commands.json`, `aliases.json`, and (in internal mode) `grades.json` periodically, or use `/customperm status` and `/customperm scan` in-game.

---

## Diagnostics and troubleshooting

### The mod isn't loading

- Check the boot log — the line `[CustomPerm] Ready —` must appear.
- If LP is present but its initialisation throws, the mod falls back to Internal with an error log. Check that your LP version is compatible.

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

Returns `GRANTED` (green) or `DENIED` (red) along with the active backend.

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

The jar is produced in `build/libs/customperm-1.0.0.jar`.

### Dev environment

```bash
./gradlew runServer           # dev server with hot-reload
./gradlew runClient           # dev client
```

See `test-complete.html` at the project root for the full interactive test procedure (LP + Internal).

### Tunable versions

In `gradle.properties`:

```properties
minecraft_version=1.21.1
neo_version=21.1.221
luckperms_api_version=5.4
```

---

## Testing

The mod ships with a comprehensive test suite that runs in a real Minecraft server.

### Run the suite locally

```bash
./gradlew runGameTestServer
```

This launches a dedicated Minecraft test server, executes all 10 GameTests, and exits with a code equal to the number of failed tests (zero = all pass). Suitable for CI pipelines.

### What's covered

| Test | Validates |
|---|---|
| `internalDeniesByDefault` | Empty grade store denies any permission (security baseline). |
| `internalGrantsViaGrade` | Assigning a grade with a perm grants exactly that perm. |
| `internalWildcardWorks` | `customperm.command.*` covers descendants but not unrelated branches. |
| `internalMultipleGradesCompose` | Multiple grades on a single user union their perms. |
| `configRoundtripsThroughDisk` | save() / load() preserve data through real JSON I/O. |
| `commandExposureGate` | Add/remove on the granted commands list takes immediate effect. |
| `wrappedCanUseFullFlow` | (Placeholder, see test-complete.html for the integration test.) |
| `opAlwaysPasses` | An op-level source is always allowed (vanilla preservation). |
| `aliasRegistersOnLiveDispatcher` | A new alias appears on the live dispatcher without `/reload`. |
| `aliasRemovesFromDispatcher` | A removed alias disappears from the live dispatcher. |

### Continuous integration

Every push to `main` and every pull request triggers `.github/workflows/gametest.yml`, which:

1. Sets up JDK 21 on Ubuntu.
2. Caches Gradle dependencies for fast subsequent runs.
3. Runs `gradlew runGameTestServer`.
4. Fails the build if any required test fails.
5. Uploads the run logs as a build artifact on failure for inspection.

### Manual end-to-end procedure

For a full LP + Internal deployment validation, open `test-complete.html` in a browser. It is an interactive checklist (24 steps across 12 phases) that records your results and exports a Markdown summary at the end.

---

## How it works (technical)

### Dispatcher wrapping

At `RegisterCommandsEvent`, the mod walks Brigadier's command tree and **clones** every root node into a fresh `LiteralCommandNode` whose `requires` chains:

```
1. The original requirement (vanilla op-level) — preserves op behaviour
2. Otherwise, check that the command is in the exposed list
3. Otherwise, ask the PermissionService whether the player has customperm.command.<root>
```

Cloned nodes are inserted into the root's internal `Map` fields (`children`/`literals`/`arguments`) via reflection. This approach sidesteps JIT inlining traps on `final` fields.

### Pluggable backend

`PermissionService` is an interface with two implementations:

- `LuckPermsService`: queries LP via the public API (`LuckPermsProvider.get()`).
- `InternalPermService`: looks up grades in `grades.json`.

Selection happens at boot through `ModList.get().isLoaded("luckperms")`.

### Re-sync

When a permission changes via LP, the `UserDataRecalculateEvent` is captured and `Commands.sendCommands(player)` is invoked for the affected player. The client tree is updated without a disconnect.

For changes via `/customperm` (internal mode), `sendCommands` is invoked directly after the mutation.

### Aliases

Registered as `Commands.literal(name).requires(...).executes(...)`. The `executes` iterates the steps and calls `server.getCommands().performPrefixedCommand(elevatedSource, step)` for each, using a `CommandSourceStack` whose `permissionLevel = 4`.

---

## Compatibility with other mods

### Mods that add commands

**Compatible automatically.** Commands are registered through the standard `RegisterCommandsEvent`; our handler runs after every other and wraps the entire tree. No integration required.

To expose a third-party mod's command: `customperm command add <name>`. To verify it is detected: `customperm scan <pattern>`.

### Mods that mutate the dispatcher dynamically

Edge case. If a mod adds commands **after** `RegisterCommandsEvent`, they aren't wrapped and keep their original `requires` (typically op-only). To force a re-wrap: `/reload` (server-side).

### LuckPerms

Privileged target. The full LP machinery works:
- Groups (`/lp creategroup`)
- Hierarchy (`/lp group <name> parent add <parent>`)
- Contexts (servers, worlds — not extensively tested but the API is honoured)
- Web editor
- SQL/MySQL/MongoDB storage

---

## Known limitations

- **No sub-command granularity**: `customperm.command.gamemode` covers every sub-mode (creative, spectator, etc.). To split, use aliases.
- **No alias parameters**: an alias is a no-arg command. To build `/heal <player>`, write `/heal_target` using `effect give @p` etc., or create multiple aliases.
- **LP contexts partially tested**: per-world, per-server contexts go through `getCachedData()` and are theoretically supported but not extensively tested.
- **No GUI**: administration is command-driven. For an interface, use the LuckPerms web editor.

---

## License

MIT — see [LICENSE](LICENSE).

---

## Credits

- [NeoForge](https://neoforged.net/) for the modding framework.
- [LuckPerms](https://luckperms.net/) for the inspiration and a clean integration API.
- Brigadier (Mojang) for the underlying command system.
