# Services automatiques — utilisateur et éditeur, V0.25

## 1. Le parcours visé

L’utilisateur lance l’application, recherche une œuvre, l’ajoute et clique sur le bouton du compte à connecter. Il ne doit pas créer une application développeur pour une utilisation ordinaire.

SeriesGuide applique ce principe en configurant les identifiants de ses fournisseurs lors de la construction/distribution de son application. Sa documentation de contribution demande bien au développeur ses propres valeurs dans `secret.properties` ; elle ne donne pas le droit de recopier celles de l’application publiée. Référence : https://github.com/UweTrottmann/SeriesGuide/blob/dev/CONTRIBUTING.md

FranchiseGuide V0.25 sépare donc **catalogues publics sans clé**, **accès gérés par l’éditeur**, et **autorisation personnelle de compte**. Aucune clé de SeriesGuide n’est reprise, aucun secret n’est inventé et aucun compte tiers n’est créé dans cette livraison.

## 2. Sources utilisables sans clé

| Type | Fournisseur | Données intégrées | Limites explicites |
|---|---|---|---|
| Séries | TVmaze public | Fiche, genres/statut/score, image si fournie, saisons, épisodes, distribution/portraits | Pas de français garanti ; pas de TMDb ID artificiel ; détails invités/filmographies TMDb et Trakt automapping non ajoutés |
| Films | Apple iTunes Search / Lookup | Titre, synopsis, genres, durée, date disponible, lien officiel | Pas de copie d’artwork ou previews ; pas de casting complet ; catalogue du pays, pas base exhaustive de films |
| Jeux | FreeToGame | Jeux free-to-play, genre, plateforme, studio/éditeur, synopsis/images si fournis | Jeux payants et catalogue console complet non couverts ; pas d’estimation personnelle de progression |
| Livres | Open Library existant | Recherche, œuvres, informations et couverture disponibles | Lecture publique à faible volume ; pas de backend de collecte massive |

Les requêtes sont déclenchées par les parcours catalogue existants, avec cache frontend borné et restrictions réseau. Une source manquante n’est jamais remplacée par des fiches générées. Le serveur ne possède pas une copie locale du catalogue.

Web/PC détecte le serveur par `GET /api/services/config` avec l’en-tête de l’application. Cette réponse ne contient que les noms de services et des booléens. Si aucun serveur automatique n’est détecté, TVmaze et Open Library peuvent utiliser leurs APIs directes ; Apple/FreeToGame dépendent du CORS de l’hôte et peuvent nécessiter le serveur personnel fourni.

## 3. Accès complets : opération unique côté éditeur

**Cette section s’adresse à l’opérateur/éditeur, pas aux utilisateurs finaux.** L’archive n’est reliée à aucun backend public déjà activé. Les API TMDb/RAWG, l’application OAuth Trakt, Steam et RetroAchievements conservent leurs exigences d’accès. Il faut enregistrer sa propre application/service auprès des fournisseurs et disposer des droits d’usage appropriés.

Le processus Java ou Node lit les variables d’environnement suivantes au démarrage :

| Variable | Rôle |
|---|---|
| `FG_TMDB_API_KEY` | Clé application TMDb v3 |
| `FG_TMDB_READ_TOKEN` | Alternative : jeton de lecture TMDb Bearer, prioritaire sur la clé v3 |
| `FG_RAWG_API_KEY` | Accès éditeur RAWG pour le catalogue de jeux plus large |
| `FG_TRAKT_CLIENT_ID` et `FG_TRAKT_CLIENT_SECRET` | Paire d’identifiants de l’application Trakt ; les deux sont nécessaires au flux appareil implémenté |
| `FG_STEAM_API_KEY` | Accès serveur du connecteur Steam existant |
| `FG_RETRO_API_KEY` | Accès serveur du connecteur RetroAchievements existant |

Définir ces variables via le mécanisme de secrets de l’environnement, puis redémarrer le serveur. Les scripts ne lisent pas automatiquement un fichier `.env`. Aucun exemple de valeur n’est une clé réelle. Ne jamais copier un secret dans `web/`, dans un dépôt public, dans un rapport QA ou dans les réglages destinés aux utilisateurs. Ne pas modifier le navigateur pour lui injecter la valeur.

Une fois les variables légitimes installées, **Web/PC s’adapte sans formulaire de clé** : TMDb remplace la source publique pour les nouvelles recherches séries/films ; RAWG couvre les nouveaux jeux ; le bouton Trakt emploie les identifiants de l’application côté serveur. L’utilisateur n’autorise que son propre compte.

Les anciens identifiants personnels configurés restent prioritaires pour compatibilité ; ils sont dans la section avancée. Une paire Trakt personnelle incomplète est refusée, pas mélangée à la paire éditeur. Si l’éditeur veut imposer le parcours commun, il distribue une bibliothèque sans overrides personnels.

