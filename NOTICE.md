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
| Redistribute it, or authorize someone else to, because you are a team or organization member | ❌ No — §1, membership grants no permission |

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

## Ownership and maintenance

CustomPerm is maintained by Team-Arcadia. Copyright in the mod is held by
**THEFricadelle** alone, who is the only party able to grant, withhold, or
withdraw any permission under the LICENSE.

Where the repository is hosted under an organization or a team account, that
hosting transfers nothing: being a member, a maintainer, or an administrator of
that organization gives no right to redistribute the mod, publish a build of it,
or authorize a third party to do either. Team members who contribute do so as
contributors, on the same terms as anyone else (§5.2). See §1 of the
[LICENSE](LICENSE).

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

## License history by version

Each release of CustomPerm is governed by the license distributed with that
release. A later license never applies retroactively: a build you obtained
lawfully stays available to you on the terms it shipped with. The proprietary
terms in [LICENSE](LICENSE) were adopted on 2026-07-14, after 1.0.5 was
published, and therefore govern no released build yet.

| Version | Published | License of the release | Corresponding source |
|---------|-----------|------------------------|----------------------|
| 0.1.0, 0.9.0, 1.0.0 | 2026-05-10 → 05-18 | See the note below | `v0.1.0`, `v0.9.0`, `v1.0.0` |
| 1.0.2 | 2026-06-05 | MIT | `v1.0.2` |
| 1.0.3 | 2026-06-10 | MIT | `v1.0.3` |
| 1.0.4 | 2026-06-11 | `GPL-3.0-only` | `v1.0.4` |
| 1.0.5 | 2026-07-08 | `GPL-3.0-only` | `v1.0.5` |
| 1.0.6 onward | not yet released | `LicenseRef-CustomPerm-ARR` | — |

**Note on 0.1.0, 0.9.0 and 1.0.0.** Those three releases carried two notices
that did not agree: the `LICENSE` file held the GPL-3.0 text, while the jar
metadata declared `MIT`. This was a packaging mistake, not a deliberate dual
licensing. The author does not contest the more permissive reading: whoever
received one of those builds may rely on the MIT terms.

**Note on 1.0.5.** The build published on 2026-07-08 declared `GPL-3.0-only`
and was distributed on those terms. Its corresponding source is the `v1.0.5`
tag, which includes the `client/` and `network/` packages present in that jar.
Those packages were added to the repository before the release but had not been
tagged, which left the published binary without a source reference; the tag
closes that gap.

## MIT-licensed portions

Parts of CustomPerm were contributed before 2026-07-14, while the project was
distributed under the MIT License. Those portions entered the codebase under
MIT, and they remain available under those terms. The MIT License permits
sublicensing, which is why they may be distributed as part of the proprietary
mod; it also requires the notice below to be preserved, which is the purpose of
this section.

The portions concerned are the contributions merged through pull request #1,
commits `123650a` and `53556dc`, authored by **curveo**. They affect
`CustomPerm.java`, `perm/LuckPermsService.java`, `command/AliasManager.java`,
`command/CommandTreeRewriter.java`, `command/CustomPermCommand.java`,
`config/ConfigManager.java`, and `docs/LUCKPERMS_COMPATIBILITY_AUDIT.md`.

The MIT License as it stood in the repository at that time:

