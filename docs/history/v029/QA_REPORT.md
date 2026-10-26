# Rapport QA — FranchiseGuide V0.29.0

## Base et contexte de reprise

Base vérifiée : ZIP V0.28 fourni, SHA-256 `8b594ed2a0e058951d14a9d48de689618f17d2ec6ed233c1ebe1aaab92e85eeb`. Le dossier Drive V0.29 contenait seulement un guide d’une tentative interrompue. Aucune archive V0.29 disponible n’a été supposée complète : cette passe a réellement repris les sources V0.28.

Scénarios visés : **recherche multifornisseur → réponse par source → reprise de la page en erreur → résultats conservés ; import → préparation/identité/dates → application ; déconnexion Trakt → invalidation réseau → aucune ancienne réponse appliquée au nouveau compte.**

## Résultats finaux disponibles

La commande `python3 tools/run_available_checks.py` termine avec **66 commandes réussies, aucun échec**, code0. Ce runner ne substitue pas ses dépendances disponibles aux tests bloqués : les exclusions sont enregistrées dans `tests/results/v029/available-qa.json`.

| Groupe exécuté | Résultat | Portée |
|---|---:|---|
| JavaScript / Node | **1 352 tests réussis** | Dont42 nouveaux ; sans les deux suites dépendant de jsdom/fake-indexeddb |
| Chromium DOM | **1 244 assertions réussies** | Dont49 nouveaux scénarios ;21 scripts, vrais scripts/DOM/CSS, fournisseurs/images/stockage contrôlés |
| Kotlin / JVM | **858 assertions réussies** | Dont24 nouvelles ; règles exécutées sur JVM, pas dans Android |
| Franchises | **1 scénario réussi** | Identités/suggestions des quatre médias |
| Comparaisons JavaScript/Kotlin | **11 réussies** | Compteurs d’import, calendrier, disponibilités, listes, univers, épisodes, détails publics et éditions/ISBN hérités |
| PC compilé | **105 contrôles HTTP +9 de démarrage réussis** | Vrai JAR compilé Java17, services locaux et ressources |
| Passerelles Java | **333 assertions réussies** | Steam, RetroAchievements et services automatiques, fournisseurs simulés |
| Structure | **173 contrôles réussis** | XML, scripts, modules, versions et raccordements ; ce n’est pas un test de bout en bout |
| Bootstrap | **8 contrôles réussis** | Téléchargement de build corrompu refusé, quelques contrôles PowerShell statiques |
| Kotlin non graphique | **Compilation contre signatures réussie** | Modèles, clients, codecs/repository contre stubs ; **ni parser Android réel ni Compose** |

Le journal final des commandes est `tests/results/v029/final-driver.log`. Le résumé détaillé par groupe est `SUMMARY.json`. Les rapports de versions précédentes dans docs/history ou tests/results/v0xx sont des traces historiques ; ils ne sont pas additionnés une seconde fois.

## Trois régressions reproduites sur le code V0.28 non modifié

`node tests/baseline_v028_reproductions.cjs` charge les témoins extraits du ZIP sous `tests/fixtures/v029-baseline/` et compare les implémentations. **Ces trois comparaisons complémentaires sont distinctes des1352 tests Node.**

1. **Dates importées perdues.** Une lecture avec une date et une nouvelle date importée étaient réduites à une seule date avant d’augmenter le compteur. Après correction, les deux dates sont conservées et le compteur devient2. Aucun autre événement n’est inventé.
2. **Réponse catalogue sans tableau.** Un objet vide TMDb était annoncé comme recherche complète et vide. Après correction, le fournisseur est en erreur, sans faux résultat vide réussi.
3. **Fusion de références publiques incompatibles.** Deux séries TVmaze aux IDs différents pouvaient être autorisées à fusionner. Après correction, ce conflit explicite bloque la fusion.

Sortie complète : `before-after-reproductions.json`. Les témoins ne sont pas utilisés par le runtime et conservent leur provenance et leurs empreintes.

## Nouveaux scénarios contrôlés

### Recherche

Échec d’un catalogue au milieu des succès des autres, pagination indépendante, arrêt des sources épuisées, reprise uniquement des sources en erreur, délai429, réponse malformed distincte du résultat vide, résultats locaux par60, recherche sans accents/non latine et référence retirée retrouvable pour réajout. Changement de texte/catégorie, fermeture puis réouverture rapide, annulation réelle de signal et retrait d’une requête en attente empêchent les résultats obsolètes de remplacer la recherche courante.

### Réimport et identité

Dates distinctes d’un film ou épisode réunies avant normalisation, notes et score locaux conservés, réimport idempotent, identité TVmaze exacte, contradictions TMDb/TVDB/source refusées pendant la préparation. Les tests de l’importeur exécutent les fonctions de production dans un contexte VM qui interdit stockage, rendu ou sauvegarde pendant le staging.