## 4. Ce que le proxy fait et ne fait pas

Les deux serveurs personnalisés implémentent les mêmes routes autorisées : `/api/services/tvmaze/…`, `/itunes/…`, `/freetogame/…`, `/tmdb/…`, `/rawg/…`, `/trakt/…`, `/trakt-auth/…`. Ce n’est pas une route acceptant une URL libre.

- L’hôte et l’origine doivent correspondre au loopback personnel, avec un en-tête d’application. Une requête cross-site et un chemin/méthode/paramètre non autorisés sont refusés.
- Le serveur n’accepte pas de redirection fournisseur. Corps bornés, UTF‑8/JSON stricts, timeout, concurrence limitée à quatre lectures, fenêtres de taux et reprise429 sont contrôlés. Apple est limité localement à18 lectures/minute ; les autres sources de cette passerelle à90/minute. Les contraintes fournisseur peuvent être plus restrictives.
- Le serveur injecte uniquement les identifiants d’application nécessaires. Aucun cookie de navigateur n’est relayé au fournisseur.
- La réponse de capacité et les erreurs n’exposent pas les secrets. Les accès personnels d’utilisateur Trakt, nécessaires au flux existant, restent traités par le stockage de session de l’application ; ce nouveau proxy n’est pas un coffre-fort ou une migration complète des jetons utilisateur au serveur.
- Le proxy n’ajoute pas de cache caché au-dessus du cache frontend : une actualisation explicite n’est pas servie secrètement depuis un ancien relevé serveur. Aucun POST n’est relancé automatiquement.

**Ne pas publier ce serveur loopback comme backend Internet multitenant.** L’en-tête client et les contrôles Host/Origin ne remplacent pas une authentification d’éditeur, des comptes applicatifs, des quotas par utilisateur et une infrastructure de secrets. Cette production hébergée est un chantier distinct, non livré et non déployé ici.

## 5. Données anciennes, identité et synchronisation

L’ouverture de la V0.25 ne transforme pas les fiches TMDb/RAWG déjà ajoutées en fiches TVmaze/FreeToGame. Leur historique et fournisseur sont conservés. Une tentative d’ajout identifiant une œuvre déjà ajoutée n’écrase pas sa source pour utiliser une autre API moins riche.

Les épisodes TVmaze possèdent leurs propres IDs ; un entier TVmaze n’est pas utilisé comme ID TMDb. Ils peuvent être suivis localement sans compte. La V0.25 ne fournit pas de rapprochement complet de tous ces épisodes avec Trakt. Les crédits/personnes restent liés à leur fournisseur. Une note TVmaze ne devient pas une note TMDb et une liste de jeux gratuits n’est pas présentée comme l’inventaire Steam possédé.

## 6. Conditions, attribution et portée

- TVmaze : https://www.tvmaze.com/api — API publique, CORS, licence CC BY-SA. Les liens de source restent affichés ; respecter l’attribution et ShareAlike lors d’une redistribution.
- FreeToGame : https://www.freetogame.com/api-doc — pas de compte/clé, free-to-play uniquement, attribution avec lien actif et limite de requêtes à respecter.
- Apple : https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/Searching.html — recherche/lookup publics documentés et limites ; conditions promotionnelles : https://developer.apple.com/library/archive/documentation/AudioVideo/Conceptual/iTuneSearchAPI/index.html. Cette implémentation ne copie pas l’artwork ni les previews et conserve le lien de boutique.
- Open Library : https://openlibrary.org/developers/api — découverte humaine faible volume, cache et identification de l’application ; pas de collecte massive ou infrastructure commerciale très fréquentée. Les anciens endpoints existants sont conservés.
- TMDb : https://developer.themoviedb.org/docs/authentication-application — accès application requis, même si l’utilisateur ne voit pas sa clé.

L’accessibilité technique d’un endpoint n’est pas une autorisation commerciale générale sur toutes les données/images. Ces intégrations ne constituent pas une certification de distribution, d’attribution exhaustive ou de monétisation.

## 7. Preuves et limites

Les scénarios de cette version emploient les vrais scripts/DOM, mais des APIs, images et accès éditeur contrôlés. Les passerelles sont vérifiées en HTTP local avec des identifiants fictifs. Les appels anonymes réels ont été tentés et bloqués par DNS ; aucun catalogue ou compte réel n’a été authentifié dans cette QA.

La source Android possède les clients publics et le panneau avancé, pas le transport natif de la passerelle éditeur Web/PC. Aucun APK ou build Compose/SDK. La mise à disposition d’une application préconfigurée à tous ses utilisateurs nécessite encore un opérateur disposant des accès éditeur légitimes et une distribution testée sur les plateformes cibles.
