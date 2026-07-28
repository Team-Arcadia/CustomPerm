# Contributing to CustomPerm

Thanks for wanting to help. CustomPerm is **source-available proprietary
software** — the code is public so you can read it, audit it, and help fix it,
but it is **not** open-source. This document explains exactly what you may and
may not do.

Read [LICENSE](LICENSE) for the binding terms. This file is a plain-language
guide, not a substitute for it.

## What you may do

- **Read, audit, and review** the full source code.
- **Open an issue** to report a bug, a crash, or a compatibility problem.
- **Fork the repository and open a pull request** — this is explicitly permitted
  by Section 5.1 of the LICENSE, under the conditions below.

## What you may not do

- **Publish or release any build made from your fork** — no jars, no source
  archives, no "my improved version" on CurseForge, Modrinth, Discord, or
  anywhere else.
- **Rebrand the fork** into a separate or competing project, or change the mod
  name, mod id, or branding.
- **Reuse the source code** — in whole or in part, verbatim or adapted — inside
  another project, mod, plugin, or product.
- **Claim authorship** of CustomPerm or any part of it.
- **Remove or alter** copyright, authorship, or license notices, including the
  SPDX headers at the top of source files.

The fork permission exists for one reason: letting you submit a pull request.
Once your PR is merged, closed, or abandoned, that permission covers nothing
further beyond keeping the fork as a historical record. A PR counts as
abandoned after **90 consecutive days** without a commit, comment, or other
activity from you, or if you say you are no longer pursuing it.

Using AI-assisted developer tooling while writing your contribution is fine —
the §3(h) restriction targets using the codebase as training data, not your
editor.

## Contributor terms (important)

By submitting a pull request, patch, or code suggestion, you agree that:

1. You grant THEFricadelle a perpetual, worldwide, irrevocable, royalty-free,
   sublicensable and transferable license to use, modify, relicense, and
   distribute your contribution as part of CustomPerm, under this or any other
   license.
2. You are the author of the contribution and have the right to submit it.
3. Your contribution contains no third-party code you are not entitled to
   submit.
4. You keep the copyright on your own contribution, but submitting it gives you
   **no ownership, co-authorship, or any other right over CustomPerm itself**.
5. Where the law allows it, you waive your moral rights in the contribution as
   against the author; where it does not, you agree not to assert them in a way
   that would block the license above. In return, your contribution will never
   be misattributed to someone else.

## What you get in return

Section 5.3 of the LICENSE gives every contributor two things:

- **Credit** in [CONTRIBUTORS.md](CONTRIBUTORS.md), which is not withdrawn later
  for any reason. Ask via the issue tracker if you want a different name or
  handle, no contact address, or no listing at all.
- **The modpack permission**, confirmed explicitly: once your PR has concluded,
  you may ship CustomPerm in a modpack you publish — referencing CurseForge or
  Modrinth, unmodified official file, notices preserved. Having forked the repo
  never costs you this.

You still may not ship a build made from your own fork. The permission covers
the official file only.

That credit is recognition of your work — it does not make you a co-owner or
co-maintainer of the project.

If you do not agree with these terms, do not submit a pull request — open an
issue describing the problem instead. That is just as useful.

## Contributions that are welcome

| Type | Welcome | Notes |
|------|---------|-------|
| Bug fixes | ✅ Yes | The best kind of PR. Include reproduction steps. |
| Crash / NPE fixes | ✅ Yes | Attach the crash report or stack trace. |
| Compatibility fixes | ✅ Yes | LuckPerms, TesseraUI, other mods. |
| Performance improvements | ✅ Yes | Explain the measurement, not just the theory. |
| Typos, localization fixes | ✅ Yes | Small and easy to merge. |
| Documentation corrections | ✅ Yes | README, guides, comments. |
| New features | ⚠️ Ask first | Open an issue before writing code — I may already have a design or a reason to refuse it. |
| Refactors / restyling | ⚠️ Ask first | Large diffs with no behavior change are usually rejected. |
| Dependency or build changes | ⚠️ Ask first | Affects distribution and the release pipeline. |

## How to submit a pull request

1. **Open an issue first** for anything beyond a small fix — it avoids wasted
   work on both sides.
2. **Fork** the repository and branch from `dev` (never from `main`).
3. **Name the branch** `fix/short-description` or `feat/short-description`.
4. **Write the code** following the conventions below.
5. **Build and test**:
   ```bash
   ./gradlew build
   ./gradlew runGameTestServer
   ```
   Both must pass. A PR that does not build will not be reviewed.
