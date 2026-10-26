# Rapport QA — FranchiseGuide V0.23.0

Base effectivement utilisée : ZIP Suite V0.22 joint, SHA-256 `bda35b8bd39c87ce054002dcb73dcddcd6d5f055e950675ec49e4c312bf162ca`. Code de sortie de la campagne disponible : **0**. Date de contrôle : 6 octobre 2026.

## Résultats finaux réellement exécutés

| Groupe | Résultat | Limite de preuve |
|---|---:|---|
| Commandes de la campagne disponible | **50 réussies, 0 échec final** | `tools/run_available_checks.py` ; trois groupes explicitement exclus, pas qualification complète d’environnement |
| Node / JavaScript | **1045 tests passés**, aucun échec ni test ignoré dans les fichiers exécutés | Dont65nouveaux tests d’offres pays/portée, catégories, liens, limites de flux et annulation |
| Chromium | **926 assertions**, dont71nouvelles | Scripts, DOM et CSS réels ; API, images, horloge et localStorage contrôlés |
| Kotlin/JVM | **654 assertions + 1 scénario franchises**, dont60nouvelles | Règles pures exécutées ; pas Android SDK/Compose ni téléphone |
| Parité JS/Kotlin | **7 comparaisons passées** | Disponibilités (4snapshots), épisodes, listes, bilan, franchises, imports et calendrier |
| PC | **81 contrôles HTTP + 9 de démarrage** | Vrai JAR Java17 sous Linux ; pas fenêtre graphique Windows/macOS |
| Passerelles Java | **265 assertions** | HTTP local avec réponses fournisseurs contrôlées ; pas compte réel |
| Structure et bootstrap | **114 + 8 contrôles** | Fichiers/branchements/versions et garde de build, pas preuve d’exécution de toutes les fonctions |
| Natif non graphique | **Compilation sur signatures passée** | Modèles, clients, imports, repository ; signatures de JSON/Android, pas leurs vrais runtimes |

Les nombres décrivent des assertions/scénarios, pas un pourcentage de reproduction SeriesGuide. Détails machine : `tests/results/v023/SUMMARY.json`. La liste exacte de commandes, codes retour, temps et exclusions est dans `available-qa.json`. Chaque commande a son journal ; `campaign-driver.log` prouve la fin de campagne.

### Groupes navigateur rejoués

- `browser_harness` : **64** assertions.
- `browser_v010` : **70** assertions.
- `browser_v011` : **64** assertions.
- `browser_v012` : **54** assertions.
- `browser_v015` : **63** assertions.
- `browser_v016` : **62** assertions.
- `browser_v017` : **72** assertions.
- `browser_v018` : **64** assertions.
- `browser_v019` : **60** assertions.
- `browser_v020` : **68** assertions.
- `browser_v021` : **65** assertions.
- `browser_v022` : **65** assertions.
- `browser_v023` : **71** assertions.
- `browser_v08` : **45** assertions.
- `browser_v09` : **39** assertions.

## Nouveau parcours, de la fiche au relevé

La fiche d’un film ou d’une série identifiée expose Où regarder. Une saison connue possède son raccourci ; le retour vers la fiche et depuis/vers À reprendre est testé. L’ouverture du panneau ne fait aucune requête. L’utilisateur demande la liste des pays ou la lecture de la sélection ; les requêtes sont des GET TMDb. Le payload est validé et lié au type/identifiant/pays/saison avant de rendre les catégories, noms, logos et sortie TMDb autorisée.

Les essais utilisent des services de validation fictifs. Ils n’établissent pas la disponibilité réelle d’une œuvre, son prix, un abonnement utilisateur ou le comportement d’un lien de lecture externe. Le lien TMDb est inspecté, pas suivi jusqu’à un fournisseur de streaming.

### Cas de données et d’erreurs

- Pays FR/US distincts, portée série/saison/spéciaux, film sans saison, absence d’ID confirmée sans rapprochement au titre.
- Catégories abonnement/gratuit/publicité/location/achat distinctes ; services uniques séparés du nombre d’offres ; répétitions identiques dédupliquées.
- Pays absent, pays présent vide, pas encore consulté et filtre sans résultat affichés différemment, sans fausse indisponibilité globale.
- URL source limitée à TMDb et au bon type/ID ; pays fixé à la sélection, paramètres arbitraires/credentials/fragments/liens étrangers exclus. Logos limités aux chemins TMDb ; texte de service non exécuté comme HTML.
- Réponses malformées, identités contradictoires, mauvais ID, JSON invalide, champs incomplets et corps surdimensionnés refusés avant remplacement du relevé.
- Ancien snapshot conservé après503/429 ou réponse invalide, à sa date initiale. Cache20relevés, réutilisation5minutes sans nouvelle date, actualisation explicite et délai429 commun aux lectures de ce panneau.
- Annulation, navigation, pays/portée/clé modifiés et bibliothèque remplacée : réponse retenue par la fixture puis libérée tardivement sans publication dans le nouveau contexte. Le faux fetch ignore volontairement AbortSignal pour vérifier la garde de retour.
- Lecture seule vérifiée en comparant l’état métier et les octets de stockage avant/après. Ni file Trakt, note, score, épisode vu ou compte gaming modifié. Export portable inchangé V10, sans cache/filtre/relevé temporaire.
- Données volumineuses contrôlées :175offres affichées par60, recherche dans celles non affichées ; règles pour1000offres d’une catégorie et refus au-delà. Ce n’est pas un nombre de services réels annoncé.
- Limites réseau1Mio/2Mio vérifiées sur Content-Length, octets réellement lus, UTF8 multioctet et UTF8 invalide, arrêt/cancellation du lecteur. Les appels historiques ne reçoivent pas implicitement ces limites optionnelles.

