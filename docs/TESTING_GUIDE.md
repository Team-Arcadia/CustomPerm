# Testing a NeoForge mod: JUnit and GameTest

How CustomPerm is tested, written so the setup can be lifted into another NeoForge 1.21.x mod. It
covers the Gradle wiring, the split between the two test layers, the harness classes that make
server-side behaviour testable, and the traps that cost the most time to diagnose.

Reference numbers from CustomPerm at the time of writing: 28 400 lines of main code, 400 JUnit tests
across 38 classes, 176 GameTests across 30 classes, two GameTest run modes, one CI job.

A French version of this document is in [TESTING_GUIDE.fr.md](TESTING_GUIDE.fr.md).

- [1. The two layers](#1-the-two-layers)
- [2. Gradle wiring](#2-gradle-wiring)
- [3. Layer 1 — JUnit](#3-layer-1--junit)
- [4. Layer 2 — GameTest](#4-layer-2--gametest)
- [5. The harness](#5-the-harness)
- [6. Two backends, one test body](#6-two-backends-one-test-body)
- [7. Traps](#7-traps)
- [8. CI](#8-ci)
- [9. Porting checklist](#9-porting-checklist)

---

## 1. The two layers

The split is decided by one question: **does this code need a running Minecraft server?**

|                     | JUnit                                                  | GameTest                                                       |
| ------------------- | ------------------------------------------------------ | -------------------------------------------------------------- |
| Runs in             | a plain JVM, no Minecraft                              | a real dedicated server, headless                               |
| Startup             | instant                                                | ~40 s per run, ~5 min for the suite                             |
| Tests               | resolution logic, config parsing, codecs, SQL          | commands, packets, events, the permission handler, player join  |
| Failure reads as    | a stack trace with line numbers                        | a test name plus your own message                               |
| Run one test alone  | yes, from the IDE                                      | no, the batch runs                                              |

Rule of thumb: **anything that can be a JUnit test should be one.** A GameTest costs a server boot; a
JUnit test runs in milliseconds. GameTest is reserved for what genuinely needs the game: Brigadier,
the network layer, NeoForge events, the player list.

In practice this means designing the mod so the interesting logic is reachable without Minecraft.
CustomPerm's permission resolution lives in `PermissionResolver` and `GradesConfig`, plain Java
classes with no Minecraft import, which is why 400 JUnit tests can cover it. The GameTests then check
the wiring around them rather than re-testing the same rules.

---

## 2. Gradle wiring

### 2.1 Source sets

JUnit uses the standard `src/test`. GameTest gets its **own source set**, because its code must be
loaded by the mod at runtime and must not reach the JUnit classpath or the published jar.

```gradle
sourceSets {
    gameTest {
        compileClasspath += sourceSets.main.output + configurations.compileClasspath
        runtimeClasspath += sourceSets.main.output + configurations.runtimeClasspath
    }
}

neoForge {
    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
            sourceSet sourceSets.gameTest   // the mod owns both at dev runtime
        }
    }
}

dependencies {
    gameTestImplementation sourceSets.main.output
}
```

The `sourceSet sourceSets.gameTest` line inside `neoForge.mods` is what makes NeoForge treat the test
classes as part of the mod. Without it the classes are on the classpath but absent from the mod's
scan data, and the annotation scan described in §4.1 never sees them.

### 2.2 Run configurations

Declare one run per backend or configuration you need to cover. Each gets its **own game directory**,
so a run never depends on whatever is left in `run/mods` from a manual session:

```gradle
neoForge {
    runs {
        gameTestServer {
            type = 'gameTestServer'
            gameDirectory = project.file('run/gametest')
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
            systemProperty 'customperm.gametest.luckperms', 'false'
        }
        gameTestServerLuckPerms {
            type = 'gameTestServer'
            gameDirectory = project.file('run/gametest-luckperms')
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
            systemProperty 'customperm.gametest.luckperms', 'true'
        }
    }
}
```

`neoforge.enabledGameTestNamespaces` is **not optional**. NeoForge filters every registered test by
namespace, and a namespace that is not listed is dropped silently. The symptom is
`IllegalArgumentException: No test functions were given!` at server startup, with nothing else in the
log to explain it.

The extra system property (`customperm.gametest.luckperms`) is the run declaring what it believes it
is, so a test can assert it. See §6.2.

### 2.3 Populating the mods folder per run

The second run needs a third-party mod present. Fetch it through a configuration that is resolved but
never placed on a compile or runtime classpath, and sync it into that run's mods folder:

```gradle
configurations {
    luckPermsGameTestMod { canBeConsumed = false; canBeResolved = true; transitive = false }
}

dependencies {
    luckPermsGameTestMod "curse.maven:luckperms-431733:5971552"   // pinned file id
}

def prepareInternal = tasks.register('prepareInternalGameTestMods', Delete) {
    delete 'run/gametest/mods'
}
def prepareLuckPerms = tasks.register('prepareLuckPermsGameTestMods', Sync) {
    from configurations.luckPermsGameTestMod
    into 'run/gametest-luckperms/mods'
}
tasks.matching { it.name == 'runGameTestServer' }.configureEach { dependsOn prepareInternal }
tasks.matching { it.name == 'runGameTestServerLuckPerms' }.configureEach { dependsOn prepareLuckPerms }
```

`Delete` on the first run matters as much as `Sync` on the second: the "no third-party mod" mode is
only meaningful if it is enforced, not assumed.

Use `tasks.matching { }.configureEach` rather than `tasks.named(...)`. The moddev plugin creates run
tasks lazily and late; `named` on a task that does not exist yet fails the configuration phase.

### 2.4 JUnit dependencies

```gradle
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
    testRuntimeOnly   'org.junit.platform:junit-platform-launcher'

    // Anything main code reaches for that NeoForge normally provides,
    // but which is not exported to the test classpath:
    testImplementation 'org.slf4j:slf4j-api:2.0.9'
    testRuntimeOnly   'org.slf4j:slf4j-simple:2.0.9'
    testImplementation 'com.google.code.gson:gson:2.11.0'

    // H2 in MySQL mode, so SQL code runs in CI without a database server
    testImplementation 'com.h2database:h2:2.4.240'
}

test { useJUnitPlatform() }
```

The SLF4J and Gson entries are the usual surprise. A class that calls `LoggerFactory.getLogger` or
uses Gson compiles fine against NeoForge but throws `NoClassDefFoundError` in a JUnit test, because
the NeoForge runtime is not on the test classpath. Declare them explicitly.

Watch for version conflicts when a third source set is involved. CustomPerm's JMH benchmarks needed
Gson forced back to the version NeoForge pins, scoped to that configuration only so the JUnit tests
keep theirs:

```gradle
configurations.named('jmhRuntimeClasspath') {
    resolutionStrategy.force 'com.google.code.gson:gson:2.10.1'
}
```

---

## 3. Layer 1 — JUnit

### 3.1 Filesystem work goes through `@TempDir`

Config loading, migration and rollback are worth testing, and all of it touches disk. JUnit's
`@TempDir` gives each test its own directory, cleaned up afterwards. This only works if the class
under test can be told where to read and write:

```java
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void freshInstallIsStampedWithTheCurrentConfigVersion() {
        ConfigManager mgr = new ConfigManager(tempDir);      // constructor takes a root
        assertTrue(mgr.load(), "a fresh install must load");
        assertEquals(SettingsConfig.CURRENT_CONFIG_VERSION, mgr.getSettings().configVersion,
            "no settings.json means a fresh install, not an upgrade");
    }

    @Test
    void settingsWrittenBeforeTheVersionFieldReadAsVersionZero() throws Exception {
        Files.writeString(tempDir.resolve("settings.json"), "{\"luckPermsFallbackMode\":\"internal\"}");
        ConfigManager mgr = new ConfigManager(tempDir);
        assertTrue(mgr.load(), "a 1.0.x settings.json must still load");
        assertEquals(0, mgr.getSettings().configVersion, "a file without the field is an upgrade to announce");
    }
}
```

The design requirement is a constructor overload taking a root path. A class that hardcodes
`FMLPaths.CONFIGDIR.get()` cannot be unit-tested at all; make the production constructor delegate to
the testable one.

The second test is the pattern worth copying: **write the old format by hand, load it, assert the
migration.** Backward compatibility breaks silently and is cheap to pin down this way.

### 3.2 One contract, several implementations

When an interface has more than one implementation that must behave identically, write the tests once
against the interface in an abstract class, and let each implementation supply its instance.

```java
abstract class PartSyncContract {

    /** A store object over the data of this test, a new one per call. */
    protected abstract ClusterStore store();

    /** Makes every store object of this test fail as an unreachable database would. */
    protected abstract void setDown(boolean down);

    @Test
    void aChangeReachesTheOtherServer() throws Exception { /* ... 8 shared tests ... */ }

    @Test
    void anUnreachableStoreUndoesTheChange() throws Exception { /* ... */ }
}
```

```java
class MemoryPartSyncTest extends PartSyncContract {
    private final MemoryStore memory = new MemoryStore();
    @Override protected ClusterStore store()        { return memory; }
    @Override protected void setDown(boolean down)  { memory.setDown(down); }
}
```

```java
class SqlPartSyncTest extends PartSyncContract {
    private String url;
    private final AtomicBoolean down = new AtomicBoolean();

    @BeforeEach
    void database() throws Exception {
        url = "jdbc:h2:mem:cp" + UUID.randomUUID().toString().replace("-", "")
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        sql().createTables();
    }

    private SqlStore sql() {
        return new SqlStore(() -> {
            if (down.get()) throw new SQLException("database down");
            return DriverManager.getConnection(url);
        });
    }

    @Override protected ClusterStore store()         { return sql(); }
    @Override protected void setDown(boolean value)  { down.set(value); }

    // plus the tests that only apply to SQL
}
```

Three things are doing work here.

**The in-memory implementation is not only a test double.** It is a real production backend
(single-server mode), so the contract tests exercise shipped code twice over rather than testing a
mock.

**H2 in MySQL mode** runs the real SQL in CI with no database server. It is not a perfect MySQL, so
keep the SQL to the intersection of what MySQL, MariaDB and H2 accept, and still run a manual pass
against the real engine before release. `DB_CLOSE_DELAY=-1` keeps the in-memory database alive while
no connection is open; a fresh UUID in the name isolates each test.

**The store takes a `Callable<Connection>`, not a `DataSource`.** That one indirection is what makes
`setDown(true)` possible, so the test can check how the code behaves against an unreachable database:
the case most likely to be wrong and the hardest to reproduce by hand.

Inject the clock the same way (`LongSupplier`) and expiry logic becomes testable without sleeping.

### 3.3 Concurrency

Snapshot semantics are worth an explicit test rather than an argument in a code review:

```java
@Test
void shouldServeConsistentSnapshot_underConcurrentReads() throws Exception {
    ConfigManager mgr = new ConfigManager(tempDir);
    mgr.load();
    ConfigSnapshot expected = mgr.getSnapshot();

    int threadCount = 50;
    ExecutorService exec = Executors.newFixedThreadPool(threadCount);
    List<Future<ConfigSnapshot>> futures = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) futures.add(exec.submit(mgr::getSnapshot));
    exec.shutdown();
    // assert every returned snapshot is the expected one
}
```

This will not catch every race, but it does catch the common one: a getter that rebuilds a snapshot
per call, or hands out a mutable internal collection.

### 3.4 Assertion messages

Every assertion carries a message saying what the rule is, not what the value was:

```java
assertEquals(0, mgr.getSettings().configVersion, "a file without the field is an upgrade to announce");
assertEquals(Set.of("from.a"), b.config.grades.get("vip").permissions, "B must show A's change, not its own");
assertNull(b.sync.publish(), "A removed holder can be created again");
```

JUnit already prints expected and actual. The message is the only place the *intent* survives, and it
is what a reader six months later needs when the test fails.

---

## 4. Layer 2 — GameTest

### 4.1 Registration: what actually happens

This is the part with the most folklore around it. The mechanism in NeoForge 1.21.1
(`net.neoforged.neoforge.gametest.GameTestHooks#registerGametests`) is:

```java
Set<Method> gameTestMethods = new HashSet<>();
ModLoader.postEvent(new RegisterGameTestsEvent(gameTestMethods));   // 1. your event handler, if any

ModList.get().getAllScanData().stream()                             // 2. the annotation scan
        .map(ModFileScanData::getAnnotations)
        .flatMap(Collection::stream)
        .filter(a -> GAME_TEST_HOLDER.equals(a.annotationType()))
        .forEach(a -> addGameTestMethods(a, gameTestMethods));      // adds ALL declared methods

for (Method m : gameTestMethods) GameTestRegistry.register(m, enabledNamespaces);
```

What follows from reading it:

- **`@GameTestHolder` on the class is enough.** The annotation scan finds every class carrying it and
  adds all of its declared methods. No registration code is needed.
- **A manual `RegisterGameTestsEvent` handler is redundant** for any class that already has
  `@GameTestHolder`. Both paths feed the same `HashSet<Method>`, so registering by hand does not
  double-run anything, but it does not add anything either.
- **The namespace filter is the real gate.** `GameTestRegistry.register(m, enabledNamespaces)` drops
  the method when its namespace is not enabled. The namespace comes from `@GameTestHolder(MODID)`;
  without the annotation it defaults to `"minecraft"` and your tests are filtered out.

So the minimum is:

```java
@GameTestHolder(MyMod.MODID)
@PrefixGameTestTemplate(false)
public class MyFeatureTest {

    @GameTest(template = "empty_3x3", timeoutTicks = 100)
    public static void somethingHolds(GameTestHelper helper) {
        // ...
        helper.succeed();
    }
}
```

Methods must be `public static` and take a single `GameTestHelper`.

`@PrefixGameTestTemplate(false)` turns off vanilla's habit of prefixing the template name with the
lowercased class name. Leave it on and a test in `MyFeatureTest` asking for `empty_3x3` looks for
`myfeaturetest.empty_3x3` and fails to find its structure. Set it once at class level.

If you want a registration count in the log for diagnostics, subscribe to `RegisterGameTestsEvent` on
the **mod** bus and log without registering:

```java
@EventBusSubscriber(modid = MyMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class TestDiagnostics {
    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        MyMod.LOGGER.info("[Tests] GameTest registration starting.");
    }
}
```

`RegisterGameTestsEvent` fires on the mod event bus, not the NeoForge bus. Subscribing to the wrong
one is silent.

### 4.2 The structure

Every `@GameTest` needs a structure to run in. For tests that never touch the world, one empty 3x3
platform serves the whole suite:

```
src/main/resources/data/<modid>/structure/empty_3x3.nbt
```

Note `structure/`, singular, under `data/` — in 1.21 this is not `structures/`. Generate it once
in-game with a structure block and commit it. CustomPerm's 176 tests share this single file.

Declare it once per class and reuse:

```java
private static final String TEMPLATE = "empty_3x3";
```

Do not write `"mymod:empty_3x3"`. The holder namespace is prefixed for you; an explicit namespace
produces `mymod:mymod:empty_3x3`.

### 4.3 Batches

Tests in one batch run sequentially in the same world; batches run one after another. This is the
only isolation mechanism GameTest offers, and it matters whenever a test touches **global mutable
state** — a static alert, a config field, a registered alias:

```java
@GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_alert_delivery")
public static void adminAlertReachesOperatorsOnlyAndOnLogin(GameTestHelper helper) { /* ... */ }
```

CustomPerm uses about 40 named batches, named `<modid>_<concern>`. The rule applied is: **a test that
mutates shared static state, or that spans several ticks, gets its own batch.** Without it, a parallel
test clearing the same alert between your `raise` and your assertion produces a failure that does not
reproduce when the test is run alone — the worst kind.

### 4.4 Time

`helper.succeed()` ends the test immediately. Anything the server defers to a later tick — a
broadcast, a scheduled task, a packet handed to the server thread — has not happened yet.

```java
CustomPerm.raiseLuckPermsUnavailable(marker);

// Broadcast is handed to the server thread, so it lands on a later tick.
helper.runAfterDelay(2, () -> {
    try {
        if (!op.chatContains(marker)) fail("An online operator must receive the alert, got: " + op.chat());
        if (player.chatContains(marker)) fail("A non-operator must not receive admin alerts.");
        helper.succeed();
    } catch (RuntimeException e) {
        cleanup(player, op, wasActive);
        throw e;
    }
});
```

Two rules that are easy to get wrong:

- **Cleanup must run on both paths.** Inside a `runAfterDelay` callback there is no enclosing
  try-with-resources any more, so resources opened before the delay have to be released explicitly in
  both the success and the exception path.
- **`timeoutTicks` must exceed the total delay.** A test whose callback fires at tick 100 with
  `timeoutTicks = 100` fails as a timeout, and the message points at the timeout rather than at what
  went wrong.

Nested `runAfterDelay` calls work, and are how CustomPerm checks "and then it does *not* happen
again".

### 4.5 Failing

Throw `GameTestAssertException` with a message saying what invariant broke and what the actual state
was. A one-line private helper keeps the test bodies readable:

```java
private static void fail(String msg) {
    throw new GameTestAssertException(msg);
}
```

```java
if (grades.userHasPermission(uuid, "customperm.command.give"))
    fail("Player gained an unrelated perm not present on the grade.");

if (!op.chatContains(marker))
    fail("An online operator must receive the alert, got: " + op.chat());
```

The GameTest report gives you the test name and the message, nothing else. Including the observed
value (`got: " + op.chat()`) is what makes a CI failure diagnosable without re-running locally.

---

## 5. The harness

The single highest-value piece of the setup. Most server-side behaviour — permissions, the command
tree a client sees, packets, chat — is only reachable through a real `ServerPlayer` on a real
connection. `GameTestHelper.makeMockServerPlayer()` is not enough: it is not in the player list and
has no channel, so nothing is sent to it.

`TestPlayer` builds a genuinely connected player over an in-memory channel, and records everything the
server sends it.

### 5.1 Construction

```java
public static TestPlayer join(ServerLevel level, GameProfile profile, int permissionLevel,
                              boolean modInstalledClientSide) {
    if (!profile.getName().matches("[A-Za-z0-9_]{1,16}")) {
        throw new IllegalArgumentException("Invalid test player name (1-16 letters, digits, _): " + profile.getName());
    }
    MinecraftServer server = level.getServer();
    CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

    // Permission level is overridden rather than written to the ops list.
    ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
        @Override protected int getPermissionLevel() { return permissionLevel; }
    };

    Connection connection = new Connection(PacketFlow.SERVERBOUND);
    EmbeddedChannel channel = new EmbeddedChannel(connection);

    if (modInstalledClientSide) {
        // This in-memory connection skips the handshake, so declare the channels by hand.
        ChannelAttributes.getOrCreateAdHocChannels(connection).addAll(List.of(
                GuiPagePayload.TYPE.id(), GuiActionResultPayload.TYPE.id()));
    }

    server.getPlayerList().placeNewPlayer(connection, player, cookie);   // the real join sequence
    return new TestPlayer(server, player, channel);
}
```

Four decisions carry the design:

**`EmbeddedChannel` instead of a socket.** Netty's in-memory channel means no port, no async, no
flakiness. Packets written by the server are readable synchronously with `readOutbound()`.

**`placeNewPlayer` runs the real join sequence**, NeoForge's login event included. This is what makes
"does a joining operator receive the pending alert?" testable at all.

**Permission level by anonymous subclass override**, not by writing `ops.json`. No file to clean up,
and each player in a test can hold a different level.

**Ad-hoc channel registration.** NeoForge refuses to send a custom payload over a connection that did
not negotiate the channel. The in-memory connection skips the handshake, so the channels are declared
directly. Leave the flag `false` and the player behaves exactly like a vanilla client — which is
itself the test for "does the server cope with a client that does not have the mod?".

The name regex is not cosmetic. LuckPerms rejects a non-conforming name with a message that gives no
hint about the cause; failing fast in the harness saves the investigation.

### 5.2 Reading what the server sent

Nothing is decoded. Packets written to the channel are kept as objects, which is faster and keeps the
assertions readable:

```java
public List<Packet<?>> drain() {
    channel.runPendingTasks();
    Object message;
    while ((message = channel.readOutbound()) != null) {
        if (message instanceof Packet<?> packet) received.add(packet);
    }
    return received;
}

public List<String> chat() {
    return drain().stream()
            .filter(p -> p instanceof ClientboundSystemChatPacket)
            .map(p -> ((ClientboundSystemChatPacket) p).content().getString())
            .toList();
}

public long commandTreesReceived() {
    return drain().stream().filter(p -> p instanceof ClientboundCommandsPacket).count();
}

public <T extends CustomPacketPayload> List<T> payloads(Class<T> type) {
    return drain().stream()
            .filter(p -> p instanceof ClientboundCustomPayloadPacket)
            .map(p -> ((ClientboundCustomPayloadPacket) p).payload())
            .filter(type::isInstance).map(type::cast).toList();
}
```

`clearReceived()` discards the join sequence so a test asserts only on what it caused.

Three ways to run a command, and when to use which:

```java
/** Like the client typing it: errors go to the player, not to the caller. Read via chat(). */
public void type(String command) {
    server.getCommands().performPrefixedCommand(source(), command);
}

/** Through the dispatcher: parse and permission errors throw. */
public int exec(String command) throws CommandSyntaxException {
    return server.getCommands().getDispatcher().execute(command, source());
}

/** Is the command in the tree this client received? */
public boolean canUse(String rootCommand) {
    var node = server.getCommands().getDispatcher().getRoot().getChild(rootCommand);
    return node != null && node.canUse(source());
}
```

`canUse` is the one that answers "is this hidden from tab-completion?", which is usually the real
requirement rather than "does it refuse when run".

### 5.3 Serverbound payloads

Testing a packet handler needs an `IPayloadContext`. Implement it against the test player, running
work inline because the test is already on the server thread:

```java
public IPayloadContext payloadContext() {
    return new IPayloadContext() {
        @Override public ICommonPacketListener listener() { return player.connection; }
        @Override public Player player() { return player; }
        @Override public CompletableFuture<Void> enqueueWork(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        @Override public <T> CompletableFuture<T> enqueueWork(Supplier<T> task) {
            return CompletableFuture.completedFuture(task.get());
        }
        @Override public PacketFlow flow() { return PacketFlow.SERVERBOUND; }
        @Override public void handle(CustomPacketPayload payload) { throw new UnsupportedOperationException(); }
        @Override public void finishCurrentTask(ConfigurationTask.Type type) { throw new UnsupportedOperationException(); }
    };
}
```

Unused methods throw rather than returning null: a test that drifts into them fails loudly instead of
producing a confusing NPE.

This turns a packet-level authorisation test into a few lines:

```java
GuiRequestPayload request = new GuiRequestPayload(GuiPage.DASHBOARD.id());
GuiRequestHandler.handleRequest(request, player.payloadContext());   // level 0
GuiRequestHandler.handleRequest(request, op.payloadContext());       // level 2 + node

if (!player.payloads(GuiPagePayload.class).isEmpty()) fail("A non-operator must receive no admin page.");
if (op.payloads(GuiPagePayload.class).size() != 1)   fail("An operator must receive exactly one admin page.");
```

### 5.4 Always close

```java
@Override
public void close() {
    if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
        server.getPlayerList().remove(player);
    }
    channel.finishAndReleaseAll();
    if (access != null) access.close();       // revoke granted nodes
}
```

`TestPlayer implements AutoCloseable`, and every test uses try-with-resources:

```java
try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_h_player", 0);
     TestPlayer op     = TestPlayer.join(helper.getLevel(), "cp_h_op", 2);
     TestPlayer admin  = TestPlayer.reader(helper.getLevel(), "cp_h_admin", 2)) {
    if (player.canUse("customperm")) fail("A level-0 player must not see /customperm.");
    if (op.canUse("customperm"))     fail("A level-2 player without the node must not see /customperm.");
    if (!admin.canUse("customperm")) fail("A level-2 player holding the node must see /customperm.");
}
helper.succeed();
```

A player left in the list receives the broadcasts of later tests and skews their assertions. This is
the most common source of order-dependent GameTest failures.

### 5.5 Test the harness

The harness is code, and a silently broken harness turns every test that uses it into a false pass.
CustomPerm has a `TestPlayerHarnessTest` asserting that the primitives work — permission level drives
command visibility, chat is captured, a command tree resync is counted — before any feature test
relies on them.

### 5.6 Capturing console output

For command output not addressed to a player, wrap the server's command source:

```java
private static CommandSourceStack capturing(MinecraftServer server, List<String> lines) {
    CommandSource capture = new CommandSource() {
        @Override public void sendSystemMessage(Component message) { lines.add(message.getString()); }
        @Override public boolean acceptsSuccess()      { return true; }
        @Override public boolean acceptsFailure()      { return true; }
        @Override public boolean shouldInformAdmins()  { return false; }
    };
    return server.createCommandSourceStack().withSource(capture);
}
```

`server.createCommandSourceStack()` is permission level 4 by construction, so this is also how you
test "the console always has access" without building anything.

---

## 6. Two backends, one test body

CustomPerm must work identically with and without LuckPerms. Rather than duplicating 176 tests, the
same test body runs in both modes and the *setup* adapts.

### 6.1 Abstract the state change, not the assertion

```java
public final class Grants implements AutoCloseable {

    public static Grants allow(UUID player, String... nodes) {
        Grants grants = new Grants(player, List.of(nodes), false);
        if (CustomPerm.isLuckPermsActive()) {
            LuckPermsTestSupport.setNodes(player, grants.nodes, true);   // like /lp user ... permission set
        } else {
            grants.grade().permissions.addAll(grants.nodes);             // a dedicated grade
            grants.assign();
        }
        return grants;
    }

    @Override
    public void close() { /* revoke through whichever backend is active */ }
}
```

The test says `Grants.allow(player, "mymod.feature")` and asserts on behaviour. Which backend stored
it is not the test's business. Note `close()` removes **only these nodes**, so several `Grants` on one
player nest correctly.

### 6.2 Assert the mode is what it claims

The failure mode this guards against is severe: if the LuckPerms run silently loses LuckPerms, every
LuckPerms-specific test *skips itself and reports a pass*. The run stays green while testing nothing.

```java
@GameTest(template = "empty_3x3", timeoutTicks = 100)
public static void backendMatchesTheRunMode(GameTestHelper helper) {
    String mode = System.getProperty("customperm.gametest.luckperms");   // set in build.gradle
    if ("true".equals(mode) && !CustomPerm.isLuckPermsActive()) {
        throw new GameTestAssertException("LuckPerms mode, but the active backend is "
            + CustomPerm.backendLabel() + ". Check that the run copied the LuckPerms jar into its mods folder.");
    }
    if ("false".equals(mode) && CustomPerm.isLuckPermsPresent()) {
        throw new GameTestAssertException("Internal mode, but LuckPerms is loaded. Its mods folder must stay empty.");
    }
    helper.succeed();
}
```

**If you take one thing from this document, take this one.** Any conditional-skip mechanism needs a
test asserting the condition is what the run intended.

### 6.3 Skipping

```java
public final class Modes {
    /** True when the internal backend is active; otherwise marks the test passed and returns false. */
    public static boolean internalOnly(GameTestHelper helper) {
        if (!CustomPerm.isLuckPermsActive()) return true;
        helper.succeed();
        return false;
    }

    public static boolean luckPermsOnly(GameTestHelper helper) { /* mirror */ }
}
```

```java
@GameTest(template = TEMPLATE, timeoutTicks = 100)
public static void internalOnlyBehaviour(GameTestHelper helper) {
    if (!Modes.internalOnly(helper)) return;
    // ...
    helper.succeed();
}
```

GameTest has no "skipped" outcome, so a skip must succeed. That is exactly why §6.2 exists.

### 6.4 Isolating the third-party API

Exactly one class imports the LuckPerms API (`LuckPermsTestSupport`). Every caller checks
`isLuckPermsActive()` first, so the class is never resolved on a runtime without LuckPerms and the
GameTests still load there.

Its other job is bridging async to synchronous:

```java
/** Waits by wall clock: GameTest ticks do not advance LuckPerms' executors. */
public static <T> T await(CompletableFuture<T> future) {
    try {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
        throw new GameTestAssertException("LuckPerms call did not complete within " + TIMEOUT_SECONDS + "s: " + e);
    }
}
```

**Always use the bounded `get(timeout, unit)`.** An unbounded `get()` on the server thread deadlocks
the whole run against a future that needs that thread, and GameTest's own timeout does not save you
because the thread is blocked. Bounded, you get a test failure with a usable message.

One more discovery worth recording: LuckPerms loads a user during the real login handshake, which a
test player skips. Its Brigadier requirement then denies every command to an unknown user, which looks
like a permission bug. The harness loads the user up front to reproduce what a real login did.

### 6.5 Probe nodes from a foreign namespace

To test how your mod answers permission queries from *other* mods, declare nodes under a namespace
that is not yours, with deliberately opposite defaults:

```java
@EventBusSubscriber(modid = MyMod.MODID)
public final class TestModNodes {
    /** Defaults to true: a refusal can only come from my mod. */
    public static final PermissionNode<Boolean> OPEN =
        new PermissionNode<>("cptest", "probe.open", PermissionTypes.BOOLEAN, (p, u, c) -> true);
    /** Defaults to false: a grant can only come from my mod. */
    public static final PermissionNode<Boolean> SHUT =
        new PermissionNode<>("cptest", "probe.shut", PermissionTypes.BOOLEAN, (p, u, c) -> false);

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) { event.addNodes(OPEN, SHUT); }
}
```

Opposite defaults let an assertion distinguish "answered the default" from "answered a decision" —
otherwise a handler that does nothing at all looks correct half the time.

---

## 7. Traps

Ordered by how much time each one costs before you find it.

**`No test functions were given!`** — the server refuses to start and nothing else is logged. Causes,
in the order worth checking: `neoforge.enabledGameTestNamespaces` not set on the run;
`@GameTestHolder(MODID)` missing, so the namespace defaults to `minecraft` and everything is filtered
out; the gameTest source set not listed in `neoForge.mods`; methods not `public static`.

**Structure not found** — `@PrefixGameTestTemplate(false)` missing, so `empty_3x3` is looked up as
`myclassname.empty_3x3`. Or the file is under `data/<modid>/structures/` instead of `structure/`.

**A test passes alone and fails in the suite** — shared mutable state and no `batch`. Give it one.

**A test fails only in CI** — usually timing. A `runAfterDelay` whose total exceeds `timeoutTicks`, or
an unbounded wait on an async API.

**`NoClassDefFoundError` for SLF4J or Gson in a JUnit test** — the NeoForge runtime is not on the test
classpath. Declare them in `testImplementation`.

**The whole GameTest run hangs** — an unbounded `CompletableFuture.get()` on the server thread.

**A green run that tested nothing** — see §6.2.

**A test player still receiving packets after `/reload`** — after `MinecraftServer.reloadResources`, a
test player that stayed connected remains in the player list with an open channel but receives no
further packets. Reconnect players after reloading data packs. Whether a real client behaves the same
was not established in CustomPerm; it is verified by hand in the manual test procedure. Worth knowing
before trusting an assertion across a reload.

---

## 8. CI

One job, both modes, then the jar:

```yaml
name: GameTests
on:
  push:         { branches: [main, dev] }
  pull_request: { branches: [main, dev] }

jobs:
  gametest:
    runs-on: ubuntu-latest
    timeout-minutes: 25
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }
      - uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ runner.os }}-${{ hashFiles('gradle.properties', 'build.gradle') }}
          restore-keys: gradle-${{ runner.os }}-

      - run: chmod +x ./gradlew
      # The task's exit code is the number of failed required tests, so this fails the job by itself.
      - run: ./gradlew runGameTestServer --no-daemon
      - run: ./gradlew runGameTestServerLuckPerms --no-daemon
      - run: ./gradlew build --no-daemon

      - name: Verify distributable jar contents
        run: |
          set -euo pipefail
          jar_file="$(ls build/libs/mymod-*.jar | head -n 1)"
          test -n "$jar_file"
          jar tf "$jar_file" | grep -Fx 'META-INF/neoforge.mods.toml'

      - name: Upload logs on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: gametest-logs
          path: |
            run/gametest/logs
            run/gametest-luckperms/logs
            build/reports
```

Points that matter:

- **`gameTestServer` exits with the number of failed required tests.** No log parsing needed; a
  non-zero exit fails the job.
- **`--no-daemon`** — a Gradle daemon in CI wastes memory and occasionally survives between steps
  holding stale state.
- **Upload the logs on failure.** A GameTest failure in CI is nearly undiagnosable from the console
  output alone; `run/<mode>/logs` is where the answer is.
- **`timeout-minutes`** — a hung run otherwise burns the full job allowance.
- **Verify the jar contents.** Cheap, and it catches a `processResources` or packaging regression that
  every test would miss.

`./gradlew build` runs the JUnit suite as part of `check`, so it needs no separate step.

---

## 9. Porting checklist

For a fresh NeoForge 1.21.x mod, in order:

1. `sourceSets { gameTest { compileClasspath += sourceSets.main.output + configurations.compileClasspath; runtimeClasspath += ... } }`
2. `gameTestImplementation sourceSets.main.output`
3. `neoForge.mods."${mod_id}" { sourceSet sourceSets.main; sourceSet sourceSets.gameTest }`
4. A `gameTestServer` run with `neoforge.enabledGameTestNamespaces = mod_id` and its own `gameDirectory`
5. `src/main/resources/data/<modid>/structure/empty_3x3.nbt` (structure block, once)
6. `testImplementation junit-jupiter` + `testRuntimeOnly junit-platform-launcher` + `test { useJUnitPlatform() }`
7. Add `slf4j-api` / `slf4j-simple` / `gson` to the test classpath if main code reaches for them
8. One `@GameTestHolder(MODID) @PrefixGameTestTemplate(false)` class, one `@GameTest` that calls
   `helper.succeed()` — confirm the run is green before writing anything real
9. Port `TestPlayer` (§5), then a harness self-test (§5.5)
10. Give every config or store class a constructor taking a root path or a connection supplier (§3.1, §3.2)
11. If there are two backends or configurations: a `Grants`-style abstraction (§6.1) **and** the mode
    assertion (§6.2)
12. CI: both runs, `--no-daemon`, logs uploaded on failure (§8)

Steps 1 to 8 are the minimum that produces a working, green setup. Everything after is what makes the
suite worth maintaining.
