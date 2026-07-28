# NOTICE — CustomPerm

**Copyright (C) 2026 THEFricadelle. All rights reserved.**
SPDX-License-Identifier: `LicenseRef-CustomPerm-ARR`

CustomPerm is **source-available proprietary software**. The source code is
public, but the project is **not open-source**. Reading the code grants you no
right to reuse it.

This file is a plain-language summary for convenience. The binding terms are in
[LICENSE](LICENSE); if the two ever disagree, the LICENSE wins.

## At a glance

| Action | Allowed? |
|--------|----------|
| Download the official build from CurseForge / Modrinth / GitHub Releases | ✅ Yes |
| Run it on your server, any number of players | ✅ Yes |
| Run it on a monetized server (donations, ranks, shop) | ✅ Yes — as long as the mod itself isn't sold or paywalled |
| Read, audit, and review the source code | ✅ Yes |
| Report bugs, open issues | ✅ Yes |
| Fork the repo to submit a pull request | ✅ Yes — see [CONTRIBUTING.md](CONTRIBUTING.md) |
| Ship the mod in your own modpack **after** contributing a PR | ✅ Yes — §5.3, official file only |
| Be credited for a merged contribution | ✅ Yes — [CONTRIBUTORS.md](CONTRIBUTORS.md), §5.3(a) |
| Include it in a CurseForge / Modrinth modpack that **references** the official unmodified file | ✅ Yes, no need to ask |
| Send the official file to players joining **your own** server (launcher / host auto-sync) | ✅ Yes — see §2.2 |
| Mention it factually: "my pack includes CustomPerm", tutorials, reviews | ✅ Yes |
| Bundle the jar in an exported / offline modpack | ❌ Written permission required |
| Offer it as a "one-click install" product in a hosting panel catalogue | ❌ Written permission required |
| Re-upload or mirror it anywhere (sites, forums, Discord, file lockers) | ❌ No |
| Modify it and distribute the result | ❌ No |
| Publish a build made from your fork | ❌ No |
| Reuse its code in another mod, plugin, or project | ❌ No |
| Sell it, rent it, or bundle it with a paid product | ❌ No |
| Claim you wrote it, or remove the copyright notices | ❌ No |
| Use the name, mod id, or logo for another project, or to imply endorsement | ❌ No |
| Use the code to train or fine-tune an AI / machine-learning model | ❌ No |

## About revocation

The right to use the Mod is revocable — but **not arbitrarily**. Revocation is
**individual** (it takes effect only against a specific person or entity, upon
written notice), **prospective** (it never makes past compliant use unlawful),
and it does **not** silently kill a compliant modpack or stop other users from
running an Official Build they lawfully obtained. It is a tool against abuse,
not a kill switch over the ecosystem. See §2.3 of the LICENSE.

## Why source-available and not open-source

The code is public so that server owners can audit what runs on their machines,
so that integration problems can be diagnosed against the real implementation,
and so that anyone who spots a bug can fix it through a pull request.

It is not open-source because the author retains exclusive control over
distribution and derivative works. Visibility is not a license.

## Third-party components

CustomPerm builds against, but does not include or redistribute, the following:

