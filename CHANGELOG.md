# Changelog

Toutes les modifications notables de ce projet sont documentées dans ce fichier.

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/)  
Versioning : [Semantic Versioning](https://semver.org/lang/fr/)

---

## [1.0.0] — YANKED

> Ce tag git (`c74b360`) était prématuré : le mod n'était pas encore finalisé
> et ne correspond à aucune release publique stable. La version publiée issue
> du travail de finalisation est **[0.9.0]**.

---

## [0.9.0] — 2026-05-14

Release de finalisation.

Cette version regroupe le travail de finalisation du mod : implémentation des
fonctionnalités prévues, corrections issues des revues, tests, documentation,
benchmarks, procédure de validation manuelle et préparation release.

### Ajouté

- **Configuration et hot-reload**
  - Création automatique de `config/customperm/grades.json`, `aliases.json` et `commands.json`.
  - `ConfigManager` avec snapshot atomique via `AtomicReference`.
  - `/customperm reload` transactionnel : si un fichier JSON est invalide, l'ancien snapshot reste actif.
  - Backups horodatés des fichiers de configuration avec rotation des 3 dernières sauvegardes.
  - Normalisation des fichiers manquants, `{}`, champs inconnus et collections explicitement `null`.

- **Moteur de permissions interne**
  - `PermissionResolver` pur Java, sans dépendance Minecraft/NeoForge.
  - Résolution multi-grades.
  - `DENY` explicite prioritaire sur `ALLOW`.
  - Wildcards `*`, `customperm.command.*`, `customperm.alias.*`.
  - Modèle deny-by-default.

- **Gestion des grades**
  - `/customperm grade create/delete/list`.
  - `/customperm grade addperm/removeperm`.
  - `/customperm grade assign/unassign`.
  - Persistance JSON des grades et assignations joueurs.
  - Déduplication des assignations de grade.
  - Suppression en cascade d'un grade dans les assignations joueurs.

- **Exposition de commandes**
  - `/customperm command add/remove/list`.
  - Commandes non exposées conservant leur comportement vanilla/moddé.
  - Wrapping Brigadier des commandes racine via `CommandTreeRewriter`.
  - Préservation de l'accès opérateur réel op level 2+.
  - Re-synchronisation du command tree client après changement de configuration.

- **Aliases et macros**
  - `/customperm alias add/remove/list`.
  - `/customperm alias addstep/removestep/steps`.
  - Parsing des steps séparés par `;`.
  - Exécution séquentielle des steps avec op level 4.
  - Continuation après erreur d'un step.
  - Ajout/retrait/remplacement d'aliases sur le dispatcher vivant.
  - Protection du nom réservé `customperm`.
  - Warning lorsqu'un alias shadow une commande existante.

- **Intégration LuckPerms**
  - Détection automatique de LuckPerms.
  - Sélection du backend LuckPerms si disponible et compatible.
  - Version minimale LuckPerms `5.4.150+`.
  - Refus cohérent des versions prerelease type `5.4.150-SNAPSHOT`.
  - Fallback vers backend interne si LuckPerms est absent, incompatible ou échoue à l'initialisation.
  - Fallback permanent vers backend interne si LuckPerms devient indisponible pendant un check.
  - Subscription à `UserDataRecalculateEvent` pour renvoyer le command tree au joueur concerné.
  - Blocage des commandes `grade` internes lorsque LuckPerms est actif, avec message d'orientation vers `/lp`.

- **Diagnostics et observabilité**
  - `/customperm status` avec backend, commandes dispatcher, commandes exposées, aliases, grades et utilisateurs avec grade.
  - `/customperm test <player> <node>` avec verdict `GRANTED` / `DENIED`.
  - `/customperm debug <player> <command>` avec rapport dispatcher, exposition, op-level, permission service et décision wrapper.
  - `/customperm scan [pattern]` avec marqueurs `EXPO`, `ALIAS`, `MOD`.
  - Libellé backend centralisé : `Internal`, `LuckPerms`, `Internal — fallback from LuckPerms`.

- **Tests et qualité**
  - Suite JUnit 5 couvrant permissions, config, grades, aliases, LuckPerms versioning et compatibilité JSON.
  - Suite NeoForge GameTest enregistrée dynamiquement.
  - 26 GameTests requis validés.
  - Benchmarks JMH pour `PermissionResolver.resolve()` et lecture concurrente du snapshot config.
  - Baseline performance documentée dans `docs/performance-baseline.md`.
  - Procédure de test manuel HTML sombre dans `docs/manual-test-procedure.html` avec export JSON/Markdown.
  - CI GitHub Actions lançant GameTests, build Gradle et vérification du contenu jar.

- **Documentation release**
  - README anglais et français mis à jour pour lister les fonctionnalités réelles du mod.
  - Documentation du fonctionnement interne : dispatcher wrapping, backend pluggable, re-sync, aliases.
  - Documentation des limites connues.
  - Processus NFR14 de portage NeoForge.

### Corrigé

- Ajout de `AliasesConfig.normalize()` et `CommandsConfig.normalize()` pour éviter les NPE avec JSON explicitement `null`.
- Stack traces conservées lors d'échecs de wrapping dispatcher.
- Les nodes enfants inconnus dans Brigadier sont ignorés avec warning au lieu d'être greffés tels quels dans l'arbre cloné.
- Les commandes exposées ne bypassent plus CustomPerm lorsque le prédicat Brigadier original est always-true.
- `LuckPermsService.hooksReady` rendu `volatile`.
- Logs améliorés pour les échecs fallback et subscription LuckPerms.
- Guard final si la sélection backend aboutit à `permissions == null`.
- Registration GameTest : skip explicite des méthodes `@GameTest` non `public static`.
- `/customperm scan` sanitise le pattern affiché en cas d'absence de résultat.
- `alias addstep customperm` est rejeté comme nom réservé.
- Les aliases tout-séparateurs (`";;;"`) sont couverts par test de régression.
- JMH configuré avec `fork = 3` et benchmarks annotés de façon cohérente.
- CI vérifie la présence de `META-INF/neoforge.mods.toml` et `META-INF/MANIFEST.MF` dans le jar.

### Changé

- `gradle.properties` définit maintenant `mod_version=0.9.0`.
- Les README ne référencent plus d'artefact généré ignoré par Git ; ils pointent vers GitHub Releases.
- `.gitignore` ignore les artefacts locaux d'outillage, exports de tests, logs, builds et média local non référencé.
- `CHANGELOG.md` sépare désormais la base initiale (`0.1.0`) de la version finalisée (`0.9.0`).

### Validation

- `./gradlew clean build --no-daemon` : succès.
- `./gradlew runGameTestServer --no-daemon` : 26/26 GameTests requis passés.
- Jar généré : `customperm-0.9.0.jar`.
- Vérification jar : `META-INF/MANIFEST.MF` et `META-INF/neoforge.mods.toml` présents.
- Tag GitHub `v0.9.0` créé et poussé.

### Compatibilité

| Composant | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ (`[21.1.0,)`) |
| Java | 21 |
| LuckPerms | 5.4.150+ (optionnel) |

---

## [0.1.0] — base initiale

Base initiale du mod avant le travail de structuration et de finalisation.

Cette version correspond au socle existant avant la livraison complète.
Elle sert de base historique, mais elle ne contient pas l'ensemble des garanties,
tests, diagnostics, intégrations et documents livrés en `0.9.0`.

### Inclus

- Structure de projet NeoForge/Gradle.
- Déclaration du mod CustomPerm.
- Premières classes de configuration, permissions, commandes et aliases.
- Première intégration du concept de permissions granulaires pour commandes Minecraft.
- Premiers fichiers README/licence/projet.

### Limites

- Fonctionnalités incomplètes par rapport au périmètre final.
- Couverture de tests incomplète.
- Benchmarks et baseline performance absents.
- CI release jar incomplète.
- Documentation fonctionnelle et procédure de test manuel non finalisées.

---

## Processus de mise à jour vers une nouvelle version NeoForge (NFR14)

Lorsqu'une nouvelle version stable de NeoForge est publiée, la mise à jour doit être livrée
en **moins d'une semaine**. Procédure :

1. Modifier `gradle.properties` à la racine du projet :

   ```properties
   minecraft_version=<nouvelle-version-mc>
   minecraft_version_range=[<nouvelle-version-mc>, <prochaine-majeure>)
   neo_version=<nouvelle-version-neo>
   neo_version_range=[<nouvelle-version-neo-majeure.mineure.0>,)
   mod_version=<nouvelle-version-mod>
   ```

2. Vérifier la compatibilité des API NeoForge à partir des release notes NeoForge.

3. Exécuter la suite complète :

   ```bash
   ./gradlew cleanTest test
   ./gradlew runGameTestServer
   ./gradlew build
   ```

4. Si tous les tests passent, ajouter une entrée dans ce `CHANGELOG.md`.

5. Mettre à jour les badges, versions, liens de téléchargement et exemples dans
   `README.md` et `README.fr.md`.

6. Créer le tag Git correspondant et publier l'artefact via GitHub Releases.

> Note : le mod ne contient pas de couche d'abstraction multi-version. Le refactoring
> multi-version ne doit être introduit que lors d'un portage vers une version majeure
> différente de NeoForge.