### Ancien défaut confirmé

Dans la base V0.22, les noms de disponibilités saison enregistrés sans pays/date pouvaient être affichés sous la région courante des réglages. Le scénario change FR→US et vérifie que l’ancien relevé ne s’approprie pas US. Les vieux noms restent consultables comme **ancien relevé sans pays/date conservés** ; le panneau utilise des lectures neuves, liées à leur pays, plutôt qu’une conversion supposée de l’ancien format.

Le premier test de non-mutation comparait deux exports générés à des heures différentes, incluant leur `createdAt`. Cette attente de test était incorrecte. Elle a été remplacée par une comparaison de l’état et des octets réellement sauvegardés ; aucune règle de production n’a été changée pour faire croire à une non-mutation. Trace préfinale dans `tests/results/v023/development/`.

### Affichage et retours

Captures et assertions à **390,768 et1360pixels**. Les contrôles principaux gardent une hauteur tactile d’au moins42pixels, les noms se replient et aucun débordement horizontal n’est mesuré dans ces cas. Les images de ces captures sont de minuscules PNG fictifs, pas des logos/catalogues réels. Échap, retour haut et retour interne sont testés. Fichiers `fixture-availability-*.png`, consultés visuellement pendant la passe.

## Exclusions et essais réellement bloqués

- `tests/retro-orchestration-v014.test.cjs` : jsdom absent.
- `tests/web-dom-v014.test.cjs` : jsdom et fake-indexeddb absents.
- `GamingCodecRetroTest` et `MediaIdentityCodecTest` avec véritable org.json : `FG_JSON_JAR` absent.

Ces groupes ne sont pas remplacés par leurs anciens résultats et n’entrent pas dans le total courant. Le nouveau client natif WatchProvidersClient est compilé contre les signatures, mais son parsing JSONObject/URLConnection n’est pas exercé sur Android réel. Le test de règles utilise des Maps JVM et ne prétend pas tester un parseur Android.

L’essai d’origine réelle est enregistré dans `real-origin-attempt.json`. Le serveur personnel Node répondait **HTTP200**. `agent-browser` n’était pas installé. La navigation Chromium vers l’adresse locale a échoué avec **ERR_BLOCKED_BY_ADMINISTRATOR** ; aucun contournement. Installation PWA, persistance réelle à l’origine, profils de navigateur, Web Locks entre vraies fenêtres et mode hors ligne installé restent non validés.

Aucune clé personnelle et aucun compte TMDb/Trakt/Steam/RetroAchievements réel n’ont été utilisés. Les variables de credentials serveur sont retirées de l’environnement des tests. Aucun abonnement, film ou succès réel acheté/débloqué/publié. Aucun build SDK/Compose, APK/AAB, émulateur ou téléphone ; aucun EXE ni essai graphique Windows/macOS. La CI incluse n’est pas présentée comme exécutée à distance.

## Intégrité des distributions

Les copies Web sont alignées à partir de `web/`. Les fichiers du JAR sont comparés aux octets canoniques. Chaque distribution reçoit un `MANIFEST.sha256`. Les RAR sont de vraies archives RAR stockées et le ZIP est une archive ZIP ; aucune extension n’est simplement renommée. Les quatre fichiers de distribution sont extraits par un lecteur indépendant libarchive, puis comparés aux dossiers préparés : chemins, octets/empreintes et permissions exécutables.

Le JAR extrait repasse `--probe`. Le serveur personnel du Web extrait est démarré sans credentials et ses réponses sont comparées au moteur canonique. Les résultats finaux de conditionnement, tailles, comptes d’entrées, empreintes et contrôles HTTP après extraction figurent dans `FranchiseGuide_V0_23_VERIFICATION_ARCHIVES.json`, fourni à côté des archives. Ces vérifications garantissent l’identité des fichiers transportés, pas la fermeture des limites fonctionnelles ci-dessus.

## Verdict

**Version de développement, pas certification commerciale ni promesse de zéro bug.** La nouvelle fonction permet de consulter des indications de disponibilité à la demande, avec provenance et contexte explicites. Elle ne comble pas les chantiers de cloud général, connecteurs gaming supplémentaires, runtime Android, live OAuth/fournisseurs ni parité sociale et de réglages exhaustive avec SeriesGuide. Voir `FEATURE_MATRIX.md` et `RELEASE_GATES.json`.