| Component | Role | Licensing |
|-----------|------|-----------|
| [NeoForge](https://neoforged.net/) | Mod loader / framework | Provided by the end user's installation, under its own license |
| [LuckPerms API](https://luckperms.net/) | Optional integration (`compileOnly`) | Provided by the end user's installation, under its own license |
| TesseraUI | Optional client GUI (`compileOnly`) | Provided by the end user's installation, under its own license |
| Brigadier (Mojang) | Command system | Part of Minecraft, under Mojang's terms |

These are compile-time or runtime dependencies resolved on the user's side. No
third-party code is bundled into the CustomPerm jar.

## Requesting permission

Anything marked ❌ above can still be granted case by case. Ask — the answer is
often yes for reasonable requests. Open an issue on the official repository:

  https://github.com/Team-Arcadia/CustomPerm/issues

Permission must be **written** to be valid. Silence is not consent: no reply, or
no objection to a use, never counts as permission. A permission granted in one
case applies to that case only.

**Author: THEFricadelle**

---

# NOTICE — CustomPerm (Version Française)

**Copyright (C) 2026 THEFricadelle. Tous droits réservés.**
SPDX-License-Identifier: `LicenseRef-CustomPerm-ARR`

CustomPerm est un **logiciel propriétaire à source visible**. Le code source est
public, mais le projet n'est **pas open-source**. Lire le code ne vous donne
aucun droit de le réutiliser.

Ce fichier est un résumé en langage clair, fourni par commodité. Les conditions
contraignantes se trouvent dans [LICENSE](LICENSE) ; en cas de divergence, la
LICENSE prévaut.

## En un coup d'œil

| Action | Autorisé ? |
|--------|-----------|
| Télécharger le build officiel depuis CurseForge / Modrinth / GitHub Releases | ✅ Oui |
| L'exécuter sur votre serveur, quel que soit le nombre de joueurs | ✅ Oui |
| L'exécuter sur un serveur monétisé (dons, grades, boutique) | ✅ Oui — tant que le mod lui-même n'est ni vendu ni derrière un paywall |
| Lire, auditer et relire le code source | ✅ Oui |
| Signaler des bugs, ouvrir des issues | ✅ Oui |
| Forker le dépôt pour soumettre une pull request | ✅ Oui — voir [CONTRIBUTING.md](CONTRIBUTING.md) |
| Diffuser le mod dans votre propre modpack **après** avoir contribué une PR | ✅ Oui — §5.3, fichier officiel uniquement |
| Être crédité pour une contribution fusionnée | ✅ Oui — [CONTRIBUTORS.md](CONTRIBUTORS.md), §5.3(a) |
| L'inclure dans un modpack CurseForge / Modrinth qui **référence** le fichier officiel non modifié | ✅ Oui, sans demander |
| Transmettre le fichier officiel aux joueurs rejoignant **votre propre** serveur (auto-sync launcher / hébergeur) | ✅ Oui — voir §2.2 |
| Le mentionner factuellement : « mon pack inclut CustomPerm », tutoriels, tests | ✅ Oui |
| Empaqueter le jar dans un modpack exporté / hors-ligne | ❌ Autorisation écrite requise |
| Le proposer en « installation en un clic » dans le catalogue d'un hébergeur | ❌ Autorisation écrite requise |
| Le ré-uploader ou le mirrorer ailleurs (sites, forums, Discord, hébergeurs de fichiers) | ❌ Non |
| Le modifier et en distribuer le résultat | ❌ Non |
| Publier un build issu de votre fork | ❌ Non |
| Réutiliser son code dans un autre mod, plugin ou projet | ❌ Non |
| Le vendre, le louer, ou le lier à un produit payant | ❌ Non |
| Prétendre l'avoir écrit, ou retirer les mentions de copyright | ❌ Non |
| Utiliser le nom, le mod id ou le logo pour un autre projet, ou pour suggérer une caution | ❌ Non |
| Utiliser le code pour entraîner ou affiner un modèle d'IA / d'apprentissage automatique | ❌ Non |

## À propos de la révocation

Le droit d'utiliser le mod est révocable — mais **pas arbitrairement**. La
révocation est **individuelle** (elle ne prend effet qu'à l'encontre d'une
personne ou entité déterminée, sur notification écrite), **non rétroactive**
(elle ne rend jamais illicite un usage passé conforme), et elle ne tue **pas**
silencieusement un modpack conforme ni n'empêche les autres utilisateurs
d'exécuter un build officiel obtenu licitement. C'est un outil contre l'abus,
pas un interrupteur sur l'écosystème. Voir §2.3 de la LICENSE.

## Pourquoi source visible et pas open-source

Le code est public pour que les propriétaires de serveurs puissent auditer ce
qui tourne sur leurs machines, pour que les problèmes d'intégration soient
diagnostiqués face à l'implémentation réelle, et pour que quiconque repère un
bug puisse le corriger via une pull request.

Ce n'est pas open-source parce que l'auteur conserve le contrôle exclusif de la
distribution et des œuvres dérivées. La visibilité n'est pas une licence.

## Composants tiers

CustomPerm compile contre les composants suivants, sans les inclure ni les
redistribuer :

| Composant | Rôle | Licence |
|-----------|------|---------|
| [NeoForge](https://neoforged.net/) | Mod loader / framework | Fourni par l'installation de l'utilisateur final, sous sa propre licence |
| [LuckPerms API](https://luckperms.net/) | Intégration optionnelle (`compileOnly`) | Fourni par l'installation de l'utilisateur final, sous sa propre licence |
| TesseraUI | GUI client optionnelle (`compileOnly`) | Fourni par l'installation de l'utilisateur final, sous sa propre licence |
| Brigadier (Mojang) | Système de commandes | Partie de Minecraft, sous les conditions de Mojang |

Ce sont des dépendances de compilation ou d'exécution résolues côté utilisateur.
Aucun code tiers n'est empaqueté dans le jar CustomPerm.

## Demander une autorisation

Tout ce qui est marqué ❌ ci-dessus peut malgré tout être accordé au cas par cas.
Demandez — la réponse est souvent oui pour les demandes raisonnables. Ouvrez une
issue sur le dépôt officiel :

  https://github.com/Team-Arcadia/CustomPerm/issues

L'autorisation doit être **écrite** pour être valable. Le silence ne vaut pas
accord : l'absence de réponse, ou l'absence d'objection à un usage, ne constitue
jamais une autorisation. Une autorisation accordée dans un cas ne vaut que pour
ce cas.

**Author: THEFricadelle**