6. **Commit** with a conventional message: `fix: prevent NPE when grade is null`.
7. **Open the pull request against `dev`**, describing what it fixes and how you
   verified it.

## Code conventions

- **Language**: all code, identifiers, comments, and log messages in **English**.
- **Naming**: `PascalCase` for classes, `camelCase` for methods and fields,
  `UPPER_SNAKE_CASE` for constants.
- **Comments**: minimal and in English — explain *why*, not *what*.
- **SPDX headers**: keep the existing header on every source file. New files
  must carry the same header.
- **No version bumps**: never change `mod_version` in `gradle.properties`.
  Releases are handled by the author.
- **No new dependencies** without asking first.
- **Scope**: one logical change per pull request.

## Reporting a bug

Include, at minimum:

- CustomPerm version, Minecraft version, NeoForge version.
- Whether LuckPerms / TesseraUI are installed, and their versions.
- Steps to reproduce.
- The relevant log excerpt or crash report (use a paste service for long logs).

## Contact

For redistribution requests, modpack permission beyond what the LICENSE already
allows, or anything else not covered here, open an issue on the official
repository:

  https://github.com/Team-Arcadia/CustomPerm/issues

**Author: THEFricadelle**

---

# Contribuer à CustomPerm (Version Française)

Merci de vouloir aider. CustomPerm est un **logiciel propriétaire à source
visible** — le code est public pour que vous puissiez le lire, l'auditer et
aider à le corriger, mais il n'est **pas** open-source. Ce document explique
précisément ce que vous pouvez et ne pouvez pas faire.

Lisez [LICENSE](LICENSE) pour les conditions contraignantes. Ce fichier est un
guide en langage clair, pas un substitut.

## Ce que vous pouvez faire

- **Lire, auditer et relire** l'intégralité du code source.
- **Ouvrir une issue** pour signaler un bug, un crash ou un problème de
  compatibilité.
- **Forker le dépôt et ouvrir une pull request** — c'est explicitement autorisé
  par la Section 5.1 de la LICENSE, dans les conditions ci-dessous.

## Ce que vous ne pouvez pas faire

- **Publier ou diffuser un build issu de votre fork** — aucun jar, aucune
  archive source, aucune « version améliorée » sur CurseForge, Modrinth, Discord
  ou ailleurs.
- **Renommer/rebrander le fork** en projet séparé ou concurrent, ni modifier le
  nom du mod, son mod id ou son identité visuelle.
- **Réutiliser le code source** — en tout ou partie, tel quel ou adapté — dans
  un autre projet, mod, plugin ou produit.
- **Revendiquer la paternité** de CustomPerm ou d'une quelconque de ses parties.
- **Supprimer ou altérer** les mentions de copyright, de paternité ou de
  licence, y compris les en-têtes SPDX en haut des fichiers source.

L'autorisation de fork existe pour une seule raison : vous permettre de soumettre
une pull request. Une fois votre PR fusionnée, fermée ou abandonnée, cette
autorisation ne couvre plus rien d'autre que la conservation du fork comme
archive. Une PR est réputée abandonnée après **90 jours consécutifs** sans
commit, commentaire ou autre activité de votre part, ou si vous déclarez ne plus
la poursuivre.

Utiliser des outils de développement assistés par IA pour rédiger votre
contribution ne pose aucun problème — la restriction du §3(h) vise l'usage du
code comme données d'entraînement, pas votre éditeur.

## Conditions applicables aux contributeurs (important)

En soumettant une pull request, un patch ou une suggestion de code, vous
acceptez que :

1. Vous accordez à THEFricadelle une licence perpétuelle, mondiale, irrévocable,
   gratuite, sous-licenciable et transférable pour utiliser, modifier,
   relicencier et distribuer votre contribution au sein de CustomPerm, sous
   cette licence ou toute autre.
2. Vous êtes l'auteur de la contribution et avez le droit de la soumettre.
3. Votre contribution ne contient aucun code tiers que vous n'auriez pas le
   droit de soumettre.
4. Vous conservez le copyright sur votre propre contribution, mais la soumettre
   ne vous donne **aucun droit de propriété, de co-paternité ou autre sur
   CustomPerm lui-même**.
5. Dans la limite permise par la loi, vous renoncez à vos droits moraux sur la
   contribution à l'égard de l'auteur ; à défaut, vous vous engagez à ne pas les
   invoquer d'une manière qui ferait obstacle à la licence ci-dessus. En
   contrepartie, votre contribution ne sera jamais attribuée à un tiers.

