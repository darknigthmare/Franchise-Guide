# Construire et tester — V0.29

## PC

Depuis la racine de la Suite, avec JDK 17+ comprenant `javac` et `jar` :

```bash
bash tools/build_pc.sh
java -jar desktop/dist/FranchiseGuide-PC.jar --probe
```

Le script recopie le Web canonique puis compile avec `--release 17`. Il faut fermer un ancien lanceur avant de reconstruire ou de vérifier la version locale.

## Web

Aucun build frontend. Avec Node 22+, `node server/start.cjs` fournit la distribution personnelle sur `http://127.0.0.1:47632`. Le serveur n’a aucune dépendance npm de production. `npm ci` et `npm test` servent uniquement à la QA, notamment aux scénarios jsdom.

## Android

Voir `ANDROID_BUILD.md`. Sources 0.29.0 et schéma portable V11, aucun APK/AAB construit dans cette livraison. Les tests de règles JVM et les compilations de signatures ne remplacent pas le SDK, Compose ni les essais appareil. Les tests des codecs avec vrai org.json n’ont pas été réexécutés ici, faute de dépendance disponible.

## Campagnes et conditionnement

`QA_REPORT.md` décrit les commandes et prérequis de `tools/run_checks.sh`, y compris Kotlin/coroutines, `FG_JSON_JAR`, Playwright et Chromium. Les étapes sont distinctes ; une campagne arrêtée avant sa fin n’est pas déclarée entièrement passée.

Les nouvelles archives de cette passe sont de vrais RAR4 en mode stockage, accompagnés d’un ZIP de la Suite. Les utilitaires `tools/create_rar.py`, `tools/extract_rar.py` et `tools/verify_archive.py` permettent de créer et de comparer leur contenu ; la lecture indépendante utilise libarchive. Le script RAR5 fourni dans les versions antérieures reste également conservé, mais n’est pas le format employé pour cette livraison. Le SHA-256 des fichiers après extraction, les noms et les permissions exécutables sont comparés ; la seule ouverture de l’archive n’est pas une preuve suffisante.

`MANIFEST.sha256` exclut son propre contenu. `python3 tools/release_gate.py` conserve une décision distincte de la QA : son code 2 est attendu tant que les validations de publication restent ouvertes.

## Vérifications disponibles sur une machine limitée

`python3 tools/run_available_checks.py` exécute les contrôles disponibles, enregistre chaque commande et distingue les dépendances absentes. Ce mode n’est pas un substitut silencieux à la campagne complète `tools/run_checks.sh`. En V0.29, jsdom/fake-indexeddb et le vrai JAR org.json manquent dans l’environnement ; `npm ci` et Maven n’ont pas pu les obtenir. La recette CI Web/PC contient désormais explicitement `npm ci --ignore-scripts` avant ses tests DOM, mais aucune exécution distante de cette recette n’est revendiquée.

## Services automatiques

Les catalogues publics ne nécessitent aucune variable API. La configuration facultative d’applications éditeur est un travail de distribution, pas un prérequis pour chaque utilisateur. Voir AUTOMATIC_SERVICES.md. Le proxy est intégré aux serveurs personnels Node et Java ; aucun endpoint hébergé ni accès privé n’est préinstallé dans cette archive.
