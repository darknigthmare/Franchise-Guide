# Sources V0.28 — ISBN et édition retenue

Consultées le 7 octobre2026 via le navigateur de recherche, pas comme preuve des requêtes applicatives réelles :
- Open Library Search API, œuvres et éditions imbriquées : https://openlibrary.org/dev/docs/api/search
- Open Library usage public et limites : https://openlibrary.org/developers/api
- Agence internationale ISBN, structure et préfixes978/979 : https://www.isbn-international.org/fr/content/quest-ce-ce-que-lisbn/10

La recherche utilise un query ISBN canonique et des références imbriquées, puis relit explicitement la fiche de l’édition. La validation métier est volontairement conservatrice si ces données sont absentes. Aucun scanner ni garantie de couverture complète.

---

# Références V0.24 — 6 octobre 2026

- TMDb TV episode details : https://developer.themoviedb.org/reference/tv-episode-details
- TMDb TV episode credits : https://developer.themoviedb.org/reference/tv-episode-credits
- Aide SeriesGuide : https://www.seriesgui.de/help

Ces pages primaires documentent le détail d’un épisode, la langue et ses crédits spécifiques. L’implémentation demande les crédits par append_to_response et vérifie l’identité avant présentation. Les seuils2 Mio/5 minutes/20 relevés/60 cartes/2 000 caractères et les gardes de formulaire sont des décisions FranchiseGuide, pas des quotas TMDb annoncés.

Aucun accès réel avec clé utilisateur, audit commercial, publication magasin ou protocole gaming supplémentaire validés. Les références historiques ci-dessous restent historiques.

---

# Vérification ciblée V0.22 — état ignoré et suivi d’épisodes

Consultation de l’aide officielle SeriesGuide le 6 octobre 2026 :
- https://www.seriesgui.de/help/how-to/basics/buttons : distingue vu, collectionné et ignoré ; le statut ignoré n’est pas synchronisé avec Trakt.
- https://www.seriesgui.de/help/how-to/backup/sync : périmètre de synchronisation SeriesGuide/Trakt.

Cette vérification documente le choix de ne pas transformer « Ignorer » en suppression distante. Les règles de sélection, aperçu, sauvegarde locale et protection des dates sont l’implémentation indépendante de FranchiseGuide ; aucune exécution avec compte réel n’en découle. Les références plus anciennes ci-dessous sont conservées comme historique, pas comme nouvelle autorisation commerciale.

---

# Sources consultées pour V0.18 — 6 octobre2026

- SeriesGuide, Export or import your data using files : https://www.seriesgui.de/help/how-to/backup/backup
- Format JSON officiel : https://github.com/UweTrottmann/SeriesGuide/blob/main/docs/backup-json-schema.md
- MDN TextDecoder et lecture de fichiers : https://developer.mozilla.org/en-US/docs/Web/API/TextDecoder/fatal ; https://developer.mozilla.org/en-US/docs/Web/API/FileReader/readAsText

La documentation SeriesGuide montre les fichiers séparés, les champs minimaux et les dates de série ISO8601 complètes/épisodes en millisecondes. Elle avertit également des effets d’une synchronisation sur les données importées. L’aperçu, les budgets32/64Mio, la comparaison des ID locaux et la suspension des anciennes actions sont des choix FranchiseGuide, pas une promesse de comportement identique de SeriesGuide.

Aucun nouveau protocole fournisseur, acquisition de droit commercial, passage Play Store ou service SaaS public n’a été validé. Les références historiques suivantes ne sont pas présentées comme nouvellement vérifiées.

---

# Sources consultées pour V0.17 — 6 octobre 2026

- SeriesGuide, synchronisation : https://www.seriesgui.de/help/how-to/backup/sync
- SeriesGuide, boutons de suivi : https://www.seriesgui.de/help/how-to/basics/buttons
- SeriesGuide, actualisation : https://www.seriesgui.de/help/how-to/basics/update

Ces pages distinguent notamment le suivi envoyé à Trakt des états locaux (favoris, masqués, ignorés et listes locales selon la documentation). Les écrans de reprise et les plans locaux groupés sont une implémentation FranchiseGuide ; ni l’API ni une parité exhaustive SeriesGuide ne sont inférées d’une simple similarité d’usage.

Aucun nouveau protocole fournisseur ni nouvelle clause commerciale n’a été validé dans cette passe. Les règles de prudence des épisodes et les limites10 000/60/80 sont des choix locaux, pas des limites officielles SeriesGuide/Trakt.

---

# Références techniques — mise à jour du 6 octobre 2026

Sources officielles consultées pendant la V0.15 :
- Steam Web API overview : https://partner.steamgames.com/doc/webapi_overview
- RetroAchievements, Getting started : https://api-docs.retroachievements.org/getting-started.html
- SeriesGuide, boutons de suivi : https://www.seriesgui.de/help/how-to/basics/buttons
- SeriesGuide, synchronisation : https://www.seriesgui.de/help/how-to/backup/sync