## Ce que vous obtenez en retour

La Section 5.3 de la LICENSE accorde deux choses à tout contributeur :

- **Le crédit** dans [CONTRIBUTORS.md](CONTRIBUTORS.md), qui n'est retiré
  ultérieurement pour aucun motif. Demandez via le tracker d'issues si vous
  souhaitez un autre nom ou pseudonyme, aucune adresse de contact, ou aucune
  mention du tout.
- **La permission modpack**, confirmée explicitement : une fois votre PR
  terminée, vous pouvez diffuser CustomPerm dans un modpack que vous publiez —
  en référençant CurseForge ou Modrinth, fichier officiel non modifié, mentions
  préservées. Avoir forké le dépôt ne vous en prive jamais.

Vous ne pouvez toujours pas diffuser un build issu de votre propre fork. La
permission ne couvre que le fichier officiel.

Ce crédit est une reconnaissance de votre travail — il ne fait pas de vous un
copropriétaire ni un co-mainteneur du projet.

Si vous n'acceptez pas ces conditions, ne soumettez pas de pull request —
ouvrez plutôt une issue décrivant le problème. C'est tout aussi utile.

## Contributions bienvenues

| Type | Bienvenue | Notes |
|------|-----------|-------|
| Corrections de bugs | ✅ Oui | Le meilleur type de PR. Incluez les étapes de reproduction. |
| Corrections de crash / NPE | ✅ Oui | Joignez le rapport de crash ou la stack trace. |
| Corrections de compatibilité | ✅ Oui | LuckPerms, TesseraUI, autres mods. |
| Améliorations de performance | ✅ Oui | Expliquez la mesure, pas seulement la théorie. |
| Fautes de frappe, localisation | ✅ Oui | Petit et facile à fusionner. |
| Corrections de documentation | ✅ Oui | README, guides, commentaires. |
| Nouvelles fonctionnalités | ⚠️ Demandez avant | Ouvrez une issue avant de coder — j'ai peut-être déjà une conception ou une raison de refuser. |
| Refactorisations / restylage | ⚠️ Demandez avant | Les gros diffs sans changement de comportement sont généralement refusés. |
| Changements de dépendances / build | ⚠️ Demandez avant | Impacte la distribution et le pipeline de release. |

## Comment soumettre une pull request

1. **Ouvrez d'abord une issue** pour tout ce qui dépasse une petite correction —
   cela évite du travail perdu des deux côtés.
2. **Forkez** le dépôt et créez une branche depuis `dev` (jamais depuis `main`).
3. **Nommez la branche** `fix/description-courte` ou `feat/description-courte`.
4. **Écrivez le code** en suivant les conventions ci-dessous.
5. **Compilez et testez** :
   ```bash
   ./gradlew build
   ./gradlew runGameTestServer
   ```
   Les deux doivent passer. Une PR qui ne compile pas ne sera pas relue.
6. **Committez** avec un message conventionnel : `fix: prevent NPE when grade is null`.
7. **Ouvrez la pull request vers `dev`**, en décrivant ce qu'elle corrige et
   comment vous l'avez vérifié.

## Conventions de code

- **Langue** : tout le code, les identifiants, les commentaires et les messages
  de log en **anglais**.
- **Nommage** : `PascalCase` pour les classes, `camelCase` pour les méthodes et
  champs, `UPPER_SNAKE_CASE` pour les constantes.
- **Commentaires** : minimalistes et en anglais — expliquez le *pourquoi*, pas
  le *quoi*.
- **En-têtes SPDX** : conservez l'en-tête existant sur chaque fichier source.
  Les nouveaux fichiers doivent porter le même en-tête.
- **Aucun changement de version** : ne modifiez jamais `mod_version` dans
  `gradle.properties`. Les releases sont gérées par l'auteur.
- **Aucune nouvelle dépendance** sans demander au préalable.
- **Périmètre** : un seul changement logique par pull request.

## Signaler un bug

Incluez au minimum :

- La version de CustomPerm, de Minecraft et de NeoForge.
- Si LuckPerms / TesseraUI sont installés, et leurs versions.
- Les étapes de reproduction.
- L'extrait de log pertinent ou le rapport de crash (utilisez un service de
  paste pour les logs volumineux).

## Contact

Pour toute demande de redistribution, d'autorisation modpack au-delà de ce que
la LICENSE permet déjà, ou tout autre point non couvert ici, ouvrez une issue
sur le dépôt officiel :

  https://github.com/Team-Arcadia/CustomPerm/issues

**Author: THEFricadelle**
