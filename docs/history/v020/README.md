# FranchiseGuide Suite 0.20.0

Poursuite réelle de la **Suite V0.19 disponible**, pas une reconstruction depuis la description : SHA-256 de l’archive de base `6ef63432d3b30203e3eb92c73fe8c8369e1eea0522f8a9d0a1bef32207f969a1`.

La Suite contient les sources Android, le moteur Web/PWA, le programme PC Java 17 compilé et les serveurs personnels Steam/RetroAchievements. Aucun identifiant fournisseur, APK/AAB, EXE natif, déploiement public ou service cloud général n’est fourni.

## Cette version

**Progression des jeux et livres, sans perte des anciennes fins.** Le parcours actuel est maintenant indépendant des lectures/parties déjà terminées. Diminuer la progression, mettre en pause, abandonner ou recommencer garde les compteurs et dates historiques. Une nouvelle fin enregistre une date ; sauvegarder deux fois le même état terminé n’ajoute pas un doublon. Unités personnelles, édition/format, plateforme et minutes réellement déclarées sont conservés pendant une actualisation catalogue.

**Statistiques et historique.** Trois familles distinctes : bibliothèque actuelle, lectures datées et relevés de plateformes. Les pages du parcours courant ne sont pas multipliées par le nombre de lectures. Les chapitres et pourcentages ne sont pas additionnés aux pages. Les heures Steam restent séparées par profil, sans être prises pour des fins de jeu ou du temps vidéo. Normal, Hardcore et cases personnelles restent distincts. Les dates manquantes et les durées inconnues ne sont pas inventées. Les genres, franchises, favoris, collections, lectures supplémentaires et séquences de jours connus restent consultables.

**Filtres et exports Web/PC.** Catégorie, visibilité, favoris, spéciaux et période ; recherche dans tout l’historique connu ; rendu initial de 100 événements. Export JSON de bilan sans titres ni libellés de profil, ou historique détaillé JSON/CSV avec titres et dates. Les exports détaillés sont limités à 10 000 événements, sans troncature silencieuse. Ils ne remplacent pas une sauvegarde.

**Entretien catalogue Web/PC.** État des fiches TMDb/RAWG/Open Library, clés manquantes, dates de lecture et délais de reprise. Un lot manuel lit jusqu’à 8 fiches ; une passe automatique jusqu’à 3. Les erreurs attendent de 5 à 60 minutes, un HTTP 429 impose aussi un délai aux autres fiches du même fournisseur. L’annulation arrête le lot ; une réponse obsolète n’est pas appliquée. Une affiche absente n’entraîne plus une boucle de réactualisations si la fiche est par ailleurs complète. Les titres manuels, Steam ou RetroAchievements sans identifiant catalogue compatible ne sont pas rapprochés par leur nom.

**Intégrité.** Le choix de fusion conserve un parcours explicite et ses dates. Des états de parcours, unités ou valeurs personnelles contradictoires demandent une résolution au lieu de choisir silencieusement. Les épisodes orphelins ayant une note privée restent protégés dans les sources Android.

## Compatibilité

Le format portable passe au **schéma V9** pour protéger les nouveaux champs. V0.20 lit les formats V1 à V8 ; les anciennes versions ne doivent pas réenregistrer des données après migration. Exporter avant la mise à jour et fermer les anciens onglets/lanceurs. Les clés locales restent hors export. Les délais du centre catalogue, comme les autres contrôles d’exécution internes, ne sont pas transférés avec le JSON portable.

## Utiliser

Lire `START_HERE.md`. PC : Java 17+, puis `desktop/FranchiseGuide-PC.bat` ou `bash desktop/FranchiseGuide-PC.sh`. Web personnel : Node 22+, `node server/start.cjs`. Le serveur Web de production n’utilise aucune dépendance npm ; les paquets npm servent aux tests supplémentaires.

## Limites vérifiables

Le nouveau centre catalogue est **Web/PC**. Android reçoit les règles de progression/statistiques, les nouveaux panneaux et codecs, et une correction de conservation des notes ; ses sources ne sont pas un APK validé. Ses filtres/export de bilan et son entretien catalogue ne sont pas tous identiques au Web : voir `docs/FEATURE_MATRIX.md`.

La QA exécutée distingue règles Node/JVM, DOM Chromium avec fixtures, HTTP réel des serveurs et signatures de compilation non-UI. Elle ne prouve pas un accès aux comptes réels, une origine PWA installée, la persistance entre vrais onglets ou un build Android SDK. Les tests bloqués par des dépendances manquantes sont indiqués dans `docs/QA_REPORT.md`, sans réutiliser leurs anciens résultats.

Les connexions, agendas, imports vérifiés, gestion des univers, reprise et organisation des versions précédentes restent présents. PSNProfiles/Xbox/Epic/GOG ne reçoivent pas de nouveau connecteur automatique. Pas de certification commerciale ni de garantie « sans bug ».
