# Construire et tester — 0.36.0

## Préparer les sources

Le dépôt contient tous les moteurs et ressources nécessaires. `web/` est canonique ; les copies `web-preview/` et `desktop/resources/web/` sont produites par `npm run sync:web` ou automatiquement pendant le build PC.

Installer Node 22+ pour le serveur et les tests, Python 3 pour les contrôles, et un JDK 17 incluant `javac` et `jar` pour le PC. Les dépendances npm sont réservées aux tests et verrouillées dans `package-lock.json`.

```bash
npm ci --ignore-scripts
npm test
npm run check:structure
npm run build:pc
python3 tests/desktop_http.py
python3 tests/desktop_startup.py
```

## Navigateur réel

```bash
npx playwright install chromium
npm run test:browser
```

Un Chrome/Chromium déjà installé peut être sélectionné par `CHROMIUM_EXECUTABLE`. Le test utilise un port local libre, un contexte navigateur isolé et des données fictives. Il exerce les vrais stockages et le service worker sans désactiver la politique CSP. Il ne remplace pas les tests des comptes réels, de l’installation autonome et des autres navigateurs.

## Android

La configuration utilise AGP 8.10.1, Kotlin/Compose 2.0.21, Gradle 8.11.1, Java 17, SDK 36 et Build Tools 35.0.0. Voir [la procédure Android](ANDROID_BUILD.md) et [le rapport de recette](QA_REPORT.md) pour les résultats exécutés.

```bash
bash tools/bootstrap-gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## Intégrité et publication

Après les modifications et la génération des livrables, ajouter les fichiers dans Git, régénérer le manifeste, puis le vérifier :

```bash
git add .
python3 tools/update_manifest.py
python3 tools/update_manifest.py --check
git add MANIFEST.sha256
```

Le manifeste ne référence que les fichiers suivis, et exclut son propre contenu. Les dépendances, caches et résultats temporaires ne font pas partie du dépôt.

Les anciens runners `run_v035_checks.py` et les suites Python de navigateur sont conservés pour leurs versions et scénarios. Ils nécessitent leurs prérequis propres, détaillés dans leurs scripts. Leur existence n’implique pas qu’ils ont été tous rejoués en 0.36.

La CI exécute trois jobs indépendants Web/PC, navigateur et Android sur `main` et les pull requests. Aucun secret de compte réel n’est nécessaire à ces contrôles. `python3 tools/release_gate.py` conserve un verdict distinct sur les validations de distribution encore ouvertes.