```
MIT License

Copyright (c) 2026 THEFricadelle

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The rest of the software is governed by [LICENSE](LICENSE), the proprietary
All Rights Reserved terms. This section preserves a notice owed for the
portions identified above; it extends no permission to anything else, and does
not place the mod as a whole under the MIT License.

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
| Le redistribuer, ou autoriser quelqu'un à le faire, au motif que vous êtes membre de l'équipe ou de l'organisation | ❌ Non — §1, l'appartenance ne donne aucune autorisation |

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

## Propriété et maintenance

CustomPerm est maintenu par Team-Arcadia. Les droits d'auteur sur le mod sont
détenus par **THEFricadelle** seul, unique partie en mesure d'accorder, de
refuser ou de retirer une autorisation au titre de la LICENSE.

Lorsque le dépôt est hébergé sous une organisation ou un compte d'équipe, cet
hébergement ne transfère rien : être membre, mainteneur ou administrateur de
cette organisation ne donne aucun droit de redistribuer le mod, d'en publier un
build, ni d'autoriser un tiers à le faire. Les membres de l'équipe qui
contribuent le font en tant que contributeurs, aux mêmes conditions que
n'importe qui d'autre (§5.2). Voir §1 de la [LICENSE](LICENSE).

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

## Historique des licences par version

Chaque version de CustomPerm est régie par la licence distribuée avec elle. Une
licence ultérieure ne s'applique jamais rétroactivement : un build obtenu
licitement reste disponible selon les termes livrés avec lui. Les termes
propriétaires de [LICENSE](LICENSE) ont été adoptés le 2026-07-14, après la
publication de la 1.0.5, et ne régissent donc encore aucun build publié.

| Version | Publiée | Licence de la version | Source correspondante |
|---------|---------|-----------------------|-----------------------|
| 0.1.0, 0.9.0, 1.0.0 | 2026-05-10 → 05-18 | Voir la note ci-dessous | `v0.1.0`, `v0.9.0`, `v1.0.0` |
| 1.0.2 | 2026-06-05 | MIT | `v1.0.2` |
| 1.0.3 | 2026-06-10 | MIT | `v1.0.3` |
| 1.0.4 | 2026-06-11 | `GPL-3.0-only` | `v1.0.4` |
| 1.0.5 | 2026-07-08 | `GPL-3.0-only` | `v1.0.5` |
| 1.0.6 et suivantes | pas encore publiée | `LicenseRef-CustomPerm-ARR` | — |

**Note sur 0.1.0, 0.9.0 et 1.0.0.** Ces trois versions portaient deux mentions
divergentes : le fichier `LICENSE` contenait le texte GPL-3.0, tandis que les
métadonnées du jar déclaraient `MIT`. Il s'agit d'une erreur d'empaquetage, pas
d'une double licence délibérée. L'auteur ne conteste pas la lecture la plus
permissive : quiconque a reçu l'un de ces builds peut se prévaloir des termes
MIT.

**Note sur la 1.0.5.** Le build publié le 2026-07-08 déclarait `GPL-3.0-only` et
a été distribué à ces conditions. Sa source correspondante est le tag `v1.0.5`,
qui contient les paquets `client/` et `network/` présents dans ce jar. Ces
paquets avaient été ajoutés au dépôt avant la publication mais n'avaient pas été
taggés, ce qui laissait le binaire publié sans référence de source ; le tag
comble ce manque.

## Portions sous licence MIT

Certaines parties de CustomPerm ont été contribuées avant le 2026-07-14, alors
que le projet était distribué sous licence MIT. Ces portions sont entrées dans
le code sous MIT et restent disponibles à ces conditions. La licence MIT
autorise la sous-licence, ce qui permet de les distribuer au sein du mod
propriétaire ; elle impose en contrepartie de conserver la mention ci-dessous,
et c'est l'objet de cette section.

Les portions concernées sont les contributions fusionnées via la pull request
#1, commits `123650a` et `53556dc`, écrites par **curveo**. Elles touchent
`CustomPerm.java`, `perm/LuckPermsService.java`, `command/AliasManager.java`,
`command/CommandTreeRewriter.java`, `command/CustomPermCommand.java`,
`config/ConfigManager.java` et `docs/LUCKPERMS_COMPATIBILITY_AUDIT.md`.

La licence MIT telle qu'elle figurait alors dans le dépôt :

```
MIT License

Copyright (c) 2026 THEFricadelle

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

Le reste du logiciel est régi par [LICENSE](LICENSE), les conditions
propriétaires Tous droits réservés. Cette section préserve une mention due au
titre des portions identifiées ci-dessus ; elle n'étend aucune autorisation
au-delà, et ne place pas le mod dans son ensemble sous licence MIT.

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
