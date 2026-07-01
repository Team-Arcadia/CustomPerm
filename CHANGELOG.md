# Changelog

All notable changes to this project are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)  
Versioning: [Semantic Versioning](https://semver.org/)

---

## [Unreleased]

### Added

- Optional TesseraUI admin panel: `/customperm gui grades|aliases|status` opens a graphical Grades/Aliases/Status screen when [TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui) is installed client-side. TesseraUI is a soft dependency (`compileOnly`, `optional` in `neoforge.mods.toml`) — without it, the command reports that the GUI is unavailable and the text commands keep working exactly as before. GUI actions reuse the existing `/customperm` commands (via a prefilled chat input) rather than duplicating any CRUD or permission logic.
- Per-command execution rate limits for exposed commands and aliases: an admin can cap how many times a single player may run a given command within a sliding time window (e.g. `/customperm ratelimit set observable 10 3600` caps `/observable` at 10 uses per hour per player).
- `/customperm ratelimit set|enable|disable|remove|list` — configure, toggle, and inspect rate limits. `disable` keeps the configured numbers so a limit can be re-enabled without re-entering them.
- New `ratelimits.json` config file (per-server, hot-reloadable, backed up alongside `grades.json`/`aliases.json`/`commands.json`/`settings.json`).
- Rate-limit counters are in-memory only (reset on server restart) and tracked per player and per command; console/command-block invocations are not limited.

---

## [1.0.4] - 2026-06-11

Hardening pass from the full-mod and LuckPerms compatibility audits.

### Fixed

- `/customperm reload` now applies `aliases.json` changes to the live dispatcher: aliases added to the file are registered, aliases removed from the file are unregistered (restoring any shadowed command), and edited steps take effect (the execution closure previously kept running the steps captured at registration time).
- Alias execution is now guarded against recursive alias chains (an alias invoking itself or a cycle) with a maximum nesting depth of 8, instead of recursing to a `StackOverflowError` with an op-4 source.
- Static dispatcher state (`ORIGINAL_ROOTS`, `WRAPPED_NODES`, `SHADOWED_ORIGINALS`, `REGISTERED_ALIASES`) is now cleared on every `RegisterCommandsEvent` and on server stop, instead of leaking old command trees across `/reload` and same-JVM server restarts and restoring stale shadowed nodes from a previous server instance.
- Removing an alias that shadowed an exposed command now re-wraps the restored command immediately instead of leaving its permission nodes inert until the next reload.
- Config files are now written atomically (temp file + move), so a crash mid-save can no longer truncate `grades.json`/`aliases.json`/`commands.json`/`settings.json`.
- Atomic config replacement now retries short-lived Windows file-lock conflicts before reporting a save failure.
- Alias step normalization now strips a single leading slash instead of all of them, so commands whose root literal starts with `/` (WorldEdit-style `//wand`) can be used as alias steps.
- `/customperm alias addstep` now emits the same shadowing warning as `alias add` when it creates a new alias that shadows an existing command.
- Permission nodes passed to `/customperm grade addperm|removeperm` are now trimmed.
- Fatal JVM errors (`Error`) thrown by an alias step are now rethrown instead of being swallowed, matching the project-wide error policy.

### Changed

- `CommandTreeRewriter` now subscribes to `RegisterCommandsEvent` at `EventPriority.LOWEST` and runs a catch-up `repair()` at `ServerStartedEvent`, so commands registered by other mods' handlers after CustomPerm's are still wrapped at boot.
- Direct command exposure remains disabled whenever LuckPerms is installed, including degraded fallback states. LuckPerms owns normal command permissions while CustomPerm aliases remain available.
- Config saves now use unique temporary files and serialize concurrent writes before replacing each destination file.
- Project licensing changed from MIT to the GNU General Public License v3.0 only (`GPL-3.0-only`).

- The LuckPerms `UserDataRecalculateEvent` subscription is now closed on server stop and re-created on the next server start, instead of leaking across server lifecycles in the same JVM (stale `MinecraftServer` reference, broken live resync after an embedded restart).
- Command-tree resyncs triggered by LuckPerms recalculation events are now coalesced per player, so a burst of recalculations (login, group inheritance, sync) sends a single command-tree packet instead of one per event.
- Pending resync state is now isolated per server lifecycle, preventing an event racing with shutdown from suppressing resyncs after an embedded restart.

- The per-event LuckPerms recalculation log line is now DEBUG instead of INFO.

### Added

- `docs/LUCKPERMS_COMPATIBILITY_AUDIT.md` — full audit of every LuckPerms touchpoint, including documented-but-unchanged findings (permanent degradation policy, vanilla-command policy with LP installed, version-gate behaviour).

### Validation

- `./gradlew clean build --no-daemon`: passed.
- `./gradlew runGameTestServer --no-daemon` without LuckPerms: 34/34 required GameTests passed.
- `./gradlew runGameTestServer --no-daemon` with LuckPerms 5.4.150: 34/34 required GameTests passed.
- Generated jar metadata verified: CustomPerm `1.0.4`, license `GPL-3.0-only`.
- Generated jar: `customperm-1.0.4.jar`.

---

## [1.0.3] - 2026-06-10

Backend policy update.

### Changed

- Direct CustomPerm command exposure is disabled while LuckPerms is active.
- LuckPerms now retains full control of normal vanilla and modded command permissions.
- CustomPerm aliases remain available with LuckPerms and continue to use `customperm.alias.<name>` nodes.
- `/customperm command add/remove` now explains that direct commands must be managed through LuckPerms.
- `/customperm status`, `/customperm debug`, and `/customperm scan` now report the effective direct-command policy.
- Direct command exposure and `customperm.command.*` remain available with the internal backend when LuckPerms is absent.
- Runtime command-tree repair remains available for the internal backend.

### Fixed

- GameTests now validate the active backend policy correctly in both clean CI environments without LuckPerms and development environments with LuckPerms installed.

### Validation

- `./gradlew test --no-daemon`: passed.
- `./gradlew runGameTestServer --no-daemon` without LuckPerms: 32/32 required GameTests passed.
- `./gradlew runGameTestServer --no-daemon` with LuckPerms 5.4.150: 32/32 required GameTests passed.
- `./gradlew clean build --no-daemon`: passed.
- GitHub Actions GameTests on Ubuntu with Java 21: passed.
- LuckPerms startup verified with zero CustomPerm direct-command wrappers.
- Alias execution verified with LuckPerms active.
- Generated jar: `customperm-1.0.3.jar`.

---

## [1.0.2] - 2026-05-21

Patch release.

### Fixed

- Alias steps now execute directly through the server command dispatcher with an op-level 4 source.
- Alias steps now bypass CustomPerm's command wrapper and use the original command node when the step targets a wrapped command.
- Alias steps now accept an optional leading `/`, so both `say ping` and `/say ping` are valid in alias definitions.
- Failed alias steps are now reported and logged while later steps continue to run.
- Alias execution now returns the number of successful steps instead of counting failed attempts as executed.

### Changed

- `gradle.properties` now sets `mod_version=1.0.2`.
- README version badges now show `1.0.2`.

### Validation

- `./gradlew test --no-daemon`: passed.
- `./gradlew runGameTestServer --no-daemon`: 30/30 required GameTests passed.
- `./gradlew clean build --no-daemon`: passed.
- Generated jar: `customperm-1.0.2.jar`.

---

## [1.0.0] - 2026-05-18

Stable public release.

This version stabilizes the intermediate `0.9.0` release for official public
distribution. It focuses on security hardening, final configuration paths,
release documentation, validation, and fixes found during manual testing.

### Added

- **Final configuration layout**
  - Server configuration now lives in `config/arcadia/customperm/`.
  - Non-destructive migration from the old `config/customperm/` directory to `config/arcadia/customperm/` when the new directory does not exist yet.
  - New `settings.json` file.
  - `settings.json` supports `luckPermsFallbackMode`:
    - `deny`: default and recommended for public servers.
    - `internal`: compatibility mode that falls back to `grades.json`.

- **Security hardening**
  - Configurable fail-closed LuckPerms fallback when LuckPerms is present but incompatible, fails to initialize, or becomes unavailable during a permission check.
  - New deny backend for LuckPerms failure cases when `luckPermsFallbackMode` is `deny`.
  - New per-command `preserveOriginalRequires` map in `commands.json`.
  - Exposed commands can now preserve their original Brigadier `requires` predicate in addition to requiring the CustomPerm permission node.

- **Manual validation**
  - Expanded dark HTML manual test procedure with detailed expected results.
  - Added cleanup phases to manual tests.
  - Added dedicated scenarios with and without LuckPerms.
  - Added checks for LuckPerms fallback modes, config migration, backup behavior, and `preserveOriginalRequires`.

### Fixed

- Removing an alias that shadows an existing command now restores the original command in the dispatcher.
- Removing an exposed command also removes its `preserveOriginalRequires` entry.
- Deleting a grade, or unassigning a player's last grade, now removes the empty `userGrades` entry.
- LuckPerms initialization and runtime failure handling now follows the configured fallback mode instead of always falling back to the internal backend.
- README troubleshooting now documents the actual LuckPerms fallback behavior.
- README version badges now show `1.0.0`.

### Changed

- `gradle.properties` now sets `mod_version=1.0.0`.
- README files document `config/arcadia/customperm/`, `settings.json`, `luckPermsFallbackMode`, and `preserveOriginalRequires`.
- `CHANGELOG.md` is now written in English to match the primary README and public release notes.
- Release notes and documentation now treat `0.9.0` as the intermediate feature release and `1.0.0` as the stable public release.

### Validation

- `./gradlew clean build --no-daemon`: passed.
- `./gradlew runGameTestServer --no-daemon`: 28/28 required GameTests passed.
- Generated jar: `customperm-1.0.0.jar`.
- Jar verification: `META-INF/MANIFEST.MF` and `META-INF/neoforge.mods.toml` present.
- Manual test procedure: 24/24 tests passed.
- Tested with LuckPerms `5.4.150`.
- GitHub tag: `v1.0.0`.

### Compatibility

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ (`[21.1.0,)`) |
| Java | 21 |
| LuckPerms | 5.4.150+ (optional) |

---

## [0.9.0] - 2026-05-14

Intermediate feature release.

This version introduced most of the code, mechanics, and operational behavior of
CustomPerm. It added the internal permission system, command exposure, aliases,
LuckPerms integration, diagnostics, automated tests, benchmarks, CI checks, and
the first complete documentation pass.

### Added

- **Configuration and hot-reload**
  - Automatic creation of `config/customperm/grades.json`, `aliases.json`, and `commands.json`.
  - `ConfigManager` with an atomic snapshot through `AtomicReference`.
  - Transactional `/customperm reload`: invalid JSON keeps the previous active snapshot.
  - Timestamped configuration backups with rotation of the latest 3 backups.
  - Normalization for missing files, `{}` files, unknown fields, and explicit `null` collections.

- **Internal permission engine**
  - Pure Java `PermissionResolver`, independent from Minecraft/NeoForge APIs.
  - Multi-grade resolution.
  - Explicit `DENY` priority over `ALLOW`.
  - Wildcards: `*`, `customperm.command.*`, and `customperm.alias.*`.
  - Deny-by-default permission model.

- **Grade management**
  - `/customperm grade create/delete/list`.
  - `/customperm grade addperm/removeperm`.
  - `/customperm grade assign/unassign`.
  - JSON persistence for grades and player assignments.
  - Grade assignment deduplication.
  - Cascade removal of deleted grades from player assignments.

- **Command exposure**
  - `/customperm command add/remove/list`.
  - Non-exposed commands keep their vanilla/modded behavior.
  - Brigadier root command wrapping through `CommandTreeRewriter`.
  - Preservation of real op level 2+ access.
  - Client command-tree resync after configuration changes.

- **Aliases and macros**
  - `/customperm alias add/remove/list`.
  - `/customperm alias addstep/removestep/steps`.
  - Step parsing with `;` separators.
  - Sequential step execution with op level 4.
  - Continuation after a step error.
  - Live alias add/remove/replace on the dispatcher.
  - Reserved-name protection for `customperm`.
  - Warning when an alias shadows an existing command.

- **LuckPerms integration**
  - Automatic LuckPerms detection.
  - LuckPerms backend selection when available and compatible.
  - Minimum LuckPerms version: `5.4.150+`.
  - Consistent rejection of prerelease-style versions such as `5.4.150-SNAPSHOT`.
  - Internal backend when LuckPerms is absent.
  - Internal fallback when LuckPerms is incompatible, fails to initialize, or becomes unavailable during a permission check.
  - Subscription to `UserDataRecalculateEvent` to resend the command tree to the affected player.
  - Internal `grade` commands are blocked while LuckPerms is active, with guidance to use `/lp`.

- **Diagnostics and observability**
  - `/customperm status` with backend, dispatcher commands, exposed commands, aliases, grades, and users with grades.
  - `/customperm test <player> <node>` with `GRANTED` / `DENIED` verdict.
  - `/customperm debug <player> <command>` with dispatcher, exposure, op-level, permission service, and wrapper decision details.
  - `/customperm scan [pattern]` with `EXPO`, `ALIAS`, and `MOD` markers.
  - Centralized backend labels: `Internal`, `LuckPerms`, and `Internal - fallback from LuckPerms`.

- **Tests and quality**
  - JUnit 5 suite covering permissions, config, grades, aliases, LuckPerms versioning, and JSON compatibility.
  - Dynamically registered NeoForge GameTest suite.
  - 26 required GameTests validated.
  - JMH benchmarks for `PermissionResolver.resolve()` and concurrent config snapshot reads.
  - Performance baseline documented in `docs/performance-baseline.md`.
  - Dark HTML manual test procedure in `docs/manual-test-procedure.html` with JSON/Markdown export.
  - GitHub Actions CI running GameTests, Gradle build, and jar content verification.

- **Release documentation**
  - English and French README files updated to list the mod's actual functionality.
  - Internal behavior documentation: dispatcher wrapping, pluggable backend, command-tree resync, aliases.
  - Known limitations documentation.
  - NFR14 NeoForge porting process.

### Fixed

- Added `AliasesConfig.normalize()` and `CommandsConfig.normalize()` to avoid NPEs with explicit JSON `null` values.
- Preserved stack traces for dispatcher wrapping failures.
- Unknown Brigadier child nodes are ignored with a warning instead of being grafted into the cloned tree.
- Exposed commands no longer bypass CustomPerm when the original Brigadier predicate is always true.
- `LuckPermsService.hooksReady` is now `volatile`.
- Improved logs for fallback failures and LuckPerms subscription failures.
- Added final guard when backend selection results in `permissions == null`.
- GameTest registration now explicitly skips non-`public static` `@GameTest` methods.
- `/customperm scan` sanitizes the displayed pattern when no result is found.
- `alias addstep customperm` is rejected as a reserved name.
- Separator-only aliases (`";;;"`) are covered by a regression test.
- JMH is configured with `fork = 3` and consistently annotated benchmarks.
- CI verifies `META-INF/neoforge.mods.toml` and `META-INF/MANIFEST.MF` in the jar.

### Changed

- `gradle.properties` set `mod_version=0.9.0`.
- README files no longer reference generated artifacts ignored by Git; they point to GitHub Releases.
- `.gitignore` excludes local tooling artifacts, test exports, logs, builds, and unreferenced local media.
- `CHANGELOG.md` separated the initial base (`0.1.0`) from the intermediate feature release (`0.9.0`).

### Validation

- `./gradlew clean build --no-daemon`: passed.
- `./gradlew runGameTestServer --no-daemon`: 26/26 required GameTests passed.
- Generated jar: `customperm-0.9.0.jar`.
- Jar verification: `META-INF/MANIFEST.MF` and `META-INF/neoforge.mods.toml` present.
- GitHub tag: `v0.9.0`.

### Compatibility

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ (`[21.1.0,)`) |
| Java | 21 |
| LuckPerms | 5.4.150+ (optional) |

---

## [0.1.0] - 2026-05-10

Initial project base before the intermediate feature release and final stable
release.

This version represents the existing foundation before the complete delivery.
It is kept as the historical baseline, but it does not include the full set of
guarantees, tests, diagnostics, integrations, and documentation delivered in
`0.9.0` and `1.0.0`.

### Included

- NeoForge/Gradle project structure.
- CustomPerm mod declaration.
- First configuration, permission, command, and alias classes.
- Initial integration of granular permission concepts for Minecraft commands.
- Initial README, license, and project files.

### Limitations

- Feature set incomplete compared with the intermediate and stable release scope.
- Test coverage incomplete.
- Benchmarks and performance baseline absent.
- Release jar CI incomplete.
- Functional documentation and manual test procedure not finalized.

---

## NeoForge Version Update Process (NFR14)

When a new stable NeoForge version is released, the update should be delivered
in **less than one week**. Procedure:

1. Update `gradle.properties` at the project root:

   ```properties
   minecraft_version=<new-mc-version>
   minecraft_version_range=[<new-mc-version>, <next-major>)
   neo_version=<new-neo-version>
   neo_version_range=[<new-neo-major-minor.0>,)
   mod_version=<new-mod-version>
   ```

2. Check NeoForge API compatibility from the NeoForge release notes.

3. Run the full suite:

   ```bash
   ./gradlew cleanTest test
   ./gradlew runGameTestServer
   ./gradlew build
   ```

4. If all tests pass, add an entry to this `CHANGELOG.md`.

5. Update badges, versions, download links, and examples in `README.md` and
   `README.fr.md`.

6. Create the matching Git tag and publish the artifact through GitHub Releases.

> Note: the mod does not include a multi-version abstraction layer. Multi-version
> refactoring should only be introduced when porting to a different major
> NeoForge version.
