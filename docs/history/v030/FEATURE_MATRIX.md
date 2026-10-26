# Fonctions et limites — FranchiseGuide V0.30.0

Base réellement utilisée : ZIP V0.29 fourni. Les résultats de cette passe sont dans QA_REPORT.md ; aucune ancienne réussite non rejouée n'est assimilée à une nouvelle validation.

| Fonction de cette passe | Web / PC | Android |
|---|---|---|
| Export protégé `.fgbackup` facultatif | Interface et code de production intégrés ; primitives Node WebCrypto testées | Écran source et primitives javax.crypto ; tests JVM |
| Phrase 12–256 caractères, Unicode exact | Validation, confirmation, effacement des champs ; aucun stockage volontaire | Pas de rememberSaveable ; tableaux mutables effacés au mieux |
| Sel/nonce aléatoires, authentification de l'en-tête | AES-256-GCM/PBKDF2 standard ; fichiers testés avec implémentation Node indépendante | Interopérabilité bidirectionnelle réelle avec JS sur JVM |
| Lecture d'un fichier protégé | Contrôle avant KDF, intégrité, UTF-8, schéma, aperçu puis confirmation | Panneau séparé, lecture bornée et préparation du même aperçu portable |
| Annulation/réponses tardives | Fermeture immédiate + signal + vérification état/ouverture ; scénario de course corrigé | Annulation coroutine et garde d'état dans le code ; pas de test UI appareil |
| Modification concurrente de bibliothèque | Refus de l'ancien export/aperçu | Garde de snapshot/génération source |
| Mot de passe erroné/contenu altéré | Pas d'application ni faux aperçu ; erreur explicite | Refus par primitives JVM ; pas de validation appareil |
| Import réellement appliqué | Les protections V0.18+ continuent : aperçu, confirmation, copie préalable, suspension Trakt, sauvegarde transactionnelle | Même panneau d'import existant, source non-SDK |
| Export JSON ordinaire et format portable V11 | Conservés | Conservés ; anciens libellés V9 corrigés |
| Chiffrement du navigateur, des credentials et snapshots | **Non fourni** | **Non fourni** |
| Reset d'une phrase perdue | **Impossible pour le fichier protégé** | Même limite |
| Identification de l'auteur d'un fichier chiffré | **Non fournie** : authenticité GCM ≠ identité de personne | Même limite |
| Installation PWA et cryptographie native en navigateur réel | Non validées, origine bloquée ; tests UI avec pont Node explicitement documenté | Sans objet |
| Android SDK/Compose, APK/AAB/appareil | Sans objet | **Non validés/non livrés** |

## Fonctions héritées conservées

Quatre médias, catalogues publics sans clé utilisateur, recherche multifornisseur avec reprise, ISBN/éditions personnelles, listes et ordre local, franchises/suggestions, fiches épisode/personnes, disponibilité par pays, agenda/.ics, visionnages datés, progression jeux/livres et statistiques, aperçus d'import, trophées, Steam OpenID, RetroAchievements, Trakt et centre de synchronisation restent dans les sources et dans les régressions disponibles.

Leur présence ne supprime pas les limitations de V0.29 : fournisseurs publics de portées différentes, pas de catalogue gaming payant/console universel sans accès éditeur, pas de correspondance TMDb inventée pour TVmaze, pas de clé privée partagée préactivée. Les API authentifiées et passerelles exigent la configuration appropriée. Aucun nouveau connecteur gaming ni cloud interappareil général n'est ajouté.

## Toujours ouvert

Compilation Android SDK/Compose et essais appareils ; WorkManager, notifications et sécurité du stockage natifs ; véritables connexions/fournisseurs ; navigateur à origine persistante, Web Locks interonglets, PWA installée et hors ligne ; interface graphique Windows/macOS ; tests de charge et temps de dérivation sur matériels lents ; audit de sécurité indépendant ; disponibilité et conditions commerciales des fournisseurs ; backend public multiutilisateur ; parité exhaustive avec SeriesGuide.

Les backups et les mots de passe restent personnels. Les fichiers logiciels et rapports déposés sur Google Drive ne constituent pas une synchronisation automatique de la bibliothèque.
