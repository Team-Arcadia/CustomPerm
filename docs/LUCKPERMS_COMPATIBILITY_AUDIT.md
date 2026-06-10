# LuckPerms Compatibility Audit — CustomPerm 1.0.3

Date: 2026-06-10
Scope: full review of every LuckPerms touchpoint in the mod — backend selection,
runtime permission checks, event integration, version gating, metadata, tests and docs.

Severity scale: **HIGH** = broken behaviour in a realistic scenario · **MEDIUM** = degraded
behaviour or misleading outcome · **LOW** = inconsistency / documentation / hardening note.

---

## Fixed in this PR

### F1 — HIGH · LP event subscription leaks across server lifecycles

`LuckPermsService.initServerHooks()` subscribed to `UserDataRecalculateEvent` without
keeping the returned `EventSubscription`, guarded by a one-way `hooksReady` flag.

Consequences when a server stops and another starts in the same JVM (integrated server
opening a second world, or any embedded restart):

1. The old subscription is never closed — its lambda retains the dead `MinecraftServer`
   (memory leak: the full server object graph stays reachable from LP's event bus).
2. `hooksReady` stays `true`, so the new server never re-subscribes — live command-tree
   resync silently stops working until the JVM restarts.

**Fix:** the subscription is stored, closed on `ServerStoppedEvent`
(`LuckPermsService.closeServerHooks()`, wired in `CustomPerm.onServerStopped`), and
re-created on the next `ServerStartedEvent`.

### F2 — MEDIUM · Resync storm on `UserDataRecalculateEvent`

LuckPerms fires `UserDataRecalculateEvent` aggressively — typically several times in a
burst for the same user (login, group inheritance recalculation, messaging-service sync,
web-editor apply). Each event caused one full `ClientboundCommandsPacket` re-send plus
one INFO log line.

**Fix:** resyncs are coalesced per player (`pendingResyncs` set — one packet per burst),
and the per-event log is downgraded from INFO to DEBUG. The one-time subscribe/unsubscribe
logs remain at INFO.

---

## Findings documented, intentionally not changed

### A1 — MEDIUM · Permanent degradation on the first LP exception

`LuckPermsService.hasPermission()` flips a one-way `degraded` flag on **any** exception,
including `IllegalStateException` from `LuckPermsProvider.get()`. This is the documented
policy (AC1–AC3, INVARIANT-301), but note the consequence: a single transient hiccup
(e.g. a permission check racing LP's startup, or one storage timeout surfacing through
the cached-data layer) permanently disables the LuckPerms backend until server restart,
with `deny` as the default fallback. In practice predicates are only evaluated after
`ServerStartedEvent` (player login), when LP is registered, so the startup race is
unlikely — but a recovery path (e.g. re-probe on the next `/customperm reload`) would be
a worthwhile follow-up.

### A2 — MEDIUM · With LuckPerms installed, vanilla commands cannot be granted at all

The 1.0.3 policy disables direct command exposure whenever LP is present, deferring
"normal command permissions" to LuckPerms. On NeoForge, however, **LuckPerms does not
gate vanilla Brigadier commands** — it serves the PermissionAPI for mods that opt in;
vanilla `/gamemode`, `/give`, etc. remain op-level-gated. So with LP installed, the only
way to give a non-op access to a vanilla command is a CustomPerm alias (which elevates to
op-4). The README's "Configure the server's normal /gamemode permission directly in
LuckPerms" workflow does not work for vanilla commands on NeoForge. Operators should be
aware that installing LP *removes* the `customperm.command.*` feature without LP providing
an equivalent.

### A3 — LOW · Version gate rejects qualified LP versions

`VersionUtils.isVersionAtLeast` returns `false` for any version with more than three
`[.\-]`-separated parts, so `5.4.150-SNAPSHOT`, `5.5.0-beta`, or build-metadata-qualified
strings are rejected even when numerically above the minimum. This is documented as a
deliberate safety stance (README: "prerelease-style versions are rejected"); release LP
builds use plain `x.y.z` so impact is limited to dev/snapshot builds of LP.

### A4 — LOW · `neoforge.mods.toml` range looser than the code gate

The optional dependency declares `versionRange="[5.4,)"` while the code enforces
`5.4.150+`. LP `5.4.0–5.4.149` passes the loader check and is then gracefully rejected by
the code gate (deny/internal per `settings.json`) — fine. LP `< 5.4` instead fails the
loader range and produces a hard NeoForge dependency error rather than the graceful
fallback. Tightening the range to `[5.4.150,)` would convert *all* old-LP cases into hard
loader errors, which is worse; the current split is acceptable but worth knowing.

### A5 — LOW · OP short-circuit asymmetry between backends

`InternalPermService` returns `true` for any op-level-2 source before consulting grades;
`LuckPermsService` queries LP verbatim (no op short-circuit — call sites add
`src.hasPermission(2) ||` themselves). Net effect on `/customperm test`: with the LP
backend, an op player can show `DENIED` for a node LP doesn't grant, while the internal
backend would show `GRANTED`. This matches LP semantics (LP itself doesn't special-case
server ops) but can confuse diagnostics.

### A6 — LOW · `getUser()` returning null is treated as deny, never loaded

For an online player LP's user object is loaded during login, so `getUser(uuid)` should
not be null in practice; if it ever is (plugin-managed unload, very early check), the
check silently denies. No lookup/load fallback is attempted — acceptable for a hot path,
noted for completeness.

### A7 — LOW · Group-level changes rely on LP's cascade

Only `UserDataRecalculateEvent` is subscribed. Group edits (`/lp group vip permission set
…`) only trigger a client resync because LP invalidates and recalculates affected users'
cached data, which fires the user event per affected online user. This holds with current
LP versions; if LP ever changes that cascade, `NodeMutateEvent`/`GroupDataRecalculateEvent`
would need explicit handling.

---

## Verification

- `./gradlew test` — pure-Java unit suite (resolver, configs, version gate).
- `./gradlew runGameTestServer` — GameTests pass without LP at runtime (LP is
  `compileOnly`); LP-specific behaviour (F1/F2) requires a dev server with the LP jar in
  `run/mods/` as per README's manual validation section.
