# Recette actuelle — FranchiseGuide 0.36.0

Évaluation du 26 octobre 2026. Base récupérée : Suite V0.35 ZIP, SHA-256 `6628593fb329ea964d6f73b95c072bef5f26810715fcf08943d2421c79dde022`. Les rapports anciens sont conservés dans `archives/v035/` et ne sont pas additionnés aux résultats ci-dessous.

## Résultats exécutés pour cette passe

| Groupe | Résultat | Portée |
| --- | ---: | --- |
| `npm test` | 1 580 réussis, 0 échec, 0 ignoré | Règles, réseau, imports, stockage et DOM sous Node 24.22.1. |
| `npm run check:structure` |216 contrôles | Présence/branchement des sources, XML, versions et ressources ; pas des parcours utilisateur. |
| Passerelles Java 17 |353 assertions | AutoServices81, SteamBridge35, SteamAuth53, RetroBridge184. Vrais serveurs HTTP locaux, réponses fournisseur fictives. |
| Build PC et `--probe` |Réussis | JAR reconstruit depuis les sources et ressources 0.36.0. |
| `tests/desktop_http.py` |109 contrôles | Ressources embarquées exactes, routes HTTP, en-têtes, refus des requêtes non autorisées, version et compatibilité Java 17. |
| `tests/desktop_startup.py` |9 contrôles | Démarrage, ports occupés, ancien service, réponse bloquée et refus des redirections. |
| `npm run test:browser` |22 contrôles | 22/22 sur Chrome Headless Shell 151.0.7922.34 et 22/22 sur Chrome 156.0.8078.47 : vraie origine locale, CSP, stockages et service worker. |
| Règles check-in Kotlin JVM |33 assertions et parité JS/Kotlin réussies |27 fichiers de modèle compilés avec Kotlin2.0.21/JDK17, sans simulation des résultats. |
| Distribution Web extraite |110 contrôles sur la suite, puis110 sur une copie isolée |Vrai HTTP local, métadonnées0.36 et repli sans RELEASE.json ; version incohérente refusée. |
| Android SDK 36 |Compilation Compose, assembleDebug et lintDebug réussis |APK 0.36.0/code 36 ; lint : **0 erreur**,31 avertissements et1 conseil. |

Les groupes de 1 580 tests comprennent les suites jsdom/fake-indexeddb auparavant non exécutées : 18 tests DOM et 20 tests d’orchestration RetroAchievements. Les 33 nouveaux cas de reprise check-in sont inclus dans le total 1 580 ; ils ne s’y ajoutent pas.

## Ce que prouve la recette navigateur

- Démarrage du vrai serveur et chargement des scripts de production.
- Ouverture des boutons d’ajout des quatre catégories.
- Import JSON à quatre catégories, aperçu sans modification et confirmation explicite.
- Conservation des catégories et notes personnelles après rechargement.
- Écriture et lecture d’une véritable copie IndexedDB.
- Deux onglets d’une même origine : second onglet en lecture seule, tentative d’écrasement refusée, reprise de l’écriture après fermeture du premier et rechargement.
- Service worker actif, rechargement hors ligne conservant quatre titres et le thème en mémoire et dans localStorage. Une URL à nonce absente du cache répond en HTTP 404 en ligne puis échoue au niveau transport hors ligne ; le rechargement est effectivement servi par le service worker.
- Absence des réponses privées `/api/` dans les caches du service worker.
- Mise en page à 390 px sans débordement horizontal du document.
- Aucune exception JavaScript non interceptée, aucun appel fournisseur requis par cette recette.

Sous Chromium 151, `navigator.onLine` redevient vrai après le rechargement alors que les requêtes réseau sont bloquées. Ce comportement a provoqué le premier échec de CI. Le test conserve cet indicateur comme diagnostic et exige désormais la preuve de transport décrite ci-dessus, ainsi que les données persistées. Le script corrigé passe sur les deux moteurs.

Les captures de recette utilisent des données fictives. Elles ne sont pas une revue de toutes les pages ni une validation sur téléphone.

