# FranchiseGuide 0.36.0

Bibliothèque personnelle pour **séries, films, jeux vidéo, livres et franchises**, avec une interface Web/PWA, un lanceur PC Java et des sources Android Kotlin/Compose.

Cette version reprend la suite V0.35 récupérable, corrige les check-ins Trakt et les passerelles locales, rétablit les tests DOM et rassemble tout le code dans ce dépôt. [Provenance exacte de l’import](docs/INTEGRATION_GITHUB.md).

## Suivre les travaux

- **[Excel des travaux restants](docs/FranchiseGuide_Reste_a_faire.xlsx)** : priorités, plateformes, état, étapes détaillées, critères de recette, dépendances et preuves dans le code.
- [Données du suivi](docs/backlog.json) : registre modifiable et exploitable par script.
- [Rapport de validation de cette version](docs/QA_REPORT.md).
- [Fonctions et limites](docs/FEATURE_MATRIX.md).

## Démarrer sur PC ou dans le navigateur

### Web personnel — Node.js 22 ou plus récent

Depuis la racine du dépôt :

```bash
npm start
```

Ouvrir **http://127.0.0.1:47632**. Le serveur et l’application n’ont pas de dépendance npm de production. `npm ci` sert aux outils de test.

Les lanceurs `FranchiseGuide-Web.bat` et `FranchiseGuide-Web.sh` sont également fournis. Ce serveur écoute uniquement sur la machine locale ; il ne constitue pas un service partagé entre utilisateurs.

### PC — Java 17 ou plus récent

Sous Windows, lancer `desktop/FranchiseGuide-PC.bat`. Sous Linux/macOS :

```bash
bash desktop/FranchiseGuide-PC.sh
```

Ou directement :

```bash
java -jar desktop/dist/FranchiseGuide-PC.jar
```

L’interface s’ouvre sur **http://127.0.0.1:47631**. Le code source du lanceur est dans `desktop/src/`. Le JAR inclut les ressources Web.

### Android

Ouvrir ce dépôt dans Android Studio ou utiliser le bootstrap Gradle fourni avec un JDK17, Android SDK36 et Build Tools35.0.0 :

```bash
bash tools/bootstrap-gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

L’APK de développement produit se trouve dans `app/build/outputs/apk/debug/`. Le rapport QA précise l’état réel de compilation et les essais effectués ; un APK de développement ne remplace pas la recette sur téléphone ni une livraison signée pour le magasin.

## Fonctions présentes

| Domaine | Contenu existant |
| --- | --- |
| Bibliothèque | Séries, films, jeux, livres, favoris, listes, notes, filtres et statistiques |
| Séries | Fiches, saisons, épisodes, vues datées, revisionnages, agenda et masquage des spoilers |
| Recherche | Bouton d’ajout par catégorie, catalogues publics et fournisseurs configurables |
| Franchises | Regroupements transversaux, franchises personnalisées et scanner multi-catégories |
| Trakt | Connexion par code appareil, synchronisation, file d’opérations, reprise et check-ins |
| Jeux | Bibliothèque Steam et succès, RetroAchievements, listes et notes de trophées manuelles |
| Livres | Open Library, ISBN, choix d’édition et progression de lecture |
| Données | Import SeriesGuide, aperçu avant remplacement/fusion, exports JSON et sauvegardes protégées |
| Web | Interface responsive, cache de l’application, récupération IndexedDB et garde d’écriture entre onglets |

L’état de validation diffère selon la plateforme. Les connecteurs de comptes ont besoin de leurs accès autorisés. Les références PSN/PSNProfiles, Xbox, Epic et GOG ne doivent pas être confondues avec des connexions automatiques effectivement intégrées.

## Catalogues et comptes

La recherche publique utilise TVmaze pour les séries, Apple/iTunes pour son catalogue cinéma limité, FreeToGame pour les jeux gratuits et Open Library pour les livres. Les images restent dépendantes des sources et de leur disponibilité.

Les accès TMDb, RAWG, Trakt, Steam et RetroAchievements peuvent être configurés dans les réglages ou dans l’environnement du serveur selon le fournisseur. Aucune clé éditeur ni authentification personnelle n’est fournie dans le dépôt. Consulter [la configuration des services](docs/AUTOMATIC_SERVICES.md), [Steam](docs/STEAM_SIGN_IN.md) et [RetroAchievements](docs/RETROACHIEVEMENTS.md).

Trakt ne synchronise pas les livres, jeux et franchises personnalisées. Exporter la bibliothèque pour transférer ces données tant qu’une synchronisation générale multiappareil n’est pas livrée.

## Développer et vérifier

```bash
npm ci --ignore-scripts
npm test
npm run check:structure
npm run build:pc
python3 tests/desktop_http.py
python3 tests/desktop_startup.py
```

Le build PC demande un **JDK**, incluant `javac` et `jar`. Il synchronise automatiquement `web-preview/` et `desktop/resources/web/` depuis `web/` avant compilation. Modifier uniquement la source canonique `web/` pour changer l’interface.

Recette sur une véritable origine navigateur :

```bash
npx playwright install chromium
npm run test:browser
```

`CHROMIUM_EXECUTABLE` permet de désigner un Chrome/Chromium installé. Cette recette utilise des données fictives et vérifie l’import confirmé, le rechargement, IndexedDB, deux onglets, le transfert du verrou d’écriture et le mode hors ligne. Elle ne connecte aucun compte fournisseur.

GitHub Actions exécute les contrôles Web/PC, navigateur et Android à chaque pull request ou modification de `main`. Ses artefacts contiennent le JAR, l’APK de développement lorsqu’il est compilé et les rapports de test.

## État de livraison

Il reste notamment à qualifier les connexions avec de vrais comptes, la synchronisation générale multiappareil, les sauvegardes externes automatiques, le fonctionnement natif sur appareils et la distribution installable/signée. Le classeur détaille ces travaux et leurs critères de fermeture.

Les anciens rapports sont conservés dans `docs/history/` et `docs/archives/v035/`. Ils décrivent leurs versions respectives ; seuls les résultats de `docs/QA_REPORT.md` concernent cette livraison.

Les licences et notices d’origine sont conservées dans [LICENSE](LICENSE), [UPSTREAM_NOTICE.md](UPSTREAM_NOTICE.md) et [les attributions](docs/THIRD_PARTY_ATTRIBUTION.md).
