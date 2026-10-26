# FranchiseGuide Suite 0.28.0

Reprise du ZIP V0.27 fourni (SHA-256 `b92b19733f0f85c2b54f1bfc90b0f6d86fc35ddc88a9734850101a12c31fd58e`). Sources Android, application Web/PWA et programme PC Java17 compilé.

## V0.28

Recherche ISBN publique explicite, vérification de l’édition et du lien à l’œuvre, association d’une fiche existante ou récupération de l’œuvre avant ajout. Référence personnelle d’édition persistée, indépendante des pages suivies et des anciennes lectures. Option de total séparée, confirmation gardée, retrait sans effacement du livre, conservation à l’actualisation et portableV11.

Défaut confirmé de V0.27 : un EAN produit comme4006381333931 était accepté par le validateur ISBN13. Il est désormais refusé, de même que les réponses pour une autre édition ou un autre ISBN. Le titre reçu de la fiche d’œuvre est aussi pris en compte lors de son actualisation Web. Les dates des premières pages d’éditions ne sont plus rajeunies par la lecture d’une page ultérieure.

Android reçoit règles, clients, codec et panneau pour les œuvres existantes ; **aucun APK, build SDK/Compose ou test appareil n’est fourni**. Les autres modules sont conservés, pas remplacés. Le guide explique la parité partielle et les limites publiques.

Démarrer : **START_HERE.md**. PC Java17+, Web Node22+ (`node server/start.cjs`). Aucune installation npm pour utiliser l’application. Les dépendances npm ne concernent que certaines suites QA. Les catalogues publics ne demandent pas de clé, mais nécessitent le réseau ; aucun secret tiers ni accès éditeur préactivé n’est embarqué.

Documentation : `docs/ISBN_ET_EDITION.md`, `docs/FEATURE_MATRIX.md`, `docs/QA_REPORT.md`. Preuves de cette version dans `tests/results/v028/`, distinctes des anciennes campagnes conservées. Les essais DOM utilisent des fournisseurs/images/stockages contrôlés ; ils ne sont pas des connexions réelles ni une installation PWA.

Le code Web canonique est `web/`. Les copies `web-preview/`, `desktop/resources/web/` et le JAR sont alignés lors du conditionnement. Le dépôt des livrables sur Google Drive n’ajoute pas une synchro cloud générale dans l’application. La version demeure de développement, sans certification de commercialisation ou d’absence de bugs.
