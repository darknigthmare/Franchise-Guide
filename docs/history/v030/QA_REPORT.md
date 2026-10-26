# Rapport QA — FranchiseGuide V0.30.0

## Provenance et périmètre

Base : **ZIP Suite V0.29 fourni**, SHA-256 `af5a7484dd47619ddb09f56456517b00588ba22233cfeaf47faa56be82e9bf71`. Aucun dossier source complet ni archive V0.30 terminé n'était récupérable au début de la reprise ; une capture isolée ne constituait pas un livrable. La passe a été reconstruite sur V0.29, dont les fonctionnalités ont été conservées.

Parcours vérifié : **bibliothèque → portable sans credentials → phrase et confirmation → chiffrement → fichier ; fichier → en-tête → mot de passe → authentification → UTF-8/JSON/schéma → aperçu → confirmation/copie locale → remplacement gardé**. Le chiffrement n'est pas un appel API de compte utilisateur.

## Campagne disponible

`python tools/run_available_checks.py` a terminé avec **70 commandes réussies, aucun échec final**, code0. Rapport exact : `tests/results/v030/available-qa.json`, journaux individuels et `final-driver.log`. Les exclusions sont explicites ; le runner ne les remplace pas par d'anciennes réussites.

| Groupe | Résultat | Portée |
|---|---:|---|
| Node | **1382 tests réussis** | Dont30 nouveaux tests du conteneur protégé, avec la vraie API WebCrypto Node et des vérifications indépendantes via node:crypto |
| Chromium UI | **1291 assertions réussies** | Dont47 nouveaux scénarios ; vrais scripts/DOM/CSS. Fournisseurs, images et stockage contrôlés |
| Kotlin/JVM | **897 assertions + un scénario franchises réussis** | Dont39 nouvelles assertions avec les véritables primitives JCA/JCE. Pas Android SDK |
| Parités métier JS/Kotlin héritées | **11 réussies** | Import, calendrier, identité, listes, univers, données publiques, éditions, etc. |
| Interopérabilité de chiffrement | **3 fichiers dans chaque sens réussis** | JVM→JS puis JS→JVM ; comparaison des octets avec ASCII, accents/emoji, espaces et caractères combinés |
| Passerelles Java | **333 assertions réussies** | HTTP local et réponses fournisseurs simulées ; pas de credentials réels |
| PC | **107 contrôles HTTP finaux +9 contrôles de démarrage** | 105 contrôles figuraient dans la campagne ; le JAR final et ses deux ressources ajoutées ont repassé 107 contrôles : `final-pc-http.json` |
| Structure et bootstrap | **182 +8 contrôles réussis** | Branchements, ressources, XML et procédure de build ; pas des tests de bout en bout |
| Kotlin non graphique | **Compilation contre signatures réussie** | Modèles, clients, repository et codecs contre stubs. Pas de build Compose, pas d'exécution des vrais services Android |

## Distinction essentielle sur le chiffrement dans les essais UI

Les nouvelles assertions de parcours utilisent **Chromium pour le DOM, File/ArrayBuffer, les formulaires et les validations**, mais délèguent les calculs de protection au **module de production sur Node WebCrypto via un pont de test local**. Le code de production du navigateur ne contient pas ce pont et utilise directement `crypto.subtle`.

Une tentative de navigation vers une origine sûre de test a été bloquée. La navigation vers le serveur local, bien qu'il réponde en HTTP200, a également échoué avec **ERR_BLOCKED_BY_ADMINISTRATOR**. `agent-browser` est absent ; aucun drapeau contournant cette politique n'a été utilisé. Le fichier `real-origin-attempt.json` conserve la preuve.

**Aucun succès de chiffrement WebCrypto natif dans une PWA installée ou un véritable profil navigateur sécurisé n'est donc revendiqué.** Les calculs cryptographiques eux-mêmes ont réellement été exécutés séparément dans Node et Java ; le bridge des tests UI n'en fait pas des essais de navigateur natif sécurisé.

## Nouvelles vérifications

