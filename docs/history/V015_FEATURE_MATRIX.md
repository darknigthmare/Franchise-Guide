# Fonctions et limites — FranchiseGuide 0.15

Livraison du 6 octobre2026. Web et PC partagent le même moteur canonique ; Android est fourni en sources. « Intégré » n’est ni une validation sur appareil, ni une authentification réelle réussie.

| Domaine | Web / PC | Android |
|---|---|---|
| Nouveau centre gaming | Filtres, échéances, profils, lot complet, pause globale/individuelle, reprise explicite et erreurs séparées ;57+6 nouveaux contrôles Chromium | Non exposé nativement ; sources existantes conservées |
| Délais gaming | Persistants, par liste et fournisseur, contrôle avant chaque lecture, respect des quotas dans les clics manuels | Pas de nouveau connecteur Steam/Retro natif |
| Session Steam tardive | Garde de génération contre déconnexion/réponse plus récente, erreur sessionStorage traitée | Authentification Steam native absente |
| Séries / films | Recherche paginée, fiches, images catalogue, saisons, langues, états, Trakt et imports | Sources, build SDK/Compose et appareils non validés |
| Jeux / livres et franchises | Données locales, progression, catalogues, liens et imports | Sources et transport existants ; pas de cloud général |
| Steam | OpenID, bibliothèque et succès sur serveur personnel | Relevés portables ; réseau/auth natifs absents |
| RetroAchievements | Profil public pseudo/ULID, catalogue/progression et normal/Hardcore | Profils et données dans le codec source ; réseau natif absent |
| PSNProfiles/Xbox/Epic/GOG | Profils/liens et import JSON neutre, sans nouvelle connexion distante | Références importées uniquement |
| Trakt | File séparée, OAuth/reprise existants ; accessible depuis le centre sans mélange | Source/worker, aucun compte réel exercé |
| Données | Corrections de fusion V0.14 conservées, champs internes non importés, contrôle partagé avec état local | Règles et codec existants ; vrais codecs JSON non réexécutés ici |
| Affichage stockage | Bandeau d’erreur replacé dans le shell, ne réorganise plus la grille PC ; nouveau panneau clair/sombre | Pas de validation visuelle Android |
| Sauvegarde | PortableV8, instantanés, récupération brute/IndexedDB ; le contrôle runtime reste local | Source, pas de validation stockage appareil |
| PWA | Fichiers/cache et règles, tests partiels contrôlés | Sans objet pour l’installation Web |
| Distribution | Vrai JAR Java17 + Web personnel Node22 | Pas d’APK/AAB, pas de build Android complet |

## Validation et écarts restants

Les nouvelles fonctionnalités sont exercées dans Chromium avec scripts/DOM/CSS réels et services, images, horloge et stockage simulés. Les suites historiques supportées sont relancées. Les suites jsdom/fake-indexeddb ne sont pas comptées car les dépendances verrouillées manquent ici ; aucune conclusion de suppression IndexedDB réelle n’est tirée du navigateur à origine opaque. Voir `QA_REPORT.md` pour les exclusions et comptes précis.

Toujours ouverts : Android SDK/Compose/appareils/notifications/widgets ; vrais comptes et tours complets OAuth ; PWA installée, origine persistante, hors-ligne et multi-onglets réels ; sessions graphiques Windows/macOS ; parité sociale/réglages exhaustive SeriesGuide ; cloud général jeux/livres/franchises ; backend public multiutilisateur ; audit indépendant, droits/catalogues/distribution commerciale. **Version de développement, pas de certification sans bug ni de produit public complet.**