### Trakt

Réponse401 après déconnexion sans nouveau renouvellement ni second appel ; ancien refresh qui ne peut ni restaurer son token ni libérer le verrou d’un autre refresh ; pagination ancienne interdite après changement de compte ; ancienne réponse POST et boucle de synchro sans mutation de file ni des erreurs du compte suivant. Le journal d’un historique envoyé mais non confirmé reste incertain : aucun succès distant réel n’est inventé.

### Audit des doublons et affichage

Identités TVmaze/Apple/FreeToGame reconnues, formes Open Library équivalentes, titres Unicode non fusionnés vers une chaîne vide, analyse bornée explicitement partielle, sans fusion automatique. Nouvel écran de recherche contrôlé à390,768,1360pixels, fermeture par Échap et absence de débordement horizontal dans les cas testés. Captures `fixture-search-reliability-*.png`, images et titres fictifs contrôlés ; aucune identité de personne réelle recherchée dans ces captures.

## Tentatives initiales conservées

La première campagne disponible comportait **un groupe Node en erreur** parce que le contexte VM hérité d’un test d’import ne chargeait pas le nouveau module `FGReliability`. Ce branchement de test a été ajouté comme dans le graphe de scripts de production, sans changer les attentes métier. Le groupe de52 tests d’import a ensuite réussi et **toute la campagne disponible a été relancée** avec le résultat final ci-dessus. Les premiers journaux sont sous `initial-attempts/`.

Le nouveau scénario navigateur a aussi nécessité une correction de sélecteur de test : le markup utilise `data-src`, pas `data-source`. Cette correction ne modifie pas la fonctionnalité. Une première compilation non-UI isolée avec mémoire par défaut a dépassé le délai alloué ; la compilation exécutée dans la campagne finale avec2Gio a réussi. Aucun de ces échecs initiaux n’est masqué comme une réussite à la première tentative.

## Ce qui n’est PAS validé

- `tests/retro-orchestration-v014.test.cjs` : dépendance jsdom absente.
- `tests/web-dom-v014.test.cjs` : jsdom et fake-indexeddb absents.
- `GamingCodecRetroTest` et `MediaIdentityCodecTest` avec un véritable `org.json` : FG_JSON_JAR absent. La compilation contre stubs ne remplace pas l’exécution du parseur.
- Android SDK, Gradle complet, Compose, APK/AAB, appareil et WorkManager réel : non exécutés. La recherche native ne possède pas dans cette version toute la nouvelle pagination Web par source.
- Aucun vrai compte/API TMDb, Trakt, Steam, RetroAchievements, Apple, TVmaze, FreeToGame ou Open Library utilisé dans les scénarios. Aucun historique utilisateur ni avis distant modifié.
- `agent-browser` absent. L’accès Chromium à un serveur local répondant HTTP200 a été tenté : **ERR_BLOCKED_BY_ADMINISTRATOR**. Pas de contournement ; trace dans `real-origin-attempt.json`.
- Les parcours DOM utilisent set_content et scripts réels avec réseau/horloge/localStorage contrôlés. Ils ne prouvent ni PWA installée, ni persistance réelle au rechargement, ni protection effective entre deux vrais onglets/contexts.
- Le programme PC est compilé et testé par requêtes HTTP locales ; la fenêtre graphique et les parcours de navigateur Windows/macOS ne sont pas exécutés.
- Pas d’audit indépendant de sécurité, de licence commerciale fournisseur, de backend multiutilisateur prêt à publier, de cloud général ou de garantie « zéro bug ».

## Conditionnement et Drive

La Web canonique est `web/`. Les copies `web-preview/`, `desktop/resources/web/` et les entrées Web du JAR sont comparées octet par octet. Après rédaction de la documentation, le PC est recompilé et son diagnostic et ses contrôles HTTP sont rejoués.

Les distributions Suite, PC et Web possèdent un MANIFEST.sha256. Le conditionnement produit un RAR4 en mode stockage pour chacune et un ZIP de la Suite. Chaque archive est extraite avec libarchive puis comparée au dossier source : chemins, taille, SHA-256 et caractère exécutable. Le rapport séparé `FranchiseGuide_V0_29_VERIFICATION_ARCHIVES.json` fournit les résultats effectivement obtenus et le nombre final de fichiers. Le serveur Web extrait et le JAR extrait font l’objet de contrôles supplémentaires.

**Drive : dépôt non effectué dans cette session.** Le connecteur lit le dossier V0.29 et son guide antérieur, mais n’expose pas l’action d’envoi. Aucune autorisation de partage n’a été changée ; les archives et rapports sont fournis en téléchargement direct, pas annoncés comme téléversés.