- Format binaire, marqueur/version, coût de dérivation fixe, taille claire bornée, longueur de fichier exacte et octets réservés contrôlés avant dérivation.
- Aléa par export ; absence de texte privé dans les résultats contrôlés ; round-trip intégral, incluant Unicode. Déchiffrement avec les primitives indépendantes `node:crypto` et génération indépendante d'une enveloppe compatible.
- Mauvais mot de passe, ciphertext, sel, nonce ou tag modifié : refus de l'authentification. Coût hostile, header altéré, troncation et données ajoutées : refus du format.
- Deux calculs concurrents refusés par le noyau JS. Annulation avant/pendant dérivation : aucun résultat rendu exploitable à l'appelant. Aucun repli automatique en clair si WebCrypto est absent.
- Limites de phrase, Unicode mal formé, caractères de contrôle et blancs refusés. Aucune troncature ni normalisation du mot de passe.
- Déchiffrement authentifié mais texte UTF-8, JSON ou schéma invalide : aucun aperçu applicable.
- Export et déchiffrement ne modifient pas la bibliothèque, les snapshots ni la file Trakt. Le portable n'inclut pas les identifiants de service contrôlés.
- Confirmation de risque et seconde confirmation d'import indépendantes. Copie de sécurité préalable et blocage pendant Trakt toujours appliqués.
- Fermeture/Échap, retour tardif, changement de bibliothèque, changement de révision et expiration du formulaire vérifiés.
- Le raccourci d'export JSON ne déclenche pas de téléchargement en clair depuis les dialogues de protection.
- UI contrôlée à390,768,1360pixels ; capture de cas fictif `fixture-protected-export-*.png`. Les boutons peuvent être dans la partie défilante sur petit écran.

## Erreurs initiales trouvées puis corrigées

1. **Buffer Node partagé.** Le premier noyau utilisait `slice()` pour copier les entrées. Une entrée `Buffer` Node pouvait partager la mémoire ; le nettoyage effaçait alors le Buffer de l'appelant. L'échange interlangage a révélé ce défaut. Le noyau crée désormais une copie explicite `new Uint8Array(value)` ; un test Buffer dédié a été ajouté. C'était une erreur de la nouvelle implémentation, pas une régression attribuée à V0.29.
2. **Course à l'annulation.** Le listener asynchrone `close` pouvait être exécuté après une réponse cryptographique tardive. La vérification `isConnected` seule ne suffisait pas. Le code vérifie maintenant `dialog.open` et annule synchroniquement sur les commandes de fermeture et `cancel`. La trace initiale est `initial-cancel-race.log`, séparée des résultats finaux.
3. **Libellés obsolètes hérités.** Le portable était déjà V11, mais certains noms d'export annonçaient V9/V10. Ils ont été alignés sans changement de schéma.

## Tests non exécutés et limites

- Deux suites Node : `retro-orchestration-v014.test.cjs` exige jsdom ; `web-dom-v014.test.cjs` exige jsdom/fake-indexeddb. Dépendances non disponibles dans cet environnement.
- Deux programmes Kotlin de codecs contre le vrai `org.json`, GamingCodecRetroTest et MediaIdentityCodecTest : FG_JSON_JAR absent. Une tentative de lecture du dépôt Maven a échoué en résolution DNS. Les stubs ne sont pas comptés comme une exécution de parseur réel.
- Android SDK/Gradle complet/Compose, APK/AAB, appareil, fournisseur de documents, clavier et WorkManager réels : non exécutés. Le nouveau panneau est source seulement.
- Aucune authentification/API réellement effectuée sur un compte utilisateur ; aucun avis/historique distant modifié. Pas de validation Apple/TVmaze/TMDb/Open Library/Steam/RAWG/Trakt/RetroAchievements en direct.
- Pas de PWA installée, persistance réelle au rechargement, Web Locks entre vrais onglets ou navigateur cible Windows/macOS/iOS.
- Pas d'audit indépendant de sécurité, certification FIPS, mesure de dérivation sur appareils lents ni garantie d'effacement intégral de la mémoire. La protection d'un export ne chiffre pas les autres copies locales.
- Le stockage actif reste localStorage et ses quotas. Pas de nouvelle synchronisation cloud ni de connecteur gaming.

## Conditionnement et Drive

La Web canonique, ses copies et les fichiers du JAR sont comparés après la dernière mise à jour des documents inclus. Le JAR est recompilé et ses contrôles HTTP sont rejoués avec les deux nouveaux modules. Les quatre archives (Suite RAR/ZIP, PC RAR, Web RAR) sont extraites avec un lecteur indépendant puis comparées aux sources : chemins, octets, SHA-256 et permissions exécutables. Le JAR extrait et le serveur Web extrait sont démarrés séparément. Les résultats réels sont fournis dans le rapport d'archives externe, pas supposés par l'existence des fichiers.

Le dépôt demandé sur Drive concerne ces livrables logiciels et leurs rapports, **pas une sauvegarde de la bibliothèque personnelle**. Le transfert n'est déclaré réussi dans la réponse finale qu'après confirmation et relecture du dossier. Aucun partage public n'est activé.
