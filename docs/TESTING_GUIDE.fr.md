# Tester un mod NeoForge : JUnit et GameTest

Comment CustomPerm est testé, rédigé pour que le dispositif soit transposable dans un autre mod
NeoForge 1.21.x. Le document couvre le câblage Gradle, la répartition entre les deux couches de test,
les classes de harnais qui rendent le comportement serveur testable, et les pièges qui coûtent le plus
de temps à diagnostiquer.

Chiffres de référence, CustomPerm au moment de la rédaction : 28 400 lignes de code principal,
400 tests JUnit répartis sur 38 classes, 176 GameTests sur 30 classes, deux modes d'exécution
GameTest, un job CI.

La version anglaise est dans [TESTING_GUIDE.md](TESTING_GUIDE.md).

- [1. Les deux couches](#1-les-deux-couches)
- [2. Câblage Gradle](#2-câblage-gradle)
- [3. Couche 1 — JUnit](#3-couche-1--junit)
- [4. Couche 2 — GameTest](#4-couche-2--gametest)
- [5. Le harnais](#5-le-harnais)
- [6. Deux backends, un seul corps de test](#6-deux-backends-un-seul-corps-de-test)
- [7. Pièges](#7-pièges)
- [8. CI](#8-ci)
- [9. Checklist de portage](#9-checklist-de-portage)

---

## 1. Les deux couches

La répartition se décide sur une seule question : **ce code a-t-il besoin d'un serveur Minecraft en
marche ?**

|                        | JUnit                                                    | GameTest                                                          |
| ---------------------- | -------------------------------------------------------- | ----------------------------------------------------------------- |
| Tourne dans            | une JVM simple, sans Minecraft                            | un vrai serveur dédié, sans affichage                              |
| Démarrage              | instantané                                                | ~40 s par run, ~5 min pour la suite                                |
| Teste                  | logique de résolution, parsing de config, codecs, SQL     | commandes, paquets, événements, handler de permissions, connexion  |
| Un échec se lit comme  | une stack trace avec numéros de ligne                     | un nom de test plus votre propre message                           |
| Lancer un test seul    | oui, depuis l'IDE                                         | non, c'est le batch qui tourne                                     |

Règle pratique : **tout ce qui peut être un test JUnit doit en être un.** Un GameTest coûte un
démarrage de serveur ; un test JUnit tourne en millisecondes. GameTest est réservé à ce qui a vraiment
besoin du jeu : Brigadier, la couche réseau, les événements NeoForge, la liste des joueurs.

Concrètement, cela impose de concevoir le mod pour que la logique intéressante soit atteignable sans
Minecraft. La résolution des permissions de CustomPerm vit dans `PermissionResolver` et
`GradesConfig`, classes Java pures sans import Minecraft, ce qui permet à 400 tests JUnit de la
couvrir. Les GameTests vérifient ensuite le câblage autour, au lieu de retester les mêmes règles.

---

## 2. Câblage Gradle

### 2.1 Source sets

JUnit utilise le `src/test` standard. GameTest reçoit son **propre source set**, parce que son code
doit être chargé par le mod à l'exécution et ne doit atteindre ni le classpath JUnit, ni le jar
publié.

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
            sourceSet sourceSets.gameTest   // le mod possède les deux à l'exécution dev
        }
    }
}

dependencies {
    gameTestImplementation sourceSets.main.output
}
```

La ligne `sourceSet sourceSets.gameTest` dans `neoForge.mods` est ce qui fait que NeoForge considère
les classes de test comme faisant partie du mod. Sans elle, les classes sont sur le classpath mais
absentes des données de scan du mod, et le scan d'annotations décrit au §4.1 ne les voit jamais.

### 2.2 Configurations d'exécution

Déclarez un run par backend ou configuration à couvrir. Chacun reçoit son **propre répertoire de
jeu**, pour qu'un run ne dépende jamais de ce qui traîne dans `run/mods` après une session manuelle :

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

`neoforge.enabledGameTestNamespaces` n'est **pas optionnel**. NeoForge filtre chaque test enregistré
par namespace, et un namespace non listé est écarté en silence. Le symptôme est
`IllegalArgumentException: No test functions were given!` au démarrage du serveur, sans rien d'autre
dans le log pour l'expliquer.

La propriété système supplémentaire (`customperm.gametest.luckperms`) est le run qui déclare ce qu'il
croit être, pour qu'un test puisse l'affirmer. Voir §6.2.

### 2.3 Remplir le dossier mods par run

Le second run exige la présence d'un mod tiers. Récupérez-le via une configuration résolue mais jamais
placée sur un classpath de compilation ou d'exécution, et synchronisez-la dans le dossier mods de ce
run :

```gradle
configurations {
    luckPermsGameTestMod { canBeConsumed = false; canBeResolved = true; transitive = false }
}

dependencies {
    luckPermsGameTestMod "curse.maven:luckperms-431733:5971552"   // id de fichier épinglé
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

Le `Delete` du premier run compte autant que le `Sync` du second : le mode « sans mod tiers » n'a de
sens que s'il est imposé, pas supposé.

Utilisez `tasks.matching { }.configureEach` plutôt que `tasks.named(...)`. Le plugin moddev crée les
tâches de run tardivement et paresseusement ; `named` sur une tâche qui n'existe pas encore fait
échouer la phase de configuration.

### 2.4 Dépendances JUnit

```gradle
dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.0'
    testRuntimeOnly   'org.junit.platform:junit-platform-launcher'

    // Tout ce que le code principal utilise et que NeoForge fournit normalement,
    // mais qui n'est pas exporté vers le classpath de test :
    testImplementation 'org.slf4j:slf4j-api:2.0.9'
    testRuntimeOnly   'org.slf4j:slf4j-simple:2.0.9'
    testImplementation 'com.google.code.gson:gson:2.11.0'

    // H2 en mode MySQL, pour que le code SQL tourne en CI sans serveur de base
    testImplementation 'com.h2database:h2:2.4.240'
}

test { useJUnitPlatform() }
```

Les entrées SLF4J et Gson sont la surprise habituelle. Une classe qui appelle
`LoggerFactory.getLogger` ou utilise Gson compile sans problème contre NeoForge mais lève un
`NoClassDefFoundError` dans un test JUnit, parce que le runtime NeoForge n'est pas sur le classpath de
test. Déclarez-les explicitement.

Attention aux conflits de version dès qu'un troisième source set entre en jeu. Les benchmarks JMH de
CustomPerm ont exigé de forcer Gson à la version épinglée par NeoForge, cantonné à cette configuration
pour que les tests JUnit gardent la leur :

```gradle
configurations.named('jmhRuntimeClasspath') {
    resolutionStrategy.force 'com.google.code.gson:gson:2.10.1'
}
```

---

## 3. Couche 1 — JUnit

### 3.1 Tout ce qui touche au disque passe par `@TempDir`

Le chargement de config, la migration et le rollback méritent d'être testés, et tout cela touche le
disque. `@TempDir` de JUnit donne à chaque test son propre répertoire, nettoyé ensuite. Cela ne
fonctionne que si la classe testée peut se faire indiquer où lire et écrire :

```java
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void freshInstallIsStampedWithTheCurrentConfigVersion() {
        ConfigManager mgr = new ConfigManager(tempDir);      // le constructeur prend une racine
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

L'exigence de conception est une surcharge de constructeur prenant un chemin racine. Une classe qui
code en dur `FMLPaths.CONFIGDIR.get()` n'est pas testable unitairement du tout ; faites déléguer le
constructeur de production vers celui qui est testable.

Le second test est le motif à reprendre : **écrire l'ancien format à la main, le charger, vérifier la
migration.** La compatibilité ascendante casse en silence et se verrouille à peu de frais ainsi.

### 3.2 Un contrat, plusieurs implémentations

Quand une interface a plus d'une implémentation qui doivent se comporter à l'identique, écrivez les
tests une seule fois contre l'interface dans une classe abstraite, et laissez chaque implémentation
fournir son instance.

```java
abstract class PartSyncContract {

    /** Un objet store sur les données de ce test, un nouveau à chaque appel. */
    protected abstract ClusterStore store();

    /** Fait échouer chaque objet store de ce test comme le ferait une base injoignable. */
    protected abstract void setDown(boolean down);

    @Test
    void aChangeReachesTheOtherServer() throws Exception { /* ... 8 tests partagés ... */ }

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

    // plus les tests qui ne concernent que SQL
}
```

Trois choses travaillent ici.

**L'implémentation en mémoire n'est pas qu'un double de test.** C'est un vrai backend de production
(mode mono-serveur), donc les tests de contrat exercent deux fois du code livré au lieu de tester un
mock.

**H2 en mode MySQL** fait tourner le vrai SQL en CI sans serveur de base. Ce n'est pas un MySQL
parfait : restez à l'intersection de ce que MySQL, MariaDB et H2 acceptent, et faites quand même une
passe manuelle sur le vrai moteur avant publication. `DB_CLOSE_DELAY=-1` maintient la base en mémoire
vivante quand aucune connexion n'est ouverte ; un UUID frais dans le nom isole chaque test.

**Le store prend un `Callable<Connection>`, pas une `DataSource`.** Cette seule indirection rend
`setDown(true)` possible, donc permet de vérifier le comportement face à une base injoignable : le cas
le plus susceptible d'être faux et le plus pénible à reproduire à la main.

Injectez l'horloge de la même façon (`LongSupplier`) et la logique d'expiration devient testable sans
attendre.

### 3.3 Concurrence

La sémantique de snapshot mérite un test explicite plutôt qu'une discussion en revue de code :

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
    // vérifier que chaque snapshot rendu est bien celui attendu
}
```

Cela n'attrapera pas toutes les courses, mais cela attrape la plus courante : un getter qui reconstruit
un snapshot à chaque appel, ou qui distribue une collection interne mutable.

### 3.4 Messages d'assertion

Chaque assertion porte un message qui dit quelle est la règle, pas quelle était la valeur :

```java
assertEquals(0, mgr.getSettings().configVersion, "a file without the field is an upgrade to announce");
assertEquals(Set.of("from.a"), b.config.grades.get("vip").permissions, "B must show A's change, not its own");
assertNull(b.sync.publish(), "A removed holder can be created again");
```

JUnit affiche déjà l'attendu et l'obtenu. Le message est le seul endroit où survit l'*intention*, et
c'est ce dont a besoin un lecteur six mois plus tard quand le test échoue.

---

## 4. Couche 2 — GameTest

### 4.1 L'enregistrement : ce qui se passe réellement

C'est la partie autour de laquelle circule le plus de folklore. Le mécanisme dans NeoForge 1.21.1
(`net.neoforged.neoforge.gametest.GameTestHooks#registerGametests`) est :

```java
Set<Method> gameTestMethods = new HashSet<>();
ModLoader.postEvent(new RegisterGameTestsEvent(gameTestMethods));   // 1. votre handler, s'il existe

ModList.get().getAllScanData().stream()                             // 2. le scan d'annotations
        .map(ModFileScanData::getAnnotations)
        .flatMap(Collection::stream)
        .filter(a -> GAME_TEST_HOLDER.equals(a.annotationType()))
        .forEach(a -> addGameTestMethods(a, gameTestMethods));      // ajoute TOUTES les méthodes déclarées

for (Method m : gameTestMethods) GameTestRegistry.register(m, enabledNamespaces);
```

Ce qui en découle :

- **`@GameTestHolder` sur la classe suffit.** Le scan d'annotations trouve chaque classe qui la porte
  et ajoute toutes ses méthodes déclarées. Aucun code d'enregistrement n'est nécessaire.
- **Un handler `RegisterGameTestsEvent` manuel est redondant** pour toute classe qui porte déjà
  `@GameTestHolder`. Les deux chemins alimentent le même `HashSet<Method>` : enregistrer à la main ne
  fait rien tourner deux fois, mais n'ajoute rien non plus.
- **Le vrai filtre est le namespace.** `GameTestRegistry.register(m, enabledNamespaces)` écarte la
  méthode si son namespace n'est pas activé. Le namespace vient de `@GameTestHolder(MODID)` ; sans
  l'annotation il vaut `"minecraft"` par défaut et vos tests sont filtrés.

Le minimum est donc :

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

Les méthodes doivent être `public static` et prendre un seul `GameTestHelper`.

`@PrefixGameTestTemplate(false)` désactive l'habitude vanilla de préfixer le nom de template par le nom
de classe en minuscules. Laissez-la active et un test de `MyFeatureTest` demandant `empty_3x3`
cherchera `myfeaturetest.empty_3x3` et ne trouvera pas sa structure. À poser une fois au niveau de la
classe.

Si vous voulez un décompte d'enregistrement dans le log à des fins de diagnostic, abonnez-vous à
`RegisterGameTestsEvent` sur le bus **mod** et journalisez sans enregistrer :

```java
@EventBusSubscriber(modid = MyMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public class TestDiagnostics {
    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        MyMod.LOGGER.info("[Tests] GameTest registration starting.");
    }
}
```

`RegisterGameTestsEvent` se déclenche sur le bus d'événements du mod, pas sur celui de NeoForge.
S'abonner au mauvais bus est silencieux.

### 4.2 La structure

Chaque `@GameTest` a besoin d'une structure pour tourner. Pour les tests qui ne touchent jamais au
monde, une unique plateforme 3x3 vide sert toute la suite :

```
src/main/resources/data/<modid>/structure/empty_3x3.nbt
```

Notez `structure/`, au singulier, sous `data/` — en 1.21 ce n'est pas `structures/`. Générez-la une
fois en jeu avec un bloc de structure et committez-la. Les 176 tests de CustomPerm partagent ce seul
fichier.

Déclarez-la une fois par classe et réutilisez :

```java
private static final String TEMPLATE = "empty_3x3";
```

N'écrivez pas `"mymod:empty_3x3"`. Le namespace du holder est préfixé pour vous ; un namespace
explicite produit `mymod:mymod:empty_3x3`.

### 4.3 Batches

Les tests d'un même batch tournent séquentiellement dans le même monde ; les batches s'enchaînent.
C'est le seul mécanisme d'isolation qu'offre GameTest, et il compte dès qu'un test touche un **état
global mutable** : une alerte statique, un champ de config, un alias enregistré.

```java
@GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "customperm_alert_delivery")
public static void adminAlertReachesOperatorsOnlyAndOnLogin(GameTestHelper helper) { /* ... */ }
```

CustomPerm utilise une quarantaine de batches nommés, sur le modèle `<modid>_<sujet>`. La règle
appliquée est : **un test qui modifie un état statique partagé, ou qui s'étale sur plusieurs ticks,
reçoit son propre batch.** Sans cela, un test parallèle qui efface la même alerte entre votre `raise`
et votre assertion produit un échec qui ne se reproduit pas quand le test est lancé seul : le pire des
cas.

### 4.4 Le temps

`helper.succeed()` termine le test immédiatement. Tout ce que le serveur reporte à un tick ultérieur —
une diffusion, une tâche planifiée, un paquet confié au thread serveur — n'a pas encore eu lieu.

```java
CustomPerm.raiseLuckPermsUnavailable(marker);

// La diffusion est confiée au thread serveur : elle atterrit à un tick ultérieur.
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

Deux règles faciles à rater :

- **Le nettoyage doit tourner sur les deux chemins.** Dans un callback `runAfterDelay` il n'y a plus de
  try-with-resources englobant : les ressources ouvertes avant le délai doivent être libérées
  explicitement, aussi bien sur le chemin de succès que sur celui d'exception.
- **`timeoutTicks` doit dépasser le délai total.** Un test dont le callback se déclenche au tick 100
  avec `timeoutTicks = 100` échoue en timeout, et le message pointe le timeout plutôt que ce qui a
  réellement échoué.

Les `runAfterDelay` imbriqués fonctionnent, et c'est ainsi que CustomPerm vérifie « et ensuite cela ne
se reproduit *pas* ».

### 4.5 Échouer

Levez `GameTestAssertException` avec un message disant quel invariant a cassé et quel était l'état
réel. Un helper privé d'une ligne garde les corps de test lisibles :

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

Le rapport GameTest vous donne le nom du test et le message, rien d'autre. Inclure la valeur observée
(`got: " + op.chat()`) est ce qui rend un échec CI diagnosticable sans relancer en local.

---

## 5. Le harnais

La pièce à plus forte valeur du dispositif. L'essentiel du comportement serveur — permissions, arbre
de commandes reçu par un client, paquets, chat — n'est atteignable qu'à travers un vrai `ServerPlayer`
sur une vraie connexion. `GameTestHelper.makeMockServerPlayer()` ne suffit pas : il n'est pas dans la
liste des joueurs et n'a pas de canal, donc rien ne lui est envoyé.

`TestPlayer` construit un joueur réellement connecté sur un canal en mémoire, et enregistre tout ce que
le serveur lui envoie.

### 5.1 Construction

```java
public static TestPlayer join(ServerLevel level, GameProfile profile, int permissionLevel,
                              boolean modInstalledClientSide) {
    if (!profile.getName().matches("[A-Za-z0-9_]{1,16}")) {
        throw new IllegalArgumentException("Invalid test player name (1-16 letters, digits, _): " + profile.getName());
    }
    MinecraftServer server = level.getServer();
    CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

    // Le niveau de permission est surchargé, pas écrit dans la liste des ops.
    ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
        @Override protected int getPermissionLevel() { return permissionLevel; }
    };

    Connection connection = new Connection(PacketFlow.SERVERBOUND);
    EmbeddedChannel channel = new EmbeddedChannel(connection);

    if (modInstalledClientSide) {
        // Cette connexion en mémoire saute la négociation : on déclare les canaux à la main.
        ChannelAttributes.getOrCreateAdHocChannels(connection).addAll(List.of(
                GuiPagePayload.TYPE.id(), GuiActionResultPayload.TYPE.id()));
    }

    server.getPlayerList().placeNewPlayer(connection, player, cookie);   // la vraie séquence de connexion
    return new TestPlayer(server, player, channel);
}
```

Quatre décisions portent la conception :

**`EmbeddedChannel` plutôt qu'une socket.** Le canal en mémoire de Netty signifie pas de port, pas
d'asynchrone, pas d'instabilité. Les paquets écrits par le serveur se lisent de façon synchrone avec
`readOutbound()`.

**`placeNewPlayer` exécute la vraie séquence de connexion**, événement de login NeoForge compris.
C'est ce qui rend testable « un opérateur qui se connecte reçoit-il l'alerte en attente ? ».

**Niveau de permission par surcharge dans une sous-classe anonyme**, et non par écriture d'`ops.json`.
Aucun fichier à nettoyer, et chaque joueur d'un test peut avoir un niveau différent.

**Enregistrement ad hoc des canaux.** NeoForge refuse d'envoyer une charge utile personnalisée sur une
connexion qui n'a pas négocié le canal. La connexion en mémoire saute la négociation, donc les canaux
sont déclarés directement. Laissez le drapeau à `false` et le joueur se comporte exactement comme un
client vanilla — ce qui constitue en soi le test de « le serveur supporte-t-il un client sans le
mod ? ».

La regex sur le nom n'est pas cosmétique. LuckPerms rejette un nom non conforme avec un message qui ne
donne aucun indice sur la cause ; échouer tôt dans le harnais épargne l'investigation.

### 5.2 Lire ce que le serveur a envoyé

Rien n'est décodé. Les paquets écrits sur le canal sont conservés comme objets, ce qui est plus rapide
et garde les assertions lisibles :

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

`clearReceived()` jette la séquence de connexion pour qu'un test n'affirme que sur ce qu'il a provoqué.

Trois façons de lancer une commande, et quand utiliser laquelle :

```java
/** Comme le client qui la tape : les erreurs vont au joueur, pas à l'appelant. Se lit via chat(). */
public void type(String command) {
    server.getCommands().performPrefixedCommand(source(), command);
}

/** Par le dispatcher : les erreurs de syntaxe et de permission lèvent. */
public int exec(String command) throws CommandSyntaxException {
    return server.getCommands().getDispatcher().execute(command, source());
}

/** La commande est-elle dans l'arbre que ce client a reçu ? */
public boolean canUse(String rootCommand) {
    var node = server.getCommands().getDispatcher().getRoot().getChild(rootCommand);
    return node != null && node.canUse(source());
}
```

`canUse` est celle qui répond à « est-ce masqué de la complétion ? », ce qui est le plus souvent la
vraie exigence, plutôt que « est-ce refusé à l'exécution ? ».

### 5.3 Charges utiles montantes

Tester un handler de paquet demande un `IPayloadContext`. Implémentez-le contre le joueur de test, en
exécutant le travail en ligne puisque le test est déjà sur le thread serveur :

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

Les méthodes inutilisées lèvent au lieu de renvoyer null : un test qui y dérive échoue bruyamment au
lieu de produire un NPE déroutant.

Cela réduit un test d'autorisation au niveau paquet à quelques lignes :

```java
GuiRequestPayload request = new GuiRequestPayload(GuiPage.DASHBOARD.id());
GuiRequestHandler.handleRequest(request, player.payloadContext());   // niveau 0
GuiRequestHandler.handleRequest(request, op.payloadContext());       // niveau 2 + nœud

if (!player.payloads(GuiPagePayload.class).isEmpty()) fail("A non-operator must receive no admin page.");
if (op.payloads(GuiPagePayload.class).size() != 1)   fail("An operator must receive exactly one admin page.");
```

### 5.4 Toujours fermer

```java
@Override
public void close() {
    if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
        server.getPlayerList().remove(player);
    }
    channel.finishAndReleaseAll();
    if (access != null) access.close();       // reprendre les nœuds accordés
}
```

`TestPlayer implements AutoCloseable`, et chaque test utilise try-with-resources :

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

Un joueur laissé dans la liste reçoit les diffusions des tests suivants et fausse leurs assertions.
C'est la source la plus courante d'échecs GameTest dépendants de l'ordre.

### 5.5 Tester le harnais

Le harnais est du code, et un harnais silencieusement cassé transforme chaque test qui l'utilise en
faux positif. CustomPerm a un `TestPlayerHarnessTest` qui vérifie que les primitives fonctionnent — le
niveau de permission pilote la visibilité des commandes, le chat est capturé, une resynchronisation
d'arbre de commandes est comptée — avant qu'un test fonctionnel s'appuie dessus.

### 5.6 Capturer la sortie console

Pour la sortie d'une commande qui ne s'adresse pas à un joueur, enveloppez la source de commande du
serveur :

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

`server.createCommandSourceStack()` est de niveau 4 par construction : c'est aussi ainsi qu'on teste
« la console a toujours accès » sans rien construire.

---

## 6. Deux backends, un seul corps de test

CustomPerm doit fonctionner à l'identique avec et sans LuckPerms. Plutôt que de dupliquer 176 tests, le
même corps de test tourne dans les deux modes et c'est la *mise en place* qui s'adapte.

### 6.1 Abstraire le changement d'état, pas l'assertion

```java
public final class Grants implements AutoCloseable {

    public static Grants allow(UUID player, String... nodes) {
        Grants grants = new Grants(player, List.of(nodes), false);
        if (CustomPerm.isLuckPermsActive()) {
            LuckPermsTestSupport.setNodes(player, grants.nodes, true);   // comme /lp user ... permission set
        } else {
            grants.grade().permissions.addAll(grants.nodes);             // un grade dédié
            grants.assign();
        }
        return grants;
    }

    @Override
    public void close() { /* reprendre via le backend actif */ }
}
```

Le test écrit `Grants.allow(player, "mymod.feature")` et affirme sur le comportement. Quel backend l'a
stocké ne regarde pas le test. Notez que `close()` ne retire **que ces nœuds**, pour que plusieurs
`Grants` sur un même joueur s'imbriquent correctement.

### 6.2 Affirmer que le mode est bien celui annoncé

Le mode de défaillance dont ceci protège est grave : si le run LuckPerms perd LuckPerms en silence,
chaque test spécifique à LuckPerms *se saute lui-même et rapporte un succès*. Le run reste vert tout en
ne testant rien.

```java
@GameTest(template = "empty_3x3", timeoutTicks = 100)
public static void backendMatchesTheRunMode(GameTestHelper helper) {
    String mode = System.getProperty("customperm.gametest.luckperms");   // posée dans build.gradle
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

**Si vous ne retenez qu'une chose de ce document, retenez celle-là.** Tout mécanisme de saut
conditionnel a besoin d'un test qui affirme que la condition est bien celle voulue par le run.

### 6.3 Sauter un test

```java
public final class Modes {
    /** Vrai quand le backend interne est actif ; sinon marque le test réussi et renvoie faux. */
    public static boolean internalOnly(GameTestHelper helper) {
        if (!CustomPerm.isLuckPermsActive()) return true;
        helper.succeed();
        return false;
    }

    public static boolean luckPermsOnly(GameTestHelper helper) { /* miroir */ }
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

GameTest n'a pas de verdict « ignoré » : un saut doit donc réussir. C'est exactement la raison d'être
du §6.2.

Conséquence à garder en tête : le total affiché en fin de run ne dit pas combien de tests ont
réellement travaillé. Chez CustomPerm, environ 44 tests se sautent en mode LuckPerms et 19 en mode
interne, pour un même « 176 tests réussis ». Journaliser le nombre de sauts par run rend le chiffre
honnête.

### 6.4 Isoler l'API tierce

Exactement une classe importe l'API LuckPerms (`LuckPermsTestSupport`). Chaque appelant vérifie
d'abord `isLuckPermsActive()`, donc la classe n'est jamais résolue sur un runtime sans LuckPerms et les
GameTests s'y chargent quand même.

Son autre rôle est de faire le pont entre asynchrone et synchrone :

```java
/** Attend à l'horloge murale : les ticks GameTest ne font pas avancer les executors de LuckPerms. */
public static <T> T await(CompletableFuture<T> future) {
    try {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception e) {
        throw new GameTestAssertException("LuckPerms call did not complete within " + TIMEOUT_SECONDS + "s: " + e);
    }
}
```

**Utilisez toujours le `get(timeout, unit)` borné.** Un `get()` non borné sur le thread serveur bloque
tout le run contre un future qui a besoin de ce thread, et le timeout propre à GameTest ne vous sauve
pas puisque le thread est bloqué. Borné, vous obtenez un échec de test avec un message exploitable.

Une découverte de plus qui mérite d'être consignée : LuckPerms charge l'utilisateur pendant la vraie
négociation de connexion, que le joueur de test saute. Sa condition Brigadier refuse alors toute
commande à un utilisateur inconnu, ce qui ressemble à un bug de permission. Le harnais charge
l'utilisateur en amont pour reproduire ce qu'aurait fait une vraie connexion.

### 6.5 Nœuds sondes d'un namespace étranger

Pour tester comment votre mod répond aux requêtes de permission d'*autres* mods, déclarez des nœuds
sous un namespace qui n'est pas le vôtre, avec des valeurs par défaut délibérément opposées :

```java
@EventBusSubscriber(modid = MyMod.MODID)
public final class TestModNodes {
    /** Par défaut vrai : un refus ne peut venir que de mon mod. */
    public static final PermissionNode<Boolean> OPEN =
        new PermissionNode<>("cptest", "probe.open", PermissionTypes.BOOLEAN, (p, u, c) -> true);
    /** Par défaut faux : un octroi ne peut venir que de mon mod. */
    public static final PermissionNode<Boolean> SHUT =
        new PermissionNode<>("cptest", "probe.shut", PermissionTypes.BOOLEAN, (p, u, c) -> false);

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) { event.addNodes(OPEN, SHUT); }
}
```

Des défauts opposés permettent à une assertion de distinguer « a répondu le défaut » de « a répondu une
décision » — sinon un handler qui ne fait rien du tout paraît correct une fois sur deux.

---

## 7. Pièges

Classés par le temps que chacun coûte avant d'être trouvé.

**`No test functions were given!`** — le serveur refuse de démarrer et rien d'autre n'est journalisé.
Causes, dans l'ordre à vérifier : `neoforge.enabledGameTestNamespaces` absente du run ;
`@GameTestHolder(MODID)` manquante, donc le namespace vaut `minecraft` et tout est filtré ; le source
set gameTest absent de `neoForge.mods` ; méthodes non `public static`.

**Structure introuvable** — `@PrefixGameTestTemplate(false)` manquante, donc `empty_3x3` est cherché
comme `myclassname.empty_3x3`. Ou le fichier est sous `data/<modid>/structures/` au lieu de
`structure/`.

**Un test passe seul et échoue dans la suite** — état mutable partagé et pas de `batch`. Donnez-lui-en
un.

**Un test n'échoue qu'en CI** — généralement une question de timing. Un `runAfterDelay` dont le total
dépasse `timeoutTicks`, ou une attente non bornée sur une API asynchrone.

**`NoClassDefFoundError` sur SLF4J ou Gson dans un test JUnit** — le runtime NeoForge n'est pas sur le
classpath de test. Déclarez-les en `testImplementation`.

**Tout le run GameTest se fige** — un `CompletableFuture.get()` non borné sur le thread serveur.

**Un run vert qui n'a rien testé** — voir §6.2.

**Un joueur de test qui reçoit encore des paquets après `/reload`** — après
`MinecraftServer.reloadResources`, un joueur de test resté connecté demeure dans la liste avec un canal
ouvert mais ne reçoit plus rien. Reconnectez les joueurs après un rechargement de data packs. Le
comportement d'un vrai client n'a pas été établi dans CustomPerm ; il est vérifié à la main dans la
procédure de test manuelle. Bon à savoir avant de faire confiance à une assertion qui traverse un
rechargement.

---

## 8. CI

Un job, les deux modes, puis le jar :

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
      # Le code de sortie de la tâche est le nombre de tests requis en échec : le job échoue seul.
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

Les points qui comptent :

- **`gameTestServer` sort avec le nombre de tests requis en échec.** Aucun parsing de log ; un code de
  sortie non nul fait échouer le job.
- **`--no-daemon`** — un démon Gradle en CI gaspille de la mémoire et survit parfois entre les étapes
  en retenant un état périmé.
- **Téléverser les logs en cas d'échec.** Un échec GameTest en CI est quasi indiagnosticable depuis la
  seule sortie console ; la réponse est dans `run/<mode>/logs`.
- **`timeout-minutes`** — sans cela, un run figé consomme toute l'allocation du job.
- **Vérifier le contenu du jar.** Peu coûteux, et cela attrape une régression de `processResources` ou
  d'empaquetage qu'aucun test ne verrait.

`./gradlew build` exécute la suite JUnit via `check` : pas d'étape séparée nécessaire.

---

## 9. Checklist de portage

Pour un mod NeoForge 1.21.x neuf, dans l'ordre :

1. `sourceSets { gameTest { compileClasspath += sourceSets.main.output + configurations.compileClasspath; runtimeClasspath += ... } }`
2. `gameTestImplementation sourceSets.main.output`
3. `neoForge.mods."${mod_id}" { sourceSet sourceSets.main; sourceSet sourceSets.gameTest }`
4. Un run `gameTestServer` avec `neoforge.enabledGameTestNamespaces = mod_id` et son propre `gameDirectory`
5. `src/main/resources/data/<modid>/structure/empty_3x3.nbt` (bloc de structure, une fois)
6. `testImplementation junit-jupiter` + `testRuntimeOnly junit-platform-launcher` + `test { useJUnitPlatform() }`
7. Ajouter `slf4j-api` / `slf4j-simple` / `gson` au classpath de test si le code principal les utilise
8. Une classe `@GameTestHolder(MODID) @PrefixGameTestTemplate(false)`, un `@GameTest` qui appelle
   `helper.succeed()` — confirmer que le run est vert avant d'écrire quoi que ce soit de réel
9. Porter `TestPlayer` (§5), puis un auto-test du harnais (§5.5)
10. Donner à chaque classe de config ou de store un constructeur prenant un chemin racine ou un
    fournisseur de connexion (§3.1, §3.2)
11. S'il y a deux backends ou configurations : une abstraction façon `Grants` (§6.1) **et** l'assertion
    de mode (§6.2)
12. CI : les deux runs, `--no-daemon`, logs téléversés en cas d'échec (§8)

Les étapes 1 à 8 sont le minimum qui produit un dispositif fonctionnel et vert. Tout ce qui suit est ce
qui rend la suite digne d'être maintenue.
