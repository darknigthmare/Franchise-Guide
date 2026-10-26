# Attributions et services tiers — V0.23 (notes historiques conservées)

FranchiseGuide est indépendant et n’est affilié ni approuvé par SeriesGuide, Plex, TMDb, RAWG, Open Library ou Trakt. LICENSE contient la GPL3 de cette livraison. Les sources correspondantes sont incluses.

SeriesGuide, Uwe Trottmann : workflows de suivi et format public d’import, GPL-3.0-or-later. https://www.seriesgui.de/ — https://github.com/UweTrottmann/SeriesGuide

Plex : référence de principe pour une fiche visuelle et un signal sonore ; aucun logo, son/jingle/thème ou code Plex redistribué. detail_signature.wav est la création locale FranchiseGuide héritée des versions précédentes.

TMDb : données et images obtenues via clé configurée. « This product uses the TMDB API but is not endorsed or certified by TMDB. » Référence : https://developer.themoviedb.org/docs/faq . **Le logo officiel approuvé et l’usage commercial ne sont pas validés pour une diffusion publique.** Ne pas traiter la présence de ce texte comme la clôture du dossier d’attribution.

RAWG : jeux et liens vers la source. https://rawg.io/apidocs — https://rawg.io/tos_api . Conditions commerciales à clarifier par écrit, notamment la contradiction entre plan gratuit et conditions. Vérifier les backlinks dans chaque écran exposant les données.

Open Library : métadonnées et couvertures de livres. https://openlibrary.org/developers/api . Respect des limites/caches et des droits particuliers des éléments à vérifier pour l’offre retenue.

Trakt : compte, suivi, notes publiques et avis après configuration. https://developer.trakt.tv/docs/api-use-policy . Identifiants/jetons et brouillons ne figurent pas dans l’export portable. Pas d’authentification ou publication réelle validée dans cette QA.

JustWatch : disponibilités reçues via TMDb, attribution conservée dans les sections disponibles par saison. Les affiches/portraits distants ne sont pas redistribués dans cette livraison ; les images de tests sont synthétiques et ne représentent pas de personnes réelles.

La liste des dépendances déclarées figure dans DEPENDENCIES_DECLARED.json. Elle ne remplace pas l’inventaire résolu, les licences transitives ou un SBOM d’APK qui n’a pas été construit.

## V0.11 — Steam et trophées
Documentation primaire : https://partner.steamgames.com/doc/webapi/ISteamUserStats et https://partner.steamgames.com/doc/webapi_overview (consultées le 5 octobre 2026). Le code ne copie pas le client Steam ; il utilise les définitions publiques d’API. Steam, PlayStation, PSNProfiles, Xbox, RetroAchievements, Epic et GOG restent les marques de leurs titulaires, sans affiliation annoncée. Aucune permission commerciale sur leurs données/images n’est présumée. Les exemples et images de QA sont fictifs et ne constituent pas des catalogues ni des relevés réels. Les images distantes éventuellement chargées à l’utilisation gardent leurs propres droits. Aucun thème, logo ou son propriétaire de ces plateformes n’est nouvellement inclus.

## V0.23 — JustWatch via TMDb

Le nouveau panneau Où regarder attribue explicitement les disponibilités à JustWatch via TMDb, même dans les états sans données. Les anciennes listes aplaties sont marquées comme des relevés historiques sans pays/date. Les logos proviennent seulement des chemins d’image TMDb reçus ; aucune affiche ou disponibilité réelle n’est préremplie. La sortie est la page TMDb fournie pour le bon titre/pays, pas un lien de lecture direct fabriqué. L’attribution nécessaire est documentée ici : https://developer.themoviedb.org/reference/movie-watch-providers et https://developer.themoviedb.org/reference/tv-season-watch-providers (consultées le 6 octobre 2026). Ces changements n’accordent pas de licence commerciale pour l’ensemble des images ou catalogues.

## V0.25 — lecture publique sans clé utilisateur

**TVmaze**, https://www.tvmaze.com/api : API publique et données sous CC BY-SA. Les fiches, résultats et distribution portent leur source TVmaze et un lien. Respecter la disposition ShareAlike et les crédits si les données sont redistribuées. Aucune donnée réelle ou photographie fournisseur n’a été téléchargée pour constituer les fixtures livrées.

**FreeToGame**, https://www.freetogame.com/api-doc : catalogue free-to-play, sans authentification, attribution requise avec lien actif. La fiche renvoie à sa source. Cette API ne couvre pas tous les jeux payants. Pas de remplacement des succès/temps Steam ou Retro par des métadonnées FreeToGame.

**Apple iTunes Search API**, https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html et conditions https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html : seules les métadonnées films limitées et le lien officiel sont intégrés. L’artwork et les aperçus promotionnels ne sont ni copiés, ni mis en cache, ni synchronisés par ce client. Pas de badge ou licence promotionnelle inventé.

**Open Library**, https://openlibrary.org/developers/api : API destinée à la découverte humaine à faible volume, pas une infrastructure massive commerciale ou une source de scraping. Respecter les quotas et l’identification de l’application pour un usage régulier. L’intégration de recherche/couvertures existante est conservée sans prétention de droit général sur chaque illustration.

Les API publiques peuvent changer ou devenir indisponibles. Les services TMDb/RAWG/Trakt continuent à exiger une application/autorisation légitime même lorsque les clés sont injectées par un serveur et non affichées aux utilisateurs. Ce changement de parcours ne constitue pas une validation des licences ni une activation de compte réel.
