# Découverte paginée V0.33 — périmètre et garanties

## Source de vérité

Les pages TVmaze sont des **pages d'index**, pas un classement de tendances. La première page demandée est `https://api.tvmaze.com/shows?page=0`, puis `page=1`, etc. Un HTTP404 sur une page non initiale indique qu'aucune page suivante n'est disponible, sans effacer les pages lues. Le `page=0` initial avec réponse invalide reste une erreur visible.

TMDb/RAWG et Open Library disposent de paramètres de page propres et de limites annoncées. FreeToGame fournit ici un index borné de jeux gratuits et sa navigation est une **pagination locale**. Le connecteur Apple/iTunes permet la recherche par titre mais aucune prétendue liste de films populaires générique.

Les recherches textuelles, filtres sur favoris/masqués/ajoutés et tris sont appliqués aux **références déjà reçues**, et jamais présentés comme exhaustifs. Les critères de requête peuvent être enregistrés, mais l'état temporaire des pages consultées n'est pas une sauvegarde et ne reçoit aucun secret dans l'export.

## Sécurité et intégrité

- Les identités utilisent fournisseur, type de média et ID source ; jamais le titre seul pour fusionner deux fiches.
- Un ajout réutilise la fiche locale retirée si elle correspond exactement ; notes et historique sont conservés. Une suppression pendant le fetch provoque un refus plutôt qu'une réapparition tardive.
- Une réponse malformée, une page en erreur, un changement de catégorie ou de filtre et un changement de bibliothèque n'écrasent pas les anciennes pages déjà reçues.
- 60 cartes initiales, 100 pages et 5 000 références maximum par session de découverte. Les grands catalogues restent plus vastes ; l'interface ne prétend pas les avoir exhaustivement parcourus.
- Le mode public TVmaze ne fournit pas automatiquement des métadonnées françaises, des films, des livres ou des trophées ; la source est indiquée dans la fiche.

## Validations

Le moteur pur `web/discovery-core.js` possède des tests Node. Le parcours réel des scripts et du DOM (`tests/browser_v033.py`) utilise des pages TVmaze simulées et vérifie l'échec/reprise exact, filtres, doublons, ajout d'un titre retiré, navigation tardive et rendu 390/768/1360 pixels. Le modèle Kotlin `model/DiscoveryRules.kt` est exécuté sur JVM ; le repository/client non-UI passe la compilation de signatures. **Aucun réseau TVmaze réel, PWA installée, test Android sur appareil ni qualification commerciale ne sont prouvés.**