Les délais locaux de 5 à 60 minutes, le lot de huit lectures et son intervalle de 30 secondes sont **des choix de l'application**, pas des quotas universels promis par ces fournisseurs. Retry-After renvoyé par le serveur prévaut dans les bornes documentées. Aucun achat de licence, accord fournisseur ou réexamen juridique exhaustif n'a été effectué.

## Références historiques conservées

Les sections suivantes retracent les choix et lectures antérieurs ; les conditions commerciales citées doivent être revalidées pour le canal de diffusion réel.

---

# Références vérifiées — 5 octobre2026

Références officielles consultées pour cette passe. Elles décrivent les services/conditions, pas la réussite d’une connexion de l’application.

- SeriesGuide : https://www.seriesgui.de/help ; https://www.seriesgui.de/help/how-to/basics/buttons
- TMDb données/images et usage commercial : https://developer.themoviedb.org/docs/faq ; https://www.themoviedb.org/api-terms-of-use
- RAWG documentation et conditions présentant une contradiction sur le gratuit commercial : https://rawg.io/apidocs ; https://rawg.io/tos_api (lecture directe finale en timeout ; résultats indexés officiels consultés également).
- Open Library usage API : https://openlibrary.org/developers/api
- Trakt : https://developer.trakt.tv/docs/api-use-policy ; https://developer.trakt.tv/docs/create-an-app ; https://forums.trakt.tv/t/what-are-the-rules-for-posting-comments/22155
- Android AGP8.10 / API36 / Gradle8.11.1 : https://developer.android.com/build/releases/agp-8-10-0-release-notes
- Empreinte Gradle officielle : https://gradle.org/release-checksums/
- Licence GPL et vente : https://www.gnu.org/licenses/gpl-faq.fr.html ; https://www.gnu.org/licenses/gpl-3.0.html
- CNIL applications mobiles : https://www.cnil.fr/fr/recommandations-applications-mobiles-modifiee

Une analyse juridique et contractuelle de la version destinée à être distribuée reste nécessaire. Aucun accord commercial, validation magasin, test API réel ou clé de service n’est inféré de ces documents.

## V0.11 — Steam et trophées
Documentation primaire : https://partner.steamgames.com/doc/webapi/ISteamUserStats et https://partner.steamgames.com/doc/webapi_overview (consultées le 5 octobre 2026). Le code ne copie pas le client Steam ; il utilise les définitions publiques d’API. Steam, PlayStation, PSNProfiles, Xbox, RetroAchievements, Epic et GOG restent les marques de leurs titulaires, sans affiliation annoncée. Aucune permission commerciale sur leurs données/images n’est présumée. Les exemples et images de QA sont fictifs et ne constituent pas des catalogues ni des relevés réels. Les images distantes éventuellement chargées à l’utilisation gardent leurs propres droits. Aucun thème, logo ou son propriétaire de ces plateformes n’est nouvellement inclus.

## Références consultées pour V0.16 (6 octobre2026)

- iCalendar RFC5545 : https://www.rfc-editor.org/rfc/rfc5545 — DATE, DTEND exclusif, CRLF et pliage75 octets UTF8.
- Trakt Sync : https://trakt.docs.apiary.io/reference/sync — événements historiques et `watched_at` UTC ; ne pas envoyer de doublons. L'application ne présume pas une déduplication serveur.
- Android NotificationManager : https://developer.android.com/reference/android/app/NotificationManager — paire tag/id et identités de notification.

Les références guident l'implémentation. Elles ne prouvent pas un test authentifié ou l'import effectif dans une application calendrier tierce.

## Références consultées pour V0.19 (6 octobre2026)

- Aide officielle SeriesGuide : https://www.seriesgui.de/help — référence de flux, pas revendication de clone exhaustif.
- TMDb collection details : https://developer.themoviedb.org/reference/collection-details — collections cinéma récupérées via le client existant, à distinguer d’un univers personnel.
- TMDb TV series details : https://developer.themoviedb.org/reference/tv-series-details — récupération des fiches/saisons via le client existant.

Aucun nouvel endpoint fournisseur ni contrat commercial ajouté par cette passe. Les données catalogue des nouveaux tests sont synthétiques ; leur existence ne prouve pas une recherche réelle ni l’exhaustivité d’une franchise.

## Passe V0.20

Sources primaires consultées pour le cadrage : aide SeriesGuide (`https://www.seriesgui.de/help`), IPlayerService Steam (`https://partner.steamgames.com/doc/webapi/IPlayerService`), documentation RetroAchievements (`https://api-docs.retroachievements.org/`). Les politiques de3/8 requêtes de métadonnées par passe, durées de cache et reprises sont des choix de FranchiseGuide, pas des quotas contractuels revendiqués. Aucun nouvel endpoint ni droit commercial n’est déduit de cette consultation. Les faits concernant l’implémentation sont vérifiables dans les sources et tests livrés, pas dans ces pages.

