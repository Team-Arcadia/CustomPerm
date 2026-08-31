# Changelog

All notable changes to this project are documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)  
Versioning: [Semantic Versioning](https://semver.org/)

---

## [Unreleased]

### Changed

- **Project relicensed to proprietary, source-available terms (All Rights Reserved)** on 2026-07-14, replacing the GNU General Public License v3.0 only (`GPL-3.0-only`). The source stays publicly viewable for reference and interoperability, but redistribution, re-upload, repackaging, sale, and derivative works now require prior written permission. Inclusion in CurseForge/Modrinth modpacks is permitted only when the unmodified official file is fetched from the platform; bundling, re-hosting, or modified builds require written permission. The change is **prospective**: it applies to builds published from the next release onward. Every version released up to and including **1.0.5** was distributed under the license shipped with it and stays available on those terms, which no later license text withdraws. Per-version licensing is tabulated in [NOTICE.md](NOTICE.md).

- **License raised to version 1.2 — ownership separated from maintenance** — the text established who the author was but never said what hosting the repository under a team account does *not* grant, which left the question open the day someone with organization rights assumes they may redistribute a build or authorize a third party to. A new **"Maintaining Organization"** definition in §1 states that hosting the mod under an organization, holding administrative rights over it or over the official repository, or being described anywhere as a maintainer or team member (a) transfers no copyright, (b) grants none of the permissions reserved to the author — in particular no right to redistribute, publish a build, or permit anything to a third party — and (c) does not entitle anyone to act as the author. Two closing paragraphs handle the two cases that follow: a member of the organization who contributes does so as a contributor under §5.2 like anyone else, and where the organization is a legal entity it holds rights only through a separate written instrument signed by the author, which this license neither creates nor evidences. §15 gains the matching operative rule: only the author can grant a permission, and a statement by a contributor, maintainer, moderator, or organization member is not one, on any channel, absent written authorization. No section was renumbered and no permission was added or withdrawn; the version number moves because the text does, which is what the versioning clause in the header requires.

- **License raised to version 1.1 — contractual mechanics added** — the text was solid on substance (what is forbidden was well covered) but missing the clauses that make a license hold up: it had **no governing law or jurisdiction**, **no severability**, **no no-waiver clause**, and its warranty/liability exclusions were stated in absolute terms. Under French law a blanket exclusion of all liability is likely void (dol, faute lourde, and personal injury can never be excluded), and a court may strike the whole clause rather than reduce it. Now added: §13 French law and jurisdiction with a carve-out for mandatory consumer-protection rules; §11 severability with reduce-then-sever; §10 no-waiver, stating that tolerating a breach is not acquiescence and that a permission granted in one case creates no precedent — this one matters specifically because permissions are granted case by case; §12 entire agreement, so wording on a CurseForge/Modrinth project page cannot be argued to modify the license; §14 language clause (English prevails); "to the fullest extent permitted by applicable law" hedges plus an explicit non-excludable carve-out on §7 and §8; an express reservation of all rights not granted; and license versioning, so each release is governed by the license shipped with it.
- **Revocation regime defined (§2.3)** — the grant was flagged "revocable" with no regime attached, leaving it ambiguous whether a compliant modpack could be killed retroactively. Revocation is now **individual** (effective only against a specific party, on written notice — no blanket or silent withdrawal), **prospective** (never makes past compliant use unlawful), and expressly does not affect a third party's compliant modpack nor other users' ability to keep running an Official Build lawfully obtained. Withdrawing the modpack permission from a given maintainer needs separate written notice and does not reach already-published pack versions. §6 termination on breach is unaffected.
- **§3(a) ambiguity removed** — the paragraph forbade making the Mod available through "modpack platforms" while §3(b) permitted exactly that. The `EXCEPT` in (b) saved the reading, but ambiguity is construed against the drafter (*contra proferentem*); (a) now carries an explicit `SUBJECT TO` referring to §3(b), §2.2, and §5.1.
- **§5.1(d) "abandoned" now defined** — same drafting risk. A pull request is deemed abandoned after ninety (90) consecutive days without commit, comment, or other activity from the contributor, or on the contributor's public statement that they are not pursuing it.
- **§5.2 moral-rights wording corrected** — "without obligation of attribution" is unenforceable against a French contributor, since moral rights cannot be waived. Replaced with the standard construction: waiver to the extent the law permits, and otherwise an undertaking not to assert them in a way that blocks the granted license, balanced by a commitment never to misattribute a contribution to a third party.
- **§6 survival list corrected** for the new numbering, and termination now names the exact rights it cuts (§2.1, §2.2, §3(b), §5.1) instead of the whole of §2.
- **"Sole copyright holder" claim corrected (§1)** — the repository history contains merged code from a second author, so the previous wording was factually inaccurate; a false ownership assertion in a license undermines the credibility of the whole instrument if challenged. "Author" is now defined as the holder of the copyright in the Mod *as a whole* and the only party entitled to grant, withhold, or withdraw permissions — which is what actually needs to be true for the license to operate. A new "Contributor" definition records that contributors keep the copyright in their own contribution, license it to the Author under §5.2, and are credited in CONTRIBUTORS.md without thereby gaining ownership or any right to grant permissions. §9 now also states that contributors' moral rights are handled by §5.2.

- **Licensing hardened while explicitly opening the door to pull requests** — the All Rights Reserved license already forbade forks outright (§3(d)), which made the contribution workflow the project actually wants technically impossible: GitHub requires a fork to open a pull request, so every contributor was in breach by construction. A new **§5.1 Contribution Fork** grants a narrow, conditional permission to fork *solely* to prepare and submit a pull request — the fork must stay recognizable as a fork, keep the LICENSE, copyright notices and mod identity (name, mod id, branding) unchanged, must never be published or released in any form (compiled or source) to third parties, must not become a separate/competing/rebranded project, and the permission lapses once the PR is merged, closed, or abandoned. Anything outside those conditions falls back under §3. §4 (Source Code and Decompilation) now carves out §5.1 so the two sections no longer contradict each other.
- **Anti-attribution clause added** — new §3(f) explicitly prohibits claiming authorship of the Mod or any part of it, presenting it as one's own work, or reusing its source code (in whole or in part, verbatim or substantially adapted) inside any other project, mod, plugin, or product. The previous wording covered redistribution and derivative works but never named code appropriation directly.
- **Contributor grant expanded (§5.2)** — the license granted over a submitted contribution is now stated as perpetual, worldwide, irrevocable, non-exclusive, royalty-free, **sublicensable and transferable**, with no obligation of compensation or attribution, plus a warranty that the contributor authored it and included no third-party code they may not submit. Made explicit: contributors keep copyright on their own contribution, but submitting one confers **no ownership, co-authorship, or any other right over the Mod itself**.
- **SPDX headers extended to every source set** — only `src/main` carried the `LicenseRef-CustomPerm-ARR` header; the test, gameTest, and JMH benchmark sources had none, leaving 23 of the 55 Java files with no notice at all. Since §3(e) forbids removing those headers, their absence weakened the claim on exactly the files a fork would find easiest to lift. All four source sets are now covered.
- **Contact address removed from `CONTRIBUTORS.md`** — the contributor entry published a third party's email address. Credit does not require a contact detail, and §5.3(a) already gives contributors the right to request its removal; the list now carries the handle alone.

### Added

- **In-game LuckPerms editor (`/customperm gui luckperms`)** — with LuckPerms active, the TesseraUI panel had nothing to offer: the Grades screen correctly reported that grades were read-only and stopped there, so the only administration path was `/lp` in chat or the web editor in a browser. The panel now carries a full editor for the LuckPerms store itself, reached from the landing menu or directly with `/customperm gui luckperms [groups|players|tracks]`. **Groups**: create, delete, permission nodes with allow/deny, contexts and expiry, inheritance, meta, prefix, suffix, weight, display name. **Players**: online players plus any player resolved by exact username, their groups, primary group, own nodes, meta, chat meta, and promote/demote on a track. **Tracks**: create, delete, append, insert at a position, remove a group. Writes are applied server-side through the LuckPerms API — not by dispatching `/lp` commands — so each action returns a real outcome and the screen refreshes itself; failures come back as a status line naming what was refused rather than a silent no-op.
- **Backend-dependent landing menu** — `/customperm gui` requested no server data and always showed the same three buttons, one of which opened a screen that could only say it did not apply. It now syncs like every other screen and follows the active backend: LuckPerms editor entries with LuckPerms, the internal Grades screen without it, Aliases and Status in both. This is the same conditional rule the Grades screen already applied internally, moved up to the menu so it is visible before the click rather than after.
- **`customperm.gui.luckperms.edit` permission node** — reading the editor keeps the op-level-2 gate every CustomPerm screen uses; writing to the permission store needs this node on top, so the editor can be delegated to a moderator without handing over `/lp` itself. Resolved by whichever backend is active, therefore grantable with `/lp` like any other node. Permission level 4 bypasses it, because a fresh install would otherwise leave the server owner unable to grant themselves the node that unlocks the editor. The node is re-checked server-side on every edit, never trusted from the client, and every applied edit is logged with the admin's name.
- **MIT notice preserved for pre-relicensing contributions (`NOTICE.md`, EN/FR)** — parts of the mod were contributed while the project was distributed under the MIT License, before the move to proprietary terms on 2026-07-14. MIT permits sublicensing, so distributing those portions inside the proprietary mod is licit; it also requires the copyright and permission notice to be preserved for substantial portions, and that notice was missing from the repository. A new **"MIT-licensed portions" / "Portions sous licence MIT"** section, placed after the third-party components, states that those portions entered under MIT and remain available on those terms, identifies them by pull request #1 and commits `123650a` and `53556dc` (contributed by curveo), lists the seven files affected, and reproduces the MIT text verbatim as it stood in the repository, with its original copyright line. It closes by stating that the rest of the software is governed by the proprietary LICENSE and that the section extends nothing beyond the portions named — it is a notice owed, not a relicensing.
- **Ownership and maintenance section (`NOTICE.md`, EN/FR)** — the plain-language summary explained the rights but never addressed the team-account question. It now records that the mod is maintained by Team-Arcadia while copyright is held by THEFricadelle alone, that hosting under an organization transfers nothing, and that team members who contribute do so as contributors on the ordinary §5.2 terms. The at-a-glance table gains a matching row: redistributing, or authorizing someone else to, on the grounds of team or organization membership is not permitted.
- **Contributor condition 6 (`CONTRIBUTING.md`, EN/FR)** — the contributor terms now state explicitly that they apply identically to members of the hosting organization, and that membership, maintainer status, and write access grant no right over the mod and no authority to permit what the LICENSE reserves to the author.
- **`CONTRIBUTING.md`** (bilingual EN/FR) — states in plain language what contributors may do (read, audit, open issues, fork for a PR) and may not do (publish fork builds, rebrand, reuse code, claim authorship, strip notices), restates the contributor terms in non-legal wording, and adds a welcome-contributions table separating accepted work (bug fixes, crash/NPE fixes, compatibility, performance, typos, docs) from work requiring prior discussion (new features, large refactors, dependency/build changes). Also documents the PR workflow (branch from `dev`, `fix/`–`feat/` naming, `./gradlew build` + `runGameTestServer` must pass, conventional commits, PR targets `dev`), the code conventions, the "never bump `mod_version`" rule, and the minimum information required in a bug report.
- **`NOTICE.md`** (bilingual EN/FR) — a plain-language rights summary with an at-a-glance allowed/forbidden table covering downloads, monetized servers, source auditing, contribution forks, the two modpack cases, re-uploads, modified builds, code reuse, resale, and attribution. Explains why the project is source-available rather than open-source, and documents third-party components (NeoForge, LuckPerms API, TesseraUI, Brigadier) as compile-time/runtime dependencies resolved on the user's side — no third-party code is bundled into the jar. States that the LICENSE prevails in case of divergence.
- **§5.3 Recognition of Contributors** — gathers in one place what a contributor actually receives, which was previously either scattered or merely implicit. (a) **Credit**: every merged contribution is credited in CONTRIBUTORS.md, no contribution is ever misattributed, and the credit survives termination of the license — it is an acknowledgment of authorship, not a revocable favour; a contributor may request a different name or handle, removal of a contact address, or no listing at all. (b) **Modpack permission**: once a pull request has concluded (merged, closed, or abandoned per §5.1(d)), the contributor may ship the Mod in a modpack they publish on the §3(b) terms — Official Channel reference, unmodified Official Build, notices preserved — and having forked the repository never removes this. That last point is the reason the clause exists: §5.1(b) forbids distributing any build from a fork and §5.1(d) ends the fork permission once the PR closes, which could reasonably be read as leaving a contributor worse off than a stranger. (c) restates that only the Official Build is ever covered, never a fork build; (d) confirms no other right is granted — exported/offline bundling, re-hosting, and modified builds still need written permission. §6 updated accordingly: breach terminates §5.3(b) along with the other granted rights, while §5.3(a) survives.
- **`CONTRIBUTORS.md`** (bilingual EN/FR) — credits everyone who has contributed code, fixes, or documentation, and states precisely what that credit does and does not mean: recognition of authorship of a specific contribution, with a commitment never to misattribute it, but no ownership, co-ownership, co-maintainership, redistribution right, or entitlement to be consulted on the project's direction or licensing. Linked from the LICENSE, both READMEs' Credits sections, and CONTRIBUTING.md. Contributors may request a different name, handle, or removal of their contact address via the issue tracker.
- **Server-to-player distribution now explicitly permitted (§2.2)** — CustomPerm declares no `displayTest` and registers network payloads, so it is required client-side; servers and hosting panels that auto-synchronize mods to connecting players were therefore performing redistribution forbidden by §3(a), which criminalized the mod's own normal deployment path. A Server Operator may now transmit the unmodified Official Build to the players joining that Operator's own server, including via launcher or host auto-sync, with notices preserved. Publishing, cataloguing, mirroring, or offering the Mod as a standalone or "one-click install" product independently of a customer's server instance still requires written permission.
- **Name and branding protection (§3(g))** — the previous text protected the code but never the identity. Using the name "CustomPerm", the mod id, logo, visual identity, or any confusingly similar designation to identify or promote another project, to imply affiliation or endorsement, or as a trademark, domain, or account name is now prohibited. Factual nominative use — stating that a pack includes it, tutorials, reviews — is expressly carved out so the clause cannot chill legitimate mention.
- **AI/ML training restriction (§3(h))** — public source under All Rights Reserved with nothing said about model training was a real gap. Use of the Mod or its source as training, fine-tuning, validation, or retrieval corpus, and inclusion in any dataset compiled for that purpose, is now prohibited. A contributor's ordinary use of AI-assisted developer tooling is expressly excluded from this restriction.
- **§9 Authorship and moral rights** — asserts the author's droit à la paternité and droit au respect de l'œuvre under French law: perpetual, inalienable, imprescriptible, and unaffected by any permission granted, by the public availability of the source, or by any termination or revocation. This is the strongest available ground against appropriation and was previously not invoked at all.
- **Definitions expanded (§1)** — added "Official Channel", "Official Build", and "Server Operator", used throughout to remove the previous reliance on informal phrasing. "You" was recast to cover anyone dealing with the Mod, not merely someone "exercising the permissions granted", which previously left violators arguably outside the definition.
- **Contact channel stated (§15)** — a written-permission regime with no stated channel was unworkable in practice; requests now route to the official repository's issue tracker, with an explicit statement that silence is not consent and that a permission covers only the case it was granted for.
- **SPDX headers on all 32 distributed source files** — every file under `src/main/java` now carries a copyright + `SPDX-License-Identifier: LicenseRef-CustomPerm-ARR` block. This makes each file self-identifying if copied in isolation, and §3(e) (no removal of notices) now has something concrete to attach to at file level. CRLF line endings preserved; the insertion is idempotent (re-running skips files already carrying the tag). Comment-only change — no behaviour impact.

### Fixed

- **Rate-limiter no longer accumulates per-player history unbounded** — `RateLimiter.HISTORY` kept a permanent `UUID → Deque` entry for every player who ever ran a rate-limited command or alias; `tryAcquire` only ever pruned the *calling* player's own timestamps, so an idle player's entry lived until the next server restart. On a long-lived server with many unique players this grew without bound (memory/GC pressure, not per-check CPU — each check stays O(maxExecutions) regardless of map size). An amortised sweep now runs at most once per 5 minutes, piggybacked on command execution (dispatch is single-threaded, so no scheduled task is needed): it prunes each player Deque to the command's current window and drops emptied entries, and drops the whole bucket for a command whose rule was removed or disabled. The window is resolved by the caller (`CommandTreeRewriter`), keeping `RateLimiter` free of any config/Minecraft dependency and unit-testable in isolation.

### Added

- **Tab-completion for every `/customperm` admin argument** — the admin command previously relied solely on Brigadier's built-in literal completion plus the two `EntityArgument.player()` fields (`grade assign/unassign`, `test`); every other argument had to be typed from memory. `SuggestionProvider`s are now attached across the tree, all fed from the live config or dispatcher so they stay accurate after any `add`/`remove`/`reload`: existing grades on `grade delete|addperm|removeperm|assign`; the target player's own grades on `grade unassign`; existing aliases on `alias addstep|removestep|steps|remove`; valid `0..n-1` step indices on `alias removestep`; not-yet-exposed dispatcher commands on `command add` and exposed commands on `command remove`; configured rules on `ratelimit enable|disable|remove` and exposed-command/alias targets on `ratelimit set`; known permission nodes (`customperm.command.*`, `customperm.alias.*`, grade perms) on `grade addperm` and `test`; the grade's current perms on `grade removeperm`; online player names on `debug` (kept as a string argument so offline lookups still work); and all dispatcher commands on `debug`/`scan`. No behaviour change beyond completion — argument parsing and permission checks are untouched.

- Documented singleplayer and LAN usage, which had never been covered. `README.md`, `README.fr.md` and `distribution/DESCRIPTION.md` now explain the two behaviours that differ from a dedicated server: `/customperm` is gated behind a real op-level-2 check, so **Allow Cheats** must be enabled for the command to be visible and runnable in a local world; and configuration lives in `.minecraft/config/arcadia/customperm/` rather than in the world save, so grades, aliases, exposed commands and rate limits are shared by every singleplayer world on that installation. Also states what is actually worth using offline (aliases and macros, the TesseraUI panel) and notes that grades and permission nodes only become meaningful once the world is opened to LAN. Documentation only — no behaviour change.

---

## [1.0.5] - 2026-07-06

### Added

- Optional TesseraUI admin panel: `/customperm gui` opens a graphical landing menu with buttons to the Grades, Aliases and Status screens (each screen has a "< Menu" button back to it), and `/customperm gui grades|aliases|status` jumps straight to one, when [TesseraUI](https://www.curseforge.com/minecraft/mc-mods/tesseraui) is installed client-side. TesseraUI is a soft dependency (`compileOnly`, `optional` in `neoforge.mods.toml`) — without it, the command reports that the GUI is unavailable and the text commands keep working exactly as before. GUI actions reuse the existing `/customperm` commands (via a prefilled chat input) rather than duplicating any CRUD or permission logic.

### Changed

- Direct command exposure now works **with LuckPerms installed**, not only without it. `/customperm command add/remove/list` is accepted regardless of backend, and the `customperm.command.<name>` node is resolved by LuckPerms when present (grant it with `/lp`, wildcards like `customperm.command.*` included) or by the internal grades otherwise. Previously CustomPerm disabled direct exposure entirely under LuckPerms. Under the hood this is non-trivial: LuckPerms' NeoForge `BrigadierInjector` reflectively overwrites every command node's `requires` predicate (root **and** every sub-argument) after CustomPerm wraps them, which is why granting `customperm.command.*` used to have no effect. CustomPerm now re-asserts an additive permission gate over the whole subtree of each exposed command — on the next server tick after boot, and on `/customperm command add|remove` and `/customperm reload` — so it runs last and wins. The gate is additive: when CustomPerm doesn't grant the source it defers to LuckPerms'/vanilla's own check, so existing gating is never removed. Grade subcommands (`/customperm grade ...`) remain LuckPerms-delegated (use `/lp` for user/group membership).
- Per-command execution rate limits for exposed commands and aliases: an admin can cap how many times a single player may run a given command within a sliding time window (e.g. `/customperm ratelimit set observable 10 3600` caps `/observable` at 10 uses per hour per player).
- `/customperm ratelimit set|enable|disable|remove|list` — configure, toggle, and inspect rate limits. `disable` keeps the configured numbers so a limit can be re-enabled without re-entering them.
- New `ratelimits.json` config file (per-server, hot-reloadable, backed up alongside `grades.json`/`aliases.json`/`commands.json`/`settings.json`).
- Rate-limit counters are in-memory only (reset on server restart) and tracked per player and per command; console/command-block invocations are not limited.
- The build published on 2026-07-08 was released under the **GNU General Public License v3.0 only** (`GPL-3.0-only`), the license in force in the repository at the time, declared as such in the jar metadata. Its corresponding source is the `v1.0.5` tag. The move to proprietary terms happened after this release and is recorded under Unreleased; it does not govern 1.0.5 or any earlier version.

### Fixed

- The TesseraUI GUI's network channel is now registered as `optional()`. Without this, NeoForge's default handshake behavior would have refused the connection of any client missing the channel — meaning a vanilla client (or any client without CustomPerm) could no longer join a server running this build at all, breaking the mod's "server-side only" guarantee for every player, not just those wanting the GUI.
- The GUI sync payload handler no longer creates an unconditional method reference into client-only code from the shared registration path. It's now dispatched through an `FMLEnvironment.dist.isClient()` guard, so a real dedicated server (whose jar has no `net.minecraft.client.*` classes, unlike the NeoForge dev/GameTest environment) never attempts to resolve them.
- A **client** running CustomPerm but not TesseraUI no longer fails to load with `NoClassDefFoundError: com/tesseraui/TesseraScreen`. The always-loaded client classes (`CustomPermClientCommands`, registered via `@EventBusSubscriber(Dist.CLIENT)`, and `ClientNetworkHandler`) previously referenced the GUI screen classes directly; JVM verification of those classes at mod-load time eagerly resolved the screens' TesseraUI supertype, which is absent on a TesseraUI-less client. All screen interaction is now routed through a lazily-loaded `TesseraGuiBridge` behind the `isTesseraUiPresent()` guard, so the TesseraUI types are only linked on a client that actually has TesseraUI. This restores the intended "TesseraUI is optional" behaviour on the client side, not just the server side.

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
