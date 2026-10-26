# Import GitHub du 26 octobre 2026

## Source récupérée

Le dépôt `darknigthmare/Franchise-Guide` contenait uniquement son README au commit
`c2bd1b8ee6bf62928c6bab2e37b27f8ec72e23c0`.

Cette intégration reprend les octets de **FranchiseGuide_Suite_V0_35.zip** conservés
dans le dossier de livraison V0.35. L'archive mesure **16 511 784 octets** et son
SHA-256 vérifié est :

`6628593fb329ea964d6f73b95c072bef5f26810715fcf08943d2421c79dde022`

Le dossier de livraison V0.36 retrouvé était vide. Des notes finales V0.35
mentionnent une autre empreinte de ZIP, mais cette archive n'a pas été récupérée.
La base retenue est donc précisément l'archive V0.35 disponible ci-dessus. Les
résultats de tests anciens ne sont pas présentés comme des tests de cette passe.

## Contenu du dépôt

- `app/` : application Android Kotlin et Compose, ressources, widgets et workers.
- `web/` : source canonique de l'interface et des règles Web.
- `server/` : serveur personnel Node et passerelles de fournisseurs.
- `desktop/` : lanceur Java, passerelles PC et JAR embarquant le moteur Web.
- `web-preview/` : copie du moteur Web produite par `npm run sync:web`.
- `tests/` : tests exécutables et jeux de données de test.
- `tools/`, `docs/`, `examples/` : outils, documentation et exemples.
- `docs/FranchiseGuide_Reste_a_faire.xlsx` : suivi détaillé des travaux restants.
- `docs/backlog.json` : données sources modifiables du suivi.

Les caches de compilation, dépendances téléchargées, profils locaux et anciens
résultats d'exécution ne sont pas versionnés. Les tests et leurs jeux de données
sont conservés. Les rapports historiques restent identifiés dans
`docs/archives/v035/` ; la recette actuelle est dans `docs/QA_REPORT.md`.

## Reprendre le travail

Modifier les fichiers de `web/`, puis exécuter `npm run build:pc` pour actualiser
les deux copies Web et reconstruire le JAR. Les modifications directes dans les
copies seront remplacées au build suivant.

Exécuter `npm test` pour les règles et passerelles, `npm run test:browser` pour
la recette sur une vraie origine Chromium, puis les contrôles Java/Android
décrits dans le README. Les fonctions qui nécessitent un compte fournisseur
réel restent ouvertes jusqu'à une recette avec ce compte.