## V0.23 — nouvelles lectures de disponibilités (consultées le 6 octobre 2026)

- https://developer.themoviedb.org/reference/movie-watch-providers
- https://developer.themoviedb.org/reference/tv-series-watch-providers
- https://developer.themoviedb.org/reference/tv-season-watch-providers
- https://developer.themoviedb.org/reference/watch-providers-available-regions

Attribution JustWatch explicitement requise ; données streaming/location/achat par pays, sans tous les deep-links de lecture. Le programme utilise uniquement la sortie TMDb fournie et validée. Les bornes1/2Mio,20relevés/5min,60cartes et le délai429 sont des politiques d’application, pas des limites contractuelles revendiquées. Les tests utilisent des services nommés fictivement, pas de disponibilité réelle validée.

## Vérifications primaires pour V0.25

- Construction SeriesGuide et secret.properties côté développeur : https://github.com/UweTrottmann/SeriesGuide/blob/dev/CONTRIBUTING.md
- TVmaze API, endpoints publics, CORS, quotas et CC BY-SA : https://www.tvmaze.com/api
- FreeToGame, pas de clé, portée free-to-play et attribution : https://www.freetogame.com/api-doc
- Apple Search/Lookup, paramètres films, pays, limites : https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html
- Apple, conditions des assets promotionnels expliquant leur omission dans ce client : https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html
- Open Library, usage public humain et faible volume : https://openlibrary.org/developers/api
- TMDb, authentication application clé/Bearer : https://developer.themoviedb.org/docs/authentication-application

Pages relues pour cette passe. Les requêtes réelles des nouveaux catalogues dans l’environnement d’exécution ont séparément échoué en DNS. Lire la documentation en ligne ne constitue pas un test de ces APIs dans l’application.

## V0.26 — sources des détails publics

Documentation officielle relue pour cette implémentation : https://www.tvmaze.com/api . Routes utilisées : `/episodes/:id?embed=show`, `/episodes/:id/guestcast`, `/episodes/:id/guestcrew`, `/people/:id`, `/people/:id/castcredits?embed=show`, `/people/:id/crewcredits?embed=show`.

La fiche personne de cette version n’utilise pas `/people/:id/guestcastcredits` : les crédits affichés sont au niveau des séries, pas une liste exhaustive de toutes les apparitions invitées. L’attribution TVmaze / CC BY-SA demeure explicite. La consultation de la documentation n’est pas un test des appels applicatifs réels : le conteneur n’a pas pu résoudre l’API lors des essais.


## V0.27 : ouvrages et éditions sans clé

Consultation officielle le7octobre2026 :
- https://openlibrary.org/dev/docs/api/search — œuvres par défaut, identifiants courts/chemins, champ `editions`, préférence de langue distincte d’un filtre.
- https://openlibrary.org/developers/api — découverte humaine faible volume, pas backend de collecte massive, limites publiques et recommandation de cache.

La route d’éditions par œuvre `/works/<ID>/editions.json?limit=30&offset=N` est utilisée avec pagination et contrôles explicites. L’appel live tenté depuis le conteneur a échoué en résolution DNS ; les tests utilisent des fixtures. Aucun emprunt ni contenu intégral consulté.

## V0.29 — recherche catalogue

Documentation primaire relue dans cette session (7 octobre2026) :
- https://www.tvmaze.com/api — recherche publique, identités d’épisodes et personnes, limites d’usage.
- https://openlibrary.org/dev/docs/api/search — page/limit et résultats de recherche.
- https://openlibrary.org/developers/api — accès public humain, faible volume, pas collecte massive.

Les contrats de TMDb/RAWG/Trakt existants ont été contrôlés dans les parseurs et fixtures. Le nombre de lignes locales60, le plafond1000candidats/20000comparaisons et les gardes de génération sont des choix de cette application, pas des règles imposées par un fournisseur. La consultation documentaire ne constitue pas une authentification ou un test d’API live.

## Références V0.35 — SeriesGuide et check-ins

- SeriesGuide — fonctionnement des check-ins : https://www.seriesgui.de/help/how-to/trakt/checkins
- SeriesGuide — actions check-in/vu/collection/skip : https://www.seriesgui.de/help/how-to/basics/buttons
- SeriesGuide — Cloud ou Trakt, données synchronisées : https://www.seriesgui.de/help/how-to/backup/sync
- SeriesGuide — Auto Backup et copie hors appareil recommandée : https://www.seriesgui.de/help/how-to/backup/autobackup
- SeriesGuide — export/import JSON : https://www.seriesgui.de/help/how-to/backup/backup

Consultées le11 octobre2026. Elles décrivent le produit de référence ; elles ne prouvent pas les comptes, APIs ou tâches Android de FranchiseGuide.
