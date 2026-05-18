# CustomPerm

> Granular permission system for Minecraft NeoForge — grant individual vanilla commands to non-op players, with or without LuckPerms.

**[English](README.md) · [Français](README.fr.md)**

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)]()
[![NeoForge](https://img.shields.io/badge/NeoForge-21.1.221+-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-21-red.svg)]()
[![License](https://img.shields.io/badge/license-MIT-blue.svg)]()
[![Version](https://img.shields.io/badge/version-0.1.0-brightgreen.svg)]()

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

- **Granular command permissions** — expose any vanilla or third-party root command to CustomPerm with `/customperm command add <name>`.
- **Default-deny model** — nothing is exposed by default; non-exposed commands keep their original vanilla or modded requirement.
- **Internal JSON backend** — manage grades, player assignments and permission nodes without any external permissions plugin.
- **LuckPerms backend** — automatically uses LuckPerms when a compatible version is installed.
- **LuckPerms version gate** — requires LuckPerms `5.4.150+`; older or prerelease-style versions are rejected for safety.
- **Configurable LP degradation fallback** — if LuckPerms becomes unavailable at runtime, `settings.json` controls whether CustomPerm fails closed (`deny`, default) or switches to the internal backend (`internal`).
- **Backend visibility** — boot logs and `/customperm status`, `/customperm debug`, `/customperm test` report whether the active backend is Internal, LuckPerms, Internal fallback from LuckPerms, or deny mode.
- **Multi-grade RBAC** — a player can hold multiple internal grades; permissions are resolved as a union of all assigned grades.
- **Explicit DENY support** — internal grades support `deniedPermissions`, and any matching DENY overrides ALLOW.
- **Wildcard permission nodes** — `*`, `customperm.command.*`, and `customperm.alias.*` are supported.
- **Aliases and macros** — create custom top-level commands such as `/fly`, `/heal`, `/starter`, backed by one or more configured command steps.
- **Alias step editing** — append, remove and inspect individual alias steps with zero-based indices.
- **Alias elevation** — alias steps execute with op level 4 so admin-signed macros can call op-only commands.
- **Alias safety guards** — `/customperm` is reserved, blank alias steps are ignored, empty aliases are rejected, and shadowing an existing command emits a warning.
- **Runtime alias registration** — aliases are added, replaced or removed on the live dispatcher without server restart.
- **Command tree wrapping** — Brigadier root commands are cloned and wrapped so exposed commands can be gated by CustomPerm permissions.
- **OP preservation** — real op-level 2+ sources always retain access; the mod does not strip operator rights.
- **Client command-tree re-sync** — after internal changes or LuckPerms recalculation events, affected players receive an updated command tree.
- **Atomic hot-reload** — `/customperm reload` loads `grades.json`, `aliases.json`, `commands.json`, and `settings.json` as one transaction; invalid JSON keeps the previous snapshot.
- **Automatic config creation and normalization** — missing files, `{}` files, unknown fields, and explicit `null` collections are normalized into safe empty structures.
- **Automatic config backups** — successful reloads write timestamped backups and keep the latest three backups per config file.
- **Concurrent-safe config reads** — the active config snapshot is held in an `AtomicReference`, avoiding locks on permission hot paths.
- **Diagnostics** — `/customperm status`, `/customperm scan`, `/customperm debug`, and `/customperm test` cover runtime inspection and troubleshooting.
- **Manual test procedure** — `docs/manual-test-procedure.html` provides a dark-theme checklist with JSON/Markdown export for release validation.
- **Performance baseline** — JMH benchmarks document permission resolution and concurrent snapshot-read performance in `docs/performance-baseline.md`.
- **CI release checks** — GitHub Actions runs GameTests, builds the distributable jar, and verifies required jar metadata.
- **Server-side only** — no client mod is required.

---

## Installation

### Requirements

- **Minecraft 1.21.1**
- **NeoForge 21.1.221** or newer
- **Java 21**
- (Optional but recommended) **LuckPerms 5.4.x or 5.5.x** for NeoForge

### Steps

1. Download the latest CustomPerm release artifact from the [Releases page](../../releases).
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
They manage ALLOW nodes. Internal DENY nodes are stored in `grades.json` under `deniedPermissions`.

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
| `*` | Global wildcard in the internal backend. Use sparingly. |
| `customperm.command.<name>` | Authorizes command `<name>` (only effective if exposed). E.g. `customperm.command.gamemode` |
| `customperm.command.*` | Wildcard: covers every exposed command. |
| `customperm.alias.<name>` | Authorizes alias `<name>`. E.g. `customperm.alias.fly` |
| `customperm.alias.*` | Alias wildcard. |

> ⚠️ **Important**: `customperm.command.<name>` only grants `<name>` if it has been exposed via `/customperm command add <name>`. Otherwise the command stays op-only regardless of permissions.

---

## Configuration files

Stored in `config/arcadia/customperm/`. Auto-created on first launch and editable on the fly (use `/customperm reload` to apply). If an older `config/customperm/` directory exists and the new directory does not, CustomPerm copies the known config files into the new location without deleting the old files.

### `commands.json`

Set of commands exposed to the system.

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
  "luckPermsFallbackMode": "deny"
}
```

`luckPermsFallbackMode` accepts:

- `deny`: default and recommended for public servers. If LuckPerms is loaded but unavailable, CustomPerm permission checks return false.
- `internal`: compatibility mode. If LuckPerms is loaded but unavailable, CustomPerm falls back to `grades.json`.

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
      "permissions": ["customperm.command.*", "customperm.alias.*"],
      "deniedPermissions": ["customperm.command.op"]
    }
  },
  "userGrades": {
    "550e8400-e29b-41d4-a716-446655440000": ["vip"],
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8": ["staff", "vip"]
  }
}
```

When LuckPerms is active, this file is ignored (permissions go through LP).

`deniedPermissions` is only used by the internal backend. A matching DENY overrides any ALLOW from any assigned grade.

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
- If LP is present but its initialisation throws, `settings.json` decides the behavior: `deny` fails closed by default, `internal` falls back to `grades.json`. Check that your LP version is compatible.

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

Gradle generates the distributable mod artifact during `build`. Generated artifacts are not committed to Git; publish the artifact through GitHub Releases.

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
./gradlew runGameTestServer
```

This launches a dedicated Minecraft test server, executes the registered GameTests, and exits with a code equal to the number of failed tests (zero = all pass). Suitable for CI pipelines.

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
| Permission resolver | Default deny, direct ALLOW, wildcard ALLOW, global wildcard, explicit DENY, DENY-over-ALLOW across multiple grades. |
| Internal grades | Create/list/delete grades, assign/unassign players, prevent duplicates, cascade grade deletion through player assignments. |
| Command exposure | Add/remove/list exposed commands, idempotent changes, non-exposed commands remain denied by CustomPerm. |
| Alias config | Create, overwrite, remove, list aliases, preserve order, split semicolon-delimited steps, ignore blank steps. |
| Alias execution | Permission node shape, op-level 4 execution, step ordering, continue-after-error behavior, empty-step filtering. |
| Config manager | Atomic snapshot reads, concurrent reload rejection, rollback after invalid JSON, backup creation, backup rotation. |
| Backward compatibility | Missing files, `{}` files, explicit `null` collections, unknown future fields, partial config files. |
| LuckPerms selection | Internal backend when LP is absent, version parsing, minimum version gate, stable backend selection. |
| GameTests | Live dispatcher registration, command exposure gates, alias live add/remove, hot reload, rollback on corrupt JSON, command-tree repush. |
| Performance | `PermissionResolver.resolve()` and concurrent config snapshot reads via JMH. |

### Continuous integration

Every push to `main` and every pull request triggers `.github/workflows/gametest.yml`, which:

1. Sets up JDK 21 on Ubuntu.
2. Caches Gradle dependencies for fast subsequent runs.
3. Runs `gradlew runGameTestServer`.
4. Builds the distributable jar with `gradlew build`.
5. Verifies that the jar contains `META-INF/neoforge.mods.toml` and `META-INF/MANIFEST.MF`.
6. Fails the build if any required test or jar check fails.
7. Uploads the run logs as a build artifact on failure for inspection.

### Manual validation in dev

GameTests cover component logic; for a full LP + Internal end-to-end check, run a dev server and client in two terminals:

```bash
./gradlew runServer    # terminal 1
./gradlew runClient    # terminal 2 — connect to 127.0.0.1
```

Then exercise the recipes from [Common workflows](#common-workflows). The in-game diagnostic commands `/customperm debug`, `/customperm test`, `/customperm status`, and `/customperm scan` are designed for live verification.

For release checks, open `docs/manual-test-procedure.html` in a browser. It contains a dark-theme checklist split between Internal and LuckPerms scenarios and can export results as JSON or Markdown.

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
1. Null player or node => false
2. No assigned grade => false
3. Any matching deniedPermissions node => false
4. Any matching permissions node => true
5. Otherwise => false
```

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
