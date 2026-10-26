# FranchiseGuide Suite 0.30.0

Version incrémentale reconstruite depuis la **Suite V0.29 ZIP fournie**, SHA-256 `af5a7484dd47619ddb09f56456517b00588ba22233cfeaf47faa56be82e9bf71`. La tentative V0.30 interrompue ne disposait ici d'aucune source/ archive complète récupérable, seulement d'une capture. Elle n'a pas été supposée terminée.

FranchiseGuide conserve les séries, films, jeux, livres, univers/suggestions, listes personnelles, suivis, dates, statistiques, catalogues publics sans clé utilisateur, imports SeriesGuide et connexions optionnelles Trakt/Steam/RetroAchievements de V0.29. C'est une application indépendante, pas une copie de l'infrastructure privée de SeriesGuide.

## Nouveauté V0.30 : export protégé facultatif

**Plus → Exporter avec une phrase secrète** crée un fichier `.fgbackup`. Chiffrement local AES-256-GCM, dérivation PBKDF2-HMAC-SHA256 à 600 000 itérations, sel et nonce aléatoires par fichier, en-tête authentifié. Les primitives sont celles de Web Crypto / javax.crypto, pas une réimplémentation de chiffrement.

L'import reconnaît le conteneur, exige sa phrase, contrôle l'authenticité et l'UTF-8 puis présente **l'aperçu portable existant avant confirmation**. Une détection de format, un déchiffrement ou une préparation ne remplace jamais la bibliothèque sans confirmation finale. Mauvais mot de passe, taille/en-tête incohérents, JSON invalide, changement de bibliothèque et annulation refusent la suite. Aucun repli automatique en clair.

Le JSON portable reste V11 et l'export ordinaire existe toujours. **Ce nouveau format ne chiffre ni la base locale, ni les snapshots automatiques, ni les secrets du navigateur. Un mot de passe perdu n'est pas récupérable.** Ce n'est ni un cloud général, ni un audit de sécurité indépendant, ni une certification commerciale.

## Utilisation

- PC : Java17+ et `desktop/FranchiseGuide-PC.bat`, ou `bash desktop/FranchiseGuide-PC.sh`. Le JAR est compilé ; Java n'est pas embarqué. Interface dans le navigateur sur 127.0.0.1:47631.
- Web : Node22+ et `node server/start.cjs`. Interface sur 127.0.0.1:47632. Les deux ports sont des origines séparées.
- Android : sources Kotlin/Compose et écran dédié fournis ; **pas d'APK/AAB ni de build SDK/Compose validé**.

Avant migration : exporter V0.29 en JSON, fermer ses onglets/lanceurs, puis garder le même navigateur/profil/origine si les données doivent être retrouvées. Les anciennes versions ne lisent pas `.fgbackup`.

Lire **START_HERE.md**, `docs/SAUVEGARDES_PROTEGEES.md` et `docs/QA_REPORT.md`. Les limites historiques encore ouvertes sont conservées dans `docs/FEATURE_MATRIX.md`.

## Vérification

Les commandes réellement exécutées et leurs journaux sont dans `tests/results/v030/`. La cryptographie est exercée par Node WebCrypto et Java/JVM, avec échanges de fichiers dans les deux sens. Les scénarios UI utilisent Chromium avec stockage/réseau contrôlés et un pont vers la véritable implémentation Node WebCrypto, car la navigation vers une origine sûre est bloquée dans cet environnement. **Pas de validation de WebCrypto natif dans un profil navigateur installé**, pas de compte réel ni d'appareil Android.

`tools/run_available_checks.py` rapporte aussi les suites non exécutables faute de dépendances. `tools/run_checks.sh` décrit la campagne complète avec les dépendances installées. Les anciens rapports sont historiques : leurs compteurs ne sont pas additionnés aux résultats courants sans réexécution.