## Correctifs couverts

Les check-ins Trakt exigent un accusé portant sur un seul visionnage, enregistrent l’intention avant mutation et passent en vérification après un résultat incertain. Les réponses obsolètes, changements de compte/application/bibliothèque, conflits de synchronisation et reprises de nettoyage sont contrôlés.

Les passerelles Node/Java masquent les secrets après décodage du JSON, appliquent un délai de repli en cas de `Retry-After` invalide et démarrent ce délai à réception de la réponse. Les en-têtes de sécurité multiples sont refusés. Les lectures Steam contrôlent URL, UTF-8 et JSON avant mise en cache.

## Limites de cette recette

Aucun compte réel Trakt, Steam ou RetroAchievements ni identifiant fournisseur réel n’a été utilisé. Les erreurs distantes et les accusés de réception sont reproduits avec des réponses contrôlées. La recette de stockage entre onglets n’est pas une simulation de deux check-ins Trakt simultanés.

L’installation autonome PWA, la mise à jour depuis une ancienne version, Safari/Firefox, l’éviction des données par le navigateur, les OS PC utilisateurs et la matrice de téléphones restent à qualifier. Le test hors ligne porte sur une application déjà chargée, avec une bibliothèque locale.

La compilation Android SDK 36/JDK 17 a produit un APK de développement 0.36.0/code 36 après correction de trois écrans Compose. Les 13 erreurs de lint initiales ont été corrigées : compatibilité API 26–32 des encodeurs URL et du lecteur borné, structure du widget et règles de sauvegarde. La commande complète assembleDebug/testDebugUnitTest/lintDebug termine avec succès en 5 min 24 s ; le rapport final contient 0 erreur, 31 avertissements et 1 conseil. Aucune règle de lint n’a été désactivée et aucune baseline ne masque ces erreurs. `testDebugUnitTest` indique `NO-SOURCE` : aucun test JUnit Android n’a été exécuté par cette tâche. Les33 assertions Kotlin séparées ne sont pas encore intégrées au workflow CI. La compilation et l’assemblage ne constituent pas une recette appareil, Play Store ou comptes réels.

## Publication vérifiée

Le commit d’import `f954889bceaa81636c5c24a37f78a5ea1951554c` a été relu par un nouveau clone public du dépôt. Il contient 589 fichiers suivis ; les 588 entrées du manifeste SHA-256, le conteneur Excel et l’état Git propre ont été vérifiés avant toute reconstruction. Depuis ce clone, `npm ci --ignore-scripts --offline` a utilisé le cache de paquets disponible, le contrôle de structure a réussi, le JAR PC a été reconstruit/probé et `npm start` a servi la page en HTTP 200. Cela ne constitue pas un essai sur une machine dépourvue de tout cache.

Les jobs GitHub Web/PC (Node 22) et Android ont également réussi sur le commit `6f5340f804987a3e1a0c6a5f6e44155908dc5897`, [exécution 40402512586](https://github.com/darknigthmare/Franchise-Guide/actions/runs/40402512586). L’APK et les rapports Android sont disponibles dans ses artefacts. La première installation Android en CI demandait un ancien paquet SDK `tools`, indisponible ; le workflow demande désormais explicitement `platform-tools` puis SDK 36/Build Tools 35. Les résultats distants sont distingués des contrôles locaux dans `docs/qa/current.json`.

## Reproduire

Le README donne les commandes de lancement et de test. Les tests navigateur utilisent `tests/browser-origin.cjs` ; ils n’enlèvent pas la CSP et ne remplacent pas localStorage/IndexedDB par des objets simulés. Les tests unitaires et DOM utilisent leurs propres jeux de données fictifs.

`tests/results/` contient les résultats générés localement et reste exclu du dépôt. Les preuves synthétiques courantes sont conservées dans `docs/qa/current.json` ; les exécutions GitHub Actions fournissent leurs artefacts lorsqu’elles aboutissent.
