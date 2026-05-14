# Changelog

Toutes les modifications notables de ce projet sont documentées dans ce fichier.

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/)
Versioning : [Semantic Versioning](https://semver.org/lang/fr/)

---

## [1.0.0] — YANKED

> Ce tag git (`c74b360`) était prématuré — le mod n'était pas encore finalisé (suite de tests
> incomplète, benchmarks absents). Il ne correspond à aucune release publique.
> La première version publique officielle est **[0.1.0]** ci-dessous.

---

## [0.1.0] — 2026-05-12

Version initiale publique de CustomPerm.

### Ajouté

- **Système de permissions granulaires** — expose des commandes vanilla individuelles à des joueurs non-op via `/customperm command add/remove/list`
- **Moteur de résolution RBAC multi-grades** — `PermissionResolver.resolve()` : DENY explicite > ALLOW, union de grades, wildcard `customperm.command.*`, court-circuit OP (INVARIANT-101, INVARIANT-201)
- **Système de grades JSON interne** — CRUD grades/permissions/assignations joueurs sans dépendance externe
- **Intégration LuckPerms (soft-dependency)** — détection automatique, fallback transparent sur backend interne si LP absent ou version < 5.4.150
- **Synchronisation automatique** — rechargement de l'arbre de commandes du joueur après modification de permissions via LP (`UserDataRecalculateEvent`)
- **Système d'alias/macros** — commandes personnalisées chaînant plusieurs sous-commandes, exécutées avec élévation op-4
- **Hot-reload atomique** — `/customperm reload` recharge les trois fichiers JSON (`grades.json`, `aliases.json`, `commands.json`) sans interruption de service ; rollback sur config invalide (INVARIANT-401)
- **Backup automatique** — rotation des 3 dernières sauvegardes avant chaque rechargement (AR10)
- **Outils de diagnostic** — `/customperm debug`, `/customperm test`, `/customperm status`, `/customperm scan`
- **Suite de tests** — 11 tests unitaires JUnit 5, 10+ GameTests NeoForge, microbenchmarks JMH (NFR1/NFR4/AR1 validés)
- **Baseline de performance documentée** — `docs/performance-baseline.md` : `PermissionResolver.resolve()` ≈ 185 ns/op (seuil NFR1 : < 5 ms)

### Compatibilité

| Composant | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.221+ (`[21.1.0,)`) |
| Java | 21 |
| LuckPerms | 5.4.150+ (optionnel) |

---

## Processus de mise à jour vers une nouvelle version NeoForge (NFR14)

Lorsqu'une nouvelle version stable de NeoForge est publiée, la mise à jour doit être livrée
en **moins d'une semaine**. Procédure :

1. Modifier `gradle.properties` à la racine du projet — **toutes ces valeurs en une seule passe** :

   ```properties
   # Versions NeoForge / Minecraft
   minecraft_version=<nouvelle-version-mc>
   minecraft_version_range=[<nouvelle-version-mc>, <prochaine-majeure>)
   neo_version=<nouvelle-version-neo>
   neo_version_range=[<nouvelle-version-neo-majeure.mineure.0>,)
   # Numéro de version du mod (fait partie du nom du jar produit)
   mod_version=<nouvelle-version-mod>
   ```

2. Vérifier la compatibilité des API NeoForge (consulter les release notes NeoForge).

3. Exécuter la suite complète :

   ```bash
   ./gradlew cleanTest test          # Tests unitaires JUnit 5
   ./gradlew runGameTestServer       # GameTests NeoForge (serveur de test)
   ./gradlew build                   # Produit build/libs/customperm-<mod_version>.jar
   ```

4. Si tous les tests passent, ajouter une entrée dans ce `CHANGELOG.md`.

5. Mettre à jour les badges et références de version dans `README.md` et `README.fr.md`
   (`version-<mod_version>`, liens de téléchargement, exemples de jar).

6. Distribuer le nouveau jar depuis `build/libs/`.

> **Note :** Le mod ne contient aucune couche d'abstraction multi-version. Le refactoring
> est introduit uniquement lors d'un portage vers une version **majeure** différente
> de NeoForge (AR — architecture decision 4.2).
